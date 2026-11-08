// Generates optimized WebP screenshots and brand icons from the app repository assets.
// Usage: node scripts/optimize-shots.mjs
import { mkdir, access } from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';

const root = path.resolve(import.meta.dirname, '..');
const appShots = path.resolve(root, '../docs/screenshots');
const out = path.resolve(root, 'public/shots');

const map = {
  dashboard: 'dashboard-ru.png',
  terminal: 'terminal-key-auth.png',
  'new-server': 'server-key.png',
  sessions: 'sessions.png',
  htop: 'htop.png',
  vim: 'vim.png',
  reconnect: 'host-key.png',
};

const widths = [480, 720, 1080];

await mkdir(out, { recursive: true });

for (const [id, file] of Object.entries(map)) {
  const src = path.join(appShots, file);
  await access(src);
  for (const width of widths) {
    const target = path.join(out, `${id}-${width}.webp`);
    await sharp(src)
      .resize({ width, withoutEnlargement: true })
      .webp({ quality: 78, effort: 5 })
      .toFile(target);
    console.log(`shots/${id}-${width}.webp`);
  }
}

// Hero derivative: htop without the Android status bar and app toolbar (test host visible there).
const heroSrc = path.join(appShots, 'htop.png');
for (const width of widths) {
  await sharp(heroSrc)
    .extract({ left: 0, top: 300, width: 1080, height: 2100 })
    .resize({ width })
    .webp({ quality: 78, effort: 5 })
    .toFile(path.join(out, `hero-${width}.webp`));
  console.log(`shots/hero-${width}.webp`);
}

const icon = path.join(root, 'public/favicon.svg');
for (const size of [180, 192, 512]) {
  const name = size === 180 ? 'apple-touch-icon.png' : `icon-${size}.png`;
  await sharp(icon, { density: 384 })
    .resize(size, size, { fit: 'contain', background: '#0a0b0d' })
    .png({ compressionLevel: 9 })
    .toFile(path.join(root, 'public', name));
  console.log(name);
}
