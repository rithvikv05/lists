package books.engine.normalizer

import books.engine.model.BookGenre
import books.engine.model.BookLengthBracket
import books.engine.model.BookMood
import books.engine.model.BookPacing
import books.engine.model.RecommendationVector

/**
 * Intelligent mapper that translates raw categories (from Google Books)
 * and subject tags (from Open Library) into standardized Genre, Mood, and Pacing vectors.
 */
object AttributeMapper {

    private val GENRE_MAPPINGS = listOf(
        listOf("sci-fi", "science fiction", "space opera", "cyberpunk", "time travel", "dystopian", "post-apocalyptic", "alien") to BookGenre.SCI_FI,
        listOf("fantasy", "magic", "wizards", "dragons", "sword and sorcery", "high fantasy", "urban fantasy", "mythology") to BookGenre.FANTASY,
        listOf("thriller", "suspense", "mystery", "crime", "detective", "murder", "espionage", "investigation") to BookGenre.MYSTERY_THRILLER,
        listOf("horror", "supernatural", "ghosts", "vampires", "haunted", "zombies", "occult") to BookGenre.HORROR,
        listOf("romance", "love story", "contemporary romance", "romantic", "chick lit") to BookGenre.ROMANCE,
        listOf("historical fiction", "historical", "regency", "victorian", "medieval", "world war") to BookGenre.HISTORICAL,
        listOf("biography", "autobiography", "memoir", "letters", "diaries") to BookGenre.BIOGRAPHY,
        listOf("philosophy", "philosophical", "ethics", "existentialism", "metaphysics", "logic") to BookGenre.PHILOSOPHY,
        listOf("psychology", "mental health", "behavioral", "neuroscience", "cognitive", "psychoanalysis") to BookGenre.PSYCHOLOGY,
        listOf("self-help", "personal development", "productivity", "motivation", "habits", "success", "business", "leadership") to BookGenre.SELF_HELP,
        listOf("science", "physics", "biology", "chemistry", "mathematics", "astronomy", "evolution", "nature") to BookGenre.SCIENCE,
        listOf("history", "civilization", "ancient", "military history", "revolution") to BookGenre.HISTORY,
        listOf("poetry", "poems", "verse", "haiku") to BookGenre.POETRY,
        listOf("comic", "graphic novel", "manga", "anime") to BookGenre.GRAPHIC_NOVEL,
        listOf("non-fiction", "nonfiction", "essay", "journalism") to BookGenre.NON_FICTION,
        listOf("fiction", "literature", "novel", "classic literature", "classics") to BookGenre.FICTION
    )

    private val MOOD_MAPPINGS = listOf(
        listOf("dark", "grim", "bleak", "dystopian", "totalitarian", "tragedy", "horror", "gothic", "macabre") to BookMood.DARK,
        listOf("tense", "suspense", "thrilling", "fast-paced", "page-turner", "urgency", "gripping", "adrenaline") to BookMood.TENSE,
        listOf("reflective", "philosophical", "contemplative", "meditative", "thought-provoking", "introspective", "deep") to BookMood.REFLECTIVE,
        listOf("atmospheric", "immersive", "evocative", "rich world", "haunting", "poetic", "gothic") to BookMood.ATMOSPHERIC,
        listOf("lighthearted", "whimsical", "feel-good", "uplifting", "charming", "sweet", "cozy") to BookMood.LIGHTHEARTED,
        listOf("funny", "humor", "humorous", "comedy", "satire", "witty", "hilarious", "parody") to BookMood.FUNNY,
        listOf("emotional", "tearjerker", "moving", "heartfelt", "poignant", "heartbreaking", "grief") to BookMood.EMOTIONAL,
        listOf("hopeful", "inspiring", "triumph", "redemption", "optimistic", "courage", "resilience") to BookMood.HOPEFUL,
        listOf("mysterious", "puzzling", "enigmatic", "secret", "investigation", "clues", "whodunit") to BookMood.MYSTERIOUS,
        listOf("challenging", "complex", "uncompromising", "dense", "erudite", "intellectual", "academic") to BookMood.CHALLENGING,
        listOf("inspiring", "motivational", "empowering", "breakthrough", "wisdom") to BookMood.INSPIRING,
        listOf("relaxing", "calming", "peaceful", "comforting", "gentle") to BookMood.RELAXING,
        listOf("informative", "educational", "reference", "in-depth", "scholarly") to BookMood.INFORMATIVE
    )

    private val FAST_PACED_KEYWORDS = setOf("thriller", "action", "suspense", "fast-paced", "adventure", "heist", "page-turner")
    private val SLOW_PACED_KEYWORDS = setOf("epic", "philosophical", "historical", "dense", "saga", "meditative", "literary fiction")

    /**
     * Map raw strings into standardized genres, moods, pacing, and build the RecommendationVector.
     */
    fun mapAttributes(
        categories: List<String>,
        subjects: List<String>,
        totalPages: Int? = null
    ): AttributeResult {
        val allRawTokens = (categories + subjects)
            .flatMap { it.lowercase().split(Regex("""[/,;>|\-]+""")) }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val detectedGenres = mutableMapOf<BookGenre, Float>()
        val detectedMoods = mutableMapOf<BookMood, Float>()

        allRawTokens.forEach { token ->
            // Check genres
            for ((keywords, genre) in GENRE_MAPPINGS) {
                if (keywords.any { token.contains(it) }) {
                    val current = detectedGenres[genre] ?: 0f
                    detectedGenres[genre] = (current + 1.0f).coerceAtMost(3.0f)
                }
            }

            // Check moods
            for ((keywords, mood) in MOOD_MAPPINGS) {
                if (keywords.any { token.contains(it) }) {
                    val current = detectedMoods[mood] ?: 0f
                    detectedMoods[mood] = (current + 1.0f).coerceAtMost(3.0f)
                }
            }
        }

        // Infer pacing
        val hasFast = allRawTokens.any { token -> FAST_PACED_KEYWORDS.any { token.contains(it) } }
        val hasSlow = allRawTokens.any { token -> SLOW_PACED_KEYWORDS.any { token.contains(it) } }
        val inferredPacing = when {
            hasFast && !hasSlow -> BookPacing.FAST
            hasSlow && !hasFast -> BookPacing.SLOW
            totalPages != null && totalPages > 600 -> BookPacing.SLOW
            totalPages != null && totalPages < 220 -> BookPacing.FAST
            else -> BookPacing.MEDIUM
        }

        // If no genre detected, default to FICTION or NON_FICTION if mentioned
        if (detectedGenres.isEmpty()) {
            detectedGenres[BookGenre.FICTION] = 1.0f
        }

        val lengthBracket = BookLengthBracket.fromPages(totalPages)

        val vector = RecommendationVector(
            genres = detectedGenres,
            moods = detectedMoods,
            pacing = inferredPacing,
            lengthBracket = lengthBracket,
            normalizedTags = allRawTokens.take(15).toSet()
        )

        return AttributeResult(
            genres = detectedGenres.keys,
            moods = detectedMoods.keys,
            pacing = inferredPacing,
            recommendationVector = vector
        )
    }

    data class AttributeResult(
        val genres: Set<BookGenre>,
        val moods: Set<BookMood>,
        val pacing: BookPacing,
        val recommendationVector: RecommendationVector
    )
}

