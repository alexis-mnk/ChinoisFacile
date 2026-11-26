package com.chinoisfacile.mandarin.domain.progression

import androidx.compose.runtime.Immutable
import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.LessonSummary

@Immutable
sealed interface HomePathItem {
    val key: String
    val contentType: String

    @Immutable
    data class UnitHeader(
        val unit: LearningUnit,
        val isFirstUnit: Boolean,
    ) : HomePathItem {
        override val key: String get() = "unit_banner_${unit.id}"
        override val contentType: String get() = "unit_banner"
    }

    @Immutable
    data class StepRow(
        val unitId: String,
        val step: LessonSummary,
        val hasIncomingFromPreviousStep: Boolean,
        val isIncomingTransitionUnlocked: Boolean,
        val hasOutgoingToNextStep: Boolean,
        val isOutgoingTransitionUnlocked: Boolean,
    ) : HomePathItem {
        override val key: String get() = stepKey(step.id)
        override val contentType: String get() = "step_row"
    }

    companion object {
        fun stepKey(lessonId: String): String = "step_$lessonId"
    }
}

object HomePathFlattener {

    fun flatten(units: List<LearningUnit>): List<HomePathItem> {
        if (units.isEmpty()) return emptyList()
        val totalItems = units.sumOf { 1 + it.lessons.size }
        val result = ArrayList<HomePathItem>(totalItems)

        units.forEachIndexed { unitIndex, unit ->
            if (unit.lessons.isEmpty()) return@forEachIndexed
            result += HomePathItem.UnitHeader(
                unit = unit,
                isFirstUnit = unitIndex == 0,
            )
            val steps = unit.lessons
            steps.forEachIndexed { stepIndex, step ->
                val prevStep = steps.getOrNull(stepIndex - 1)
                val nextStep = steps.getOrNull(stepIndex + 1)
                result += HomePathItem.StepRow(
                    unitId = unit.id,
                    step = step,
                    hasIncomingFromPreviousStep = prevStep != null,
                    isIncomingTransitionUnlocked = prevStep != null &&
                        prevStep.isCompleted &&
                        step.isStepLessonUnlocked,
                    hasOutgoingToNextStep = nextStep != null,
                    isOutgoingTransitionUnlocked = nextStep != null &&
                        step.isCompleted &&
                        nextStep.isStepLessonUnlocked,
                )
            }
        }
        return result
    }

    fun activeStepKey(activeTarget: ActivePathTarget?): String? =
        activeTarget?.let { HomePathItem.stepKey(it.lessonId) }

    fun findActiveItemIndex(
        items: List<HomePathItem>,
        activeTarget: ActivePathTarget?,
        headerItemCount: Int,
    ): Int {
        if (activeTarget == null) return -1
        val itemIndex = items.indexOfFirst {
            it is HomePathItem.StepRow && it.step.id == activeTarget.lessonId
        }
        return if (itemIndex >= 0) headerItemCount + itemIndex else -1
    }
}
