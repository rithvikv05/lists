export function cleanCanonicalTitle(rawTitle: string): string {
  return rawTitle
    .replace(/(?:,\s*)?(?:season|series|part|vol\.?|volume)\s*\d+.*$/i, '')
    .replace(/(?:the final season|complete series|box set).*$/i, '')
    .replace(/\s*\((?:tv series|tv show|series|film|movie|anime|manga|novel|comic|video game|board game|game|\d{4})\)/i, '')
    .trim();
}

export function toCleanKey(title: string): string {
  const canonical = cleanCanonicalTitle(title);
  return canonical.toLowerCase().replace(/[^a-z0-9]/g, '');
}

