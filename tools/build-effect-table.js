// Builds the mob effect table (id -> name, category) from the vanilla data generator output.
//
//   java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports
//   node tools/build-effect-table.js
//
// Writes data/effects-777.tsv. Like the block and item tables, this is generated from Mojang's
// own game data and is not committed: regenerate it locally when the target version changes.
//
// The effect registry is not sent over the network (it is built into the client), so the client
// has to know the numbers itself: `Update Mob Effect` carries only an id. The category
// (beneficial/harmful/neutral) is decided in the game's code, not in the data, and it matters
// for the HUD: vanilla draws beneficial effects in a row and the rest in a column. The map below
// is transcribed from `net.minecraft.world.effect.MobEffects` in the 26.3 server jar
// (`javap -p -c` shows the category next to every registration).
const fs = require('fs');
const path = require('path');

const source = process.argv[2] ?? 'tools/server/generated/reports/registries.json';
const target = process.argv[3] ?? 'data/effects-777.tsv';

const CATEGORIES = {
  speed: 'BENEFICIAL',
  slowness: 'HARMFUL',
  haste: 'BENEFICIAL',
  mining_fatigue: 'HARMFUL',
  strength: 'BENEFICIAL',
  instant_health: 'BENEFICIAL',
  instant_damage: 'HARMFUL',
  jump_boost: 'BENEFICIAL',
  nausea: 'HARMFUL',
  regeneration: 'BENEFICIAL',
  resistance: 'BENEFICIAL',
  fire_resistance: 'BENEFICIAL',
  water_breathing: 'BENEFICIAL',
  invisibility: 'BENEFICIAL',
  blindness: 'HARMFUL',
  night_vision: 'BENEFICIAL',
  hunger: 'HARMFUL',
  weakness: 'HARMFUL',
  poison: 'HARMFUL',
  wither: 'HARMFUL',
  health_boost: 'BENEFICIAL',
  absorption: 'BENEFICIAL',
  saturation: 'BENEFICIAL',
  glowing: 'NEUTRAL',
  levitation: 'HARMFUL',
  luck: 'BENEFICIAL',
  unluck: 'HARMFUL',
  slow_falling: 'BENEFICIAL',
  conduit_power: 'BENEFICIAL',
  dolphins_grace: 'BENEFICIAL',
  bad_omen: 'NEUTRAL',
  hero_of_the_village: 'BENEFICIAL',
  darkness: 'HARMFUL',
  trial_omen: 'NEUTRAL',
  raid_omen: 'NEUTRAL',
  wind_charged: 'HARMFUL',
  weaving: 'HARMFUL',
  oozing: 'HARMFUL',
  infested: 'HARMFUL',
  breath_of_the_nautilus: 'BENEFICIAL',
};

const registries = JSON.parse(fs.readFileSync(source, 'utf8'));
const effects = registries['minecraft:mob_effect']?.entries;
if (!effects) {
  throw new Error(`no minecraft:mob_effect registry in ${source}`);
}

const rows = Object.entries(effects).map(([name, entry]) => {
  const short = name.replace(/^minecraft:/, '');
  const category = CATEGORIES[short];
  if (!category) throw new Error(`no category for effect ${short}: update the map from the server jar`);
  return [entry.protocol_id, short, category];
});
rows.sort((a, b) => a[0] - b[0]);

const maxId = rows.reduce((max, row) => Math.max(max, row[0]), 0);
for (let i = 0; i <= maxId; i++) {
  if (!rows.some((row) => row[0] === i)) console.warn(`warning: effect id ${i} is unused`);
}
if (Object.keys(CATEGORIES).length !== rows.length) {
  console.warn(`warning: category map has ${Object.keys(CATEGORIES).length} names, registry has ${rows.length}`);
}

fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, rows.map((row) => row.join('\t')).join('\n') + '\n', 'utf8');

console.log(`effects: ${rows.length}, max id: ${maxId}`);
console.log(`written: ${target}`);
