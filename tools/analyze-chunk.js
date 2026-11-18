// Parses a raw Chunk Data packet body and prints the layout, for protocol debugging.
const fs = require('fs');

const file = process.argv[2];
const buf = fs.readFileSync(file);
let p = 0;

const i32 = () => { const v = buf.readInt32BE(p); p += 4; return v; };
const i16 = () => { const v = buf.readInt16BE(p); p += 2; return v; };
const varint = () => {
  let v = 0, s = 0;
  while (true) {
    const b = buf[p++];
    v |= (b & 0x7f) << s;
    if (!(b & 0x80)) return v;
    s += 7;
    if (s > 35) throw new Error('varint too long at ' + p);
  }
};
const hex = (from, n) => buf.slice(from, from + n).toString('hex').replace(/(..)/g, '$1 ');

console.log(`file=${file} bytes=${buf.length}`);
const chunkX = i32();
const chunkZ = i32();
console.log(`chunk ${chunkX},${chunkZ}`);

const heightmapCount = varint();
console.log(`heightmaps=${heightmapCount}`);
for (let i = 0; i < heightmapCount; i++) {
  const type = varint();
  const longs = varint();
  console.log(`  heightmap type=${type} longs=${longs}`);
  p += longs * 8;
}

const dataLength = varint();
console.log(`data field length=${dataLength} at offset ${p}, remaining ${buf.length - p}`);
const data = buf.slice(p, p + dataLength);

let q = 0;
const dvarint = () => {
  let v = 0, s = 0;
  while (true) {
    const b = data[q++];
    v |= (b & 0x7f) << s;
    if (!(b & 0x80)) return v;
    s += 7;
  }
};

const sectionCount = dvarint();
console.log(`sections=${sectionCount}`);
for (let s = 0; s < sectionCount; s++) {
  const start = q;
  const blockCount = data.readInt16BE(q); q += 2;
  const fluidCount = data.readInt16BE(q); q += 2;
  const bits = data[q]; q += 1;
  let paletteLength = null;
  if (bits !== 0) {
    if (bits <= 8) { paletteLength = dvarint(); q += paletteLength; }
  } else {
    q += 1; // single value varint, assume small
  }
  console.log(
    `  section ${s}: blockCount=${blockCount} fluid=${fluidCount} bits=${bits} paletteLen=${paletteLength} head=${data.slice(start, start + 16).toString('hex').replace(/(..)/g, '$1 ')}`,
  );
  if (s > 3) { console.log('  ...'); break; }
}
