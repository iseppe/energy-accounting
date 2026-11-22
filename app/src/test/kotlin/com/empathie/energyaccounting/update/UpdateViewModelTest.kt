package com.empathie.energyaccounting.update

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.settings.AppSettings
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UpdateViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<UpdateViewModel>()
    private lateinit var settings: AppSettings
    private lateinit var cacheDir: File
    private var now = 1_000_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("empathie_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settings = AppSettings(context)
        cacheDir = Files.createTempDirectory("update-view-model").toFile()
    }

    @After
    fun tearDown() {
        runBlocking {
            viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        }
        Dispatchers.resetMain()
        cacheDir.deleteRecursively()
    }

    @Test
    fun `init deletes stale update files`() = runTest {
        val updates = File(cacheDir, "updates").apply { mkdirs() }
        val part = File(updates, "old.apk.part").apply { writeBytes(byteArrayOf(1)) }
        val oldApk = File(updates, "old.apk").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(now - UpdatePolicy.CHECK_INTERVAL_MS - 1)
        }
        val freshApk = File(updates, "fresh.apk").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(now)
        }

        newViewModel(FakeReleaseSource())

        assertFalse(part.exists())
        assertFalse(oldApk.exists())
        assertTrue(freshApk.exists())
    }

    @Test
    fun `debug builds never check`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source, releaseBuild = false)

        viewModel.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(0, source.latestCalls)
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
    }

    @Test
    fun `disabled settings never check`() = runTest {
        settings.setUpdateChecksEnabled(false)
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source)

        viewModel.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(0, source.latestCalls)
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
    }

    @Test
    fun `manual check still works when automatic checks are off`() = runTest {
        settings.setUpdateChecksEnabled(false)
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source)

        viewModel.checkNow()
        advanceUntilIdle()

        assertEquals(1, source.latestCalls)
        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Available)
        assertEquals(SemanticVersion(0, 4, 0), (state as UpdateUiState.Available).info.version)
        assertFalse(settings.updateChecksEnabled())
    }

    @Test
    fun `debug builds never check manually either`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source, releaseBuild = false)

        viewModel.checkNow()
        advanceUntilIdle()

        assertEquals(0, source.latestCalls)
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
    }

    @Test
    fun `automatic check is throttled inside 24 hours`() = runTest {
        settings.setLastUpdateCheckEpochMillis(now - 60_000)
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source)

        viewModel.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(0, source.latestCalls)
    }

    @Test
    fun `a snooze expiry forces a check inside the throttle`() = runTest {
        settings.setLastUpdateCheckEpochMillis(now - 60_000)
        settings.snoozeUpdate("0.4.0", now - 30_000)
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source)

        viewModel.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(1, source.latestCalls)
        assertTrue(viewModel.state.value is UpdateUiState.Available)
    }

    @Test
    fun `a newer release becomes Available`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source)

        viewModel.checkOnLaunch()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Available)
        assertEquals(SemanticVersion(0, 4, 0), (state as UpdateUiState.Available).info.version)
        assertEquals(now, settings.lastUpdateCheckEpochMillis())
    }

    @Test
    fun `an equal or older release is UpToDate`() = runTest {
        val source = FakeReleaseSource(release = updateInfo(SemanticVersion(0, 3, 0)))
        val viewModel = newViewModel(source)

        viewModel.checkNow()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
        assertEquals(UpdateCheckStatus.UpToDate, viewModel.checkStatus.value)

        source.release = updateInfo(SemanticVersion(0, 2, 0))
        viewModel.checkNow()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
        assertEquals(UpdateCheckStatus.UpToDate, viewModel.checkStatus.value)
    }

    @Test
    fun `Later snoozes the same version`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onLater()

        assertEquals("0.4.0", settings.snoozedUpdateVersion())
        assertEquals(now + UpdatePolicy.SNOOZE_INTERVAL_MS, settings.snoozedUpdateUntilEpochMillis())
        assertTrue(viewModel.state.value is UpdateUiState.Idle)

        now += UpdatePolicy.SNOOZE_INTERVAL_MS + 1
        viewModel.checkOnLaunch()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UpdateUiState.Available)
        assertEquals(2, source.latestCalls)
    }

    @Test
    fun `Update downloads, verifies and installs`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val verifier = FakeVerifier()
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, verifier, installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        advanceUntilIdle()

        assertEquals(1, source.downloadCalls)
        assertEquals(File(cacheDir, "updates/app.apk"), verifier.seen)
        assertEquals(updateInfo().apkSha256, verifier.seenSha256)
        assertEquals("com.empathie.energyaccounting", verifier.seenPackageName)
        assertNotNull(installer.installed)
        assertEquals(File(cacheDir, "updates/app.apk"), installer.installed)
        assertTrue(installer.installed!!.exists())
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
        assertEquals("0.4.0", settings.snoozedUpdateVersion())
    }

    @Test
    fun `checksum failure deletes the file and shows a checksum error`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val verifier = FakeVerifier(VerificationResult.Invalid(VerificationError.CHECKSUM))
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, verifier, installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Error)
        assertEquals(VerificationError.CHECKSUM, (state as UpdateUiState.Error).error)
        assertNotNull(verifier.seen)
        assertFalse(verifier.seen!!.exists())
        assertNull(installer.installed)
    }

    @Test
    fun `signature or package failure shows the matching error`() = runTest {
        for (error in listOf(VerificationError.SIGNATURE, VerificationError.PACKAGE)) {
            val source = FakeReleaseSource(release = updateInfo())
            val verifier = FakeVerifier(VerificationResult.Invalid(error))
            val installer = FakeInstaller()
            val viewModel = newViewModel(source, verifier, installer)

            viewModel.checkNow()
            advanceUntilIdle()
            viewModel.onUpdate()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is UpdateUiState.Error)
            assertEquals(error, (state as UpdateUiState.Error).error)
            assertFalse(verifier.seen!!.exists())
            assertNull(installer.installed)
        }
    }

    @Test
    fun `permission required pauses until the user returns`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val installer = FakeInstaller(canInstallValue = false)
        val viewModel = newViewModel(source, installer = installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UpdateUiState.PermissionRequired)
        assertEquals(0, source.downloadCalls)
        assertNull(installer.installed)

        viewModel.openInstallSettings()
        assertEquals(1, installer.openedSettings)

        installer.canInstallValue = true
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(1, source.downloadCalls)
        assertNotNull(installer.installed)
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
    }

    @Test
    fun `cancel download leaves Idle and no part file`() = runTest {
        val source = FakeReleaseSource(release = updateInfo(), hangDownload = true)
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, installer = installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()

        val part = File(cacheDir, "updates/app.apk.part")
        assertTrue(viewModel.state.value is UpdateUiState.Downloading)
        assertTrue(part.exists())

        viewModel.showDebugDialog()
        assertTrue(viewModel.state.value is UpdateUiState.Downloading)

        viewModel.cancelDownload()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UpdateUiState.Idle)
        assertFalse(part.exists())
        assertNull(installer.installed)
    }

    @Test
    fun `showDebugDialog refuses a blank URL`() = runTest {
        val source = FakeReleaseSource()
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, installer = installer)

        viewModel.showDebugDialog()

        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Available)
        assertTrue((state as UpdateUiState.Available).info.apkUrl.isBlank())

        viewModel.onUpdate()
        advanceUntilIdle()

        val error = viewModel.state.value
        assertTrue(error is UpdateUiState.Error)
        assertEquals(UpdateError.UNKNOWN, (error as UpdateUiState.Error).kind)
        assertEquals(0, source.downloadCalls)
        assertNull(installer.installed)
    }

    @Test
    fun `manual check bypasses the throttle`() = runTest {
        settings.setLastUpdateCheckEpochMillis(now)
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source)

        viewModel.checkOnLaunch()
        advanceUntilIdle()
        assertEquals(0, source.latestCalls)

        viewModel.checkNow()
        advanceUntilIdle()
        assertEquals(1, source.latestCalls)
        assertTrue(viewModel.state.value is UpdateUiState.Available)
    }

    @Test
    fun `disabling checks dismisses the dialog`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val viewModel = newViewModel(source)

        viewModel.checkNow()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is UpdateUiState.Available)

        viewModel.setChecksEnabled(false)

        assertFalse(settings.updateChecksEnabled())
        assertFalse(viewModel.checksEnabled.value)
        assertTrue(viewModel.state.value is UpdateUiState.Idle)

        viewModel.checkOnLaunch()
        advanceUntilIdle()
        assertEquals(1, source.latestCalls)
    }

    @Test
    fun `network failure is reported without a dialog`() = runTest {
        val source = FakeReleaseSource(error = IOException("offline"))
        val viewModel = newViewModel(source)

        viewModel.checkNow()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UpdateUiState.Idle)
        assertEquals(UpdateCheckStatus.Failed, viewModel.checkStatus.value)
        assertEquals(0L, settings.lastUpdateCheckEpochMillis())
    }

    @Test
    fun `retry repeats the failed update`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val verifier = FakeVerifier(VerificationResult.Invalid(VerificationError.CHECKSUM))
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, verifier, installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is UpdateUiState.Error)

        verifier.result = VerificationResult.Valid
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, source.downloadCalls)
        assertNotNull(installer.installed)
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
    }

    @Test
    fun `cancel while verifying deletes the APK and never installs`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val verifier = FakeVerifier()
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, verifier, installer)
        verifier.duringVerify = { viewModel.cancelDownload() }

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        advanceUntilIdle()

        assertEquals(1, source.downloadCalls)
        assertNotNull(verifier.seen)
        assertNull(installer.installed)
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
        assertFalse(File(cacheDir, "updates/app.apk").exists())
        assertFalse(File(cacheDir, "updates/app.apk.part").exists())
    }

    @Test
    fun `snooze expiry forces one check only`() = runTest {
        settings.setLastUpdateCheckEpochMillis(now - 60_000)
        settings.snoozeUpdate("0.4.0", now - 30_000)
        val source = FakeReleaseSource(release = updateInfo())

        val first = newViewModel(source)
        first.checkOnLaunch()
        advanceUntilIdle()
        assertEquals(1, source.latestCalls)
        assertTrue(first.state.value is UpdateUiState.Available)

        now += 3_600_000
        val second = newViewModel(source)
        second.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(1, source.latestCalls)
        assertTrue(second.state.value is UpdateUiState.Idle)
    }

    @Test
    fun `insufficient space reports a storage error`() = runTest {
        val bigInfo = updateInfo().copy(apkSizeBytes = Long.MAX_VALUE / 2)
        val source = FakeReleaseSource(release = bigInfo)
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, installer = installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Error)
        assertEquals(UpdateError.STORAGE, (state as UpdateUiState.Error).kind)
        assertEquals(0, source.downloadCalls)
        assertNull(installer.installed)
    }

    @Test
    fun `a failure to finalize the download reports a storage error`() = runTest {
        val blocker = File(cacheDir, "updates/app.apk/blocker").apply {
            parentFile?.mkdirs()
            writeText("keep")
        }
        val source = FakeReleaseSource(release = updateInfo())
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, installer = installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Error)
        assertEquals(UpdateError.STORAGE, (state as UpdateUiState.Error).kind)
        assertTrue(blocker.exists())
        assertFalse(File(cacheDir, "updates/app.apk.part").exists())
        assertNull(installer.installed)
    }

    @Test
    fun `an installer failure shows the installer error`() = runTest {
        val source = FakeReleaseSource(release = updateInfo())
        val installer = FakeInstaller(canInstallValue = true).apply {
            installError = ActivityNotFoundException("No installer")
        }
        val viewModel = newViewModel(source, installer = installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Error)
        assertEquals(UpdateError.INSTALLER, (state as UpdateUiState.Error).kind)
        assertNull(installer.installed)
    }

    @Test
    fun `a download failure shows a network error and deletes the part`() = runTest {
        val source = FakeReleaseSource(release = updateInfo()).apply {
            downloadError = IOException("connection reset")
        }
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, installer = installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Error)
        assertEquals(UpdateError.NETWORK, (state as UpdateUiState.Error).kind)
        assertFalse(File(cacheDir, "updates/app.apk.part").exists())
        assertFalse(File(cacheDir, "updates/app.apk").exists())
        assertNull(installer.installed)
    }

    @Test
    fun `disabling checks cancels a download and deletes the part`() = runTest {
        val source = FakeReleaseSource(release = updateInfo(), hangDownload = true)
        val installer = FakeInstaller()
        val viewModel = newViewModel(source, installer = installer)

        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.onUpdate()
        val part = File(cacheDir, "updates/app.apk.part")
        assertTrue(part.exists())

        viewModel.setChecksEnabled(false)
        advanceUntilIdle()

        assertFalse(part.exists())
        assertFalse(File(cacheDir, "updates/app.apk").exists())
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
        assertFalse(settings.updateChecksEnabled())
        assertNull(installer.installed)
    }

    @Test
    fun `disabling checks cancels a check in flight`() = runTest {
        val source = FakeReleaseSource(release = updateInfo(), hangCheck = true)
        val viewModel = newViewModel(source)

        viewModel.checkNow()
        assertEquals(UpdateCheckStatus.Checking, viewModel.checkStatus.value)

        viewModel.setChecksEnabled(false)
        advanceUntilIdle()

        assertEquals(UpdateCheckStatus.Idle, viewModel.checkStatus.value)
        assertTrue(viewModel.state.value is UpdateUiState.Idle)
        assertEquals(1, source.latestCalls)
    }

    private fun TestScope.newViewModel(
        source: FakeReleaseSource,
        verifier: FakeVerifier = FakeVerifier(),
        installer: FakeInstaller = FakeInstaller(),
        releaseBuild: Boolean = true,
    ): UpdateViewModel = UpdateViewModel(
        source = source,
        settings = settings,
        verifier = verifier,
        installer = installer,
        cacheDir = cacheDir,
        currentVersion = SemanticVersion(0, 3, 0),
        currentVersionName = "0.3.0",
        isReleaseBuild = releaseBuild,
        now = { now },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    ).also { viewModels += it }

    private fun updateInfo(version: SemanticVersion = SemanticVersion(0, 4, 0)) = UpdateInfo(
        version = version,
        tagName = "v$version",
        releaseName = "Empathie Energy Accounting $version",
        releaseNotes = "- Faster update checks\n- Fixed a bug",
        releaseUrl = "https://github.com/iseppe/energy-accounting/releases/tag/v$version",
        apkUrl = "https://example.com/app.apk",
        apkAssetName = "app.apk",
        apkSizeBytes = 3L,
        apkSha256 = "a".repeat(64),
    )

    private class FakeReleaseSource(
        var release: UpdateInfo? = null,
        var error: Exception? = null,
        var hangDownload: Boolean = false,
        var hangCheck: Boolean = false,
    ) : ReleaseSource {
        var latestCalls = 0
        var downloadCalls = 0
        var downloadError: Exception? = null

        override suspend fun latestRelease(): UpdateInfo? {
            latestCalls++
            if (hangCheck) awaitCancellation()
            error?.let { throw it }
            return release
        }

        override suspend fun downloadApk(
            info: UpdateInfo,
            destination: File,
            onProgress: (downloaded: Long, total: Long) -> Unit,
        ) {
            downloadCalls++
            destination.writeBytes(byteArrayOf(1, 2, 3))
            downloadError?.let { throw it }
            onProgress(1L, info.apkSizeBytes)
            if (hangDownload) awaitCancellation()
            onProgress(3L, info.apkSizeBytes)
        }
    }

    private class FakeVerifier(
        var result: VerificationResult = VerificationResult.Valid,
    ) : ApkVerifier {
        var seen: File? = null
        var seenSha256: String? = null
        var seenPackageName: String? = null
        var duringVerify: (() -> Unit)? = null

        override fun verify(
            apk: File,
            expectedSha256: String,
            expectedPackageName: String,
        ): VerificationResult {
            seen = apk
            seenSha256 = expectedSha256
            seenPackageName = expectedPackageName
            duringVerify?.invoke()
            return result
        }
    }

    private class FakeInstaller(var canInstallValue: Boolean = true) : ApkInstaller {
        var installed: File? = null
        var openedSettings = 0
        var installError: Exception? = null

        override fun canInstall(): Boolean = canInstallValue

        override fun openInstallPermissionSettings() {
            openedSettings++
        }

        override fun install(apk: File) {
            installError?.let { throw it }
            installed = apk
        }
    }
}
