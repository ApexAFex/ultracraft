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
const poses = new Map();
// Копии исходных поз: в\parts\ смещения потом складываются по родителям, и читать их как
// «позу части» уже нельзя.
const originalPoses = new Map();
let current = null;

for (const line of lines) {
  const cells = line.replace(/^\uFEFF/, '').split('\t');
  if (cells[0] === 'SIZE') {
    textures = { width: Number(cells[1]), height: Number(cells[2]) };
  } else if (cells[0] === 'PART') {
    current = {
      path: cells[1],
      name: cells[1].split('/').pop(),
      x: Number(cells[2]),
      y: Number(cells[3]),
      z: Number(cells[4]),
      rx: Number(cells[5]),
      ry: Number(cells[6]),
      rz: Number(cells[7]),
      cubes: [],
    };
    poses.set(current.path, current);
    originalPoses.set(current.path, {
      x: current.x, y: current.y, z: current.z,
      rx: current.rx, ry: current.ry, rz: current.rz,
    });
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

// Части бывают вложенными (голова → уши → морда). В дампе у каждой только своя поза,
// поэтому смещения складываются по всей цепочке родителей — иначе уши окажутся в центре
// модели, а не на голове. Если у родителя есть поворот, простое сложение уже врёт: тогда
// для части собирается настоящая матрица (см. ниже).
for (const part of parts) {
  const chain = part.path.split('/');
  let x = 0;
  let y = 0;
  let z = 0;
  let rotatedParent = false;
  for (let depth = 2; depth <= chain.length; depth++) {
    const parent = poses.get(chain.slice(0, depth).join('/'));
    if (!parent) continue;
    if (parent.rx !== 0 || parent.ry !== 0 || parent.rz !== 0) rotatedParent = true;
    x += parent.x;
    y += parent.y;
    z += parent.z;
  }
  part.x = x;
  part.y = y;
  part.z = z;
  part.rotatedParent = rotatedParent;
}

// --- матрицы для вложенных поворотов -------------------------------------------------
// Матрица 4×4 по столбцам, как в OpenGL: [m00 m10 m20 m30, m01 ...].
const identity = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];

function multiply(a, b) {
  const out = new Array(16).fill(0);
  for (let c = 0; c < 4; c++) {
    for (let r = 0; r < 4; r++) {
      let sum = 0;
      for (let k = 0; k < 4; k++) sum += a[k * 4 + r] * b[c * 4 + k];
      out[c * 4 + r] = sum;
    }
  }
  return out;
}

const translation = (x, y, z) => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1];

const rotationX = (angle) => {
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  return [1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1];
};
const rotationY = (angle) => {
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  return [c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1];
};
const rotationZ = (angle) => {
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  return [c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
};

/** Поза части в ванильной системе: T * Rz * Ry * Rx. */
function poseMatrix(pose) {
  let matrix = translation(pose.x, pose.y, pose.z);
  if (pose.rz !== 0) matrix = multiply(matrix, rotationZ(pose.rz));
  if (pose.ry !== 0) matrix = multiply(matrix, rotationY(pose.ry));
  if (pose.rx !== 0) matrix = multiply(matrix, rotationX(pose.rx));
  return matrix;
}

/** Ванильная матрица → наша: Y вверх, пиксели в блоки, низ модели на y0. */
function toOurSpace(matrix, y0) {
  // Линейная часть: F * M * F, где F переворачивает Y.
  const flip = [1, -1, 1];
  const out = new Array(16).fill(0);
  for (let c = 0; c < 3; c++) {
    for (let r = 0; r < 3; r++) {
      out[c * 4 + r] = flip[r] * matrix[c * 4 + r] * flip[c];
    }
  }
  // Перенос: F * t / 16, плюс сдвиг модели на y0, чтобы низ был на нуле.
  out[12] = matrix[12] / 16;
  out[13] = (y0 - matrix[13]) / 16;
  out[14] = matrix[14] / 16;
  out[15] = 1;
  return out;
}

// Самый низ модели: без учёта поворотов, но этого достаточно, чтобы поставить её на ноль.
const lowest = parts.reduce(
  (max, part) => part.cubes.reduce((inner, cube) => Math.max(inner, part.y + cube.maxY), max),
  -Infinity,
);
const y0 = process.argv[3] !== undefined ? Number(process.argv[3]) : lowest;

for (const part of parts) {
  let matrix = identity();
  const chain = part.path.split('/');
  for (let depth = 2; depth <= chain.length; depth++) {
    const parent = originalPoses.get(chain.slice(0, depth).join('/'));
    if (parent) matrix = multiply(matrix, poseMatrix(parent));
  }
  part.matrix = part.rotatedParent ? toOurSpace(matrix, y0) : null;
}


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
    if (part.matrix) {
      // Вложенный поворот: печатаем матрицу, смещения и углы уже внутри неё.
      const values = part.matrix.map((value) => ff(value)).join(', ');
      args.push('matrix = floatArrayOf(' + values + ')');
    } else {
      if (part.x !== 0) args.push('offsetX = ' + ff(blocks(part.x)));
      if (y0 - part.y !== 0) args.push('offsetY = ' + ff(blocks(y0 - part.y)));
      if (part.z !== 0) args.push('offsetZ = ' + ff(blocks(part.z)));
      if (part.rx !== 0) args.push('rotX = ' + ff(-part.rx));
      if (part.ry !== 0) args.push('rotY = ' + ff(part.ry));
      if (part.rz !== 0) args.push('rotZ = ' + ff(-part.rz));
    }
    console.log('        MobBox(' + args.join(', ') + '),');
  }
}
