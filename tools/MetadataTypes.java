import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;

/**
 * Печатает таблицу типов метаданных: номер в сети, имя поля и класс сериализатора.
 *
 * Номер в сети — это порядок регистрации сериализаторов, а поля класса называются по смыслу
 * (`DIRECTION`, `ITEM_STACK` и так далее). Так таблица получается из самого jar, а не из вики.
 */
public final class MetadataTypes {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        List<String> rows = new ArrayList<>();
        for (Field field : EntityDataSerializers.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() != EntityDataSerializer.class) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof EntityDataSerializer<?> serializer)) continue;
            int id;
            try {
                id = EntityDataSerializers.getSerializedId(serializer);
            } catch (Exception error) {
                continue;
            }
            if (id < 0) continue;
            rows.add(id + "\t" + field.getName() + "\t" + serializer.getClass().getName());
        }
        rows.sort((a, b) -> Integer.compare(
            Integer.parseInt(a.split("\t")[0]), Integer.parseInt(b.split("\t")[0])));
        StringBuilder out = new StringBuilder();
        for (String row : rows) out.append(row).append('\n');
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), out.toString());
        System.out.println("типов: " + rows.size());
    }
}
