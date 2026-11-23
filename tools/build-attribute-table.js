// Builds the attribute table (id -> name) from the vanilla data generator output.
//
//   node tools/build-attribute-table.js
//
// Writes data/attributes-777.tsv. Item stacks carry `attribute_modifiers` with numeric
// attribute ids; the tooltip needs the names («+2 к урону»).
const fs = require('fs');
const path = require('path');

const source = process.argv[2] ?? 'tools/server/generated/reports/registries.json';
const target = process.argv[3] ?? 'data/attributes-777.tsv';

const registries = JSON.parse(fs.readFileSync(source, 'utf8'));
const attributes = registries['minecraft:attribute']?.entries;
if (!attributes) throw new Error(`no minecraft:attribute registry in ${source}`);

const rows = Object.entries(attributes).map(([name, entry]) => [
  entry.protocol_id,
  name.replace(/^minecraft:/, ''),
]);
rows.sort((a, b) => a[0] - b[0]);

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => row.join('\t')).join('\n') + '\n', 'utf8');

console.log(`attributes: ${rows.length}`);
console.log(`written: ${target}`);
