package com.empathie.energyaccounting.ui

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.ui.daily.DailyAccountScreen
import com.empathie.energyaccounting.ui.daily.DailyAccountViewModel
import com.empathie.energyaccounting.ui.theme.EmpathieTheme
import com.empathie.energyaccounting.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class DailyAccountDragTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var repo: EnergyAccountingRepository
    private lateinit var viewModel: DailyAccountViewModel
    private var showScreen by mutableStateOf(true)
    private val date = LocalDate.of(2026, 11, 12)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.empathie.energyaccounting.uitest", context.packageName)
        db = Room.inMemoryDatabaseBuilder(context, EnergyAccountingDatabase::class.java).allowMainThreadQueries().build()
        repo = EnergyAccountingRepository(db)
        runBlocking {
            db.ensureSeeded()
            val dimension = repo.observeActiveDimensions().first().first()
            listOf("A", "B", "C").forEachIndexed { index, name ->
                repo.saveEntry(
                    Entry(
                        date = date, timeSlot = TimeSlot.LATE_MORNING, activity = name,
                        situationalNote = null, direction = Direction.SPEND, energyValue = 10,
                        dimension = dimension, createdAtEpochMillis = (index + 1) * 100L,
                    )
                )
            }
        }
        viewModel = DailyAccountViewModel(repo, Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC))
        compose.setContent { if (showScreen) EmpathieTheme(ThemeMode.LIGHT) { DailyAccountScreen(viewModel) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("C").fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun tearDown() {
        compose.runOnIdle { showScreen = false }
        compose.waitForIdle()
        runBlocking {
            viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            db.withTransaction { }
        }
        db.close()
    }

    private fun names(slot: TimeSlot): List<String> = runBlocking {
        repo.observeDailyAccount(date).first().ledgerFor(slot).entries.map { it.activity }
    }

    private fun center(bounds: DpRect): Offset = with(compose.density) {
        Offset((bounds.left + bounds.right).toPx() / 2f, (bounds.top + bounds.bottom).toPx() / 2f)
    }

    private fun nearTop(bounds: DpRect): Offset = with(compose.density) {
        Offset((bounds.left + bounds.right).toPx() / 2f, bounds.top.toPx() + 2f)
    }

    private fun drag(from: Offset, to: Offset) {
        compose.onRoot().performTouchInput {
            down(from)
            advanceEventTime(700)
            moveTo(to, 100)
            up()
        }
    }

    @Test
    fun longPressMovesEntryWithinSlot() {
        val c = compose.onNodeWithText("C").getUnclippedBoundsInRoot()
        val a = compose.onNodeWithText("A").getUnclippedBoundsInRoot()
        drag(center(c), nearTop(a))
        compose.waitUntil(5_000) { names(TimeSlot.LATE_MORNING) == listOf("C", "A", "B") }
    }

    @Test
    fun longPressMovesEntryIntoEmptySlot() {
        val a = compose.onNodeWithText("A").getUnclippedBoundsInRoot()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val afternoon = compose.onNodeWithText(context.getString(R.string.time_slot_afternoon)).getUnclippedBoundsInRoot()
        drag(center(a), center(afternoon))
        compose.waitUntil(5_000) { names(TimeSlot.AFTERNOON) == listOf("A") }
        assertEquals(listOf("B", "C"), names(TimeSlot.LATE_MORNING))
    }

    @Test
    fun ordinaryTapStillOpensTheEditor() {
        compose.onNodeWithText("A").performClick()
        compose.waitUntil(5_000) { viewModel.draftState.value?.activity == "A" }
    }

    @Test
    fun cancelledDragDoesNotMoveEntry() {
        val c = compose.onNodeWithText("C").getUnclippedBoundsInRoot()
        val a = compose.onNodeWithText("A").getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput {
            down(center(c))
            advanceEventTime(700)
            moveTo(center(a), 100)
            cancel()
        }
        compose.waitForIdle()
        assertEquals(listOf("A", "B", "C"), names(TimeSlot.LATE_MORNING))
    }

    @Test
    fun releasingOutsideDoesNotReuseThePreviousDropTarget() {
        val c = compose.onNodeWithText("C").getUnclippedBoundsInRoot()
        val a = compose.onNodeWithText("A").getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput {
            down(center(c))
            advanceEventTime(700)
            moveTo(nearTop(a), 100)
            updatePointerTo(0, Offset(2f, 20f))
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("A", "B", "C"), names(TimeSlot.LATE_MORNING))
    }

    @Test
    fun accessibilityActionsMoveWithinAndAcrossSlots() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun action(label: String) {
            val actions = compose.onNodeWithText("C").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { assertTrue(actions.first { it.label == label }.action()) }
        }
        action(context.getString(R.string.move_entry_up))
        compose.waitUntil(5_000) { names(TimeSlot.LATE_MORNING) == listOf("A", "C", "B") }
        action(context.getString(R.string.move_entry_to_slot, context.getString(R.string.time_slot_afternoon)))
        compose.waitUntil(5_000) { names(TimeSlot.AFTERNOON) == listOf("C") }
    }

    @Test
    fun draggingAcrossAnOffscreenSlotMovesTheEntry() {
        runBlocking {
            val dimension = repo.observeActiveDimensions().first().first()
            repeat(12) { index ->
                repo.saveEntry(
                    Entry(
                        date = date, timeSlot = TimeSlot.LATE_MORNING, activity = "Extra $index",
                        situationalNote = null, direction = Direction.SPEND, energyValue = 10,
                        dimension = dimension, createdAtEpochMillis = 1000L + index,
                    )
                )
            }
        }
        compose.waitUntil(5_000) { viewModel.account.value.ledgerFor(TimeSlot.LATE_MORNING).entries.size == 15 }
        val start = center(compose.onNodeWithText("A").getUnclippedBoundsInRoot())
        val edgeY = with(compose.density) { compose.onRoot().getUnclippedBoundsInRoot().bottom.toPx() - 5f }
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performTouchInput {
                down(start)
                advanceEventTime(700)
                moveTo(Offset(start.x, edgeY), 100)
            }
            compose.mainClock.advanceTimeBy(6_000)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val afternoon = center(compose.onNodeWithText(context.getString(R.string.time_slot_afternoon)).getUnclippedBoundsInRoot())
            compose.onRoot().performTouchInput {
                moveTo(afternoon, 100)
                up()
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitUntil(5_000) { names(TimeSlot.AFTERNOON) == listOf("A") }
    }
}
