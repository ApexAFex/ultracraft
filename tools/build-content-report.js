// Собирает docs/content-appendix.md — полный состав Minecraft 26.3 по данным самой игры.
//
//   java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports
//   node tools/build-content-report.js
//
// Два источника:
//   reports/registries.json  — реестры (блоки, предметы, сущности, эффекты, меню, звуки);
//   data/minecraft/**        — файлы данных в серверном jar (рецепты, зачарования, биомы,
//                              структуры, лут, торговля).
//
// Отчёт описывает игру, а не наш клиент, поэтому генерируется независимо от состояния
// проекта. Приложение не редактируется вручную: правки потеряются при следующем запуске.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');

const registriesPath = process.argv[2] ?? path.join(root, 'tools/server/generated/reports/registries.json');
const outPath = process.argv[3] ?? path.join(root, 'docs/content-appendix.md');

// Серверный jar: в нём лежат файлы данных, которых нет в отчётах реестров.
// Годится и распакованный `server-26.3.jar`, и версия из установки сервера.
function findServerJar() {
  const direct = [
    'tools/mcd/server-26.3.jar',
    'tools/server/versions/26.3/server-26.3.jar',
  ].map((p) => path.join(root, p));
  for (const candidate of direct) {
    if (fs.existsSync(candidate)) return candidate;
  }
  const versions = path.join(root, 'tools/server/versions');
  if (fs.existsSync(versions)) {
    for (const version of fs.readdirSync(versions).sort().reverse()) {
      const candidate = path.join(versions, version, `server-${version}.jar`);
      if (fs.existsSync(candidate)) return candidate;
    }
  }
  return null;
}

// Читает только имена файлов из zip: данных нам не нужно, а распаковывать 26 МБ — лишнее.
// Разбирается центральный каталог; Zip64 не поддерживается и честно об этом сообщает.
function listZipNames(file) {
  const fd = fs.openSync(file, 'r');
  try {
    const size = fs.fstatSync(fd).size;
    const tailLength = Math.min(size, 1 << 20);
    const tail = Buffer.alloc(tailLength);
    fs.readSync(fd, tail, 0, tailLength, size - tailLength);

    let eocd = -1;
    for (let i = tail.length - 22; i >= 0; i -= 1) {
      if (tail.readUInt32LE(i) === 0x06054b50) {
        eocd = i;
        break;
      }
    }
    if (eocd < 0) throw new Error('не найден конец центрального каталога zip');

    const count = tail.readUInt16LE(eocd + 10);
    const directorySize = tail.readUInt32LE(eocd + 12);
    const directoryOffset = tail.readUInt32LE(eocd + 16);
    if (directoryOffset === 0xffffffff || directorySize === 0xffffffff) {
      throw new Error('zip64 не поддерживается: нужен обычный серверный jar');
    }

    const directory = Buffer.alloc(directorySize);
    fs.readSync(fd, directory, 0, directorySize, directoryOffset);

    const names = [];
    let at = 0;
    for (let i = 0; i < count; i += 1) {
      if (directory.readUInt32LE(at) !== 0x02014b50) throw new Error('битый центральный каталог zip');
      const nameLength = directory.readUInt16LE(at + 28);
      const extraLength = directory.readUInt16LE(at + 30);
      const commentLength = directory.readUInt16LE(at + 32);
      names.push(directory.toString('utf8', at + 46, at + 46 + nameLength));
      at += 46 + nameLength + extraLength + commentLength;
    }
    return names;
  } finally {
    fs.closeSync(fd);
  }
}

const registries = JSON.parse(fs.readFileSync(registriesPath, 'utf8'));

function registryNames(id) {
  const registry = registries[id];
  if (!registry) throw new Error(`в отчёте нет реестра ${id}`);
  return Object.keys(registry.entries)
    .map((key) => key.replace(/^minecraft:/, ''))
    .sort();
}

const jarPath = findServerJar();
let dataFiles = [];
if (jarPath) {
  const entries = listZipNames(jarPath);
  dataFiles = entries.filter((name) => name.startsWith('data/minecraft/'));
} else {
  console.warn('warning: серверный jar не найден — списки из файлов данных будут пропущены');
}

