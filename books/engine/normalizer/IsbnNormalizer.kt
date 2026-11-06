package books.engine.normalizer

/**
 * High-performance ISBN and Barcode validator and transformer.
 * Supports ISBN-10, ISBN-13 (EAN-13), checksum validation, and bi-directional conversion.
 */
object IsbnNormalizer {

    private val CLEAN_REGEX = Regex("[^0-9X]")

    /**
     * Clean raw string by stripping whitespace, hyphens, and punctuation.
     * Preserves trailing 'X' if present for ISBN-10.
     */
    fun clean(raw: String?): String {
        if (raw == null) return ""
        val trimmed = raw.trim().uppercase()
        return trimmed.replace(CLEAN_REGEX, "")
    }

    /**
     * Determines whether the given string is a valid ISBN-10 or ISBN-13.
     */
    fun isValidIsbn(raw: String?): Boolean {
        val cleaned = clean(raw)
        return when (cleaned.length) {
            10 -> isValidIsbn10(cleaned)
            13 -> isValidIsbn13(cleaned)
            else -> false
        }
    }

    /**
     * Determines if a search input string looks like an ISBN / Barcode
     * (i.e. user typed or scanned a barcode directly into the search bar).
     */
    fun isQueryAnIsbn(query: String?): Boolean {
        val cleaned = clean(query)
        if (cleaned.length == 10 && (isValidIsbn10(cleaned) || cleaned.all { it.isDigit() })) return true
        if (cleaned.length == 13 && (cleaned.startsWith("978") || cleaned.startsWith("979"))) {
            return isValidIsbn13(cleaned) || cleaned.all { it.isDigit() }
        }
        return false
    }

    /**
     * Validate ISBN-10 using modulo 11 checksum.
     */
    fun isValidIsbn10(cleaned: String): Boolean {
        if (cleaned.length != 10) return false
        var sum = 0
        for (i in 0 until 9) {
            val c = cleaned[i]
            if (!c.isDigit()) return false
            sum += (c - '0') * (10 - i)
        }
        val lastChar = cleaned[9]
        val checkDigit = if (lastChar == 'X') 10 else if (lastChar.isDigit()) lastChar - '0' else return false
        sum += checkDigit
        return sum % 11 == 0
    }

    /**
     * Validate ISBN-13 using modulo 10 checksum with alternating weights 1 and 3.
     */
    fun isValidIsbn13(cleaned: String): Boolean {
        if (cleaned.length != 13) return false
        if (!cleaned.startsWith("978") && !cleaned.startsWith("979")) return false
        var sum = 0
        for (i in 0 until 12) {
            val c = cleaned[i]
            if (!c.isDigit()) return false
            val weight = if (i % 2 == 0) 1 else 3
            sum += (c - '0') * weight
        }
        val lastDigit = cleaned[12]
        if (!lastDigit.isDigit()) return false
        val checkDigit = (10 - (sum % 10)) % 10
        return checkDigit == (lastDigit - '0')
    }

    /**
     * Convert a valid ISBN-10 to standard ISBN-13 (prefixed with 978 and recalculated checksum).
     */
    fun isbn10ToIsbn13(isbn10: String): String? {
        val cleaned = clean(isbn10)
        if (cleaned.length != 10) return null
        val core = "978" + cleaned.substring(0, 9)
        var sum = 0
        for (i in 0 until 12) {
            val weight = if (i % 2 == 0) 1 else 3
            sum += (core[i] - '0') * weight
        }
        val check = (10 - (sum % 10)) % 10
        return core + check
    }

    /**
     * Convert ISBN-13 to ISBN-10 (only possible for 978-prefix ISBNs).
     */
    fun isbn13ToIsbn10(isbn13: String): String? {
        val cleaned = clean(isbn13)
        if (cleaned.length != 13 || !cleaned.startsWith("978")) return null
        val core = cleaned.substring(3, 12)
        var sum = 0
        for (i in 0 until 9) {
            sum += (core[i] - '0') * (10 - i)
        }
        val rem = 11 - (sum % 11)
        val check = when (rem) {
            11 -> "0"
            10 -> "X"
            else -> rem.toString()
        }
        return core + check
    }

    /**
     * Given an unknown ISBN string, return pair of (ISBN-10?, ISBN-13?).
     */
    fun resolveIsbnPair(raw: String?): Pair<String?, String?> {
        val cleaned = clean(raw)
        return when (cleaned.length) {
            10 -> Pair(cleaned, isbn10ToIsbn13(cleaned))
            13 -> Pair(isbn13ToIsbn10(cleaned), cleaned)
            else -> Pair(null, null)
        }
    }
}

