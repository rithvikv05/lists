package com.lists.app.data.model

import kotlin.math.roundToInt

sealed class DisplayBadge {
    data class Text(val label: String) : DisplayBadge()
    data class Numeric(val value: Float, val isOutOfTen: Boolean) : DisplayBadge() {
        val formatted: String get() = ((value * 10).roundToInt() / 10f).toString()
    }
}

object RatingConverter {
    const val REVIEW_NONE = 0
    const val REVIEW_SKIP = 1
    const val REVIEW_TIMEPASS = 2
    const val REVIEW_GO_FOR_IT = 3
    const val REVIEW_PERFECTION = 4

    fun getReviewLabel(reviewRating: Int): String? {
        return when (reviewRating) {
            REVIEW_SKIP -> "Skip"
            REVIEW_TIMEPASS -> "Timepass"
            REVIEW_GO_FOR_IT -> "Go for It"
            REVIEW_PERFECTION -> "Perfection"
            else -> null
        }
    }

    fun fivePointToFourWord(rating: Float): Int {
        if (rating <= 0f) return REVIEW_NONE
        return when {
            rating < 2.5f -> REVIEW_SKIP
            rating < 3.5f -> REVIEW_TIMEPASS
            rating < 4.5f -> REVIEW_GO_FOR_IT
            else -> REVIEW_PERFECTION
        }
    }

    fun tenPointToFourWord(rating: Float): Int {
        if (rating <= 0f) return REVIEW_NONE
        return when {
            rating < 5.0f -> REVIEW_SKIP
            rating < 7.0f -> REVIEW_TIMEPASS
            rating < 9.0f -> REVIEW_GO_FOR_IT
            else -> REVIEW_PERFECTION
        }
    }

    fun fourWordToFivePoint(reviewRating: Int): Float {
        return when (reviewRating) {
            REVIEW_SKIP -> 2.0f
            REVIEW_TIMEPASS -> 3.0f
            REVIEW_GO_FOR_IT -> 4.0f
            REVIEW_PERFECTION -> 5.0f
            else -> 0f
        }
    }

    fun fourWordToTenPoint(reviewRating: Int): Float {
        return when (reviewRating) {
            REVIEW_SKIP -> 4.0f
            REVIEW_TIMEPASS -> 6.0f
            REVIEW_GO_FOR_IT -> 8.0f
            REVIEW_PERFECTION -> 10.0f
            else -> 0f
        }
    }

    fun fivePointToTenPoint(rating: Float): Float {
        return (rating * 2f).coerceIn(0f, 10f)
    }

    fun tenPointToFivePoint(rating: Float): Float {
        return (rating / 2f).coerceIn(0f, 5f)
    }

    fun isRatedByUser(item: ListItem): Boolean {
        return (item.reviewRating in 1..4) || (item.userRating > 0f)
    }

    fun getUserRatingScore(item: ListItem): Float {
        if (!isRatedByUser(item)) return -1f
        if (item.userRating > 0f) {
            return if (item.userRating <= 5.0f && item.reviewRating in 1..4 && fourWordToFivePoint(item.reviewRating) == item.userRating) {
                item.userRating * 2f
            } else if (item.userRating <= 5.0f) {
                item.userRating * 2f
            } else {
                item.userRating
            }
        }
        return fourWordToTenPoint(item.reviewRating)
    }

    fun getDisplayBadge(item: ListItem, scale: RatingScaleOption): DisplayBadge? {
        if (!isRatedByUser(item)) {
            return null
        }

        return when (scale) {
            RatingScaleOption.SKIP_PERFECTION -> {
                val label = if (item.reviewRating in 1..4) {
                    getReviewLabel(item.reviewRating)
                } else if (item.userRating > 5.0f) {
                    getReviewLabel(tenPointToFourWord(item.userRating))
                } else {
                    getReviewLabel(fivePointToFourWord(item.userRating))
                }
                if (label != null) DisplayBadge.Text(label) else null
            }

            RatingScaleOption.SCALE_5 -> {
                val value = if (item.userRating > 0f) {
                    if (item.userRating > 5.0f) {
                        tenPointToFivePoint(item.userRating)
                    } else {
                        item.userRating
                    }
                } else {
                    fourWordToFivePoint(item.reviewRating)
                }
                DisplayBadge.Numeric(value, isOutOfTen = false)
            }

            RatingScaleOption.SCALE_10 -> {
                val value = if (item.userRating > 0f) {
                    if (item.userRating <= 5.0f) {
                        fivePointToTenPoint(item.userRating)
                    } else {
                        item.userRating
                    }
                } else {
                    fourWordToTenPoint(item.reviewRating)
                }
                DisplayBadge.Numeric(value, isOutOfTen = true)
            }
        }
    }
}