// Имена файлов внутри data/minecraft/<каталог> без расширения; вложенность сохраняется
// (у template_pool и структурных пулов она многоуровневая).
function dataNames(directory, extension = '.json') {
  const prefix = `data/minecraft/${directory}/`;
  return dataFiles
    .filter((name) => name.startsWith(prefix) && name.endsWith(extension))
    .map((name) => name.slice(prefix.length).slice(0, -extension.length))
    .sort();
}

const blocks = registryNames('minecraft:block');
const items = registryNames('minecraft:item');
const entities = registryNames('minecraft:entity_type');
const effects = registryNames('minecraft:mob_effect');
const potions = registryNames('minecraft:potion');
const menus = registryNames('minecraft:menu');
const particles = registryNames('minecraft:particle_type');
const attributes = registryNames('minecraft:attribute');
const gameRules = registryNames('minecraft:game_rule');
const fluids = registryNames('minecraft:fluid');
const blockEntities = registryNames('minecraft:block_entity_type');
const creativeTabs = registryNames('minecraft:creative_mode_tab');
const professions = registryNames('minecraft:villager_profession');
const stats = registryNames('minecraft:custom_stat');
const sounds = registryNames('minecraft:sound_event');

const blockSet = new Set(blocks);
const itemOnly = items.filter((name) => !blockSet.has(name));
const itemBlocks = items.filter((name) => blockSet.has(name));

// Состояния (block states) считаются по отчёту blocks.json: один блок — это несколько
// состояний (наклон, половинка, включённость), и именно они приходят в чанке.
const blocksReportPath = path.join(root, 'tools/server/generated/reports/blocks.json');
let blockStates = null;
if (fs.existsSync(blocksReportPath)) {
  const report = JSON.parse(fs.readFileSync(blocksReportPath, 'utf8'));
  blockStates = Object.values(report).reduce((sum, block) => sum + (block.states?.length ?? 0), 0);
}

// Семейства дерева: 13 пород в 26.3 (включая бамбук, грибные и poplar).
const woodSpecies = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak', 'poplar', 'bamboo', 'crimson', 'warped'];
const blockSuffixes = [
  'stairs', 'slab', 'wall', 'fence', 'fence_gate', 'door', 'trapdoor', 'sign', 'hanging_sign',
  'button', 'pressure_plate', 'carpet', 'pane', 'bars', 'leaves', 'log', 'wood', 'planks',
  'sapling', 'ore', 'bricks', 'tiles', 'shulker_box', 'concrete', 'terracotta', 'wool', 'glass',
  'candle', 'torch', 'lantern', 'chain', 'rail', 'bed', 'banner', 'skull', 'pot', 'coral',
  'mushroom', 'snow', 'ice', 'flower', 'grass',
];
const itemSuffixes = [
  'spawn_egg', 'sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'helmet', 'chestplate', 'leggings',
  'boots', 'potion', 'splash_potion', 'lingering_potion', 'tipped_arrow', 'arrow', 'bucket',
  'boat', 'chest_boat', 'minecart', 'music_disc', 'smithing_template', 'banner_pattern',
  'pottery_sherd', 'dye', 'banner', 'bed', 'sign', 'door', 'shulker_box',
];

function matchesSuffix(name, suffix) {
  return name === suffix || name.endsWith(`_${suffix}`);
}

function suffixTable(list, suffixes) {
  return suffixes
    .map((suffix) => [suffix, list.filter((name) => matchesSuffix(name, suffix)).length])
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
}

function table(headers, rows) {
  const lines = [`| ${headers.join(' | ')} |`, `|${headers.map(() => '---').join('|')}|`];
  for (const row of rows) lines.push(`| ${row.join(' | ')} |`);
  return lines.join('\n');
}

// Абзац из названий: markdown всё равно переносит строки, но исходник так читается.
function paragraph(names, width = 104) {
  const lines = [];
  let line = '';
  for (const name of names) {
    const piece = line ? `, ${name}` : name;
    if (line && line.length + piece.length > width) {
      lines.push(`${line},`);
      line = name;
    } else {
      line += piece;
    }
  }
  if (line) lines.push(line);
  return lines.join('\n');
}

