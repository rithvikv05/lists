package com.lists.app.data.model

import com.lists.app.formatDecimal1

sealed class DisplayBadge {
    data class Text(val label: String) : DisplayBadge()
    data class Numeric(val value: Float, val isOutOfTen: Boolean) : DisplayBadge() {
        val formatted: String get() = formatDecimal1(value)
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
        return (rating * 2.0f).coerceIn(0f, 10f)
    }

    fun tenPointToFivePoint(rating: Float): Float {
        return (rating / 2.0f).coerceIn(0f, 5f)
    }

    fun getBadge(item: ListItem, scale: RatingScaleOption): DisplayBadge? {
        return when (scale) {
            RatingScaleOption.SKIP_PERFECTION -> {
                val label = when {
                    item.reviewRating > 0 -> getReviewLabel(item.reviewRating)
                    item.userRating > 0f -> getReviewLabel(fivePointToFourWord(item.userRating))
                    else -> null
                }
                label?.let { DisplayBadge.Text(it) }
            }
            RatingScaleOption.SCALE_5 -> {
                val num = when {
                    item.userRating > 0f -> item.userRating
                    item.reviewRating > 0 -> fourWordToFivePoint(item.reviewRating)
                    else -> null
                }
                num?.let { DisplayBadge.Numeric(it, isOutOfTen = false) }
            }
            RatingScaleOption.SCALE_10 -> {
                val num = when {
                    item.userRating > 0f -> fivePointToTenPoint(item.userRating)
                    item.reviewRating > 0 -> fourWordToTenPoint(item.reviewRating)
                    else -> null
                }
                num?.let { DisplayBadge.Numeric(it, isOutOfTen = true) }
            }
        }
    }

    fun getDisplayBadge(item: ListItem, scale: RatingScaleOption): DisplayBadge? = getBadge(item, scale)

    fun getUserRatingScore(item: ListItem): Float {
        if (item.userRating > 0f) return item.userRating
        if (item.reviewRating > 0) return fourWordToFivePoint(item.reviewRating)
        return 0f
    }
}
