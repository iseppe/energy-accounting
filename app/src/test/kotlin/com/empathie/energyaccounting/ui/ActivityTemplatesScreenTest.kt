package com.empathie.energyaccounting.ui

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ActivityTemplatesScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var repo: EnergyAccountingRepository
    private lateinit var vm: ManageExportViewModel
    private lateinit var updateVm: UpdateViewModel
    private lateinit var context: Context
    private var showScreen by mutableStateOf(true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, EnergyAccountingDatabase::class.java).allowMainThreadQueries().build()
        repo = EnergyAccountingRepository(db)
        runBlocking {
            db.ensureSeeded()
            val dimension = repo.observeActiveDimensions().first().first()
            listOf("Alpha", "Beta", "Gamma").forEachIndexed { index, name ->
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
        compose.waitUntil(5_000) { vm.templates.value.size == 3 }
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
    fun templatesStartCollapsedAndCanBeExpanded() {
        compose.onNodeWithText("Alpha").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.show_templates)).performClick()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.hide_templates)).performClick()
        compose.onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun searchIsCaseInsensitiveAndStarRemainsDirectlyAccessible() {
        compose.onNodeWithContentDescription(context.getString(R.string.show_templates)).performClick()
        compose.onNodeWithText(context.getString(R.string.search_templates)).performTextInput("bEt")
        compose.onNodeWithText("Alpha").assertDoesNotExist()
        compose.onNodeWithText("Beta").assertIsDisplayed()
        compose.onNodeWithTag("manage-list").performTouchInput {
            swipeUp(startY = height * 0.8f, endY = height * 0.6f, durationMillis = 300)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.pin)).performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking { repo.observeTemplates().first().first { it.activityName == "Beta" }.isPinned }
        }
        val beta = runBlocking { repo.observeTemplates().first().first { it.activityName == "Beta" } }
        assertTrue("Beta should be pinned after the star action: $beta", beta.isPinned)
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.templates.value.first { it.activityName == "Beta" }.isPinned
        }
        compose.onNodeWithContentDescription(context.getString(R.string.unpin)).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            !runBlocking { repo.observeTemplates().first().first { it.activityName == "Beta" }.isPinned }
        }
    }

    @Test
    fun createIsAvailableWhenCollapsed() {
        compose.onNodeWithContentDescription(context.getString(R.string.create)).performClick()
        compose.onNodeWithText(context.getString(R.string.activity)).assertIsDisplayed()
    }

    @Test
    fun templatePinActionPersists() = runBlocking {
        val beta = repo.observeTemplates().first().first { it.activityName == "Beta" }
        vm.toggleTemplatePin(beta)
        assertTrue(repo.observeTemplates().first().first { it.activityName == "Beta" }.isPinned)
    }

    @Test
    fun searchingWithoutMatchesShowsAnEmptyState() {
        compose.onNodeWithContentDescription(context.getString(R.string.show_templates)).performClick()
        compose.onNodeWithText(context.getString(R.string.search_templates)).performTextInput("no match")
        compose.onNodeWithText(context.getString(R.string.no_matching_templates)).assertExists()
        compose.onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun overflowMenuCanEditAndDeleteATemplate() {
        compose.onNodeWithContentDescription(context.getString(R.string.show_templates)).performClick()
        compose.onNodeWithText(context.getString(R.string.search_templates)).performTextInput("Beta")
        compose.onNodeWithTag("manage-list").performTouchInput {
            swipeUp(startY = height * 0.8f, endY = height * 0.6f, durationMillis = 300)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.template_options)).assertIsDisplayed().performClick()
        compose.onNodeWithText(context.getString(R.string.edit)).performClick()
        compose.onNodeWithText(context.getString(R.string.activity)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.template_options)).performClick()
        compose.onNodeWithText(context.getString(R.string.delete)).performClick()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking { repo.observeTemplates().first().none { it.activityName == "Beta" } }
        }
    }

    @Test
    fun laterSettingsRemainReachableWithManyTemplates() {
        runBlocking {
            val dimension = repo.observeActiveDimensions().first().first()
            repeat(60) { index ->
                repo.saveTemplate(ActivityTemplate(
                    activityName = "Extra $index", defaultDirection = Direction.SPEND,
                    defaultEnergyValue = 10, defaultDimension = dimension,
                ))
            }
        }
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.templates.value.size == 63
        }
        compose.onNodeWithContentDescription(context.getString(R.string.show_templates)).performClick()
        val title = context.getString(R.string.energy_dimensions_management)
        compose.onNodeWithTag("manage-list").performScrollToNode(hasText(title))
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
