// Builds a compact block-state table (id -> name) from the vanilla data generator output.
//
//   java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports
//   node tools/build-block-table.js
//
// Writes data/blocks-777.tsv. The file is generated from Mojang's own game data and is
// therefore not committed; regenerate it locally when the target version changes.
const fs = require('fs');
const path = require('path');

const source = process.argv[2] ?? 'tools/server/generated/reports/blocks.json';
const target = process.argv[3] ?? 'data/blocks-777.tsv';

const blocks = JSON.parse(fs.readFileSync(source, 'utf8'));
const rows = [];

for (const [blockName, definition] of Object.entries(blocks)) {
  for (const state of definition.states ?? []) {
    rows.push([state.id, blockName.replace(/^minecraft:/, '')]);
  }
}

rows.sort((a, b) => a[0] - b[0]);

const maxId = rows.reduce((max, row) => Math.max(max, row[0]), 0);
for (let i = 0; i <= maxId; i++) {
  if (!rows.some((row) => row[0] === i)) {
    console.warn(`warning: state id ${i} has no block`);
  }
}

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => `${row[0]}\t${row[1]}`).join('\n') + '\n', 'utf8');

console.log(`blocks: ${Object.keys(blocks).length}, states: ${rows.length}, max id: ${maxId}`);
console.log(`written: ${target}`);
