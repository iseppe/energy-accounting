package com.empathie.energyaccounting.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.export.BackupSerializer
import com.empathie.energyaccounting.data.export.PractitionerPdfGenerator
import com.empathie.energyaccounting.data.export.RestoreMode
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.data.settings.AppSettings
import com.empathie.energyaccounting.domain.model.ActivityTemplate
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.reminder.ReminderScheduler
import com.empathie.energyaccounting.ui.manage.ManageExportViewModel
import com.empathie.energyaccounting.ui.manage.ManageMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class ManageExportViewModelTest {

    private lateinit var db1: EnergyAccountingDatabase
    private lateinit var db2: EnergyAccountingDatabase
    private lateinit var scheduler: RecordingReminderScheduler

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private class RecordingReminderScheduler(context: Context) : ReminderScheduler(context) {
        val scheduled = mutableListOf<CheckInReminder>()

        override fun scheduleOrCancel(reminder: CheckInReminder, now: ZonedDateTime) {
            scheduled += reminder
        }
    }

    @Before
    fun setUp() = runTest {
        db1 = Room.inMemoryDatabaseBuilder(context, EnergyAccountingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db2 = Room.inMemoryDatabaseBuilder(context, EnergyAccountingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db1.ensureSeeded()
        db2.ensureSeeded()
        scheduler = RecordingReminderScheduler(context)
    }

    @After
    fun tearDown() {
        db1.close()
        db2.close()
    }

    private fun viewModel(repo: EnergyAccountingRepository): ManageExportViewModel =
        ManageExportViewModel(
            repo = repo,
            backupSerializer = BackupSerializer(db2),
            pdfGenerator = PractitionerPdfGenerator(),
            reminderScheduler = scheduler,
            settings = AppSettings(context),
        )

    @Test
    fun `saving a duplicate template name reports a message instead of throwing`() = runTest {
        val repo = EnergyAccountingRepository(db1)
        val cognitive = repo.observeActiveDimensions().first()
            .first { it.systemKey == SystemDimensionKey.COGNITIVE }

        repo.saveEntry(
            Entry(
                date = LocalDate.of(2026, 11, 12),
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "Morning routine",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 30,
                primaryDimension = cognitive,
                createdAtEpochMillis = 1000L,
            )
        )

        val viewModel = viewModel(repo)
        viewModel.saveTemplate(
            ActivityTemplate(
                activityName = "  MORNING ROUTINE ",
                defaultDirection = Direction.GAIN,
                defaultEnergyValue = 10,
                defaultPrimaryDimension = cognitive,
            )
        )

        assertEquals(ManageMessage.TEMPLATE_NAME_IN_USE, viewModel.message.value)
        assertEquals(1, repo.observeTemplates().first().size)
    }

    @Test
    fun `restore reschedules every restored reminder`() = runTest {
        val repo1 = EnergyAccountingRepository(db1)
        repo1.updateReminder(CheckInReminder(TimeSlot.EVENING, 21, 30, true))
        val json = BackupSerializer(db1).exportJson()

        val viewModel = viewModel(EnergyAccountingRepository(db2))
        viewModel.restore(json, RestoreMode.MERGE)

        val evening = scheduler.scheduled.firstOrNull { it.timeSlot == TimeSlot.EVENING }
        assertNotNull(evening)
        assertTrue(evening!!.isEnabled)
        assertEquals(21, evening.hour)
        assertEquals(30, evening.minute)
    }
}
