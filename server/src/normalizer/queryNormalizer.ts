export interface NormalizedQuery {
  original: string;
  cleanQuery: string;
  extractedYear?: string;
  variants: string[];
}

const YEAR_REGEX = /\b(19\d{2}|20\d{2})\b/;

const ROMAN_TO_ARABIC: [RegExp, string][] = [
  [/\bVIII\b/gi, '8'],
  [/\bVII\b/gi, '7'],
  [/\bIII\b/gi, '3'],
  [/\bIV\b/gi, '4'],
  [/\bIX\b/gi, '9'],
  [/\bVI\b/gi, '6'],
  [/\bII\b/gi, '2'],
  [/\bV\b/gi, '5'],
  [/\bX\b/gi, '10'],
  [/\bI\b/gi, '1']
];

const ARABIC_TO_ROMAN: [RegExp, string][] = [
  [/\b10\b/g, 'X'],
  [/\b9\b/g, 'IX'],
  [/\b8\b/g, 'VIII'],
  [/\b7\b/g, 'VII'],
  [/\b6\b/g, 'VI'],
  [/\b5\b/g, 'V'],
  [/\b4\b/g, 'IV'],
  [/\b3\b/g, 'III'],
  [/\b2\b/g, 'II'],
  [/\b1\b/g, 'I']
];

const LEADING_ARTICLES = [
  'the ', 'a ', 'an ', 'der ', 'die ', 'das ', 'le ', 'la ', 'les ', 'el ', 'los '
];

export function foldDiacritics(str: string): string {
  return str.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

export function stripLeadingArticles(str: string): string {
  const trimmed = str.trim();
  const lower = trimmed.toLowerCase();
  for (const art of LEADING_ARTICLES) {
    if (lower.startsWith(art)) {
      return trimmed.slice(art.length).trim();
    }
  }
  return trimmed;
}

export function normalizeQuery(input: string): NormalizedQuery {
  const trimmed = input.trim();
  const folded = foldDiacritics(trimmed);
  const yearMatch = folded.match(YEAR_REGEX);
  const extractedYear = yearMatch ? yearMatch[0] : undefined;

  let cleanQuery = extractedYear
    ? folded.replace(extractedYear, '').replace(/\s+/g, ' ').trim()
    : folded;

  if (!cleanQuery) cleanQuery = trimmed;

  const variants = [cleanQuery];

  // Roman to Arabic
  let toArabic = cleanQuery;
  for (const [roman, arabic] of ROMAN_TO_ARABIC) {
    toArabic = toArabic.replace(roman, arabic);
  }
  if (toArabic !== cleanQuery && !variants.includes(toArabic)) {
    variants.push(toArabic);
  }

  // Arabic to Roman
  let toRoman = cleanQuery;
  for (const [arabic, roman] of ARABIC_TO_ROMAN) {
    toRoman = toRoman.replace(arabic, roman);
  }
  if (toRoman !== cleanQuery && !variants.includes(toRoman)) {
    variants.push(toRoman);
  }

  return {
    original: trimmed,
    cleanQuery,
    extractedYear,
    variants
  };
}

