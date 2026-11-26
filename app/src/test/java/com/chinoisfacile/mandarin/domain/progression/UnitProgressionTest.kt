package com.chinoisfacile.mandarin.domain.progression

import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.LessonSummary
import com.chinoisfacile.mandarin.data.model.VocabWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitProgressionTest {

    private fun word(id: String, lessonId: String, hanzi: String) = VocabWord(
        id = id,
        lessonId = lessonId,
        hskLevel = 1,
        hanzi = hanzi,
        pinyin = "ni",
        english = "test",
        exampleHanzi = hanzi,
        examplePinyin = "ni",
        exampleEnglish = "test",
        exampleTokens = listOf(hanzi),
    )

    private fun quiz(
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
        u1q1Unlocked: Boolean = false,
        u1q1Completed: Boolean = false,
        u1q2Unlocked: Boolean = false,
        u1q2Completed: Boolean = false,
        u2q1Unlocked: Boolean = false,
        u2q1Completed: Boolean = false,
    ): List<LearningUnit> = listOf(
        LearningUnit(
            id = "u1",
            hskLevel = 1,
            orderIndex = 1,
            title = "Unit 1 — First Steps",
            description = "Desc 1",
            lessons = listOf(
                quiz("u1_q1", "u1", 1, "Lesson 1 — Greetings", u1q1Unlocked, u1q1Completed, 5),
                quiz("u1_q2", "u1", 2, "Quiz 2 — Courtesy", u1q2Unlocked, u1q2Completed, 5),
            ),
        ),
        LearningUnit(
            id = "u2",
            hskLevel = 1,
            orderIndex = 2,
            title = "Unit 2 — Family",
            description = "Desc 2",
            lessons = listOf(
                quiz("u2_q6", "u2", 6, "Quiz 6 — Family", u2q1Unlocked, u2q1Completed, 5),
            ),
        ),
    )

    @Test
    fun formatQuizTitleNormalizesLessonAndQuizPrefixesPreservingNumber() {
        assertEquals(
            "Quiz 1 — Greetings & Politeness",
            UnitProgressionResolver.formatQuizTitle("Lesson 1 — Greetings & Politeness"),
        )
        assertEquals(
            "Quiz 33 — Law & Governance",
            UnitProgressionResolver.formatQuizTitle("quiz 33 — Law & Governance"),
        )
        assertEquals("Quiz 6", UnitProgressionResolver.formatQuizShortLabel(6))
    }

    @Test
    fun freshStartUnlocksUnit1LessonOnlyAndKeepsQuiz1Locked() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(),
            completedUnitLessonIds = emptySet(),
        )

        val u1 = resolved[0]
        assertTrue(u1.isLessonUnlocked)
        assertFalse(u1.isLessonCompleted)
        assertEquals(10, u1.totalWordCount)
        assertEquals("Quiz 1 — Greetings", u1.lessons[0].title)
        assertTrue(u1.lessons[0].isFirstInUnit)
        assertFalse(u1.lessons[0].isUnlocked)
        assertFalse(u1.lessons[1].isFirstInUnit)
        assertFalse(u1.lessons[1].isUnlocked)

        val u2 = resolved[1]
        assertFalse(u2.isLessonUnlocked)
        assertFalse(u2.lessons[0].isUnlocked)

        assertEquals(ActivePathTarget.UnitLesson("u1"), UnitProgressionResolver.findActiveTarget(resolved))
    }

    @Test
    fun completingUnitLessonUnlocksFirstQuizAndKeepsLessonReopenable() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(),
            completedUnitLessonIds = setOf("u1"),
        )

        val u1 = resolved[0]
        assertTrue(u1.isLessonUnlocked)
        assertTrue(u1.isLessonCompleted)
        assertTrue(u1.lessons[0].isUnlocked)
        assertFalse(u1.lessons[1].isUnlocked)

        assertEquals(
            ActivePathTarget.Quiz("u1", "u1_q1"),
            UnitProgressionResolver.findActiveTarget(resolved),
        )
    }

    @Test
    fun completingLastQuizOfUnit1UnlocksUnit2LessonWithoutPrematurelyUnlockingQuiz6() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(
                u1q1Unlocked = true,
                u1q1Completed = true,
                u1q2Unlocked = true,
                u1q2Completed = true,
            ),
            completedUnitLessonIds = setOf("u1"),
        )

        val u2 = resolved[1]
        assertTrue(u2.isLessonUnlocked)
        assertFalse(u2.isLessonCompleted)
        assertFalse(u2.lessons[0].isUnlocked)

        assertEquals(ActivePathTarget.UnitLesson("u2"), UnitProgressionResolver.findActiveTarget(resolved))
    }

    @Test
    fun existingQuizProgressKeepsUnitLessonAccessibleWithoutBlockingQuizzes() {
        val resolved = UnitProgressionResolver.resolveUnits(
            units = sampleUnits(
                u1q1Unlocked = true,
                u1q1Completed = true,
                u1q2Unlocked = true,
                u1q2Completed = false,
            ),
            completedUnitLessonIds = emptySet(),
        )

        val u1 = resolved[0]
        assertTrue(u1.isLessonUnlocked)
        assertFalse(u1.isLessonCompleted)
        assertTrue(u1.lessons[0].isUnlocked)
        assertTrue(u1.lessons[1].isUnlocked)

        assertEquals(
            ActivePathTarget.Quiz("u1", "u1_q2"),
            UnitProgressionResolver.findActiveTarget(resolved),
        )
    }

    @Test
    fun quizGroupFilteringAndLaunchTargetFollowSelectedOrPendingUnlockedQuiz() {
        val groups = listOf(
            UnitLessonQuizGroup(
                lessonId = "u2_q6",
                orderIndex = 6,
                shortLabel = "Quiz 6",
                title = "Quiz 6 — Family",
                isUnlocked = true,
                isCompleted = true,
                words = listOf(word("w1", "u2_q6", "家"), word("w2", "u2_q6", "爸")),
            ),
            UnitLessonQuizGroup(
                lessonId = "u2_q7",
                orderIndex = 7,
                shortLabel = "Quiz 7",
                title = "Quiz 7 — People",
                isUnlocked = true,
                isCompleted = false,
                words = listOf(word("w3", "u2_q7", "人")),
            ),
            UnitLessonQuizGroup(
                lessonId = "u2_q8",
                orderIndex = 8,
                shortLabel = "Quiz 8",
                title = "Quiz 8 — Verbs",
                isUnlocked = false,
                isCompleted = false,
                words = listOf(word("w4", "u2_q8", "去")),
            ),
        )

        assertEquals(
            UnitLessonFilterSelection.SingleQuiz("u2_q7"),
            UnitProgressionResolver.defaultFilterSelection(groups),
        )

        assertEquals(
            listOf("w3"),
            UnitProgressionResolver.filterWords(groups, UnitLessonFilterSelection.SingleQuiz("u2_q7"))
                .map { it.word.id },
        )
        assertEquals(
            listOf("w1", "w2", "w3", "w4"),
            UnitProgressionResolver.filterWords(groups, UnitLessonFilterSelection.All)
                .map { it.word.id },
        )

        assertEquals(
            "u2_q7",
            UnitProgressionResolver.resolveLaunchQuizTarget(
                groups = groups,
                selection = UnitLessonFilterSelection.SingleQuiz("u2_q7"),
                isUnitLessonCompleted = true,
            )?.lessonId,
        )
        assertEquals(
            "u2_q6",
            UnitProgressionResolver.resolveLaunchQuizTarget(
                groups = groups,
                selection = UnitLessonFilterSelection.SingleQuiz("u2_q6"),
                isUnitLessonCompleted = true,
            )?.lessonId,
        )
        assertEquals(
            "u2_q7",
            UnitProgressionResolver.resolveLaunchQuizTarget(
                groups = groups,
                selection = UnitLessonFilterSelection.SingleQuiz("u2_q8"),
                isUnitLessonCompleted = true,
            )?.lessonId,
        )
        assertEquals(
            "u2_q7",
            UnitProgressionResolver.resolveLaunchQuizTarget(
                groups = groups,
                selection = UnitLessonFilterSelection.All,
                isUnitLessonCompleted = true,
            )?.lessonId,
        )

        val lockedGroups = groups.map { it.copy(isUnlocked = false, isCompleted = false) }
        assertNull(
            UnitProgressionResolver.resolveLaunchQuizTarget(
                groups = lockedGroups,
                selection = UnitLessonFilterSelection.SingleQuiz("u2_q6"),
                isUnitLessonCompleted = false,
            ),
        )
        assertEquals(
            "u2_q6",
            UnitProgressionResolver.resolveLaunchQuizTarget(
                groups = lockedGroups,
                selection = UnitLessonFilterSelection.All,
                isUnitLessonCompleted = true,
            )?.lessonId,
        )
    }
}
