"""Какие файлы текстур игра берёт для каждого типа частиц.

В 26.3 это данные, а не код: `assets/minecraft/particles/<тип>.json` с полем `textures`.
Скрипт печатает таблицу «тип → файлы», чтобы сверить её с `ParticleTextures.kt`.
"""
import json
import os
import zipfile

jar = os.path.join(os.environ["APPDATA"], ".minecraft", "versions", "26.3", "26.3.jar")
archive = zipfile.ZipFile(jar)
names = sorted(
    n for n in archive.namelist() if n.startswith("assets/minecraft/particles/") and n.endswith(".json")
)
for name in names:
    data = json.loads(archive.read(name))
    textures = data.get("textures") or []
    short = name.split("/")[-1].replace(".json", "")
    print("%s\t%s" % (short, ",".join(textures)))
print("всего:", len(names))
