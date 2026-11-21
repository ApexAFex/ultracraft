// Печатает геометрию модели моба из клиентского jar: коробки, их координаты и начало UV.
//
// Модели мобов в игре — код, а не данные. Скрипт вызывает у класса модели метод
// `createBodyLayer` (или другой, указанный вторым аргументом), запекает меш через
// `LayerDefinition.bakeRoot()` и обходит готовое дерево `ModelPart`. Координаты у коробок
// уже с учётом позы части и в блоках (1/16 пикселя), UV — в пикселях текстуры.
//
//   tools\mob-dump.ps1 net.minecraft.client.model.animal.squid.SquidModel
//   tools\mob-dump.ps1 net.minecraft.client.model.animal.wolf.AdultWolfModel createBodyLayer 64 32
//
// Третий и четвёртый аргументы — размер листа текстуры: у современных моделей фабрика
// возвращает `MeshDefinition` без материала, и размер иначе не откуда взять.
// Нужен JDK 25: классы 26.3 собраны под class file version 69.

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MaterialDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

final class mob_dump {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("нужно имя класса модели");
            System.exit(2);
        }
        Class<?> type = Class.forName(args[0]);
        String methodName = args.length > 1 ? args[1] : "createBodyLayer";
        int hintWidth = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        int hintHeight = args.length > 3 ? Integer.parseInt(args[3]) : 0;

        Object result = callFactory(type, methodName);

        MeshDefinition mesh;
        int textureWidth = hintWidth > 0 ? hintWidth : 64;
        int textureHeight = hintHeight > 0 ? hintHeight : 64;
        if (result instanceof MeshDefinition) {
            mesh = (MeshDefinition) result;
        } else {
            mesh = (MeshDefinition) field(result, "mesh");
            MaterialDefinition material = (MaterialDefinition) field(result, "material");
            textureWidth = (int) floatField(material, "xTexSize");
            textureHeight = (int) floatField(material, "yTexSize");
        }
        System.out.printf("SIZE\t%d\t%d%n", textureWidth, textureHeight);

        LayerDefinition definition = LayerDefinition.create(mesh, textureWidth, textureHeight);
        dump(definition.bakeRoot(), "root", textureWidth, textureHeight);
    }

    /** Фабрика слоя: без аргументов или с одной деформацией куба. */
    private static Object callFactory(Class<?> type, String name) throws Exception {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name)) continue;
            method.setAccessible(true);
            if (method.getParameterCount() == 0) {
                return method.invoke(null);
            }
            if (method.getParameterCount() == 1
                && method.getParameterTypes()[0] == CubeDeformation.class) {
                return method.invoke(null, new CubeDeformation(0.0F));
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static void dump(ModelPart part, String name, int texWidth, int texHeight)
        throws Exception {
        PartPose pose = part.getInitialPose();
        System.out.printf(
            "PART\t%s\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f%n",
            name, pose.x(), pose.y(), pose.z(), pose.xRot(), pose.yRot(), pose.zRot());

        for (ModelPart.Cube cube : cubes(part)) {
            float minU = Float.MAX_VALUE;
            float minV = Float.MAX_VALUE;
            for (ModelPart.Polygon polygon : cube.polygons) {
                for (ModelPart.Vertex vertex : (ModelPart.Vertex[]) field(polygon, "vertices")) {
                    minU = Math.min(minU, vertex.u() * texWidth);
                    minV = Math.min(minV, vertex.v() * texHeight);
                }
            }
            System.out.printf(
                "CUBE\t%s\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.1f\t%.1f%n",
                name,
                cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ,
                minU, minV);
        }

        @SuppressWarnings("unchecked")
        var children = (java.util.Map<String, ModelPart>) field(part, "children");
        for (var child : children.entrySet()) {
            dump(child.getValue(), name + "/" + child.getKey(), texWidth, texHeight);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ModelPart.Cube> cubes(ModelPart part) throws Exception {
        return (List<ModelPart.Cube>) field(part, "cubes");
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static float floatField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(target);
    }
}
