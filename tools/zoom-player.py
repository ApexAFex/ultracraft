"""Увеличенный вырез модели игрока из снимка, чтобы разглядеть руки."""
import sys

from PIL import Image

name = sys.argv[1] if len(sys.argv) > 1 else "arms-only-shot"
box = (560, 330, 760, 560)
im = Image.open("screenshots/%s.png" % name).crop(box)
im.resize((im.width * 3, im.height * 3), Image.NEAREST).save("screenshots/%s-zoom.png" % name)
print("screenshots/%s-zoom.png" % name)
