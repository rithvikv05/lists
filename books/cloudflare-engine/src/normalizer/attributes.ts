import {
  BookGenre,
  BookMood,
  BookPacing,
  BookLengthBracket,
  RecommendationVector,
} from '../types';

const GENRE_MAPPINGS: Array<[string[], BookGenre]> = [
  [['sci-fi', 'science fiction', 'space opera', 'cyberpunk', 'time travel', 'dystopian', 'post-apocalyptic', 'alien'], 'SCI_FI'],
  [['fantasy', 'magic', 'wizards', 'dragons', 'sword and sorcery', 'high fantasy', 'urban fantasy', 'mythology'], 'FANTASY'],
  [['thriller', 'suspense', 'mystery', 'crime', 'detective', 'murder', 'espionage', 'investigation', 'noir'], 'MYSTERY_THRILLER'],
  [['horror', 'supernatural', 'ghosts', 'vampires', 'haunted', 'zombies', 'occult', 'gothic'], 'HORROR'],
  [['romance', 'love story', 'contemporary romance', 'romantic', 'chick lit'], 'ROMANCE'],
  [['historical fiction', 'historical', 'regency', 'victorian', 'medieval', 'world war'], 'HISTORICAL'],
  [['biography', 'autobiography', 'memoir', 'diaries', 'letters'], 'BIOGRAPHY'],
  [['philosophy', 'philosophical', 'ethics', 'existentialism', 'metaphysics', 'logic'], 'PHILOSOPHY'],
  [['psychology', 'mental health', 'behavioral', 'neuroscience', 'cognitive', 'psychoanalysis'], 'PSYCHOLOGY'],
  [['self-help', 'personal development', 'productivity', 'habits', 'motivation', 'business', 'leadership'], 'SELF_HELP'],
  [['science', 'physics', 'biology', 'chemistry', 'mathematics', 'astronomy', 'evolution', 'nature'], 'SCIENCE'],
  [['history', 'civilization', 'ancient', 'military history', 'revolution'], 'HISTORY'],
  [['poetry', 'poems', 'verse', 'haiku'], 'POETRY'],
  [['comic', 'graphic novel', 'comics'], 'GRAPHIC_NOVEL'],
  [['manga', 'manhwa', 'japanese comic'], 'MANGA'],
  [['non-fiction', 'nonfiction', 'essay', 'journalism'], 'NON_FICTION'],
  [['fiction', 'literature', 'novel', 'classic literature', 'classics'], 'FICTION'],
];

const MOOD_MAPPINGS: Array<[string[], BookMood]> = [
  [['dark', 'grim', 'bleak', 'dystopian', 'totalitarian', 'tragedy', 'horror', 'gothic', 'macabre'], 'DARK'],
  [['tense', 'suspense', 'thrilling', 'fast-paced', 'page-turner', 'urgency', 'gripping', 'adrenaline'], 'TENSE'],
  [['reflective', 'philosophical', 'contemplative', 'meditative', 'thought-provoking', 'introspective', 'deep'], 'REFLECTIVE'],
  [['atmospheric', 'immersive', 'evocative', 'rich world', 'haunting', 'poetic'], 'ATMOSPHERIC'],
  [['lighthearted', 'whimsical', 'feel-good', 'uplifting', 'charming', 'sweet', 'cozy'], 'LIGHTHEARTED'],
  [['funny', 'humor', 'humorous', 'comedy', 'satire', 'witty', 'hilarious', 'parody'], 'FUNNY'],
  [['emotional', 'tearjerker', 'moving', 'heartfelt', 'poignant', 'heartbreaking', 'grief'], 'EMOTIONAL'],
  [['hopeful', 'inspiring', 'triumph', 'redemption', 'optimistic', 'courage', 'resilience'], 'HOPEFUL'],
  [['mysterious', 'puzzling', 'enigmatic', 'secret', 'investigation', 'clues', 'whodunit'], 'MYSTERIOUS'],
  [['challenging', 'complex', 'uncompromising', 'dense', 'erudite', 'intellectual', 'academic'], 'CHALLENGING'],
  [['inspiring', 'motivational', 'empowering', 'breakthrough', 'wisdom'], 'INSPIRING'],
  [['relaxing', 'calming', 'peaceful', 'comforting', 'gentle'], 'RELAXING'],
  [['informative', 'educational', 'reference', 'in-depth', 'scholarly'], 'INFORMATIVE'],
];

