// Debug: разбирает сохранённый пакет чанка до данных о свете и печатает их.
//
//   node tools/inspect-chunk-light.js tools/chunks/chunk_-4_2.bin
//
// Нужен, чтобы не гадать о формате: пакет `Chunk Data` в 26.3 — заголовок, heightmap-ы,
// массив секций, список блок-сущностей и только потом свет. Маски света — массивы i64
// с VarInt-количеством, дальше идут массивы по 2048 байт.
const fs = require('fs');

const file = process.argv[2];
if (!file) {
  console.error('нужен путь к .bin');
  process.exit(1);
}
const buf = fs.readFileSync(file);
let p = 0;

const i32 = () => { const value = buf.readInt32BE(p); p += 4; return value; };
const i16 = () => { const value = buf.readInt16BE(p); p += 2; return value; };
const u8 = () => buf[p++];
const varint = () => {
  let value = 0;
  let shift = 0;
  for (;;) {
    const byte = buf[p++];
    value |= (byte & 0x7f) << shift;
    if ((byte & 0x80) === 0) return value;
    shift += 7;
  }
};

const skipNbt = (type) => {
  switch (type) {
    case 0: return;
    case 1: p += 1; return;
    case 2: p += 2; return;
    case 3: p += 4; return;
    case 4: p += 8; return;
    case 5: p += 4; return;
    case 6: p += 8; return;
    case 7: { const n = i32(); p += n; return; }
    case 8: { const n = buf.readUInt16BE(p); p += 2 + n; return; }
    case 9: { const element = u8(); const n = i32(); for (let i = 0; i < n; i += 1) skipNbt(element); return; }
    case 10: {
      for (;;) {
        const child = u8();
        if (child === 0) return;
        const nameLength = buf.readUInt16BE(p);
        p += 2 + nameLength;
        skipNbt(child);
      }
    }
    case 11: { const n = i32(); p += n * 4; return; }
    case 12: { const n = i32(); p += n * 8; return; }
    default: throw new Error(`неизвестный тег ${type}`);
  }
};

const chunkX = i32();
const chunkZ = i32();
const heightmaps = varint();
for (let i = 0; i < heightmaps; i += 1) {
  varint();
  const longs = varint();
  p += longs * 8;
}
const dataLength = varint();
p += dataLength;
const blockEntities = varint();
for (let i = 0; i < blockEntities; i += 1) {
  u8();
  i16();
  varint();
  // Необязательный NBT: ноль вместо типа тега значит «данных нет».
  const tagType = u8();
  if (tagType !== 0) skipNbt(tagType);
}
const lightStart = p;

const readMask = () => {
  const count = varint();
  const bits = [];
  for (let i = 0; i < count; i += 1) {
    const value = buf.readBigUInt64LE(p);
    p += 8;
    for (let bit = 0; bit < 64; bit += 1) {
      if ((value >> BigInt(bit)) & 1n) bits.push(i * 64 + bit);
    }
  }
  return bits;
};

const masks = {
  sky: readMask(),
  block: readMask(),
  emptySky: readMask(),
  emptyBlock: readMask(),
};

const readLayers = () => {
  const count = varint();
  const sizes = [];
  for (let i = 0; i < count; i += 1) {
    sizes.push(buf.readUInt16BE(p));
    p += 2 + sizes[sizes.length - 1];
  }
  return sizes;
};
const skyLayers = readLayers();
const blockLayers = readLayers();

console.log(`чанк ${chunkX},${chunkZ}: heightmaps=${heightmaps}, data=${dataLength}Б, блок-сущностей=${blockEntities}`);
console.log(`свет начинается на ${lightStart}, заканчивается на ${p}, всего ${buf.length}`);
console.log(`маски: небо=${masks.sky.length}, блоки=${masks.block.length}, пустое небо=${masks.emptySky.length}, пустые блоки=${masks.emptyBlock.length}`);
console.log(`  небо: ${masks.sky.slice(0, 40).join(',')}`);
console.log(`  блоки: ${masks.block.slice(0, 40).join(',')}`);
console.log(`  пустое небо: ${masks.emptySky.slice(0, 40).join(',')}`);
console.log(`  пустые блоки: ${masks.emptyBlock.slice(0, 40).join(',')}`);
console.log(`слои света: небо ${skyLayers.length} × ${skyLayers[0]}Б, блоки ${blockLayers.length} × ${blockLayers[0]}Б`);
