"""Собирает `ParticleTextures.kt` из данных игры.

В 26.3 у каждого типа частиц есть файл `assets/minecraft/particles/<тип>.json` с полем
`textures` — списком картинок по кадрам. Скрипт читает их из клиентского jar и печатает
Kotlin-таблицу: типы с одинаковым набором картинок попадают в одну ветку `when`.
"""
import json
import os
import zipfile

OUT = os.path.join("app", "src", "main", "kotlin", "mcplus", "app", "gl", "ParticleTextures.kt")
JAR = os.path.join(os.environ["APPDATA"], ".minecraft", "versions", "26.3", "26.3.jar")

archive = zipfile.ZipFile(JAR)
entries = {}
for name in archive.namelist():
    if not name.startswith("assets/minecraft/particles/") or not name.endswith(".json"):
        continue
    data = json.loads(archive.read(name))
    frames = [t.split(":", 1)[-1] for t in data.get("textures") or []]
    if frames:
        entries[name.split("/")[-1][:-5]] = frames

# Одинаковые наборы картинок — в одну ветку.
groups = {}
for particle, frames in sorted(entries.items()):
    groups.setdefault(tuple(frames), []).append(particle)

lines = []
lines.append("package mcplus.app.gl")
lines.append("")
lines.append("/**")
lines.append(" * Какие файлы из `textures/particle` рисуют каждый тип частиц.")
lines.append(" *")
lines.append(" * Это данные самой игры: `assets/minecraft/particles/<тип>.json`, поле `textures`.")
lines.append(" * Файл собран скриптом `tools/build-particle-table.py`; править его руками не нужно —")
lines.append(" * сначала меняются данные игры, потом таблица.")
lines.append(" *")
lines.append(" * Тип без своей картинки получает `generic_0…7`, как и в игре.")
lines.append(" */")
lines.append("object ParticleTextures {")
lines.append("")
lines.append("    /** Какие картинки нужны типу частицы. */")
lines.append("    fun filesFor(name: String): List<String> = when (name) {")

for frames, particles in sorted(groups.items(), key=lambda item: (len(item[1]), item[1][0])):
    names = ", ".join('"%s"' % p for p in particles)
    if len(frames) == 1:
        body = 'listOf("%s")' % frames[0]
    else:
        body = "listOf(" + ", ".join('"%s"' % f for f in frames) + ")"
    lines.append("        %s -> %s" % (names, body))
    lines.append("")

lines.append("        else -> (0..7).map { \"generic_$it\" }")
lines.append("    }")
lines.append("")
lines.append("    /** Все типы, для которых собираются спрайты (порядок — как в реестре игры). */")
lines.append("    val names: List<String> = listOf(")
current = "        "
for particle in sorted(entries):
    piece = '"%s", ' % particle
    if len(current) + len(piece) > 96:
        lines.append(current.rstrip())
        current = "        "
    current += piece
lines.append(current.rstrip().rstrip(","))
lines.append("    )")
lines.append("}")

with open(OUT, "w", encoding="utf-8", newline="\n") as handle:
    handle.write("\n".join(lines) + "\n")

print("типов:", len(entries), "веток:", len(groups))
