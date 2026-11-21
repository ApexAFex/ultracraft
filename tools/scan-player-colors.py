"""Разобрать снимок по цветам: где на модели белое (тело), розовое (руки), белое (голова).

Печатает для каждой строки найденные цветные отрезки — так видно, стоят ли руки по бокам
тела или наезжают на него.
"""
import sys

from PIL import Image

name = sys.argv[1] if len(sys.argv) > 1 else "check-slim"
x0, y0, x1, y1 = (560, 300, 760, 620)
im = Image.open("screenshots/%s.png" % name).convert("RGB")
px = im.load()

def classify(r, g, b):
    if r > 180 and b > 180 and g > 180:
        return "бел"
    if r > 150 and b > 120 and g < 100:
        return "роз"
    if abs(r - g) < 30 and abs(g - b) < 30 and 60 <= r <= 160:
        return "сер"
    return None

for y in range(y0, y1, 6):
    runs = []
    current = None
    start = None
    for x in range(x0, x1):
        c = classify(*px[x, y])
        if c != current:
            if current:
                runs.append((current, start, x - 1))
            current = c
            start = x
    if current:
        runs.append((current, start, x1 - 1))
    if runs:
        print(y, " ".join("%s:%d-%d(%d)" % (c, a, b, b - a + 1) for c, a, b in runs))
