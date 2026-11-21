// Собирает таблицу статистики из отчётов сервера: data/stats-777.tsv.
//
// В игре статистика — это реестры `minecraft:stat_type` (9 видов: mined, crafted, used…)
// и `minecraft:custom_stat` (78 счётчиков вроде «прыжков» или «пройденных блоков»).
// Оба лежат в tools/server/generated/reports/registries.json вместе с protocol_id.
//
//   node tools/build-stats-table.js
//
// Формат строки: `<вид>\t<id>\t<имя>`, где вид — `type` (вид счётчика) или `custom`.

const fs = require('fs');
const path = require('path');

const reports = path.join(__dirname, 'server', 'generated', 'reports', 'registries.json');
if (!fs.existsSync(reports)) {
  console.error('Нет отчётов сервера: ' + reports);
  console.error('Сгенерируй их: java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports');
  process.exit(1);
}

const registries = JSON.parse(fs.readFileSync(reports, 'utf8'));
const types = registries['minecraft:stat_type'].entries;
const custom = registries['minecraft:custom_stat'].entries;

const rows = [];
for (const [key, value] of Object.entries(types)) {
  rows.push(['type', value.protocol_id, key.replace('minecraft:', '')]);
}
for (const [key, value] of Object.entries(custom)) {
  rows.push(['custom', value.protocol_id, key.replace('minecraft:', '')]);
}
rows.sort((a, b) => (a[0] === b[0] ? a[1] - b[1] : a[0] < b[0] ? -1 : 1));

const out = path.join(__dirname, '..', 'data', 'stats-777.tsv');
fs.writeFileSync(out, rows.map((row) => row.join('\t')).join('\n') + '\n', 'utf8');
console.log('Записано ' + rows.length + ' строк в ' + out);
