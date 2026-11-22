"""Какие типы сущностей ещё рисуются цветной коробкой.

Проверяются все места, где клиент выбирает, чем рисовать сущность: модели мобов
(`MobModel.kt`), гуманоиды (`HumanoidLook.kt`), текстуры (`MobRenderer.kt`), снаряды
(`Projectiles.kt`), кубы блоков (`BlockEntityLooks.kt`), особые сущности\n(`SpecialEntities.kt`) и особые случаи в `GlView.kt`
(выпавший предмет, рамка, картина, сфера опыта). Это грубая, но честная проверка:
имя типа должно встречаться в исходниках, иначе сущность остаётся коробкой.
"""
from pathlib import Path

root = Path(".")
entities = []
for line in (root / "data/entities-777.tsv").read_text(encoding="utf-8").splitlines():
    parts = line.split("\t")
    if len(parts) < 2:
        continue
    entities.append(parts[1].strip())

sources = [
    "app/src/main/kotlin/mcplus/app/gl/MobModel.kt",
    "app/src/main/kotlin/mcplus/app/gl/HumanoidLook.kt",
    "app/src/main/kotlin/mcplus/app/gl/MobRenderer.kt",
    "app/src/main/kotlin/mcplus/app/gl/Projectiles.kt",
    "app/src/main/kotlin/mcplus/app/gl/BlockEntityLooks.kt",
    "app/src/main/kotlin/mcplus/app/gl/SpecialEntities.kt",
    "app/src/main/kotlin/mcplus/app/gl/GlView.kt",
]
blob = "\n".join((root / path).read_text(encoding="utf-8") for path in sources)

missing = [name for name in entities if '"%s"' % name not in blob]
print("всего типов:", len(entities))
print("без своего вида:", len(missing))
for name in missing:
    print("  ", name)
