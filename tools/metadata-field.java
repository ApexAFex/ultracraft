// Печатает номера поля метаданных (`EntityDataAccessor.id`) для класса сущности.
//
// Номер поля в `Set Entity Data` — это порядок объявления в статическом инициализаторе класса
// плюс число полей всех родителей, а не что-то, что видно в javap. Поэтому номера берутся у
// самого сервера: `SynchedEntityData.defineId` нумерует поля при инициализации класса.
//
//   tools\metadata-field.ps1 net.minecraft.world.entity.animal.sheep.Sheep
//
// Нужен JDK 25: классы 26.3 собраны под class file version 69.

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.minecraft.network.syncher.EntityDataAccessor;

final class metadata_field {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("нужно имя класса сущности");
            System.exit(2);
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        for (String name : args) {
            Class<?> type = Class.forName(name);
            System.out.println("CLASS\t" + name);
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != EntityDataAccessor.class) continue;
                field.setAccessible(true);
                Object accessor = field.get(null);
                if (accessor == null) continue;
                System.out.printf("FIELD\t%s\t%d%n", field.getName(), id(accessor));
            }
        }
    }

    /** Номер поля: приватное `id` внутри `EntityDataAccessor`. */
    private static int id(Object accessor) throws Exception {
        Field id = accessor.getClass().getDeclaredField("id");
        id.setAccessible(true);
        return id.getInt(accessor);
    }
}
