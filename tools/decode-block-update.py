"""Decodes a `Block Update` body from a hex dump: position + state name."""

import sys

TABLE = r"C:\проекты\майн+\data\blocks-777.tsv"


def read_varint(data, offset):
    value = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, offset
        shift += 7


def unpack_position(value):
    x = value >> 38
    if x >= 1 << 25:
        x -= 1 << 26
    z = (value >> 12) & 0x3FFFFFF
    if z >= 1 << 25:
        z -= 1 << 26
    y = value & 0xFFF
    if y >= 1 << 11:
        y -= 1 << 12
    return x, y, z


names = []
with open(TABLE, encoding="utf-8") as handle:
    for line in handle:
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 2:
            names.append(parts[1])

for text in sys.argv[1:]:
    data = bytes(int(part, 16) for part in text.split())
    packed = int.from_bytes(data[:8], "big", signed=True)
    state, _ = read_varint(data, 8)
    x, y, z = unpack_position(packed)
    name = names[state] if state < len(names) else "out of table"
    print(f"{text}  ->  ({x}, {y}, {z}) state={state} = {name}")
