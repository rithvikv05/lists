import app from './src/index';

async function runTests() {
  console.log('--- TEST 1: Health Check ---');
  const res1 = await app.request('/health');
  console.log('Status:', res1.status, await res1.json());

  console.log('\n--- TEST 2: Search "Dune" ---');
  const res2 = await app.request('/api/v1/books/search?q=dune&limit=3');
  const data2 = await res2.json() as any;
  console.log('Count:', data2.count);
  for (const b of data2.results || []) {
    console.log(`- "${b.title}" by ${b.primaryAuthor}`);
    console.log(`  ISBN: ${b.primaryIsbn || 'N/A'}, Pages: ${b.totalPages || 'N/A'}`);
    console.log(`  Cover: ${b.coverUrl ? 'Yes' : 'No'}`);
    console.log(`  Genres: ${b.normalizedGenres?.join(', ')}`);
    console.log(`  Moods: ${b.suggestedMoods?.join(', ')}`);
    console.log(`  Pacing: ${b.suggestedPacing}`);
  }

  console.log('\n--- TEST 3: Barcode / Direct ISBN (9780141187761 - 1984) ---');
  const res3 = await app.request('/api/v1/books/isbn/9780141187761');
  const data3 = await res3.json() as any;
  console.log('ISBN Lookup Status:', res3.status);
  if (data3.book) {
    console.log(`- Title: ${data3.book.title}`);
    console.log(`  Author: ${data3.book.primaryAuthor}`);
    console.log(`  Pages: ${data3.book.totalPages}`);
    console.log(`  Publisher: ${data3.book.publisher}`);
    console.log(`  ISBN13: ${data3.book.isbn13}`);
    console.log(`  Cover: ${data3.book.coverUrl}`);
  }

  console.log('\n--- TEST 4: Reading Speed & ETA ---');
  const res4 = await app.request('/api/v1/books/reading-speed', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      totalPages: 400,
      currentPage: 150,
      sessions: [
        { durationMinutes: 30, pagesRead: 20 },
        { durationMinutes: 45, pagesRead: 35 },
      ]
    })
  });
  console.log('Speed calc:', await res4.json());

  console.log('\n--- TEST 5: Recommendation Matching ---');
  const res5 = await app.request('/api/v1/books/recommend', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      favoriteGenres: ['SCI_FI'],
      preferredMoods: ['DARK', 'TENSE'],
      preferredPacing: 'MEDIUM',
      limit: 3
    })
  });
  const data5 = await res5.json() as any;
  console.log('Recommendations count:', data5.recommendations?.length);
  for (const r of data5.recommendations || []) {
    console.log(`- ${r.book.title} (Match: ${r.similarityScore}%) -> ${r.matchReason}`);
  }
}

runTests().catch(console.error);

