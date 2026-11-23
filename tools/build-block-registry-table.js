// Builds the block registry table (id -> name) from the vanilla data generator output.
//
//   node tools/build-block-registry-table.js
//
// Writes data/blocks-registry-777.tsv. Item components (`can_place_on`, `can_break`) and
// block tags reference the *registry* of blocks, not block states, so the big `blocks-777.tsv`
// (1286 blocks × states) does not help: these numbers are short registry ids.
const fs = require('fs');
const path = require('path');

const source = process.argv[2] ?? 'tools/server/generated/reports/registries.json';
const target = process.argv[3] ?? 'data/blocks-registry-777.tsv';

const registries = JSON.parse(fs.readFileSync(source, 'utf8'));
const blocks = registries['minecraft:block']?.entries;
if (!blocks) throw new Error(`no minecraft:block registry in ${source}`);

const rows = Object.entries(blocks).map(([name, entry]) => [
  entry.protocol_id,
  name.replace(/^minecraft:/, ''),
]);
rows.sort((a, b) => a[0] - b[0]);

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => row.join('\t')).join('\n') + '\n', 'utf8');

console.log(`blocks (registry): ${rows.length}`);
console.log(`written: ${target}`);
