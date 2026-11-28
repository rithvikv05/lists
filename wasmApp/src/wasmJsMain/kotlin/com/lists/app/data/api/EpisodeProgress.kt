package com.lists.app.data.api

/** Choose the latest season with checked episodes, or the next season if it is complete. */
fun currentProgressSeason(episodes: List<EpisodeItem>, checked: Set<String>, savedSeason: Int = 1): Int {
    val seasons = episodes.map { it.season }.distinct().sorted()
    if (seasons.isEmpty()) return 1
    val latest = episodes.filter { it.key in checked }.maxOfOrNull { it.season }
        ?: return if (savedSeason in seasons) savedSeason else if (1 in seasons) 1 else seasons.first()
    val currentEpisodes = episodes.filter { it.season == latest }
    if (currentEpisodes.isNotEmpty() && currentEpisodes.all { it.key in checked }) {
        return seasons.firstOrNull { it > latest } ?: latest
    }
    return latest
}
