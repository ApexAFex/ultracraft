// Independent NBT inspection of a dumped registry payload, to compare against the Kotlin reader.
const fs = require('fs');

const buf = fs.readFileSync(process.argv[2]);
let p = 0;
const u8 = () => buf[p++];
const i16 = () => { const v = buf.readInt16BE(p); p += 2; return v; };
const i32 = () => { const v = buf.readInt32BE(p); p += 4; return v; };
const i64 = () => { const v = buf.readBigInt64BE(p); p += 8; return v; };
const f32 = () => { const v = buf.readFloatBE(p); p += 4; return v; };
const f64 = () => { const v = buf.readDoubleBE(p); p += 8; return v; };
const str = () => { const n = buf.readUInt16BE(p); p += 2; const s = buf.slice(p, p + n).toString('utf8'); p += n; return s; };
const varint = () => { let v = 0, s = 0; while (true) { const b = buf[p++]; v |= (b & 0x7f) << s; if (!(b & 0x80)) return v; s += 7; } };

const pstr = () => { const n = varint(); const s = buf.slice(p, p + n).toString('utf8'); p += n; return s; };

function payload(type) {
  switch (type) {
    case 0: return null;
    case 1: return u8() << 24 >> 24;
    case 2: return i16();
    case 3: return i32();
    case 4: return i64();
    case 5: return f32();
    case 6: return f64();
    case 7: { const n = i32(); const b = buf.slice(p, p + n); p += n; return `<bytes ${n}>`; }
    case 8: return str();
    case 9: { const et = u8(); const n = i32(); const a = []; for (let i = 0; i < n; i++) a.push(payload(et)); return a; }
    case 10: {
      const o = {};
      while (true) { const t = u8(); if (t === 0) break; const name = str(); o[name] = payload(t); }
      return o;
    }
    case 11: { const n = i32(); for (let i = 0; i < n; i++) i32(); return `<ints ${n}>`; }
    case 12: { const n = i32(); for (let i = 0; i < n; i++) i64(); return `<longs ${n}>`; }
    default: throw new Error(`unknown tag ${type} at ${p}`);
  }
}

const registryId = pstr();
const count = varint();
console.log(`registry=${registryId} entries=${count} bytes=${buf.length}`);

for (let i = 0; i < count; i++) {
  const start = p;
  const name = pstr();
  const hasData = u8();
  let summary = 'no data';
  if (hasData) {
    const type = u8();
    // network NBT has no root name
    const value = payload(type);
    summary = `min_y=${value.min_y} height=${value.height} keys=${Object.keys(value).length}`;
  } else {
    continue;
  }
  console.log(`  [${i}] ${name}: ${summary} (${p - start} байт)`);
}
console.log(`consumed ${p}/${buf.length}`);