const FAST_PACED = ['thriller', 'action', 'suspense', 'fast-paced', 'adventure', 'heist', 'page-turner'];
const SLOW_PACED = ['epic', 'philosophical', 'historical', 'dense', 'saga', 'meditative', 'literary fiction'];

export function getLengthBracket(totalPages?: number): BookLengthBracket {
  const p = totalPages ?? 300;
  if (p < 250) return 'SHORT';
  if (p < 450) return 'MEDIUM';
  if (p < 700) return 'LONG';
  return 'EPIC';
}

export function mapAttributes(
  categories: string[] = [],
  subjects: string[] = [],
  totalPages?: number
): {
  normalizedGenres: BookGenre[];
  suggestedMoods: BookMood[];
  suggestedPacing?: BookPacing;
  lengthBracket: BookLengthBracket;
  recommendationVector: RecommendationVector;
} {
  const allTokens = [...categories, ...subjects]
    .flatMap((c) => c.toLowerCase().split(/[/,;>|\-]+/))
    .map((t) => t.trim())
    .filter(Boolean);

  const genreWeights: Record<string, number> = {};
  const moodWeights: Record<string, number> = {};

  for (const token of allTokens) {
    for (const [keywords, genre] of GENRE_MAPPINGS) {
      if (keywords.some((k) => token.includes(k))) {
        genreWeights[genre] = Math.min(3.0, (genreWeights[genre] || 0) + 1.0);
      }
    }
    for (const [keywords, mood] of MOOD_MAPPINGS) {
      if (keywords.some((k) => token.includes(k))) {
        moodWeights[mood] = Math.min(3.0, (moodWeights[mood] || 0) + 1.0);
      }
    }
  }

  // If no genre mapped, default to FICTION
  if (Object.keys(genreWeights).length === 0) {
    genreWeights['FICTION'] = 1.0;
  }

  const hasFast = allTokens.some((t) => FAST_PACED.some((k) => t.includes(k)));
  const hasSlow = allTokens.some((t) => SLOW_PACED.some((k) => t.includes(k)));
  const suggestedPacing: BookPacing =
    hasFast && !hasSlow
      ? 'FAST'
      : hasSlow && !hasFast
      ? 'SLOW'
      : totalPages && totalPages > 600
      ? 'SLOW'
      : totalPages && totalPages < 220
      ? 'FAST'
      : 'MEDIUM';

  const lengthBracket = getLengthBracket(totalPages);

  const vector: RecommendationVector = {
    genres: genreWeights,
    moods: moodWeights,
    pacing: suggestedPacing,
    lengthBracket,
    normalizedTags: Array.from(new Set(allTokens)).slice(0, 15),
  };

  return {
    normalizedGenres: Object.keys(genreWeights) as BookGenre[],
    suggestedMoods: Object.keys(moodWeights) as BookMood[],
    suggestedPacing,
    lengthBracket,
    recommendationVector: vector,
  };
}

export function computeCosineSimilarity(a: RecommendationVector, b: RecommendationVector): number {
  let dotProduct = 0.0;
  let normA = 0.0;
  let normB = 0.0;

  // Genre alignment (45% weight)
  const allGenres = new Set([...Object.keys(a.genres), ...Object.keys(b.genres)]);
  for (const g of allGenres) {
    const vA = a.genres[g] || 0;
    const vB = b.genres[g] || 0;
    dotProduct += vA * vB * 0.45;
    normA += vA * vA * 0.45;
    normB += vB * vB * 0.45;
  }

  // Mood alignment (35% weight)
  const allMoods = new Set([...Object.keys(a.moods), ...Object.keys(b.moods)]);
  for (const m of allMoods) {
    const vA = a.moods[m] || 0;
    const vB = b.moods[m] || 0;
    dotProduct += vA * vB * 0.35;
    normA += vA * vA * 0.35;
    normB += vB * vB * 0.35;
  }

  // Pacing match (10% weight)
  if (a.pacing && b.pacing) {
    const paceMatch = a.pacing === b.pacing ? 1.0 : 0.4;
    dotProduct += paceMatch * 0.1;
    normA += 0.1;
    normB += 0.1;
  }

  // Length match (10% weight)
  const lenMatch = a.lengthBracket === b.lengthBracket ? 1.0 : 0.5;
  dotProduct += lenMatch * 0.1;
  normA += 0.1;
  normB += 0.1;

  if (normA === 0 || normB === 0) return 0.0;
  return Math.max(0.0, Math.min(1.0, dotProduct / (Math.sqrt(normA) * Math.sqrt(normB))));
}