const out = [];
out.push('# Приложение: полный состав Minecraft 26.3');
out.push('');
out.push('Сгенерировано `tools/build-content-report.js` из реестров игры и файлов данных серверного');
out.push('jar. **Не редактировать вручную** — правки потеряются при следующей генерации. Здесь');
out.push('описан оригинал, а не наш клиент: файл нужен, чтобы сверять наполнение и видеть, чего');
out.push('именно не хватает.');
out.push('');
out.push('```');
out.push('node tools/build-content-report.js');
out.push('```');
out.push('');
out.push('Источники: `tools/server/generated/reports/registries.json` (дата-генератор) и');
out.push(jarPath ? `\`${path.relative(root, jarPath).replace(/\\/g, '/')}\` (файлы данных).` : 'файлы данных — **не найдены**, см. предупреждение при генерации.');
out.push('');

out.push('## Сводка');
out.push('');
out.push(table(['Что', 'Сколько', 'Откуда'], [
  ['Блоки', blocks.length, 'реестр `minecraft:block`'],
  ['— их состояний', blockStates ?? '—', 'отчёт `blocks.json`'],
  ['Предметы', items.length, 'реестр `minecraft:item`'],
  ['— из них блоки', itemBlocks.length, 'имя совпадает с блоком'],
  ['— только предметы', itemOnly.length, 'инструменты, еда, зелья и прочее'],
  ['Типы сущностей', entities.length, 'реестр `minecraft:entity_type`'],
  ['Эффекты', effects.length, 'реестр `minecraft:mob_effect`'],
  ['Зелья', potions.length, 'реестр `minecraft:potion`'],
  ['Зачарования', dataNames('enchantment').length || '—', '`data/minecraft/enchantment`'],
  ['Биомы', dataNames('worldgen/biome').length || '—', '`data/minecraft/worldgen/biome`'],
  ['Рецепты', dataNames('recipe').length || '—', '`data/minecraft/recipe`'],
  ['Достижения', dataNames('advancement').length || '—', '`data/minecraft/advancement`'],
  ['Лут-таблицы', dataNames('loot_table').length || '—', '`data/minecraft/loot_table`'],
  ['Сделки жителей', dataNames('villager_trade').length || '—', '`data/minecraft/villager_trade`'],
  ['Кусочки структур', dataNames('structure', '.nbt').length || '—', '`data/minecraft/structure` (NBT)'],
  ['Определения структур', dataNames('worldgen/structure').length || '—', '`worldgen/structure`'],
  ['Шаблонные пулы', dataNames('worldgen/template_pool').length || '—', '`worldgen/template_pool`'],
  ['Частицы', particles.length, 'реестр `minecraft:particle_type`'],
  ['Звуки', sounds.length, 'реестр `minecraft:sound_event`'],
  ['Меню (экраны-контейнеры)', menus.length, 'реестр `minecraft:menu`'],
  ['Правила игры', gameRules.length, 'реестр `minecraft:game_rule`'],
  ['Атрибуты', attributes.length, 'реестр `minecraft:attribute`'],
  ['Блок-сущности', blockEntities.length, 'реестр `minecraft:block_entity_type`'],
  ['Профессии жителей', professions.length, 'реестр `minecraft:villager_profession`'],
  ['Статистика', stats.length, 'реестр `minecraft:custom_stat`'],
  ['Жидкости', fluids.length, 'реестр `minecraft:fluid`'],
  ['Вкладки творческого режима', creativeTabs.length, 'реестр `minecraft:creative_mode_tab`'],
]));
out.push('');

out.push(`## Блоки (${blocks.length})`);
out.push('');
out.push('### Семейства дерева');
out.push('');
out.push(table(['Порода', 'Блоков'], woodSpecies.map((species) => [
  species,
  blocks.filter((name) => name === species || name.startsWith(`${species}_`)).length,
])));
out.push('');
out.push('### По типам');
out.push('');
out.push('Тип — суффикс имени, поэтому блок попадает и в семейство, и в тип (например,');
out.push('`oak_stairs` — и «оук», и «ступени»). Сумма больше числа блоков: это разные разрезы.');
out.push('');
out.push(table(['Тип', 'Блоков'], suffixTable(blocks, blockSuffixes)));
out.push('');
out.push('### Все блоки по алфавиту');
out.push('');
out.push(paragraph(blocks));
out.push('');

out.push(`## Предметы (${items.length})`);
out.push('');
out.push('### По типам');
out.push('');
out.push(table(['Тип', 'Предметов'], suffixTable(items, itemSuffixes)));
out.push('');
out.push('### Все предметы по алфавиту');
out.push('');
out.push(paragraph(items));
out.push('');

