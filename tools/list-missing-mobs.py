"""Какие типы сущностей из data/entities-777.tsv ещё рисуются цветной коробкой.

Список моделей берётся из MobModel.kt и HumanoidLook.kt: скрипт читает оба файла и ищет
там имена в кавычках. Это грубая, но честная проверка покрытия — так видно, что осталось.
"""
from pathlib import Path

root = Path(".")
entities = []
for line in (root / "data/entities-777.tsv").read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    parts = line.split("\t")
    if len(parts) < 2:
        continue
    entities.append(parts[1].strip())

sources = [
    (root / "app/src/main/kotlin/mcplus/app/gl/MobModel.kt").read_text(encoding="utf-8"),
    (root / "app/src/main/kotlin/mcplus/app/gl/HumanoidLook.kt").read_text(encoding="utf-8"),
    (root / "app/src/main/kotlin/mcplus/app/gl/MobRenderer.kt").read_text(encoding="utf-8"),
]
blob = "\n".join(sources)

missing = [name for name in entities if '"%s"' % name not in blob]
print("всего типов:", len(entities))
print("без модели:", len(missing))
for name in missing:
    print("  ", name)
