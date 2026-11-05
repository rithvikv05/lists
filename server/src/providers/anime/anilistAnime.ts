import { searchAniList } from '../tv/anilist';
import { SearchResult } from '../../types';

export async function searchAnilistAnime(query: string, year?: string): Promise<SearchResult[]> {
  return searchAniList(query, 'ANIME', year);
}
