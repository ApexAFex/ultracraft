// Превращает дамп модели (tools/mob-dump.ps1) в строки Kotlin для MobModel.kt.
//
// Модели в jar описаны в системе игры: Y идёт вниз, части повёрнуты вокруг своих осей.
// Здесь тот же пересчёт, что делается руками для каждой модели:
//   * Y переворачивается (y → −y), низ модели ставится на y = 0;
//   * повороты вокруг X и Z меняют знак, вокруг Y — нет;
//   * координаты коробки и отступы переводятся из пикселей в блоки (÷16).
//
// Использование:
//   node tools/build-mob-model.js tools/blaze-dump.txt 20
// Второй аргумент — Y0: «самый низ» модели в пикселях (по умолчанию считается сам).

const fs = require('fs');

const path = process.argv[2];
if (!path) {
  console.error('Укажи файл дампа: node tools/build-mob-model.js <dump.txt> [y0]');
  process.exit(1);
}

const lines = fs.readFileSync(path, 'utf8').split(/\r?\n/);
let textures = { width: 64, height: 32 };
const parts = [];
let current = null;

for (const line of lines) {
  const cells = line.split('\t');
  if (cells[0] === 'SIZE') {
    textures = { width: Number(cells[1]), height: Number(cells[2]) };
  } else if (cells[0] === 'PART') {
    current = {
      name: cells[1].split('/').pop(),
      x: Number(cells[2]),
      y: Number(cells[3]),
      z: Number(cells[4]),
      rx: Number(cells[5]),
      ry: Number(cells[6]),
      rz: Number(cells[7]),
      cubes: [],
    };
    parts.push(current);
  } else if (cells[0] === 'CUBE' && current) {
    current.cubes.push({
      minX: Number(cells[2]),
      minY: Number(cells[3]),
      minZ: Number(cells[4]),
      maxX: Number(cells[5]),
      maxY: Number(cells[6]),
      maxZ: Number(cells[7]),
      u: Number(cells[8]),
      v: Number(cells[9]),
    });
  }
}

// Самый низ модели: без учёта поворотов, но этого достаточно, чтобы поставить её на ноль.
const lowest = parts.reduce(
  (max, part) => part.cubes.reduce((inner, cube) => Math.max(inner, part.y + cube.maxY), max),
  -Infinity,
);
const y0 = process.argv[3] !== undefined ? Number(process.argv[3]) : lowest;

const round = (value) => Math.round(value * 10000) / 10000;
const blocks = (pixels) => round(pixels / 16);
// В Kotlin числа по умолчанию Double, поэтому везде дописывается f.
const ff = (value) => round(value).toString() + 'f';

console.log('// Текстура ' + textures.width + '×' + textures.height + ', низ модели на ' + round(y0) + ' px');
for (const part of parts) {
  for (const cube of part.cubes) {
    const args = [
      '"' + part.name + '"',
      ff(blocks(cube.minX)),
      ff(blocks(-cube.maxY)),
      ff(blocks(cube.minZ)),
      ff(blocks(cube.maxX)),
      ff(blocks(-cube.minY)),
      ff(blocks(cube.maxZ)),
      'u = ' + cube.u,
      'v = ' + cube.v,
    ];
    if (part.x !== 0) args.push('offsetX = ' + ff(blocks(part.x)));
    if (y0 - part.y !== 0) args.push('offsetY = ' + ff(blocks(y0 - part.y)));
    if (part.z !== 0) args.push('offsetZ = ' + ff(blocks(part.z)));
    if (part.rx !== 0) args.push('rotX = ' + ff(-part.rx));
    if (part.ry !== 0) args.push('rotY = ' + ff(part.ry));
    if (part.rz !== 0) args.push('rotZ = ' + ff(-part.rz));
    console.log('        MobBox(' + args.join(', ') + '),');
  }
}