out.push(`## Сущности (${entities.length})`);
out.push('');
out.push(paragraph(entities));
out.push('');

out.push(`## Эффекты (${effects.length})`);
out.push('');
out.push(paragraph(effects));
out.push('');

out.push(`## Зелья (${potions.length})`);
out.push('');
out.push(paragraph(potions));
out.push('');

const enchantments = dataNames('enchantment');
out.push(`## Зачарования (${enchantments.length || '?'})`);
out.push('');
if (enchantments.length) out.push(paragraph(enchantments)); else out.push('_нет данных: серверный jar не найден_');
out.push('');

out.push(`## Меню (${menus.length})`);
out.push('');
out.push('Меню — это экраны с слотами. `generic_9x3` — сундук, `crafting` — верстак,');
out.push('`inventory` — свой инвентарь (в реестре его нет: это меню игрока, а не блок).');
out.push('');
out.push(paragraph(menus));
out.push('');

out.push('## Мир');
out.push('');
const biomes = dataNames('worldgen/biome');
const dimensions = dataNames('dimension_type');
const structureTypes = dataNames('worldgen/structure');
const presets = dataNames('worldgen/world_preset');
const damageTypes = dataNames('damage_type');
out.push(`### Типы измерений (${dimensions.length || '?'})`);
out.push('');
if (dimensions.length) out.push(paragraph(dimensions)); else out.push('_нет данных_');
out.push('');
out.push(`### Биомы (${biomes.length || '?'})`);
out.push('');
if (biomes.length) out.push(paragraph(biomes)); else out.push('_нет данных_');
out.push('');
out.push(`### Определения структур (${structureTypes.length || '?'})`);
out.push('');
if (structureTypes.length) out.push(paragraph(structureTypes)); else out.push('_нет данных_');
out.push('');
out.push(`### Пресеты мира (${presets.length || '?'})`);
out.push('');
if (presets.length) out.push(paragraph(presets)); else out.push('_нет данных_');
out.push('');
out.push(`### Типы урона (${damageTypes.length || '?'})`);
out.push('');
if (damageTypes.length) out.push(paragraph(damageTypes)); else out.push('_нет данных_');
out.push('');

out.push(`## Правила игры (${gameRules.length})`);
out.push('');
out.push(paragraph(gameRules));
out.push('');

out.push(`## Атрибуты (${attributes.length})`);
out.push('');
out.push(paragraph(attributes));
out.push('');

out.push(`## Частицы (${particles.length})`);
out.push('');
out.push(paragraph(particles));
out.push('');

out.push('## Прочие файлы данных');
out.push('');
out.push(table(['Что', 'Сколько'], [
  ['Рецепты', dataNames('recipe').length || '—'],
  ['Достижения', dataNames('advancement').length || '—'],
  ['Лут-таблицы', dataNames('loot_table').length || '—'],
  ['Сделки жителей', dataNames('villager_trade').length || '—'],
  ['Кусочки структур (NBT)', dataNames('structure', '.nbt').length || '—'],
  ['Шаблонные пулы', dataNames('worldgen/template_pool').length || '—'],
  ['Размещённые фичи', dataNames('worldgen/placed_feature').length || '—'],
  ['Фичи', dataNames('worldgen/feature').length || '—'],
  ['Картины', dataNames('painting_variant').length || '—'],
  ['Пластинки', dataNames('jukebox_song').length || '—'],
  ['Узоры брони', dataNames('trim_pattern').length || '—'],
  ['Узоры флагов', dataNames('banner_pattern').length || '—'],
  ['Черепки', dataNames('decorated_pot_pattern').length || '—'],
  ['Варианты кошек', dataNames('cat_variant').length || '—'],
  ['Варианты волков', dataNames('wolf_variant').length || '—'],
  ['Варианты свиней', dataNames('pig_variant').length || '—'],
  ['Варианты лягушек', dataNames('frog_variant').length || '—'],
  ['Варианты коров', dataNames('cow_variant').length || '—'],
  ['Варианты кур', dataNames('chicken_variant').length || '—'],
]));
out.push('');

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, `${out.join('\n')}\n`, 'utf8');

console.log(`блоков: ${blocks.length}, предметов: ${items.length}, сущностей: ${entities.length}`);
console.log(`записано: ${path.relative(root, outPath).replace(/\\/g, '/')}`);

