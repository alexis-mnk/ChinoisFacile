package com.chinoisfacile.mandarin.domain.progression

import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.LessonSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePathFlattenerTest {

    private fun step(
        id: String,
        unitId: String,
        orderIndex: Int,
        isStepLessonUnlocked: Boolean = false,
        isQuizUnlocked: Boolean = false,
        isQuizCompleted: Boolean = false,
    ) = LessonSummary(
        id = id,
        unitId = unitId,
        hskLevel = 6,
        orderIndex = orderIndex,
        title = "Quiz $orderIndex — Topic $orderIndex",
        stepTheme = "Topic $orderIndex",
        isUnlocked = isQuizUnlocked,
        isCompleted = isQuizCompleted,
        isStepLessonUnlocked = isStepLessonUnlocked,
        isStepLessonCompleted = isQuizUnlocked || isQuizCompleted,
        bestScore = if (isQuizCompleted) 100 else 0,
        completedCount = if (isQuizCompleted) 1 else 0,
        wordCount = 8,
    )

    @Test
    fun flattenProducesUniqueKeysAndExactIntraAndInterUnitConnectorStates() {
        val units = listOf(
            LearningUnit(
                id = "u1",
                hskLevel = 6,
                orderIndex = 1,
                title = "Unit 1",
                description = "First unit",
                lessons = listOf(
                    step("l1", "u1", 1, isStepLessonUnlocked = true, isQuizUnlocked = true, isQuizCompleted = true),
                    step("l2", "u1", 2, isStepLessonUnlocked = true, isQuizUnlocked = false, isQuizCompleted = false),
                    step("l3", "u1", 3, isStepLessonUnlocked = false, isQuizUnlocked = false, isQuizCompleted = false),
                ),
            ),
            LearningUnit(
                id = "u2",
                hskLevel = 6,
                orderIndex = 2,
                title = "Unit 2",
                description = "Second unit",
                lessons = (4..35).map { idx ->
                    step("l$idx", "u2", idx)
                },
            ),
        )

        val items = HomePathFlattener.flatten(units)
        assertEquals(1 + 3 + 1 + 32, items.size)
        assertEquals(items.size, items.map { it.key }.distinct().size)

        val u1Header = items[0] as HomePathItem.UnitHeader
        assertTrue(u1Header.isFirstUnit)

        val step1 = items[1] as HomePathItem.StepRow
        assertFalse(step1.hasIncomingFromPreviousStep)
        assertTrue(step1.hasOutgoingToNextStep)
        assertTrue(step1.isOutgoingTransitionUnlocked)

        val step2 = items[2] as HomePathItem.StepRow
        assertTrue(step2.hasIncomingFromPreviousStep)
        assertTrue(step2.isIncomingTransitionUnlocked)
        assertTrue(step2.hasOutgoingToNextStep)
        assertFalse(step2.isOutgoingTransitionUnlocked)

        val step3 = items[3] as HomePathItem.StepRow
        assertTrue(step3.hasIncomingFromPreviousStep)
        assertFalse(step3.isIncomingTransitionUnlocked)
        assertFalse(step3.hasOutgoingToNextStep)

        val u2Header = items[4] as HomePathItem.UnitHeader
        assertFalse(u2Header.isFirstUnit)

        val step4 = items[5] as HomePathItem.StepRow
        assertFalse(step4.hasIncomingFromPreviousStep)
        assertTrue(step4.hasOutgoingToNextStep)

        val lastStep = items.last() as HomePathItem.StepRow
        assertTrue(lastStep.hasIncomingFromPreviousStep)
        assertFalse(lastStep.hasOutgoingToNextStep)
    }

    @Test
    fun findActiveItemIndexLocatesDeepStepIncludingHeaderOffset() {
        val units = listOf(
            LearningUnit(
                id = "u1",
                hskLevel = 6,
                orderIndex = 1,
                title = "Unit 1",
                description = "Desc",
                lessons = (1..32).map { idx ->
                    step("l$idx", "u1", idx, isStepLessonUnlocked = idx == 1)
                },
            ),
        )

        val items = HomePathFlattener.flatten(units)
        val deepTarget = ActivePathTarget.StepLesson(unitId = "u1", lessonId = "l25", orderIndex = 25)

        // items index for l25 is 1 (UnitHeader) + 24 = 25; with 3 screen header items -> 28
        assertEquals(28, HomePathFlattener.findActiveItemIndex(items, deepTarget, headerItemCount = 3))
        assertEquals("step_l25", HomePathFlattener.activeStepKey(deepTarget))
        assertEquals(-1, HomePathFlattener.findActiveItemIndex(items, null, headerItemCount = 3))
        assertNull(HomePathFlattener.activeStepKey(null))
    }
}
