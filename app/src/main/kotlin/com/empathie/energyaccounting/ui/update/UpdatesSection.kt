package com.empathie.energyaccounting.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.empathie.energyaccounting.BuildConfig
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.ui.common.LocalAppHaptics
import com.empathie.energyaccounting.update.UpdateCheckStatus
import com.empathie.energyaccounting.update.UpdateViewModel

/**
 * Updates card for Manage & Export, shown in release builds only.
 *
 * The "Check now" button stays available even when automatic checks are off; only a check that is
 * already running disables it. The result line is driven by [UpdateViewModel.checkStatus], because
 * a successful background check never opens the dialog.
 */
@Composable
fun UpdatesSection(viewModel: UpdateViewModel) {
    val checksEnabled by viewModel.checksEnabled.collectAsStateWithLifecycle()
    val checkStatus by viewModel.checkStatus.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.updates_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.update_check_automatic),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = checksEnabled,
                    onCheckedChange = { enabled ->
                        haptics.tick()
                        viewModel.setChecksEnabled(enabled)
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.update_current_version),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = {
                    haptics.tick()
                    viewModel.checkNow()
                },
                enabled = checkStatus != UpdateCheckStatus.Checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (checkStatus == UpdateCheckStatus.Checking) {
                            R.string.update_checking
                        } else {
                            R.string.update_check_now
                        }
                    )
                )
            }
            when (checkStatus) {
                UpdateCheckStatus.UpToDate -> Text(
                    text = stringResource(R.string.update_up_to_date),
                    style = MaterialTheme.typography.bodySmall,
                )

                UpdateCheckStatus.Failed -> Text(
                    text = stringResource(R.string.update_check_failed),
                    style = MaterialTheme.typography.bodySmall,
                )

                else -> Unit
            }
        }
    }
}
