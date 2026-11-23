package com.minimal.lists.data.api

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GamePlaytimeMetadataTest {
    private val time = GamePlaytime("howlongtobeat", "https://howlongtobeat.com/game/42", 3600, 7200, 10800)

    @Test fun estimatesSurviveCompactionUsedByBackupAndRestore() {
        val input = """{"id":"catalog:abc","sourceRefs":[{"source":"igdb","id":"123"}],"genres":["Adventure"]}"""
        val stored = GamePlaytimeRepository.withSaved(input, "A Game", "2020", time)
        val restored = compactMetadataSnapshot(stored)
        assertEquals(time, GamePlaytimeRepository.readSaved(restored, "A Game", "2020"))
        assertEquals("catalog:abc", JSONObject(restored).getString("id"))
        assertEquals("123", JSONObject(restored).getJSONArray("sourceRefs").getJSONObject(0).getString("id"))
    }

    @Test fun doesNotApplyEstimatesToRenamedOrDifferentYearGame() {
        val stored = GamePlaytimeRepository.withSaved("", "Prey", "2006", time)
        assertNull(GamePlaytimeRepository.readSaved(stored, "Prey", "2017"))
        assertNull(GamePlaytimeRepository.readSaved(stored, "Prey 2", "2006"))
    }

    @Test fun syncKeepsLocalEstimatesWhenOtherDeviceHasNoEstimates() {
        val saved = GamePlaytimeRepository.withSaved("{}", "A Game", "2020", time)
        val merged = GamePlaytimeRepository.mergeSaved("""{"id":"updated"}""", saved, "A Game", "2020")
        assertEquals(time, GamePlaytimeRepository.readSaved(merged, "A Game", "2020"))
        assertEquals("updated", JSONObject(merged).getString("id"))
    }

    @Test fun compactSnapshotsKeepEstimatesWhenTrimmingLargeMetadata() {
        val large = JSONObject().put("genres", org.json.JSONArray(List(200) { "long genre name $it" })).toString()
        val saved = GamePlaytimeRepository.withSaved(large, "A Game", "2020", time)
        assertTrue(saved.toByteArray().size <= 2048)
        assertEquals(time, GamePlaytimeRepository.readSaved(saved, "A Game", "2020"))
    }
}
