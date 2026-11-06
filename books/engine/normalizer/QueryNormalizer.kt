package books.engine.normalizer

import java.lang.StringBuilder
import kotlin.math.abs
import kotlin.math.min

object QueryNormalizer {

    private val YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

    private val ROMAN_TO_ARABIC = listOf(
        Regex("""\bVIII\b""", RegexOption.IGNORE_CASE) to "8",
        Regex("""\bVII\b""", RegexOption.IGNORE_CASE) to "7",
        Regex("""\bIII\b""", RegexOption.IGNORE_CASE) to "3",
        Regex("""\bIV\b""", RegexOption.IGNORE_CASE) to "4",
        Regex("""\bIX\b""", RegexOption.IGNORE_CASE) to "9",
        Regex("""\bVI\b""", RegexOption.IGNORE_CASE) to "6",
        Regex("""\bII\b""", RegexOption.IGNORE_CASE) to "2",
        Regex("""\bV\b""", RegexOption.IGNORE_CASE) to "5",
        Regex("""\bX\b""", RegexOption.IGNORE_CASE) to "10",
        Regex("""\bI\b""", RegexOption.IGNORE_CASE) to "1"
    )

    fun foldDiacritics(str: String): String {
        val sb = StringBuilder(str.length)
        for (ch in str) {
            val replacement = when (ch) {
                'á', 'à', 'â', 'ä', 'ã', 'å', 'ā' -> 'a'
                'Á', 'À', 'Â', 'Ä', 'Ã', 'Å', 'Ā' -> 'A'
                'é', 'è', 'ê', 'ë', 'ē', 'ė', 'ę' -> 'e'
                'É', 'È', 'Ê', 'Ë', 'Ē', 'Ė', 'Ę' -> 'E'
                'í', 'ì', 'î', 'ï', 'ī', 'į' -> 'i'
                'Í', 'Ì', 'Î', 'Ï', 'Ī', 'Į' -> 'I'
                'ó', 'ò', 'ô', 'ö', 'õ', 'ō', 'ø' -> 'o'
                'Ó', 'Ò', 'Ô', 'Ö', 'Õ', 'Ō', 'Ø' -> 'O'
                'ú', 'ù', 'û', 'ü', 'ū' -> 'u'
                'Ú', 'Ù', 'Û', 'Ü', 'Ū' -> 'U'
                'ñ' -> 'n'; 'Ñ' -> 'N'
                'ç' -> 'c'; 'Ç' -> 'C'
                'ý', 'ÿ' -> 'y'; 'Ý' -> 'Y'
                'š' -> 's'; 'Š' -> 'S'
                'ž' -> 'z'; 'Ž' -> 'Z'
                else -> ch
            }
            sb.append(replacement)
        }
        return sb.toString()
    }

    fun stripLeadingArticles(str: String): String {
        val lower = str.trim()
        val articles = listOf("the ", "a ", "an ", "der ", "die ", "das ", "le ", "la ", "les ", "el ", "los ")
        for (art in articles) {
            if (lower.startsWith(art, ignoreCase = true)) {
                return lower.substring(art.length).trim()
            }
        }
        return lower
    }

    fun clean(query: String): String {
        return foldDiacritics(query.trim())
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else min(prev, min(dp[j], dp[j - 1])) + 1
                prev = temp
            }
        }
        return dp[b.length]
    }

    fun tokenJaccardSimilarity(s1: String, s2: String): Double {
        val t1 = s1.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotBlank() }.toSet()
        val t2 = s2.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotBlank() }.toSet()
        if (t1.isEmpty() || t2.isEmpty()) return 0.0
        val intersection = t1.intersect(t2).size
        val union = t1.union(t2).size
        return intersection.toDouble() / union.toDouble()
    }

    /**
     * Compare two titles and return a match score (0.0 to 1.0).
     */
    fun titleMatchScore(titleA: String, titleB: String): Double {
        val a = clean(stripLeadingArticles(titleA.lowercase()))
        val b = clean(stripLeadingArticles(titleB.lowercase()))
        if (a == b) return 1.0

        if (a.contains(b) || b.contains(a)) {
            val minLen = min(a.length, b.length).toDouble()
            val maxLen = kotlin.math.max(a.length, b.length).toDouble()
            if (minLen / maxLen > 0.6) return 0.85
        }

        val jaccard = tokenJaccardSimilarity(a, b)
        if (jaccard > 0.7) return jaccard

        val maxLen = kotlin.math.max(a.length, b.length)
        if (maxLen > 3 && abs(a.length - b.length) <= 3) {
            val dist = levenshteinDistance(a, b)
            if (dist <= 2) return 0.80
        }

        return jaccard * 0.7
    }
}

