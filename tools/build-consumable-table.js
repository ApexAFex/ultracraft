// Builds the table of items that are used with a visible animation: eaten, drunk, blown, etc.
//
//   node tools/build-consumable-table.js
//
// Source: the vanilla data generator report `reports/minecraft/components/item/*.json`
// (run the server jar with `--reports` first). Every item JSON lists its default components,
// and `minecraft:consumable` is the one that decides the animation (`eat`, `drink`, ...).
// The client cannot guess this from the item id, and the packet that starts using an item
// carries no component data, so the table is generated from the game's own data.
//
// Writes data/consumables-777.tsv: item, animation, sound, seconds, particles.
const fs = require('fs');
const path = require('path');

// Генератор данных пишет отчёты туда, откуда его запустили: из корня репозитория или
// из папки сервера. Оба места равноправны.
const candidates = [
  'generated/reports/minecraft/components/item',
  'tools/server/generated/reports/minecraft/components/item',
];
const source = process.argv[2] ?? candidates.find((dir) => fs.existsSync(dir));
if (!source) {
  throw new Error(`report not found, run: java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports`);
}
const target = process.argv[3] ?? 'data/consumables-777.tsv';

const files = fs.readdirSync(source).filter((name) => name.endsWith('.json'));
const rows = [];
for (const file of files) {
  const item = `minecraft:${path.basename(file, '.json')}`;
  const json = JSON.parse(fs.readFileSync(path.join(source, file), 'utf8'));
  const components = json.components ?? {};
  const consumable = components['minecraft:consumable'];
  if (!consumable) continue;
  const animation = (consumable.animation ?? 'eat').replace(/^minecraft:/, '');
  const sound = (consumable.sound ?? defaultSound(animation)).replace(/^minecraft:/, '');
  const seconds = consumable.consume_seconds ?? 1.6;
  const particles = components['minecraft:consumable']?.has_consume_particles ?? true;
  rows.push([item, animation, sound, seconds, particles ? 1 : 0]);
}

function defaultSound(animation) {
  return animation === 'drink' ? 'minecraft:entity.generic.drink' : 'minecraft:entity.generic.eat';
}

rows.sort((a, b) => a[0].localeCompare(b[0]));
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(
  target,
  '# item\tanimation\tsound\tseconds\tparticles\n' +
    rows.map((row) => row.join('\t')).join('\n') +
    '\n',
  'utf8',
);

console.log(`consumable items: ${rows.length}`);
console.log(`written: ${target}`);
