package com.chinoisfacile.mandarin.domain.progression

import com.chinoisfacile.mandarin.data.model.GrammarNote
import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.VocabWord

sealed interface ActivePathTarget {
    val unitId: String
    val lessonId: String
    val orderIndex: Int

    data class StepLesson(
        override val unitId: String,
        override val lessonId: String,
        override val orderIndex: Int,
    ) : ActivePathTarget

    data class Quiz(
        override val unitId: String,
        override val lessonId: String,
        override val orderIndex: Int,
    ) : ActivePathTarget
}

sealed interface StepLessonCardItem {
    val key: String

    data class WordCard(val word: VocabWord) : StepLessonCardItem {
        override val key: String get() = "word_${word.id}"
    }

    data class GrammarCard(val note: GrammarNote) : StepLessonCardItem {
        override val key: String get() = "grammar_${note.id}"
    }
}

object StepLessonDeckBuilder {
    /**
     * Interleaves each word card with its matching sentence-pattern card (when present),
     * avoiding duplicate grammar cards within the same step.
     */
    fun buildDeck(
        words: List<VocabWord>,
        notesByTrigger: Map<String, GrammarNote>,
    ): List<StepLessonCardItem> {
        val result = ArrayList<StepLessonCardItem>(words.size + notesByTrigger.size)
        val insertedNoteIds = HashSet<String>()
        for (word in words) {
            result += StepLessonCardItem.WordCard(word)
            val note = notesByTrigger[word.hanzi]
            if (note != null && insertedNoteIds.add(note.id)) {
                result += StepLessonCardItem.GrammarCard(note)
            }
        }
        return result
    }
}

object UnitProgressionResolver {

    private val QUIZ_PREFIX_REGEX = Regex("^(?:Lesson|Quiz)\\b", RegexOption.IGNORE_CASE)
    private val STEP_THEME_PREFIX_REGEX = Regex("^(?:Lesson|Quiz)\\s+\\d+\\s*[—–:-]\\s*", RegexOption.IGNORE_CASE)
    private val STANDALONE_STEP_REGEX = Regex("^(?:Lesson|Quiz)\\s+\\d+$", RegexOption.IGNORE_CASE)

    fun formatQuizTitle(rawTitle: String): String =
        rawTitle.trim().replaceFirst(QUIZ_PREFIX_REGEX, "Quiz")

    fun formatQuizShortLabel(orderIndex: Int): String = "Quiz $orderIndex"

    fun formatStepLessonLabel(orderIndex: Int): String = "Lesson $orderIndex"

    fun extractStepTheme(rawTitle: String): String {
        val trimmed = rawTitle.trim()
        if (trimmed.isEmpty() || STANDALONE_STEP_REGEX.matches(trimmed)) {
            return ""
        }
        val stripped = trimmed.replaceFirst(STEP_THEME_PREFIX_REGEX, "").trim()
        return if (stripped == trimmed && QUIZ_PREFIX_REGEX.containsMatchIn(trimmed)) "" else stripped
    }

    fun formatStepLessonTitle(orderIndex: Int, rawTitle: String): String {
        val theme = extractStepTheme(rawTitle)
        return if (theme.isNotBlank()) "Lesson $orderIndex — $theme" else "Lesson $orderIndex"
    }

    fun resolveUnits(
        units: List<LearningUnit>,
        completedStepLessonIds: Set<String>,
    ): List<LearningUnit> {
        val sortedUnits = units.sortedBy { it.orderIndex }
        val resolved = ArrayList<LearningUnit>(sortedUnits.size)
        var isVeryFirstStep = true
        var previousQuizCompleted = false

        for (unit in sortedUnits) {
            val sortedSteps = unit.lessons.sortedBy { it.orderIndex }
            val resolvedSteps = ArrayList<com.chinoisfacile.mandarin.data.model.LessonSummary>(sortedSteps.size)

            for (step in sortedSteps) {
                val isStepLessonCompleted = step.id in completedStepLessonIds
                val hasLegacyOrExistingQuizProgress = step.isUnlocked || step.isCompleted
                val isStepLessonUnlocked = isVeryFirstStep ||
                    previousQuizCompleted ||
                    isStepLessonCompleted ||
                    hasLegacyOrExistingQuizProgress
                val effectiveQuizUnlocked = step.isUnlocked || step.isCompleted || isStepLessonCompleted

                resolvedSteps += step.copy(
                    title = formatQuizTitle(step.title),
                    stepTheme = extractStepTheme(step.title),
                    isUnlocked = effectiveQuizUnlocked,
                    isStepLessonUnlocked = isStepLessonUnlocked,
                    isStepLessonCompleted = isStepLessonCompleted,
                )

                isVeryFirstStep = false
                previousQuizCompleted = step.isCompleted
            }

            resolved += unit.copy(
                lessons = resolvedSteps,
                totalWordCount = resolvedSteps.sumOf { it.wordCount },
            )
        }

        return resolved
    }

    fun findActiveTarget(units: List<LearningUnit>): ActivePathTarget? {
        for (unit in units) {
            for (step in unit.lessons) {
                if (step.isStepLessonUnlocked && !step.isStepLessonCompleted && !step.isUnlocked && !step.isCompleted) {
                    return ActivePathTarget.StepLesson(
                        unitId = unit.id,
                        lessonId = step.id,
                        orderIndex = step.orderIndex,
                    )
                }
                if (step.isUnlocked && !step.isCompleted) {
                    return ActivePathTarget.Quiz(
                        unitId = unit.id,
                        lessonId = step.id,
                        orderIndex = step.orderIndex,
                    )
                }
            }
        }

        val allSteps = units.flatMap { it.lessons }
        val lastUnlockedQuiz = allSteps.lastOrNull { it.isUnlocked }
        if (lastUnlockedQuiz != null) {
            return ActivePathTarget.Quiz(
                unitId = lastUnlockedQuiz.unitId,
                lessonId = lastUnlockedQuiz.id,
                orderIndex = lastUnlockedQuiz.orderIndex,
            )
        }

        val lastUnlockedStepLesson = allSteps.lastOrNull { it.isStepLessonUnlocked }
        if (lastUnlockedStepLesson != null) {
            return ActivePathTarget.StepLesson(
                unitId = lastUnlockedStepLesson.unitId,
                lessonId = lastUnlockedStepLesson.id,
                orderIndex = lastUnlockedStepLesson.orderIndex,
            )
        }

        return null
    }
}
