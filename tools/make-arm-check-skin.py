"""Скин для проверки ширины рук: тело белое, руки розовые, всё видно и днём."""
from PIL import Image

SKIN = "screenshots/arm-check.png"

img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

def fill(x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), color)

fill(0, 0, 32, 16, (240, 240, 240, 255))     # голова
fill(16, 16, 40, 32, (240, 240, 240, 255))   # тело
fill(0, 16, 16, 32, (90, 90, 90, 255))       # правая нога
fill(16, 48, 32, 64, (90, 90, 90, 255))      # левая нога
fill(40, 16, 56, 32, (240, 40, 200, 255))    # правая рука
fill(32, 48, 48, 64, (240, 40, 200, 255))    # левая рука

img.save(SKIN)
print("Готово:", SKIN)
