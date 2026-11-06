package books.engine.model

import kotlin.math.sqrt

/**
 * Standardized mood dimensions inspired by StoryGraph's recommendation model,
 * designed for on-device vectorization and privacy-first local recommendations.
 */
enum class BookMood(val displayName: String, val tag: String) {
    DARK("Dark", "dark"),
    LIGHTHEARTED("Lighthearted", "lighthearted"),
    ATMOSPHERIC("Atmospheric", "atmospheric"),
    REFLECTIVE("Reflective", "reflective"),
    TENSE("Tense", "tense"),
    HOPEFUL("Hopeful", "hopeful"),
    SAD("Sad", "sad"),
    EMOTIONAL("Emotional", "emotional"),
    CHALLENGING("Challenging", "challenging"),
    FUNNY("Funny", "funny"),
    MYSTERIOUS("Mysterious", "mysterious"),
    INSPIRING("Inspiring", "inspiring"),
    RELAXING("Relaxing", "relaxing"),
    INFORMATIVE("Informative", "informative")
}

/**
 * Pacing dimension of literature.
 */
enum class BookPacing(val displayName: String) {
    SLOW("Slow Paced"),
    MEDIUM("Medium Paced"),
    FAST("Fast Paced")
}

/**
 * Standardized literary genres for normalization and recommendation filtering.
 */
enum class BookGenre(val displayName: String, val categoryKeywords: List<String>) {
    FICTION("Fiction", listOf("fiction", "general fiction", "literature")),
    NON_FICTION("Non-Fiction", listOf("non-fiction", "nonfiction", "general non-fiction")),
    SCI_FI("Science Fiction", listOf("science fiction", "sci-fi", "space opera", "dystopian", "cyberpunk", "time travel")),
    FANTASY("Fantasy", listOf("fantasy", "magic", "high fantasy", "urban fantasy", "mythology", "sword and sorcery")),
    MYSTERY_THRILLER("Mystery & Thriller", listOf("mystery", "thriller", "suspense", "crime", "detective", "noir")),
    ROMANCE("Romance", listOf("romance", "love story", "contemporary romance", "historical romance")),
    HISTORICAL("Historical Fiction", listOf("historical fiction", "historical", "period piece")),
    HORROR("Horror", listOf("horror", "gothic", "supernatural", "psychological horror", "ghost")),
    BIOGRAPHY("Biography & Memoir", listOf("biography", "autobiography", "memoir", "diaries")),
    PHILOSOPHY("Philosophy", listOf("philosophy", "ethics", "existentialism", "epistemology")),
    PSYCHOLOGY("Psychology & Mind", listOf("psychology", "mental health", "cognitive science", "neuroscience")),
    SELF_HELP("Self-Help & Productivity", listOf("self-help", "personal development", "productivity", "habits", "business")),
    SCIENCE("Science & Nature", listOf("science", "nature", "physics", "biology", "astronomy", "evolution")),
    HISTORY("History", listOf("history", "world war", "civilization", "ancient history")),
    POETRY("Poetry", listOf("poetry", "poems", "verse")),
    GRAPHIC_NOVEL("Graphic Novels & Comics", listOf("comics", "graphic novel", "comic strip")),
    MANGA("Manga", listOf("manga", "japanese comic", "manhwa"))
}

/**
 * Page length classification.
 */
enum class BookLengthBracket(val displayName: String, val minPages: Int, val maxPages: Int) {
    SHORT("Short (< 250 pages)", 0, 249),
    MEDIUM("Standard (250–450 pages)", 250, 449),
    LONG("Long (450–700 pages)", 450, 699),
    EPIC("Epic (700+ pages)", 700, Int.MAX_VALUE);

    companion object {
        fun fromPages(pages: Int?): BookLengthBracket {
            val p = pages ?: 300
            return entries.firstOrNull { p in it.minPages..it.maxPages } ?: MEDIUM
        }
    }
}

/**
 * Normalized content-attribute recommendation vector.
 * Allows on-device content-based filtering via Cosine Similarity between books
 * or between a user's reading taste profile and candidate books.
 */
data class RecommendationVector(
    val genres: Map<BookGenre, Float> = emptyMap(),
    val moods: Map<BookMood, Float> = emptyMap(),
    val pacing: BookPacing? = null,
    val lengthBracket: BookLengthBracket = BookLengthBracket.MEDIUM,
    val normalizedTags: Set<String> = emptySet()
) {
    /**
     * Compute cosine similarity between this vector and another book's vector (range: 0.0 to 1.0).
     */
    fun similarityTo(other: RecommendationVector): Double {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        // Genre alignment (weight: 45%)
        BookGenre.entries.forEach { genre ->
            val vA = (genres[genre] ?: 0f).toDouble()
            val vB = (other.genres[genre] ?: 0f).toDouble()
            dotProduct += vA * vB * 0.45
            normA += vA * vA * 0.45
            normB += vB * vB * 0.45
        }

        // Mood alignment (weight: 35%)
        BookMood.entries.forEach { mood ->
            val vA = (moods[mood] ?: 0f).toDouble()
            val vB = (other.moods[mood] ?: 0f).toDouble()
            dotProduct += vA * vB * 0.35
            normA += vA * vA * 0.35
            normB += vB * vB * 0.35
        }

        // Pacing bonus (weight: 10%)
        if (pacing != null && other.pacing != null) {
            val paceMatch = if (pacing == other.pacing) 1.0 else 0.4
            dotProduct += paceMatch * 0.10
            normA += 0.10
            normB += 0.10
        }

        // Length bonus (weight: 10%)
        val lengthMatch = if (lengthBracket == other.lengthBracket) 1.0 else 0.5
        dotProduct += lengthMatch * 0.10
        normA += 0.10
        normB += 0.10

        if (normA == 0.0 || normB == 0.0) return 0.0
        return (dotProduct / (sqrt(normA) * sqrt(normB))).coerceIn(0.0, 1.0)
    }
}

