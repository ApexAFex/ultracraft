// Builds a compact item table (id -> name) from the vanilla data generator output.
//
//   java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports
//   node tools/build-item-table.js
//
// Writes data/items-777.tsv. Like the block table, this is generated from Mojang's own game
// data and is not committed: regenerate it locally when the target version changes.
//
// The item registry is not sent over the network (it is built into the client), so the client
// has to know the numbers itself. A stack on the wire carries only an id.
const fs = require('fs');
const path = require('path');

const source = process.argv[2] ?? 'tools/server/generated/reports/registries.json';
const target = process.argv[3] ?? 'data/items-777.tsv';

const registries = JSON.parse(fs.readFileSync(source, 'utf8'));
const items = registries['minecraft:item']?.entries;
if (!items) {
  throw new Error(`no minecraft:item registry in ${source}`);
}

const rows = Object.entries(items).map(([name, entry]) => [
  entry.protocol_id,
  name.replace(/^minecraft:/, ''),
]);

rows.sort((a, b) => a[0] - b[0]);

const maxId = rows.reduce((max, row) => Math.max(max, row[0]), 0);
for (let i = 0; i <= maxId; i++) {
  if (!rows.some((row) => row[0] === i)) {
    console.warn(`warning: item id ${i} is unused`);
  }
}

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => `${row[0]}\t${row[1]}`).join('\n') + '\n', 'utf8');

console.log(`items: ${rows.length}, max id: ${maxId}`);
console.log(`written: ${target}`);
