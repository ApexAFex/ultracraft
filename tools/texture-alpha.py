import io
import os
import zipfile

from PIL import Image

jar = os.path.expandvars(r"%APPDATA%\.minecraft\versions\26.3\26.3.jar")
z = zipfile.ZipFile(jar)
names = [n for n in z.namelist() if n.startswith("assets/minecraft/textures/block/") and n.endswith(".png")]
print("textures in jar:", len(names))


def alpha_stats(name):
    im = Image.open(io.BytesIO(z.read(name))).convert("RGBA")
    hist = im.getchannel("A").histogram()
    total = sum(hist)
    below = sum(hist[:128])
    zero = hist[0]
    short = name.rsplit("/", 1)[-1]
    print(f"  {short}: {im.size[0]}x{im.size[1]}, alpha<128 {below * 100 // total}%, alpha=0 {zero * 100 // total}%")


for n in ["water_still", "water_flow", "grass_block_top", "grass_block_side", "stone", "oak_leaves", "sand"]:
    path = "assets/minecraft/textures/block/" + n + ".png"
    if path in z.namelist():
        alpha_stats(path)
    else:
        print("  " + n + ": missing")
