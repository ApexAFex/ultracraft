// Builds the entity type table (id -> name) from the vanilla data generator output.
//
//   java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports
//   node tools/build-entity-table.js
//
// Writes data/entities-777.tsv. The entity registry is built into the client, so `Spawn Entity`
// carries only a number; without the table a cow and a creeper are the same coloured box.
const fs = require('fs');
const path = require('path');

const source = process.argv[2] ?? 'tools/server/generated/reports/registries.json';
const target = process.argv[3] ?? 'data/entities-777.tsv';

const registries = JSON.parse(fs.readFileSync(source, 'utf8'));
const entities = registries['minecraft:entity_type']?.entries;
if (!entities) throw new Error(`no minecraft:entity_type registry in ${source}`);

const rows = Object.entries(entities).map(([name, entry]) => [
  entry.protocol_id,
  name.replace(/^minecraft:/, ''),
]);
rows.sort((a, b) => a[0] - b[0]);

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => row.join('\t')).join('\n') + '\n', 'utf8');

console.log(`entity types: ${rows.length}`);
console.log(`written: ${target}`);
