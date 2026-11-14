import { SearchResult } from '../types';

/** Preserve product type rather than disguising an expansion or bundle as its base game. */
export function gameKind(type?: string): string | undefined {
  const key = (type || '').toLowerCase().replace(/[^a-z]+/g, '_').replace(/^_|_$/g, '');
  if (['main_game', 'game', 'remake', 'remaster'].includes(key)) return 'game';
  const kinds: Record<string, string> = {
    dlc: 'game_dlc', dlc_addon: 'game_dlc', addon: 'game_dlc', expansion: 'game_expansion',
    standalone_expansion: 'game_expansion', bundle: 'game_bundle', compilation: 'game_collection',
    collection: 'game_collection', demo: 'game_demo', playtest: 'game_demo', music: 'game_soundtrack',
    soundtrack: 'game_soundtrack', mod: 'game_mod', episode: 'game_episode', season: 'game_season',
    port: 'game_port', expanded_game: 'game_edition',
    pack: 'game_bundle', series: 'game_series', application: 'game_software', software: 'game_software',
  };
  return kinds[key];
}

/** Conservative fallback for storefronts that label add-on products simply as "game". */
export function extraProductTitle(title: string): boolean {
  return /\b(?:demo|playtest|soundtrack|supporter\s+(?:pack|edition)|collector(?:['’]s|s)?\s+edition|(?:digital\s+)?deluxe\s+edition|launch\s+edition|season\s+pass|expansion\s+pack|complete\s+pack|bundle|collection|compilation)\s*(?:\([^)]*\))?$/iu.test(title) ||
    /(?:\s[-–—:]\s*|\()\s*(?:dlc|art\s*book|digital\s+extras|bonus\s+content|supporter(?:'s)?|collection)(?:\b.*)?$/iu.test(title);
}

export function visibleGame(item: SearchResult, includeExtras = false, allowSparse = false): boolean {
  if (item.category !== 'GAME') return true;
  if (!includeExtras && item.entityKind && item.entityKind !== 'game') return false;
  const authoritativeBase = item.entityKind === 'game' && item.sourceRefs?.some(r => r.source === 'igdb');
  if (!includeExtras && !authoritativeBase && extraProductTitle(item.title)) return false;
  if (/^[\s]*[$€£¥₹]\s*\d/u.test(item.creator) && !item.overview) return false;
  // An unclassified store hit is a discovery candidate, not another empty result card.
  const steamOnly = item.sourceRefs?.some(r => r.source === 'steam') && !item.sourceRefs.some(r => r.source !== 'steam');
  if (!allowSparse && steamOnly && (!item.entityKind || (!item.overview && !item.creator && !item.releaseYear))) return false;
  return true;
}
