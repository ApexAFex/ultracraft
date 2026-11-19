// Builds the particle type table (id -> name) from the vanilla data generator output.
//
//   node tools/build-particle-table.js
//
// Writes data/particles-777.tsv. `Level Particles` carries a number; the table turns it into a
// name so the client knows whether the particle has extra data and what colour to use.
const fs = require('fs');
const path = require('path');

const source = process.argv[2] ?? 'tools/server/generated/reports/registries.json';
const target = process.argv[3] ?? 'data/particles-777.tsv';

const registries = JSON.parse(fs.readFileSync(source, 'utf8'));
const particles = registries['minecraft:particle_type']?.entries;
if (!particles) throw new Error(`no minecraft:particle_type registry in ${source}`);

const rows = Object.entries(particles).map(([name, entry]) => [
  entry.protocol_id,
  name.replace(/^minecraft:/, ''),
]);
rows.sort((a, b) => a[0] - b[0]);

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => row.join('\t')).join('\n') + '\n', 'utf8');

console.log(`particle types: ${rows.length}`);
console.log(`written: ${target}`);
