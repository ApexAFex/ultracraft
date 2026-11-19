// Builds the data component table (id -> name) from the vanilla data generator output.
//
//   node tools/build-component-table.js
//
// Writes data/components-777.tsv. Item stacks carry a patch of components; to read the
// `damage` component the client must know its numeric id.
const fs = require('fs');
const path = require('path');

const source = process.argv[2] ?? 'tools/server/generated/reports/registries.json';
const target = process.argv[3] ?? 'data/components-777.tsv';

const registries = JSON.parse(fs.readFileSync(source, 'utf8'));
const components = registries['minecraft:data_component_type']?.entries;
if (!components) throw new Error(`no minecraft:data_component_type registry in ${source}`);

const rows = Object.entries(components).map(([name, entry]) => [
  entry.protocol_id,
  name.replace(/^minecraft:/, ''),
]);
rows.sort((a, b) => a[0] - b[0]);

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => row.join('\t')).join('\n') + '\n', 'utf8');

console.log(`data components: ${rows.length}`);
console.log(`written: ${target}`);
