package com.empathie.energyaccounting.update

import android.content.ActivityNotFoundException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.BuildConfig
import com.empathie.energyaccounting.data.settings.AppSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the update dialog needs to render. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val downloaded: Long, val total: Long) : UpdateUiState
    data class Verifying(val info: UpdateInfo) : UpdateUiState
    data class PermissionRequired(val info: UpdateInfo) : UpdateUiState
    data class Installing(val info: UpdateInfo) : UpdateUiState
    data class Error(
        val info: UpdateInfo?,
        val error: VerificationError?,
        val kind: UpdateError,
    ) : UpdateUiState
}

/** Why an update attempt failed, for the error copy the dialog shows. */
enum class UpdateError { NETWORK, STORAGE, INSTALLER, UNKNOWN }

/** The result line under "Check now" in the Updates card. */
sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data object UpToDate : UpdateCheckStatus
    data object Failed : UpdateCheckStatus
}

/**
 * Drives the update check, download, verification and install flow.
 *
 * The ViewModel is deliberately UI-free: it owns the state machine and delegates every network,
 * verification and install action to its collaborators, which keeps the whole flow testable
 * without an Activity. A [Job] is kept for the download so [cancelDownload] can stop it and its
 * coroutine cleanup can remove the partially written file.
 */
class UpdateViewModel(
    private val source: ReleaseSource,
    private val settings: AppSettings,
    private val verifier: ApkVerifier,
    private val installer: ApkInstaller,
    private val cacheDir: File,
    private val currentVersion: SemanticVersion,
    private val currentVersionName: String,
    private val isReleaseBuild: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val updatesDir = File(cacheDir, UPDATES_DIRECTORY)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _checkStatus = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)
    val checkStatus: StateFlow<UpdateCheckStatus> = _checkStatus.asStateFlow()

    private val _checksEnabled = MutableStateFlow(settings.updateChecksEnabled())
    val checksEnabled: StateFlow<Boolean> = _checksEnabled.asStateFlow()

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            updatesDir.mkdirs()
            cleanUpdatesDirectory(removePartFiles = true)
        }
    }

    /**
     * Runs the automatic check on launch or resume.
     *
     * Nothing happens in a debug build, when automatic checks are off, while a dialog is already
     * showing, or inside the 24-hour throttle. The one exception is an expired snooze: the first
     * launch after "Later" runs out must bring the prompt back even if a check ran recently.
     */
    fun checkOnLaunch() {
        if (!isReleaseBuild) return
        if (!_checksEnabled.value) return
        if (_state.value !is UpdateUiState.Idle) return
        if (_checkStatus.value == UpdateCheckStatus.Checking) return

        val snoozeJustExpired = hasSnoozeJustExpired()
        val throttleAllows = UpdatePolicy.shouldCheck(
            now = now(),
            lastCheck = settings.lastUpdateCheckEpochMillis(),
            enabled = true,
        )
        if (!snoozeJustExpired && !throttleAllows) return

        check(manual = false)
    }

    /** The "Check now" button: always checks, even inside the 24-hour throttle. */
    fun checkNow() {
        if (_checkStatus.value == UpdateCheckStatus.Checking) return
        if (_state.value.isPromptShowing() || _state.value.isDownloadInProgress()) return
        check(manual = true)
    }

    private fun check(manual: Boolean) {
        _checkStatus.value = UpdateCheckStatus.Checking
        viewModelScope.launch {
            try {
                val info = source.latestRelease()
                // Only a successful response (including "no releases yet") resets the throttle.
                settings.setLastUpdateCheckEpochMillis(now())
                when {
                    info == null || info.version <= currentVersion -> {
                        _checkStatus.value = UpdateCheckStatus.UpToDate
                        _state.value = UpdateUiState.Idle
                    }

                    !manual && UpdatePolicy.isSnoozed(
                        version = info.version.toString(),
                        snoozedVersion = settings.snoozedUpdateVersion(),
                        snoozedUntil = settings.snoozedUpdateUntilEpochMillis(),
                        now = now(),
                    ) -> {
                        _checkStatus.value = UpdateCheckStatus.Idle
                        _state.value = UpdateUiState.Idle
                    }

                    else -> {
                        _checkStatus.value = UpdateCheckStatus.Idle
                        _state.value = UpdateUiState.Available(info)
                    }
                }
            } catch (e: CancellationException) {
                _checkStatus.value = UpdateCheckStatus.Idle
                throw e
            } catch (_: Exception) {
                // Never a dialog for a failed check: only the card's result line changes.
                _checkStatus.value = UpdateCheckStatus.Failed
                _state.value = UpdateUiState.Idle
            }
        }
    }

    /** "Later": hide this version for a day, then dismiss. */
    fun onLater() {
        val info = _state.value.infoOrNull()
        downloadJob?.cancel()
        downloadJob = null
        if (info != null) {
            settings.snoozeUpdate(
                version = info.version.toString(),
                untilEpochMillis = now() + UpdatePolicy.SNOOZE_INTERVAL_MS,
            )
        }
        _state.value = UpdateUiState.Idle
    }

    /** "Update": start the download, unless installs are not allowed yet or the URL is a preview. */
    fun onUpdate() {
        if (_state.value.isDownloadInProgress()) return
        val info = _state.value.infoOrNull() ?: return

        if (info.apkUrl.isBlank()) {
            _state.value = UpdateUiState.Error(info, error = null, kind = UpdateError.UNKNOWN)
            return
        }
        if (!installer.canInstall()) {
            _state.value = UpdateUiState.PermissionRequired(info)
            return
        }
        startDownload(info)
    }

    /** Cancel button during a download: stop the job, let its cleanup delete the `.part`, go idle. */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        if (_state.value is UpdateUiState.Downloading || _state.value is UpdateUiState.Verifying) {
            _state.value = UpdateUiState.Idle
        }
    }

    /** "Try again" after an error: repeat the failed install, or check again if there is no info. */
    fun retry() {
        val failed = _state.value as? UpdateUiState.Error
        if (failed?.info != null) {
            onUpdate()
        } else {
            checkNow()
        }
    }

    /** Called on every resume: continue after the install permission, otherwise run the throttle. */
    fun onResume() {
        val current = _state.value
        if (current is UpdateUiState.PermissionRequired) {
            if (installer.canInstall()) {
                startDownload(current.info)
            }
            return
        }
        checkOnLaunch()
    }

    /** Turn automatic checks on or off; turning them off also closes and cancels everything. */
    fun setChecksEnabled(enabled: Boolean) {
        settings.setUpdateChecksEnabled(enabled)
        _checksEnabled.value = enabled
        if (!enabled) {
            downloadJob?.cancel()
            downloadJob = null
            _state.value = UpdateUiState.Idle
        }
    }

    /** Opens the system screen that allows installs from this app. */
    fun openInstallSettings() {
        installer.openInstallPermissionSettings()
    }

    /** Developer Mode preview: fabricated data, and a blank URL so it can never download. */
    fun showDebugDialog() {
        val next = SemanticVersion(
            major = currentVersion.major,
            minor = currentVersion.minor,
            patch = currentVersion.patch + 1,
        )
        _state.value = UpdateUiState.Available(
            UpdateInfo(
                version = next,
                tagName = "v$next",
                releaseName = "Empathie Energy Accounting v$next (preview)",
                releaseNotes = "Made-up release notes for layout review from v$currentVersionName.\n\n" +
                    "- A calmer insights breakdown\n" +
                    "- Quieter reminder scheduling\n" +
                    "- Bug fixes",
                releaseUrl = "https://github.com/iseppe/energy-accounting/releases",
                apkUrl = "",
                apkAssetName = "empathie-energy-accounting-v$next.apk",
                apkSizeBytes = 0L,
                apkSha256 = "",
            ),
        )
    }

    private fun startDownload(info: UpdateInfo) {
        if (downloadJob?.isActive == true) return
        updatesDir.mkdirs()
        if (updatesDir.usableSpace < info.apkSizeBytes * 2) {
            _state.value = UpdateUiState.Error(info, error = null, kind = UpdateError.STORAGE)
            return
        }

        cleanUpdatesDirectory(removePartFiles = true)
        val part = File(updatesDir, "${info.apkAssetName}.part")
        val target = File(updatesDir, info.apkAssetName)
        _state.value = UpdateUiState.Downloading(info, downloaded = 0L, total = info.apkSizeBytes)
        downloadJob = viewModelScope.launch {
            download(info, part, target)
        }
    }

    private suspend fun download(info: UpdateInfo, part: File, target: File) {
        try {
            source.downloadApk(info, part) { downloaded, total ->
                _state.value = UpdateUiState.Downloading(info, downloaded, total)
            }
            target.delete()
            if (!part.renameTo(target)) {
                throw IOException("Could not finalize ${info.apkAssetName}")
            }
            _state.value = UpdateUiState.Verifying(info)

            when (val result = verifier.verify(target, info.apkSha256, BuildConfig.APPLICATION_ID)) {
                is VerificationResult.Invalid -> {
                    target.delete()
                    _state.value = UpdateUiState.Error(info, result.error, UpdateError.UNKNOWN)
                }

                VerificationResult.Valid -> {
                    // Snooze before installing: a cancelled system installer must not re-prompt at once.
                    settings.snoozeUpdate(
                        version = info.version.toString(),
                        untilEpochMillis = now() + UpdatePolicy.SNOOZE_INTERVAL_MS,
                    )
                    _state.value = UpdateUiState.Installing(info)
                    try {
                        installer.install(target)
                    } catch (_: Exception) {
                        _state.value = UpdateUiState.Error(info, error = null, kind = UpdateError.INSTALLER)
                        return
                    }
                    _state.value = UpdateUiState.Idle
                }
            }
        } catch (e: CancellationException) {
            part.delete()
            _state.value = UpdateUiState.Idle
            throw e
        } catch (_: ActivityNotFoundException) {
            part.delete()
            _state.value = UpdateUiState.Error(info, error = null, kind = UpdateError.INSTALLER)
        } catch (_: IOException) {
            part.delete()
            _state.value = UpdateUiState.Error(info, error = null, kind = UpdateError.NETWORK)
        } catch (_: Exception) {
            part.delete()
            _state.value = UpdateUiState.Error(info, error = null, kind = UpdateError.UNKNOWN)
        }
    }

    private fun hasSnoozeJustExpired(): Boolean {
        val snoozedVersion = settings.snoozedUpdateVersion()
        if (snoozedVersion.isNullOrBlank()) return false
        val snoozedUntil = settings.snoozedUpdateUntilEpochMillis()
        if (snoozedUntil <= 0) return false
        val current = now()
        return snoozedUntil <= current && settings.lastUpdateCheckEpochMillis() < snoozedUntil
    }

    private fun cleanUpdatesDirectory(removePartFiles: Boolean) {
        val cutoff = now() - UpdatePolicy.CHECK_INTERVAL_MS
        updatesDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val isPart = file.name.endsWith(PART_SUFFIX)
            if ((removePartFiles && isPart) || file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun UpdateUiState.infoOrNull(): UpdateInfo? = when (this) {
        is UpdateUiState.Available -> info
        is UpdateUiState.Downloading -> info
        is UpdateUiState.Verifying -> info
        is UpdateUiState.PermissionRequired -> info
        is UpdateUiState.Installing -> info
        is UpdateUiState.Error -> info
        UpdateUiState.Idle, UpdateUiState.Checking -> null
    }

    private fun UpdateUiState.isDownloadInProgress(): Boolean =
        this is UpdateUiState.Downloading ||
            this is UpdateUiState.Verifying ||
            this is UpdateUiState.Installing

    private fun UpdateUiState.isPromptShowing(): Boolean =
        this is UpdateUiState.Available || this is UpdateUiState.PermissionRequired

    private companion object {
        const val UPDATES_DIRECTORY = "updates"
        const val PART_SUFFIX = ".part"
    }
}
