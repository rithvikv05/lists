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

    /**
     * Converts a 5-point rating (0.0 .. 5.0) to 4-word rating:
     * 1.0 – 2.4 -> Skip
     * 2.5 – 3.4 -> Timepass
     * 3.5 – 4.4 -> Go for it
     * 4.5 – 5.0 -> Perfection
     */
    fun fivePointToFourWord(rating: Float): Int {
        if (rating <= 0f) return REVIEW_NONE
        return when {
            rating < 2.5f -> REVIEW_SKIP
            rating < 3.5f -> REVIEW_TIMEPASS
            rating < 4.5f -> REVIEW_GO_FOR_IT
            else -> REVIEW_PERFECTION
        }
    }

    /**
     * Converts a 10-point rating (0.0 .. 10.0) to 4-word rating:
     * 1.0 – 4.9 -> Skip
     * 5.0 – 6.9 -> Timepass
     * 7.0 – 8.9 -> Go for it
     * 9.0 – 10.0 -> Perfection
     */
    fun tenPointToFourWord(rating: Float): Int {
        if (rating <= 0f) return REVIEW_NONE
        return when {
            rating < 5.0f -> REVIEW_SKIP
            rating < 7.0f -> REVIEW_TIMEPASS
            rating < 9.0f -> REVIEW_GO_FOR_IT
            else -> REVIEW_PERFECTION
        }
    }

    /**
     * Reverse conversion to 5-point scale:
     * Skip -> 2.0
     * Timepass -> 3.0
     * Go for it -> 4.0
     * Perfection -> 5.0
     */
    fun fourWordToFivePoint(reviewRating: Int): Float {
        return when (reviewRating) {
            REVIEW_SKIP -> 2.0f
            REVIEW_TIMEPASS -> 3.0f
            REVIEW_GO_FOR_IT -> 4.0f
            REVIEW_PERFECTION -> 5.0f
            else -> 0f
        }
    }

    /**
     * Reverse conversion to 10-point scale:
     * Skip -> 4.0
     * Timepass -> 6.0
     * Go for it -> 8.0
     * Perfection -> 10.0
     */
    fun fourWordToTenPoint(reviewRating: Int): Float {
        return when (reviewRating) {
            REVIEW_SKIP -> 4.0f
            REVIEW_TIMEPASS -> 6.0f
            REVIEW_GO_FOR_IT -> 8.0f
            REVIEW_PERFECTION -> 10.0f
            else -> 0f
        }
    }

    /**
     * 5-point to 10-point: Multiply by 2.
     */
    fun fivePointToTenPoint(rating: Float): Float {
        return (rating * 2f).coerceIn(0f, 10f)
    }

    /**
     * 10-point to 5-point: Divide by 2.
     */
    fun tenPointToFivePoint(rating: Float): Float {
        return (rating / 2f).coerceIn(0f, 5f)
    }

    /**
     * Checks if the item has been rated by the user.
     * External database ratings (item.rating) are NEVER counted unless the user explicitly saved a rating.
     */
    fun isRatedByUser(item: ListItem): Boolean {
        return (item.reviewRating in 1..4) || (item.userRating > 0f) || (item.rating > 0f)
    }

    /**
     * Unified user rating score (on a 10-point scale) for sorting.
     * If unrated, returns -1f so unrated items go to the bottom.
     */
    fun getUserRatingScore(item: ListItem): Float {
        if (!isRatedByUser(item)) return -1f
        val effective = if (item.userRating > 0f) item.userRating else item.rating
        if (effective > 0f) {
            return if (effective <= 5.0f && item.reviewRating in 1..4 && fourWordToFivePoint(item.reviewRating) == effective) {
                effective * 2f
            } else if (effective <= 5.0f) {
                // If the user rating is <= 5.0, convert to 10-point scale (* 2)
                effective * 2f
            } else {
                effective
            }
        }
        return fourWordToTenPoint(item.reviewRating)
    }

    /**
     * Computes the display badge according to the active RatingScaleOption.
     * Returns null if unrated, ensuring NO random rating is shown anywhere.
     */
    fun getDisplayBadge(item: ListItem, scale: RatingScaleOption): DisplayBadge? {
        if (!isRatedByUser(item)) {
            return null
        }

        return when (scale) {
            RatingScaleOption.SKIP_PERFECTION -> {
                val label = when {
                    item.reviewRating == 1 -> "S"
                    item.reviewRating == 2 -> "T"
                    item.reviewRating == 3 -> "G"
                    item.reviewRating == 4 -> "P"
                    else -> {
                        val score = if (item.userRating > 0f) item.userRating else item.rating
                        when {
                            score <= 0f -> null
                            score < 3.75f -> "S"
                            score < 6.25f -> "T"
                            score < 8.75f -> "G"
                            else -> "P"
                        }
                    }
                }
                if (label != null) DisplayBadge.Text(label) else null
            }

            RatingScaleOption.SCALE_5 -> {
                val effective = if (item.userRating > 0f) item.userRating else item.rating
                val value = if (effective > 0f) {
                    if (effective > 5.0f) {
                        tenPointToFivePoint(effective)
                    } else {
                        effective
                    }
                } else {
                    fourWordToFivePoint(item.reviewRating)
                }
                DisplayBadge.Numeric(value, isOutOfTen = false)
            }

            RatingScaleOption.SCALE_10 -> {
                val effective = if (item.userRating > 0f) item.userRating else item.rating
                val value = if (effective > 0f) {
                    if (effective <= 5.0f && item.userRating > 0f && item.userRating <= 5.0f) {
                        fivePointToTenPoint(effective)
                    } else {
                        effective
                    }
                } else {
                    fourWordToTenPoint(item.reviewRating)
                }
                DisplayBadge.Numeric(value, isOutOfTen = true)
            }

            RatingScaleOption.SCALE_100 -> {
                val effective = if (item.userRating > 0f) item.userRating else item.rating
                val value = if (effective > 10.0f) effective else effective * 10.0f
                DisplayBadge.Text("${value.toInt()}%")
            }

            RatingScaleOption.EMOJI_3 -> {
                val effective = if (item.userRating > 0f) item.userRating else item.rating
                val label = when {
                    effective >= 8f -> "Love"
                    effective >= 4f -> "Like"
                    effective > 0f -> "Dislike"
                    item.reviewRating >= 3 -> "Love"
                    item.reviewRating == 2 -> "Like"
                    item.reviewRating == 1 -> "Dislike"
                    else -> "Like"
                }
                DisplayBadge.Text(label)
            }
        }
    }

    fun getBadge(item: ListItem, scale: RatingScaleOption): DisplayBadge? = getDisplayBadge(item, scale)
}
