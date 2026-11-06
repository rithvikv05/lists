import { cleanIsbn, isValidIsbn } from '../normalizer/isbn';

export function resolveBestCover(
  primaryIsbn?: string,
  openLibraryCoverId?: number,
  googleCoverUrl?: string
): { coverUrl?: string; thumbnailCoverUrl?: string } {
  const validIsbn = primaryIsbn && isValidIsbn(cleanIsbn(primaryIsbn)) ? cleanIsbn(primaryIsbn) : undefined;

  // Candidate 1: Open Library ISBN High-Res Mirror
  const olIsbnCover = validIsbn ? `https://covers.openlibrary.org/b/isbn/${validIsbn}-L.jpg?default=false` : undefined;

  // Candidate 2: Open Library internal cover ID
  const olIdCover = openLibraryCoverId && openLibraryCoverId > 0
    ? `https://covers.openlibrary.org/b/id/${openLibraryCoverId}-L.jpg?default=false`
    : undefined;

  // Candidate 3: Cleaned Google Books cover
  const cleanedGoogleCover = googleCoverUrl ? cleanGoogleCoverUrl(googleCoverUrl) : undefined;

  const coverUrl = olIsbnCover || olIdCover || cleanedGoogleCover;
  const thumbnailCoverUrl = cleanedGoogleCover || (validIsbn ? `https://covers.openlibrary.org/b/isbn/${validIsbn}-M.jpg` : coverUrl);

  return { coverUrl, thumbnailCoverUrl };
}

export function cleanGoogleCoverUrl(url: string): string {
  if (!url) return '';
  return url
    .replace('http://', 'https://')
    .replace('&edge=curl', '')
    .replace('&zoom=5', '&zoom=1');
}

