"""Увеличить кусок снимка: разглядеть мелких мобов (рыбы, головастик)."""
import sys

from PIL import Image

name = sys.argv[1] if len(sys.argv) > 1 else "fish5"
x0, y0, x1, y1 = (int(v) for v in sys.argv[2:6]) if len(sys.argv) > 5 else (380, 120, 820, 340)
scale = 3
im = Image.open("screenshots/%s.png" % name).crop((x0, y0, x1, y1))
im.resize((im.width * scale, im.height * scale), Image.NEAREST).save(
    "screenshots/%s-zoom.png" % name
)
print("screenshots/%s-zoom.png" % name)
