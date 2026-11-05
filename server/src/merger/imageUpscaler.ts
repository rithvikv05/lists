export function enhanceCoverUrl(url: string): string {
  if (!url) return '';
  let enhanced = url.trim();

  // Apple iTunes / Apple Books
  if (enhanced.includes('apple.com') || enhanced.includes('mzstatic.com')) {
    enhanced = enhanced
      .replace(/100x100bb\./g, '1200x1200bb.')
      .replace(/600x600bb\./g, '1200x1200bb.')
      .replace(/800x800bb\./g, '1200x1200bb.');
  }
  // Amazon Media CDN (IMDb)
  else if (enhanced.includes('media-amazon.com')) {
    enhanced = enhanced.replace(/\._V1_.*\.jpg/g, '._V1_SX1200.jpg');
  }
  // Google Books
  else if (enhanced.includes('books.google.com')) {
    enhanced = enhanced
      .replace(/^http:\/\//, 'https://')
      .replace(/&edge=curl/g, '')
      .replace(/zoom=[12]/g, 'zoom=3');
  }
  // Open Library
  else if (enhanced.includes('covers.openlibrary.org')) {
    enhanced = enhanced
      .replace(/-[MS]\.jpg/g, '-L.jpg');
  }

  return enhanced;
}

