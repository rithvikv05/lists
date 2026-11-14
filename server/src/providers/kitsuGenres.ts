export interface KitsuGenreRelationships { genres?: { data?: Array<{ id: string; type: string }> } }
export interface KitsuIncludedGenre { id: string; type: string; attributes?: { name?: string } }

/** Included genres belong to specific records; never spread every genre across all hits. */
export function kitsuGenres(relationships: KitsuGenreRelationships | undefined, included: KitsuIncludedGenre[] = []): string[] {
  const names = new Map(included.filter(r => r.type === 'genres' && typeof r.attributes?.name === 'string')
    .map(r => [r.id, r.attributes!.name!]));
  return [...new Set((relationships?.genres?.data || []).filter(r => r.type === 'genres')
    .map(r => names.get(r.id) || '').filter(Boolean))];
}
