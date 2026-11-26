package com.chinoisfacile.mandarin.domain.progression

import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.LessonSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitProgressionTest {

    private fun step(
        id: String,
        unitId: String,
        orderIndex: Int,
        title: String,
        isUnlocked: Boolean = false,
        isCompleted: Boolean = false,
        wordCount: Int = 5,
    ) = LessonSummary(
        id = id,
        unitId = unitId,
        hskLevel = 1,
        orderIndex = orderIndex,
        title = title,
        isUnlocked = isUnlocked,
        isCompleted = isCompleted,
        bestScore = if (isCompleted) 100 else 0,
        completedCount = if (isCompleted) 1 else 0,
        wordCount = wordCount,
    )

    private fun sampleUnits(
        u1s1QuizUnlocked: Boolean = false,
        u1s1QuizCompleted: Boolean = false,
        u1s2QuizUnlocked: Boolean = false,
        u1s2QuizCompleted: Boolean = false,
        u2s1QuizUnlocked: Boolean = false,
        u2s1QuizCompleted: Boolean = false,
    ): List<LearningUnit> = listOf(
        LearningUnit(
            id = "u1",
            hskLevel = 1,
            orderIndex = 1,
            title = "Unit 1 — First Steps",
            description = "Desc 1",
            lessons = listOf(
                step("u1_q1", "u1", 1, "Lesson 1 — Greetings & Politeness", u1s1QuizUnlocked, u1s1QuizCompleted, 5),
                step("u1_q2", "u1", 2, "Quiz 2 — Courtesy", u1s2QuizUnlocked, u1s2QuizCompleted, 5),
            ),
        ),
        LearningUnit(
            id = "u2",
            hskLevel = 1,
            orderIndex = 2,
            title = "Unit 2 — Family",
            description = "Desc 2",
            lessons = listOf(
                step("u2_q6", "u2", 6, "Quiz 6 — Family", u2s1QuizUnlocked, u2s1QuizCompleted, 5),
            ),
        ),
    )

    @Test
    fun formattingHelpersNormalizeStepThemesAndLabels() {
        assertEquals(
            "Quiz 1 — Greetings & Politeness",
            UnitProgressionResolver.formatQuizTitle("Lesson 1 — Greetings & Politeness"),
        )
        assertEquals(
            "Quiz 33 — Law & Governance",
            UnitProgressionResolver.formatQuizTitle("quiz 33 — Law & Governance"),
        )
        assertEquals("Quiz 6", UnitProgressionResolver.formatQuizShortLabel(6))
        assertEquals("Lesson 6", UnitProgressionResolver.formatStepLessonLabel(6))
        assertEquals(
            "Greetings & Politeness",
            UnitProgressionResolver.extractStepTheme("Quiz 1 — Greetings & Politeness"),
        )
        assertEquals(
            "Greetings & Politeness",
            UnitProgressionResolver.extractStepTheme("Lesson 1 — Greetings & Politeness"),
        )
        assertEquals("", UnitProgressionResolver.extractStepTheme("Quiz 4"))
        assertEquals(
            "Lesson 1 — Greetings & Politeness",
            UnitProgressionResolver.formatStepLessonTitle(1, "Quiz 1 — Greetings & Politeness"),
        )
        assertEquals(
            "Lesson 4",
            UnitProgressionResolver.formatStepLessonTitle(4, "Quiz 4"),
        )
    }

    @Test
    fun freshStartUnlocksOnlyStep1LessonAndKeepsQuiz1AndLaterStepsLocked() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(),
            completedStepLessonIds = emptySet(),
        )

        val u1 = resolved[0]
        assertEquals(10, u1.totalWordCount)

        val step1 = u1.lessons[0]
        assertEquals("Quiz 1 — Greetings & Politeness", step1.title)
        assertEquals("Greetings & Politeness", step1.stepTheme)
        assertTrue(step1.isStepLessonUnlocked)
        assertFalse(step1.isStepLessonCompleted)
        assertFalse(step1.isUnlocked)
        assertFalse(step1.isCompleted)

        val step2 = u1.lessons[1]
        assertFalse(step2.isStepLessonUnlocked)
        assertFalse(step2.isStepLessonCompleted)
        assertFalse(step2.isUnlocked)

        val step6 = resolved[1].lessons[0]
        assertFalse(step6.isStepLessonUnlocked)
        assertFalse(step6.isUnlocked)

        assertEquals(
            ActivePathTarget.StepLesson(unitId = "u1", lessonId = "u1_q1", orderIndex = 1),
            UnitProgressionResolver.findActiveTarget(resolved),
        )
    }

    @Test
    fun completingStep1LessonUnlocksQuiz1WhileKeepingStep2Locked() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(),
            completedStepLessonIds = setOf("u1_q1"),
        )

        val step1 = resolved[0].lessons[0]
        assertTrue(step1.isStepLessonUnlocked)
        assertTrue(step1.isStepLessonCompleted)
        assertTrue(step1.isUnlocked)
        assertFalse(step1.isCompleted)

        val step2 = resolved[0].lessons[1]
        assertFalse(step2.isStepLessonUnlocked)
        assertFalse(step2.isUnlocked)

        assertEquals(
            ActivePathTarget.Quiz(unitId = "u1", lessonId = "u1_q1", orderIndex = 1),
            UnitProgressionResolver.findActiveTarget(resolved),
        )
    }

    @Test
    fun completingQuiz1UnlocksStep2LessonWithoutPrematurelyUnlockingQuiz2() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(
                u1s1QuizUnlocked = true,
                u1s1QuizCompleted = true,
            ),
            completedStepLessonIds = setOf("u1_q1"),
        )

        val step1 = resolved[0].lessons[0]
        assertTrue(step1.isCompleted)
        assertTrue(step1.isStepLessonCompleted)

        val step2 = resolved[0].lessons[1]
        assertTrue(step2.isStepLessonUnlocked)
        assertFalse(step2.isStepLessonCompleted)
        assertFalse(step2.isUnlocked)

        assertEquals(
            ActivePathTarget.StepLesson(unitId = "u1", lessonId = "u1_q2", orderIndex = 2),
            UnitProgressionResolver.findActiveTarget(resolved),
        )
    }

    @Test
    fun completingLastQuizOfUnit1UnlocksUnit2FirstLessonWithoutUnlockingQuiz6() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(
                u1s1QuizUnlocked = true,
                u1s1QuizCompleted = true,
                u1s2QuizUnlocked = true,
                u1s2QuizCompleted = true,
            ),
            completedStepLessonIds = setOf("u1_q1", "u1_q2"),
        )

        val step6 = resolved[1].lessons[0]
        assertTrue(step6.isStepLessonUnlocked)
        assertFalse(step6.isStepLessonCompleted)
        assertFalse(step6.isUnlocked)

        assertEquals(
            ActivePathTarget.StepLesson(unitId = "u2", lessonId = "u2_q6", orderIndex = 6),
            UnitProgressionResolver.findActiveTarget(resolved),
        )
    }

    @Test
    fun legacyQuizProgressKeepsStepLessonAccessibleAndDoesNotRelockUnlockedQuizzes() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(
                u1s1QuizUnlocked = true,
                u1s1QuizCompleted = true,
                u1s2QuizUnlocked = true,
                u1s2QuizCompleted = false,
            ),
            completedStepLessonIds = emptySet(),
        )

        val step1 = resolved[0].lessons[0]
        assertTrue(step1.isStepLessonUnlocked)
        assertTrue(step1.isUnlocked)
        assertTrue(step1.isCompleted)

        val step2 = resolved[0].lessons[1]
        assertTrue(step2.isStepLessonUnlocked)
        assertTrue(step2.isUnlocked)
        assertFalse(step2.isCompleted)

        assertEquals(
            ActivePathTarget.Quiz(unitId = "u1", lessonId = "u1_q2", orderIndex = 2),
            UnitProgressionResolver.findActiveTarget(resolved),
        )
    }

    @Test
    fun whenAllStepsCompletedActiveTargetFallsBackToLastUnlockedQuiz() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(
                u1s1QuizUnlocked = true,
                u1s1QuizCompleted = true,
                u1s2QuizUnlocked = true,
                u1s2QuizCompleted = true,
                u2s1QuizUnlocked = true,
                u2s1QuizCompleted = true,
            ),
            completedStepLessonIds = setOf("u1_q1", "u1_q2", "u2_q6"),
        )

        assertEquals(
            ActivePathTarget.Quiz(unitId = "u2", lessonId = "u2_q6", orderIndex = 6),
            UnitProgressionResolver.findActiveTarget(resolved),
        )
    }
}
