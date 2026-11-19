// Builds the menu table (id -> name) from the vanilla data generator output.
//
//   java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports
//   node tools/build-menu-table.js
//
// Writes data/menus-777.tsv. The menu registry is built into the client, so `Open Screen`
// carries only a number; without the table the client cannot tell a chest from a furnace.
const fs = require('fs');
const path = require('path');

const source = process.argv[2] ?? 'tools/server/generated/reports/registries.json';
const target = process.argv[3] ?? 'data/menus-777.tsv';

const registries = JSON.parse(fs.readFileSync(source, 'utf8'));
const menus = registries['minecraft:menu']?.entries;
if (!menus) throw new Error(`no minecraft:menu registry in ${source}`);

const rows = Object.entries(menus).map(([name, entry]) => [
  entry.protocol_id,
  name.replace(/^minecraft:/, ''),
]);
rows.sort((a, b) => a[0] - b[0]);

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => row.join('\t')).join('\n') + '\n', 'utf8');

console.log(`menus: ${rows.length}`);
console.log(`written: ${target}`);
