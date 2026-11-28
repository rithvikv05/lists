package com.minimal.lists.data.api

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GamePlaytimeMetadataTest {
    @Test fun completionMinutesSurviveBackupAndMissingRemoteMetadata() {
        val stored = """{"gameCompletionLog":[123,456,789]}"""
        val restored = compactMetadataSnapshot(stored)
        assertEquals(456, JSONObject(restored).getJSONArray("gameCompletionLog").getInt(1))
        val merged = GamePlaytimeRepository.mergeSaved("{}", stored, "Game", "2020")
        assertEquals(789, JSONObject(merged).getJSONArray("gameCompletionLog").getInt(2))
        val removed = GamePlaytimeRepository.mergeSaved("""{"gameCompletionLog":[0,0,0]}""", stored, "Game", "2020")
        assertEquals(0, JSONObject(removed).getJSONArray("gameCompletionLog").getInt(0))
    }
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

    @Test fun previousCacheFormatCanBePromotedIntoBackupMetadata() {
        val old = """{"id":42,"main":3600,"extras":7200,"complete":10800,"savedAt":1}"""
        val cached = GamePlaytimeRepository.readLegacyCached(old)
        assertEquals(time, cached)
        val metadata = GamePlaytimeRepository.withSaved("{}", "A Game", "2020", cached!!)
        assertEquals(time, GamePlaytimeRepository.readSaved(compactMetadataSnapshot(metadata), "A Game", "2020"))
        assertNull(GamePlaytimeRepository.readLegacyCached("""{"id":0,"main":3600}"""))
    }
}
