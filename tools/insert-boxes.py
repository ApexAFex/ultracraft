"""Вставляет список коробок модели в MobModel.kt перед закрывающей скобкой объекта.

Использование: python tools/insert-boxes.py <файл с MobBox-строками> <ИмяСписка> <комментарий>
"""
import sys

source, name, comment = sys.argv[1], sys.argv[2], sys.argv[3]
target = "app/src/main/kotlin/mcplus/app/gl/MobModel.kt"

with open(source, encoding="utf-8") as handle:
    boxes = [line.rstrip() for line in handle if line.strip().startswith("MobBox(")]

with open(target, encoding="utf-8") as handle:
    lines = handle.read().split("\n")

body = ["", "    /**", "     * %s" % comment, "     */", "    private val %s = listOf(" % name]
body += boxes
body += ["    )", "}"]

# Последняя строка файла — закрывающая скобка объекта.
while lines and lines[-1] == "":
    lines.pop()
if lines[-1] != "}":
    raise SystemExit("Файл не заканчивается закрывающей скобкой")
lines = lines[:-1] + body
with open(target, "w", encoding="utf-8", newline="\n") as handle:
    handle.write("\n".join(lines) + "\n")

print("вставлено коробок:", len(boxes))
