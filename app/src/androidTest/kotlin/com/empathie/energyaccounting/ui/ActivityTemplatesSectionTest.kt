package com.empathie.energyaccounting.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.BuildConfig
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.data.diagnostics.DebugLogExporter
import com.empathie.energyaccounting.data.export.BackupSerializer
import com.empathie.energyaccounting.data.export.PractitionerPdfGenerator
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.data.settings.AppSettings
import com.empathie.energyaccounting.domain.model.ActivityTemplate
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.reminder.ReminderScheduler
import com.empathie.energyaccounting.ui.manage.ManageExportScreen
import com.empathie.energyaccounting.ui.manage.ManageExportViewModel
import com.empathie.energyaccounting.update.AndroidApkInstaller
import com.empathie.energyaccounting.update.AndroidApkVerifier
import com.empathie.energyaccounting.update.GitHubReleaseSource
import com.empathie.energyaccounting.update.SemanticVersion
import com.empathie.energyaccounting.update.UpdateViewModel
import com.empathie.energyaccounting.ui.theme.EmpathieTheme
import com.empathie.energyaccounting.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityTemplatesSectionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var repo: EnergyAccountingRepository
    private lateinit var vm: ManageExportViewModel
    private lateinit var updateVm: UpdateViewModel
    private var showScreen by mutableStateOf(true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assertEquals("com.empathie.energyaccounting.uitest", context.packageName)
        db = Room.inMemoryDatabaseBuilder(context, EnergyAccountingDatabase::class.java).allowMainThreadQueries().build()
        repo = EnergyAccountingRepository(db)
        runBlocking {
            db.ensureSeeded()
            val dimension = repo.observeActiveDimensions().first().first()
            listOf("Alpha", "Beta").forEachIndexed { index, name ->
                repo.saveTemplate(ActivityTemplate(
                    activityName = name, defaultDirection = Direction.SPEND, defaultEnergyValue = 10,
                    defaultDimension = dimension, isPinned = index == 0,
                ))
            }
        }
        vm = ManageExportViewModel(repo, BackupSerializer(db), PractitionerPdfGenerator(), ReminderScheduler(context), AppSettings(context), DebugLogExporter(db))
        updateVm = UpdateViewModel(
            source = GitHubReleaseSource(BuildConfig.UPDATE_REPO),
            settings = AppSettings(context),
            verifier = AndroidApkVerifier(context),
            installer = AndroidApkInstaller(context),
            cacheDir = context.cacheDir,
            currentVersion = SemanticVersion(0, 0, 0),
            currentVersionName = BuildConfig.VERSION_NAME,
            isReleaseBuild = false,
        )
        compose.setContent { if (showScreen) EmpathieTheme(ThemeMode.LIGHT) { ManageExportScreen(vm, updateVm) } }
        compose.waitUntil(5_000) { vm.templates.value.size == 2 }
    }

    @After
    fun tearDown() {
        compose.runOnIdle { showScreen = false }
        compose.waitForIdle()
        runBlocking {
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            db.withTransaction { }
        }
        db.close()
    }

    @Test
    fun collapsedSectionSupportsSearchStarAndDelete() {
        compose.onNodeWithText("Alpha").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.show_templates)).performClick()
        compose.onNodeWithText(context.getString(R.string.search_templates)).performTextInput("bEt")
        compose.onNodeWithText("Alpha").assertDoesNotExist()
        compose.onNodeWithText("Beta").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.pin)).performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { repo.observeTemplates().first().first { it.activityName == "Beta" }.isPinned } }
        compose.onNodeWithContentDescription(context.getString(R.string.template_options)).performClick()
        compose.onNodeWithText(context.getString(R.string.delete)).performClick()
        compose.waitUntil(5_000) { runBlocking { repo.observeTemplates().first().none { it.activityName == "Beta" } } }
    }
}
