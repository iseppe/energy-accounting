package com.empathie.energyaccounting

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.empathie.energyaccounting.data.export.BackupSerializer
import com.empathie.energyaccounting.data.export.PractitionerPdfGenerator
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.reminder.CheckInReminderReceiver
import com.empathie.energyaccounting.reminder.ReminderScheduler
import com.empathie.energyaccounting.ui.daily.DailyAccountScreen
import com.empathie.energyaccounting.ui.daily.DailyAccountViewModel
import com.empathie.energyaccounting.ui.insights.InsightsScreen
import com.empathie.energyaccounting.ui.insights.InsightsViewModel
import com.empathie.energyaccounting.ui.manage.ManageExportScreen
import com.empathie.energyaccounting.ui.manage.ManageExportViewModel
import com.empathie.energyaccounting.ui.theme.EmpathieTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The Add Entry request carried by a check-in notification.
 *
 * A null [timeSlot] means the notification's slot name was unrecognised; the sheet then
 * falls back to the clock-based default instead of refusing to open.
 */
internal data class AddSheetDeepLink(val timeSlot: TimeSlot?)

internal fun Intent.toAddSheetDeepLink(): AddSheetDeepLink? {
    if (!getBooleanExtra(CheckInReminderReceiver.EXTRA_OPEN_ADD_SHEET, false)) return null
    val slotName = getStringExtra(CheckInReminderReceiver.EXTRA_TIME_SLOT)
    return AddSheetDeepLink(
        slotName?.let { name -> TimeSlot.entries.firstOrNull { it.name == name } }
    )
}

private data class DeepLinkEvent(val id: Long, val timeSlot: TimeSlot?)

/**
 * Single-activity shell with the three tabs and the notification deep link.
 *
 * A tapped check-in notification switches to the Daily Account tab, selects today and
 * opens the entry sheet already pointed at the slot that fired. Extras are consumed and
 * the event is cleared after handling, so a rotation never replays it and a second
 * notification for the same slot still opens a fresh sheet.
 */
class MainActivity : ComponentActivity() {

    private val deepLinkEvents = MutableStateFlow<DeepLinkEvent?>(null)
    private var nextDeepLinkId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readDeepLink(intent, isFreshLaunch = savedInstanceState == null)

        setContent {
            val app = application as EnergyAccountingApp

            val dailyViewModel: DailyAccountViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { DailyAccountViewModel(app.repository) }
                }
            )
            val insightsViewModel: InsightsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { InsightsViewModel(app.repository) }
                }
            )
            val manageViewModel: ManageExportViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ManageExportViewModel(
                            repo = app.repository,
                            backupSerializer = BackupSerializer(app.database),
                            pdfGenerator = PractitionerPdfGenerator(),
                            reminderScheduler = ReminderScheduler(app),
                            settings = app.settings,
                        )
                    }
                }
            )

            val themeMode by manageViewModel.themeMode.collectAsStateWithLifecycle()
            val deepLink by deepLinkEvents.collectAsStateWithLifecycle()
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }

            LaunchedEffect(deepLink?.id) {
                val event = deepLink ?: return@LaunchedEffect
                selectedTab = 0
                dailyViewModel.goToToday()
                dailyViewModel.openNewEntrySheet(event.timeSlot)
                deepLinkEvents.value = null
            }

            EmpathieTheme(themeMode = themeMode) {
                AppShell(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    dailyViewModel = dailyViewModel,
                    insightsViewModel = insightsViewModel,
                    manageViewModel = manageViewModel,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDeepLink(intent, isFreshLaunch = true)
    }

    private fun readDeepLink(intent: Intent?, isFreshLaunch: Boolean) {
        val link = intent?.toAddSheetDeepLink() ?: return
        if (isFreshLaunch) {
            nextDeepLinkId += 1
            deepLinkEvents.value = DeepLinkEvent(id = nextDeepLinkId, timeSlot = link.timeSlot)
        }
        intent.removeExtra(CheckInReminderReceiver.EXTRA_OPEN_ADD_SHEET)
        intent.removeExtra(CheckInReminderReceiver.EXTRA_TIME_SLOT)
    }
}

@Composable
private fun AppShell(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    dailyViewModel: DailyAccountViewModel,
    insightsViewModel: InsightsViewModel,
    manageViewModel: ManageExportViewModel,
) {
    val tabs = listOf(
        R.string.tab_daily_account to Icons.Filled.Today,
        R.string.tab_insights to Icons.Filled.Insights,
        R.string.tab_manage to Icons.Filled.Settings,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (labelRes, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { onSelectTab(index) },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DailyAccountScreen(dailyViewModel)
                1 -> InsightsScreen(insightsViewModel)
                else -> ManageExportScreen(manageViewModel)
            }
        }
    }
}
