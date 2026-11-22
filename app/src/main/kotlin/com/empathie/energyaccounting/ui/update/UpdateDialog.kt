package com.empathie.energyaccounting.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.ui.common.LocalAppHaptics
import com.empathie.energyaccounting.update.UpdateError
import com.empathie.energyaccounting.update.UpdateUiState
import com.empathie.energyaccounting.update.UpdateViewModel
import com.empathie.energyaccounting.update.VerificationError
import java.util.Locale

/**
 * Floating update prompt, shown over every tab while the ViewModel has something to say.
 *
 * The dialog is deliberately dismissible like "Later": system back and an outside tap both run
 * [UpdateViewModel.onLater], which snoozes this version for a day. Installing and verifying states
 * keep their progress visible, and only the ViewModel decides when the dialog goes away.
 */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalAppHaptics.current

    if (state is UpdateUiState.Idle || state is UpdateUiState.Checking) return

    Dialog(
        onDismissRequest = viewModel::onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val current = state) {
                    UpdateUiState.Idle, UpdateUiState.Checking -> Unit

                    is UpdateUiState.Available -> AvailableContent(
                        state = current,
                        onLater = {
                            haptics.tick()
                            viewModel.onLater()
                        },
                        onUpdate = {
                            haptics.tick()
                            viewModel.onUpdate()
                        },
                    )

                    is UpdateUiState.Downloading -> DownloadingContent(
                        state = current,
                        onCancel = {
                            haptics.tick()
                            viewModel.cancelDownload()
                        },
                    )

                    is UpdateUiState.Verifying -> VerifyingContent(
                        onCancel = {
                            haptics.tick()
                            viewModel.cancelDownload()
                        },
                    )

                    is UpdateUiState.PermissionRequired -> PermissionContent(
                        onLater = {
                            haptics.tick()
                            viewModel.onLater()
                        },
                        onOpenSettings = {
                            haptics.tick()
                            viewModel.openInstallSettings()
                        },
                    )

                    is UpdateUiState.Installing -> InstallingContent()

                    is UpdateUiState.Error -> ErrorContent(
                        state = current,
                        onLater = {
                            haptics.tick()
                            viewModel.onLater()
                        },
                        onRetry = {
                            haptics.tick()
                            viewModel.retry()
                        },
                        onViewRelease = { url ->
                            haptics.tick()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(icon: ImageVector, titleRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.update_release_notes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text = notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AvailableContent(
    state: UpdateUiState.Available,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    DialogHeader(Icons.Filled.SystemUpdate, R.string.update_available_title)
    Text(
        text = stringResource(R.string.update_available_version, state.info.version.toString()),
        style = MaterialTheme.typography.bodyMedium,
    )
    ReleaseNotes(state.info.releaseNotes)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onLater) {
            Text(stringResource(R.string.update_later))
        }
        Button(onClick = onUpdate, enabled = state.info.apkUrl.isNotBlank()) {
            Text(stringResource(R.string.update_now))
        }
    }
}

@Composable
private fun DownloadingContent(
    state: UpdateUiState.Downloading,
    onCancel: () -> Unit,
) {
    DialogHeader(Icons.Filled.SystemUpdate, R.string.update_downloading)
    // Follow the app locale, not the system default, so an in-app language change is reflected.
    val locale = LocalConfiguration.current.locales[0]
    if (state.total > 0L) {
        LinearProgressIndicator(
            progress = { (state.downloaded.toFloat() / state.total.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.update_download_progress,
                formatBytes(state.downloaded, locale),
                formatBytes(state.total, locale),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.update_cancel))
        }
    }
}

@Composable
private fun VerifyingContent(onCancel: () -> Unit) {
    DialogHeader(Icons.Filled.SystemUpdate, R.string.update_verifying)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.update_cancel))
        }
    }
}

@Composable
private fun PermissionContent(onLater: () -> Unit, onOpenSettings: () -> Unit) {
    DialogHeader(Icons.Filled.Lock, R.string.update_permission_title)
    Text(
        text = stringResource(R.string.update_permission_body),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onLater) {
            Text(stringResource(R.string.update_later))
        }
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.update_open_settings))
        }
    }
}

@Composable
private fun InstallingContent() {
    DialogHeader(Icons.Filled.SystemUpdate, R.string.update_installing)
}

@Composable
private fun ErrorContent(
    state: UpdateUiState.Error,
    onLater: () -> Unit,
    onRetry: () -> Unit,
    onViewRelease: (String) -> Unit,
) {
    DialogHeader(Icons.Filled.Warning, R.string.update_error_title)
    Text(
        text = stringResource(errorMessageRes(state)),
        style = MaterialTheme.typography.bodyMedium,
    )
    val releaseUrl = state.info?.releaseUrl.orEmpty()
    if (releaseUrl.isNotBlank()) {
        TextButton(
            onClick = { onViewRelease(releaseUrl) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.update_view_release))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onLater) {
            Text(stringResource(R.string.update_later))
        }
        Button(onClick = onRetry) {
            Text(stringResource(R.string.update_retry))
        }
    }
}

internal fun errorMessageRes(state: UpdateUiState.Error): Int = when (val error = state.error) {
    VerificationError.CHECKSUM -> R.string.update_error_checksum
    VerificationError.PACKAGE -> R.string.update_error_package
    VerificationError.SIGNATURE -> R.string.update_error_signature
    VerificationError.UNREADABLE -> R.string.update_error_unreadable
    null -> when (state.kind) {
        UpdateError.NETWORK -> R.string.update_error_network
        UpdateError.STORAGE -> R.string.update_error_storage
        UpdateError.INSTALLER -> R.string.update_error_installer
        UpdateError.UNKNOWN -> R.string.update_error_generic
    }
}

/** Human-readable size for the download line, e.g. "1.2 MB". */
private fun formatBytes(bytes: Long, locale: Locale): String {
    return when {
        bytes >= 1024L * 1024L -> String.format(locale, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(locale, "%.1f KB", bytes / 1024.0)
        else -> String.format(locale, "%d B", bytes)
    }
}
