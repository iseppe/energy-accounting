package com.empathie.energyaccounting

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.empathie.energyaccounting.data.diagnostics.DebugLogExporter
import com.empathie.energyaccounting.data.export.BackupSerializer
import com.empathie.energyaccounting.data.export.PractitionerPdfGenerator
import com.empathie.energyaccounting.data.settings.AppSettings
import com.empathie.energyaccounting.data.settings.LocaleHelper
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.reminder.CheckInReminderReceiver
import com.empathie.energyaccounting.reminder.ReminderScheduler
import com.empathie.energyaccounting.ui.common.AppHaptics
import com.empathie.energyaccounting.ui.common.LocalAppHaptics
import com.empathie.energyaccounting.ui.common.shouldRequestNotificationPermission
import com.empathie.energyaccounting.ui.daily.DailyAccountScreen
import com.empathie.energyaccounting.ui.daily.DailyAccountViewModel
import com.empathie.energyaccounting.ui.insights.InsightsScreen
import com.empathie.energyaccounting.ui.insights.InsightsViewModel
import com.empathie.energyaccounting.ui.manage.ManageExportScreen
import com.empathie.energyaccounting.ui.manage.ManageExportViewModel
import com.empathie.energyaccounting.ui.theme.EmpathieTheme
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors
import com.empathie.energyaccounting.ui.update.UpdateDialog
import com.empathie.energyaccounting.update.AndroidApkInstaller
import com.empathie.energyaccounting.update.AndroidApkVerifier
import com.empathie.energyaccounting.update.GitHubReleaseSource
import com.empathie.energyaccounting.update.SemanticVersion
import com.empathie.energyaccounting.update.UpdateViewModel
import kotlinx.coroutines.delay
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
 * Single-activity shell with the three tabs, the notification deep link, in-app locale
 * wrapping, predictive back and edge-to-edge system bar contrast.
 */
class MainActivity : ComponentActivity() {

    private val deepLinkEvents = MutableStateFlow<DeepLinkEvent?>(null)
    private var nextDeepLinkId = 0L

    override fun attachBaseContext(newBase: Context) {
        val language = AppSettings(newBase).appLanguage()
        super.attachBaseContext(LocaleHelper.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readDeepLink(intent, isFreshLaunch = savedInstanceState == null)

        setContent {
            val app = application as EnergyAccountingApp

            val dailyViewModel: DailyAccountViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { DailyAccountViewModel(app.repository, app.clock) }
                }
            )
            val insightsViewModel: InsightsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { InsightsViewModel(app.repository, app.clock) }
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
                            debugLogExporter = DebugLogExporter(app.database),
                            clock = app.clock,
                        )
                    }
                }
            )

            val updateViewModel: UpdateViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        UpdateViewModel(
                            source = GitHubReleaseSource(BuildConfig.UPDATE_REPO),
                            settings = app.settings,
                            verifier = AndroidApkVerifier(app),
                            installer = AndroidApkInstaller(app),
                            cacheDir = app.cacheDir,
                            currentVersion = SemanticVersion.parse(BuildConfig.VERSION_NAME)
                                ?: SemanticVersion(0, 0, 0),
                            currentVersionName = BuildConfig.VERSION_NAME,
                            isReleaseBuild = !BuildConfig.DEBUG,
                        )
                    }
                }
            )

            val themeMode by manageViewModel.themeMode.collectAsStateWithLifecycle()
            val vibrationEnabled by manageViewModel.vibrationEnabled.collectAsStateWithLifecycle()
            val reminders by manageViewModel.reminders.collectAsStateWithLifecycle()
            val deepLink by deepLinkEvents.collectAsStateWithLifecycle()
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }

            val requestNotificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                // The reminder section keeps a visible settings path if this is denied.
            }

            val view = LocalView.current
            val haptics = remember(vibrationEnabled) {
                AppHaptics(vibrationEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }

            LaunchedEffect(deepLink?.id) {
                val event = deepLink ?: return@LaunchedEffect
                selectedTab = 0
                dailyViewModel.goToToday()
                dailyViewModel.openNewEntrySheet(event.timeSlot)
                deepLinkEvents.value = null
            }

            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                EmpathieTheme(themeMode = themeMode) {
                    SyncSystemBars()

                    LaunchedEffect(Unit) {
                        delay(1_500)
                        updateViewModel.checkOnLaunch()
                    }

                    // Restored reminders can be enabled without a switch toggle, so ask once
                    // when the reminders flow first shows an enabled reminder and permission is
                    // still missing.
                    LaunchedEffect(reminders) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (
                            shouldRequestNotificationPermission(
                                sdkInt = Build.VERSION.SDK_INT,
                                permissionGranted = granted,
                                reminders = reminders,
                            ) && !app.settings.notificationPermissionPrompted()
                        ) {
                            app.settings.setNotificationPermissionPrompted(true)
                            requestNotificationPermission.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    }

                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        updateViewModel.onResume()
                    }

                    BackHandler(enabled = selectedTab != 0) {
                        selectedTab = 0
                    }

                    AppShell(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        dailyViewModel = dailyViewModel,
                        insightsViewModel = insightsViewModel,
                        manageViewModel = manageViewModel,
                        updateViewModel = updateViewModel,
                    )

                    UpdateDialog(updateViewModel)
                }
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
private fun SyncSystemBars() {
    val colors = LocalEmpathieColors.current
    val view = LocalView.current
    LaunchedEffect(colors.isDark) {
        val activity = view.context as? ComponentActivity ?: return@LaunchedEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !colors.isDark
            isAppearanceLightNavigationBars = !colors.isDark
        }
    }
}

@Composable
private fun AppShell(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    dailyViewModel: DailyAccountViewModel,
    insightsViewModel: InsightsViewModel,
    manageViewModel: ManageExportViewModel,
    updateViewModel: UpdateViewModel,
) {
    val tabs = listOf(
        R.string.tab_daily_account to Icons.Filled.Today,
        R.string.tab_insights to Icons.Filled.Insights,
        R.string.tab_manage to Icons.Filled.Settings,
    )
    val haptics = LocalAppHaptics.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (labelRes, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            haptics.tick()
                            onSelectTab(index)
                        },
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
                else -> ManageExportScreen(manageViewModel, updateViewModel)
            }
        }
    }
}
