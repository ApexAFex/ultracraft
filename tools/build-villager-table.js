// Собирает таблицу климатов и профессий жителей из отчёта сервера.
//
// `Villager Data` в пакете — это три номера реестров, а не имена. Номера задаёт сервер,
// поэтому единственный честный источник — его `registries.json`, который появляется после
// `java -jar server.jar --reports` (tools/server/generated/reports/registries.json).
//
//   node tools/build-villager-table.js
//   node tools/build-villager-table.js <registries.json> <data/villager-777.tsv>
//
// Формат выхода — TSV: вид (type/profession), номер, имя.

const fs = require('fs');
const path = require('path');

const input = process.argv[2] ?? 'tools/server/generated/reports/registries.json';
const output = process.argv[3] ?? 'data/villager-777.tsv';

const raw = JSON.parse(fs.readFileSync(input, 'utf8'));

/** Один реестр отчёта: имя записи (minecraft:plains) → номер. */
function entries(registry) {
    const block = raw[registry];
    if (!block || !block.entries) return [];
    return Object.entries(block.entries)
        .map(([name, value]) => ({
            name: name.replace(/^minecraft:/, ''),
            id: value.protocol_id,
        }))
        .filter((entry) => Number.isInteger(entry.id))
        .sort((a, b) => a.id - b.id);
}

const types = entries('minecraft:villager_type');
const professions = entries('minecraft:villager_profession');

if (types.length === 0 || professions.length === 0) {
    console.error('В отчёте нет реестров жителей: сначала запусти server.jar --reports');
    process.exit(1);
}

const lines = [];
for (const entry of types) lines.push(['type', entry.id, entry.name].join('\t'));
for (const entry of professions) lines.push(['profession', entry.id, entry.name].join('\t'));

fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, lines.join('\n') + '\n', 'utf8');
console.log(`${output}: климатов ${types.length}, профессий ${professions.length}`);
