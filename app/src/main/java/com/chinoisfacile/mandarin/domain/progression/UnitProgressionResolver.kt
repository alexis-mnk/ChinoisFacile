package com.chinoisfacile.mandarin.domain.progression

import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.VocabWord

sealed interface ActivePathTarget {
    val unitId: String

    data class UnitLesson(override val unitId: String) : ActivePathTarget

    data class Quiz(
        override val unitId: String,
        val lessonId: String,
    ) : ActivePathTarget
}

data class UnitLessonQuizGroup(
    val lessonId: String,
    val orderIndex: Int,
    val shortLabel: String,
    val title: String,
    val isUnlocked: Boolean,
    val isCompleted: Boolean,
    val words: List<VocabWord>,
)

data class UnitLessonWordItem(
    val word: VocabWord,
    val lessonId: String,
    val quizOrderIndex: Int,
    val quizShortLabel: String,
    val quizTitle: String,
)

sealed interface UnitLessonFilterSelection {
    data class SingleQuiz(val lessonId: String) : UnitLessonFilterSelection

    data object All : UnitLessonFilterSelection
}

object UnitProgressionResolver {

    private val QUIZ_PREFIX_REGEX = Regex("^(?:Lesson|Quiz)\\b", RegexOption.IGNORE_CASE)

    fun formatQuizTitle(rawTitle: String): String =
        rawTitle.trim().replaceFirst(QUIZ_PREFIX_REGEX, "Quiz")

    fun formatQuizShortLabel(orderIndex: Int): String = "Quiz $orderIndex"

    fun resolveUnits(
        units: List<LearningUnit>,
        completedUnitLessonIds: Set<String>,
    ): List<LearningUnit> {
        val sortedUnits = units.sortedBy { it.orderIndex }
        val resolved = ArrayList<LearningUnit>(sortedUnits.size)

        for ((index, unit) in sortedUnits.withIndex()) {
            val sortedQuizzes = unit.lessons.sortedBy { it.orderIndex }
            val previousUnit = resolved.getOrNull(index - 1)
            val prevUnitCompleted = previousUnit != null &&
                previousUnit.lessons.isNotEmpty() &&
                previousUnit.lessons.last().isCompleted
            val hasExistingQuizProgress = sortedQuizzes.any { it.isUnlocked || it.isCompleted }
            val isLessonCompleted = unit.id in completedUnitLessonIds
            val isLessonUnlocked = (index == 0) ||
                prevUnitCompleted ||
                hasExistingQuizProgress ||
                isLessonCompleted

            val resolvedQuizzes = sortedQuizzes.mapIndexed { quizIdx, quiz ->
                val effectiveUnlocked = quiz.isUnlocked ||
                    quiz.isCompleted ||
                    (quizIdx == 0 && isLessonCompleted)
                quiz.copy(
                    title = formatQuizTitle(quiz.title),
                    isUnlocked = effectiveUnlocked,
                    isFirstInUnit = quizIdx == 0,
                )
            }

            resolved += unit.copy(
                lessons = resolvedQuizzes,
                isLessonUnlocked = isLessonUnlocked,
                isLessonCompleted = isLessonCompleted,
                totalWordCount = resolvedQuizzes.sumOf { it.wordCount },
            )
        }

        return resolved
    }

    fun findActiveTarget(units: List<LearningUnit>): ActivePathTarget? {
        for (unit in units) {
            if (unit.isLessonUnlocked && !unit.isLessonCompleted && unit.lessons.none { it.isCompleted }) {
                return ActivePathTarget.UnitLesson(unit.id)
            }
            val nextQuiz = unit.lessons.firstOrNull { it.isUnlocked && !it.isCompleted }
            if (nextQuiz != null) {
                return ActivePathTarget.Quiz(unitId = unit.id, lessonId = nextQuiz.id)
            }
        }

        val lastUnlockedQuiz = units
            .flatMap { it.lessons }
            .lastOrNull { it.isUnlocked }
        if (lastUnlockedQuiz != null) {
            return ActivePathTarget.Quiz(
                unitId = lastUnlockedQuiz.unitId,
                lessonId = lastUnlockedQuiz.id,
            )
        }

        return units.lastOrNull { it.isLessonUnlocked }?.let { ActivePathTarget.UnitLesson(it.id) }
    }

    fun defaultFilterSelection(groups: List<UnitLessonQuizGroup>): UnitLessonFilterSelection {
        val targetGroup = groups.firstOrNull { !it.isCompleted } ?: groups.firstOrNull()
        return if (targetGroup != null) {
            UnitLessonFilterSelection.SingleQuiz(targetGroup.lessonId)
        } else {
            UnitLessonFilterSelection.All
        }
    }

    fun filterWords(
        groups: List<UnitLessonQuizGroup>,
        selection: UnitLessonFilterSelection,
    ): List<UnitLessonWordItem> {
        val selectedGroups = when (selection) {
            is UnitLessonFilterSelection.SingleQuiz -> groups.filter { it.lessonId == selection.lessonId }
            UnitLessonFilterSelection.All -> groups
        }
        return selectedGroups.flatMap { group ->
            group.words.map { word ->
                UnitLessonWordItem(
                    word = word,
                    lessonId = group.lessonId,
                    quizOrderIndex = group.orderIndex,
                    quizShortLabel = group.shortLabel,
                    quizTitle = group.title,
                )
            }
        }
    }

    fun resolveLaunchQuizTarget(
        groups: List<UnitLessonQuizGroup>,
        selection: UnitLessonFilterSelection,
        isUnitLessonCompleted: Boolean,
    ): UnitLessonQuizGroup? {
        if (groups.isEmpty()) return null

        fun isEffectivelyUnlocked(index: Int, group: UnitLessonQuizGroup): Boolean =
            group.isUnlocked || group.isCompleted || (index == 0 && isUnitLessonCompleted)

        if (selection is UnitLessonFilterSelection.SingleQuiz) {
            val selectedIndex = groups.indexOfFirst { it.lessonId == selection.lessonId }
            if (selectedIndex >= 0 && isEffectivelyUnlocked(selectedIndex, groups[selectedIndex])) {
                return groups[selectedIndex]
            }
        }

        val firstPendingUnlocked = groups.withIndex().firstOrNull { (idx, group) ->
            isEffectivelyUnlocked(idx, group) && !group.isCompleted
        }?.value
        if (firstPendingUnlocked != null) {
            return firstPendingUnlocked
        }

        return groups.withIndex().firstOrNull { (idx, group) ->
            isEffectivelyUnlocked(idx, group)
        }?.value
    }
}
