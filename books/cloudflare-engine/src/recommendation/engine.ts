import {
  BookMetadata,
  RecommendationRequest,
  RecommendationResponse,
  RecommendedBook,
  BookGenre,
  BookMood,
  RecommendationVector,
} from '../types';
import { computeCosineSimilarity } from '../normalizer/attributes';

export function rankRecommendations(
  request: RecommendationRequest,
  candidatePool: BookMetadata[]
): RecommendationResponse {
  const targetVector = buildTargetVector(request, candidatePool);

  const recommendations: RecommendedBook[] = candidatePool
    .filter((b) => {
      // Exclude seed books from being recommended to the user
      if (request.favoriteBookIds && request.favoriteBookIds.includes(b.id)) return false;
      if (request.favoriteBookIds && b.primaryIsbn && request.favoriteBookIds.includes(b.primaryIsbn)) return false;
      return true;
    })
    .map((candidate) => {
      const similarity = computeCosineSimilarity(targetVector, candidate.recommendationVector);
      const similarityScore = Math.round(similarity * 100);

      // Find overlapping moods & genres
      const matchedMoods = candidate.suggestedMoods.filter((m) =>
        Boolean(targetVector.moods[m] && targetVector.moods[m] > 0)
      );
      const matchedGenres = candidate.normalizedGenres.filter((g) =>
        Boolean(targetVector.genres[g] && targetVector.genres[g] > 0)
      );

      const matchReason = buildMatchReason(matchedGenres, matchedMoods, candidate.suggestedPacing);

      return {
        book: candidate,
        similarityScore,
        matchedMoods,
        matchedGenres,
        matchReason,
      };
    })
    .filter((r) => r.similarityScore > 0)
    .sort((a, b) => b.similarityScore - a.similarityScore)
    .slice(0, request.limit || 15);

  return {
    totalCandidates: candidatePool.length,
    recommendations,
  };
}

function buildTargetVector(
  request: RecommendationRequest,
  candidatePool: BookMetadata[]
): RecommendationVector {
  const genres: Record<string, number> = {};
  const moods: Record<string, number> = {};

  // Add explicit genre preferences
  if (request.favoriteGenres) {
    for (const g of request.favoriteGenres) {
      genres[g] = 2.0;
    }
  }

  // Add explicit mood preferences
  if (request.preferredMoods) {
    for (const m of request.preferredMoods) {
      moods[m] = 2.0;
    }
  }

  // Factor in seed books user loved
  if (request.favoriteBookIds && request.favoriteBookIds.length > 0) {
    const seedBooks = candidatePool.filter((b) =>
      request.favoriteBookIds!.includes(b.id) || (b.primaryIsbn && request.favoriteBookIds!.includes(b.primaryIsbn))
    );
    for (const book of seedBooks) {
      for (const [g, w] of Object.entries(book.recommendationVector.genres)) {
        genres[g] = (genres[g] || 0) + w;
      }
      for (const [m, w] of Object.entries(book.recommendationVector.moods)) {
        moods[m] = (moods[m] || 0) + w;
      }
    }
  }

  return {
    genres,
    moods,
    pacing: request.preferredPacing,
    lengthBracket: 'MEDIUM',
    normalizedTags: [],
  };
}

function buildMatchReason(genres: BookGenre[], moods: BookMood[], pacing?: string): string {
  const parts: string[] = [];
  if (genres.length > 0) {
    parts.push(`Genre: ${genres.slice(0, 2).join(', ')}`);
  }
  if (moods.length > 0) {
    parts.push(`Mood: ${moods.slice(0, 2).map((m) => m.toLowerCase()).join(', ')}`);
  }
  if (pacing) {
    parts.push(`${pacing.toLowerCase()} pace`);
  }
  return parts.length > 0 ? parts.join(' · ') : 'Related literature profile';
}
