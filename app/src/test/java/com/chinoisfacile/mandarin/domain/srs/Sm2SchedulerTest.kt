package com.chinoisfacile.mandarin.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2SchedulerTest {

    private val scheduler = Sm2Scheduler()

    @Test
    fun `first successful review schedules 1 day and second schedules 6 days`() {
        val today = 20_000L
        val first = scheduler.calculateNextReview(
            current = Sm2State(),
            quality = 4,
            todayEpochDay = today,
        )
        assertEquals(1, first.repetitions)
        assertEquals(1, first.intervalDays)
        assertEquals(today + 1L, first.nextReviewEpochDay)

        val second = scheduler.calculateNextReview(
            current = first,
            quality = 4,
            todayEpochDay = today + 1L,
        )
        assertEquals(2, second.repetitions)
        assertEquals(6, second.intervalDays)
        assertEquals(today + 7L, second.nextReviewEpochDay)

        val third = scheduler.calculateNextReview(
            current = second,
            quality = 5,
            todayEpochDay = today + 7L,
        )
        assertEquals(3, third.repetitions)
        assertTrue(third.intervalDays >= 14)
        assertTrue(Sm2Scheduler.isMastered(third.repetitions, third.intervalDays))
    }

    @Test
    fun `low quality resets repetitions and interval to 1 day`() {
        val existing = Sm2State(
            repetitions = 4,
            easeFactor = 2.5,
            intervalDays = 15,
            nextReviewEpochDay = 20_015L,
        )
        val result = scheduler.calculateNextReview(
            current = existing,
            quality = 1,
            todayEpochDay = 20_015L,
        )
        assertEquals(0, result.repetitions)
        assertEquals(1, result.intervalDays)
        assertEquals(20_016L, result.nextReviewEpochDay)
        assertFalse(Sm2Scheduler.isMastered(result.repetitions, result.intervalDays))
    }

    @Test
    fun `ease factor never drops below 1_3`() {
        var state = Sm2State(repetitions = 2, easeFactor = 1.4, intervalDays = 6)
        repeat(10) {
            state = scheduler.calculateNextReview(
                current = state,
                quality = 0,
                todayEpochDay = 20_000L + it,
            )
        }
        assertEquals(Sm2State.MIN_EASE_FACTOR, state.easeFactor, 0.0001)
    }
}
