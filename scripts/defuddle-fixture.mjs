// Runs Defuddle on a saved page and prints the same JSON the app gets from its browser.
// Use: node scripts/defuddle-fixture.mjs page.html https://the.page/address > out.json
// Needs: npm install defuddle linkedom (in any folder on the NODE_PATH).
import { Defuddle } from 'defuddle/node';
import { readFileSync } from 'fs';

const [file, url] = process.argv.slice(2);
const r = await Defuddle(readFileSync(file, 'utf8'), url);
process.stdout.write(JSON.stringify({
  ok: true, url, content: r.content || '', title: r.title || '', author: r.author || '',
  published: r.published || '', image: r.image || '', site: r.site || '', description: r.description || '',
  domain: r.domain || '', language: r.language || '', wordCount: r.wordCount || 0,
}));
