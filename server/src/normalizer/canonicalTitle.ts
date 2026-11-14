/** Display titles and part/volume numbers carry identity. */
export function cleanCanonicalTitle(rawTitle: string): string {
  return rawTitle.normalize('NFKC').replace(/\s+/gu, ' ').trim();
}
export function toCleanKey(title: string): string {
  return cleanCanonicalTitle(title).normalize('NFD').replace(/\p{M}/gu, '')
    .toLocaleLowerCase('en').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}
