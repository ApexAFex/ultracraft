// Builds the item stats table (armour points, toughness, max damage) from the data generator.
//
//   java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports
//   node tools/build-item-stats.js
//
// Writes data/item-stats-777.tsv. Like the block, item and effect tables this is generated from
// Mojang's own game data and is not committed.
//
// Why a table at all: in 26.x armour points live in the item's `minecraft:attribute_modifiers`
// component, and durability in `minecraft:max_damage`. The client sees only item ids on the wire,
// so it has to know these numbers itself — exactly like item names.
const fs = require('fs');
const path = require('path');

const reports = process.argv[2] ?? 'tools/server/generated/reports';
const target = process.argv[3] ?? 'data/item-stats-777.tsv';

const registries = JSON.parse(fs.readFileSync(path.join(reports, 'registries.json'), 'utf8'));
const items = registries['minecraft:item']?.entries;
if (!items) throw new Error(`no minecraft:item registry in ${reports}`);

const componentsDir = path.join(reports, 'minecraft', 'components', 'item');

/** Сумма значений одного вида модификаторов (armor, armor_toughness) в компонентах предмета. */
function modifierSum(components, type) {
  const modifiers = components['minecraft:attribute_modifiers'] ?? [];
  return modifiers
    .filter((modifier) => modifier.type === type && (modifier.operation ?? 'add_value') === 'add_value')
    .reduce((sum, modifier) => sum + Number(modifier.amount ?? 0), 0);
}

const rows = [];
for (const [name, entry] of Object.entries(items)) {
  const short = name.replace(/^minecraft:/, '');
  const file = path.join(componentsDir, `${short}.json`);
  if (!fs.existsSync(file)) continue;
  const components = JSON.parse(fs.readFileSync(file, 'utf8')).components ?? {};
  const armour = Math.round(modifierSum(components, 'minecraft:armor'));
  const toughness = Math.round(modifierSum(components, 'minecraft:armor_toughness'));
  const maxDamage = Number(components['minecraft:max_damage'] ?? 0);
  if (armour === 0 && maxDamage === 0) continue;
  rows.push([entry.protocol_id, armour, toughness, maxDamage]);
}
rows.sort((a, b) => a[0] - b[0]);

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => row.join('\t')).join('\n') + '\n', 'utf8');

const withArmour = rows.filter((row) => row[1] > 0).length;
console.log(`item stats: ${rows.length} rows, ${withArmour} with armour, max id: ${rows[rows.length - 1][0]}`);
console.log(`written: ${target}`);
