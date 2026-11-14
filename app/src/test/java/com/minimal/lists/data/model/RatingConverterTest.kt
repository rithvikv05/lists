package com.minimal.lists.data.model

import com.minimal.lists.data.db.ListItem
import org.junit.Assert.*
import org.junit.Test

class RatingConverterTest {
    private val movie = ListItem(listId = 1, title = "Movie", category = Category.MOVIE.id, rating = 8.7f)

    @Test fun publicScoreDoesNotCountAsPersonalRating() {
        assertFalse(RatingConverter.isRatedByUser(movie))
        assertEquals(-1f, RatingConverter.getUserRatingScore(movie), 0f)
        RatingScaleOption.entries.forEach { scale ->
            assertNull(RatingConverter.getDisplayBadge(movie, scale))
        }
        assertEquals(8.7f, movie.rating, 0f)
    }

    @Test fun explicitPersonalRatingsRemainVisible() {
        val rated = movie.copy(rating = 8f, userRating = 4f, reviewRating = 3)
        assertTrue(RatingConverter.isRatedByUser(rated))
        assertEquals(8f, RatingConverter.getUserRatingScore(rated), 0f)
        val badge = RatingConverter.getDisplayBadge(rated, RatingScaleOption.SCALE_5) as DisplayBadge.Numeric
        assertEquals(4f, badge.value, 0f)
    }

    @Test fun clearingPersonalRatingDoesNotFallBackToPublicScore() {
        val cleared = movie.copy(userRating = 0f, reviewRating = 0)
        assertNull(RatingConverter.getDisplayBadge(cleared, RatingScaleOption.SCALE_10))
    }
}
