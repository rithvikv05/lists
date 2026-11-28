package com.minimal.lists.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeProgressTest {
    private val episodes = listOf(EpisodeItem(0, 1, "Special"), EpisodeItem(1, 1, "One"), EpisodeItem(1, 2, "Two"),
        EpisodeItem(2, 1, "Three"), EpisodeItem(2, 2, "Four"), EpisodeItem(3, 1, "Five"))

    @Test fun startsAtSeasonOneInsteadOfSpecials() {
        assertEquals(1, currentProgressSeason(episodes, emptySet()))
    }

    @Test fun usesSavedSeasonWhenThereAreNoCheckboxes() {
        assertEquals(2, currentProgressSeason(episodes, emptySet(), savedSeason = 2))
    }

    @Test fun picksLatestSeasonEvenWhenEarlierEpisodesAreUnchecked() {
        assertEquals(2, currentProgressSeason(episodes, setOf("S1E1", "S2E1")))
    }

    @Test fun advancesWhenLatestCheckedSeasonIsComplete() {
        assertEquals(3, currentProgressSeason(episodes, setOf("S2E1", "S2E2")))
    }

    @Test fun staysOnLastSeasonWhenFinished() {
        assertEquals(3, currentProgressSeason(episodes, setOf("S3E1")))
    }

    @Test fun ignoresUnknownCheckedEpisodes() {
        assertEquals(1, currentProgressSeason(episodes, setOf("S99E1")))
    }
}
