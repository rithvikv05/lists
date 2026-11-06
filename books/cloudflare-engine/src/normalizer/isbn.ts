export function cleanIsbn(raw?: string | null): string {
  if (!raw) return '';
  return raw.trim().toUpperCase().replace(/[^0-9X]/g, '');
}

export function isValidIsbn10(cleaned: string): boolean {
  if (cleaned.length !== 10) return false;
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    const c = cleaned.charCodeAt(i) - 48;
    if (c < 0 || c > 9) return false;
    sum += c * (10 - i);
  }
  const lastChar = cleaned[9];
  const checkDigit = lastChar === 'X' ? 10 : lastChar.charCodeAt(0) - 48;
  if (checkDigit < 0 || checkDigit > 10) return false;
  sum += checkDigit;
  return sum % 11 === 0;
}

export function isValidIsbn13(cleaned: string): boolean {
  if (cleaned.length !== 13) return false;
  if (!cleaned.startsWith('978') && !cleaned.startsWith('979')) return false;
  let sum = 0;
  for (let i = 0; i < 12; i++) {
    const c = cleaned.charCodeAt(i) - 48;
    if (c < 0 || c > 9) return false;
    const weight = i % 2 === 0 ? 1 : 3;
    sum += c * weight;
  }
  const lastDigit = cleaned.charCodeAt(12) - 48;
  if (lastDigit < 0 || lastDigit > 9) return false;
  const checkDigit = (10 - (sum % 10)) % 10;
  return checkDigit === lastDigit;
}

export function isValidIsbn(raw?: string | null): boolean {
  const cleaned = cleanIsbn(raw);
  return cleaned.length === 10 ? isValidIsbn10(cleaned) : cleaned.length === 13 ? isValidIsbn13(cleaned) : false;
}

export function isQueryAnIsbn(query?: string | null): boolean {
  const cleaned = cleanIsbn(query);
  if (cleaned.length === 10 && (isValidIsbn10(cleaned) || /^[0-9]{10}$/.test(cleaned))) return true;
  if (cleaned.length === 13 && (cleaned.startsWith('978') || cleaned.startsWith('979'))) {
    return isValidIsbn13(cleaned) || /^[0-9]{13}$/.test(cleaned);
  }
  return false;
}

export function isbn10ToIsbn13(isbn10: string): string | null {
  const cleaned = cleanIsbn(isbn10);
  if (cleaned.length !== 10) return null;
  const core = '978' + cleaned.substring(0, 9);
  let sum = 0;
  for (let i = 0; i < 12; i++) {
    const weight = i % 2 === 0 ? 1 : 3;
    sum += (core.charCodeAt(i) - 48) * weight;
  }
  const check = (10 - (sum % 10)) % 10;
  return core + check;
}

export function isbn13ToIsbn10(isbn13: string): string | null {
  const cleaned = cleanIsbn(isbn13);
  if (cleaned.length !== 13 || !cleaned.startsWith('978')) return null;
  const core = cleaned.substring(3, 12);
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    sum += (core.charCodeAt(i) - 48) * (10 - i);
  }
  const rem = 11 - (sum % 11);
  const check = rem === 11 ? '0' : rem === 10 ? 'X' : rem.toString();
  return core + check;
}

export function resolveIsbnPair(raw?: string | null): { isbn10?: string; isbn13?: string } {
  const cleaned = cleanIsbn(raw);
  if (cleaned.length === 10) {
    return { isbn10: cleaned, isbn13: isbn10ToIsbn13(cleaned) || undefined };
  } else if (cleaned.length === 13) {
    return { isbn10: isbn13ToIsbn10(cleaned) || undefined, isbn13: cleaned };
  }
  return {};
}

