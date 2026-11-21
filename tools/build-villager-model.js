// Turns the villager model dump (tools/villager-model.tsv) into Kotlin MobBox entries.
//
// The dump comes from the client jar's VillagerModel (see tools/villager_dump.java), so the
// numbers are vanilla's own instead of eyeballed proportions. The model is written in the
// game's own space: 16 pixels per block, +Y down, the origin at the neck. MobBox uses blocks
// with +Y up and the feet at y = 0, hence the conversions below:
//
//   x = px / 16                     y = (24 - py) / 16        (24 px — leg bottom, i.e. feet)
//   rotation around X and Z flips sign when Y flips; rotation around Y stays.
//
//   node tools/build-villager-model.js tools/villager-model.tsv

const fs = require('fs');

const PX = 16;
/** Feet are 24 pixels below the model origin (legs hang from y = 12 and are 12 px long). */
const FEET_PX = 24;

const lines = fs.readFileSync(process.argv[2] ?? 'tools/villager-model.tsv', 'utf8')
    // Windows-редакторы любят дописать BOM: без него первая строка не распознается.
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/)
    .filter((line) => line.trim().length > 0);

/** Part name -> absolute pose (parents add up) and rotation in vanilla space. */
const parts = new Map();
const cubes = [];

for (const line of lines) {
    const fields = line.split('\t');
    if (fields[0] === 'PART') {
        const [, path, x, y, z, rotX, rotY, rotZ] = fields;
        const parent = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : null;
        const base = parent === null ? { x: 0, y: 0, z: 0, rotX: 0, rotY: 0, rotZ: 0 } : parts.get(parent);
        parts.set(path, {
            x: base.x + Number(x),
            y: base.y + Number(y),
            z: base.z + Number(z),
            rotX: base.rotX + Number(rotX),
            rotY: base.rotY + Number(rotY),
            rotZ: base.rotZ + Number(rotZ),
        });
    } else if (fields[0] === 'CUBE') {
        const [, part, x, y, z, w, h, d, u, v, growX, growY, growZ] = fields;
        cubes.push({
            part, x: Number(x), y: Number(y), z: Number(z),
            w: Number(w), h: Number(h), d: Number(d),
            u: Number(u), v: Number(v),
            grow: Math.max(Number(growX), Number(growY), Number(growZ)),
        });
    }
}

const block = (px) => round(px / PX);
const round = (value) => {
    const text = value.toFixed(4).replace(/0+$/, '').replace(/\.$/, '');
    return text === '-0' ? '0' : text;
};

const names = {
    'root/head': 'head',
    'root/head/nose': 'nose',
    'root/head/hat': 'hat',
    'root/head/hat/hat_rim': 'hatRim',
    'root/left_leg': 'legFL',
    'root/right_leg': 'legFR',
    'root/arms': 'arms',
    'root/body': 'body',
    'root/body/jacket': 'jacket',
};

const out = [];
for (const cube of cubes) {
    const pose = parts.get(cube.part);
    const name = names[cube.part] ?? cube.part.replace(/^root\//, '');
    const minY = block(-(cube.y + cube.h));
    const maxY = block(-cube.y);
    const offsetY = block(FEET_PX - pose.y);
    // Ноги качаются вокруг бедра, то есть вокруг верхней грани коробки.
    const pivot = name.startsWith('leg') ? offsetY : '0';
    const args = [
        `"${name}"`,
        block(cube.x),
        minY,
        block(cube.z),
        block(cube.x + cube.w),
        maxY,
        block(cube.z + cube.d),
    ];
    if (pivot !== '0') args.push(`pivotY = ${pivot}`);
    args.push(`u = ${Math.round(cube.u)}`, `v = ${Math.round(cube.v)}`);
    if (cube.grow !== 0) args.push(`grow = ${block(cube.grow)}`);
    if (pose.x !== 0) args.push(`offsetX = ${block(pose.x)}`);
    if (offsetY !== '0') args.push(`offsetY = ${offsetY}`);
    if (pose.z !== 0) args.push(`offsetZ = ${block(pose.z)}`);
    if (pose.rotX !== 0) args.push(`rotX = ${round(-pose.rotX)}`);
    if (pose.rotY !== 0) args.push(`rotY = ${round(pose.rotY)}`);
    if (pose.rotZ !== 0) args.push(`rotZ = ${round(-pose.rotZ)}`);
    out.push(`        MobBox(${args.join(', ')}),`);
}

console.log(`        // ${cubes.length} коробок: голова, нос, шляпа, поля шляпы, руки, тело, мантия, ноги.`);
console.log(out.join('\n'));
