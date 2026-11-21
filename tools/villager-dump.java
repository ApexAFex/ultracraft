// Prints the vanilla villager model (parts, poses, boxes, texture offsets) as TSV.
//
// Mob models are code, not data, so the only honest source of the numbers is the game
// itself: this dumps `net.minecraft.client.model.npc.VillagerModel` from the client jar.
// The output is read by tools/build-villager-model.js, which turns it into the Kotlin
// MobBox entries in MobModel.kt.
//
//   tools/villager-dump.ps1                  # печатает TSV в консоль
//   tools/villager-dump.ps1 tools/villager-model.tsv
//
// Запускать нужно JDK 25: классы 26.3 собраны под class file version 69.

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.model.npc.VillagerModel;

final class villager_dump {

    public static void main(String[] args) throws Exception {
        MeshDefinition mesh = VillagerModel.createBodyModel();
        dump(mesh.getRoot(), "root", 0);
    }

    private static void dump(PartDefinition part, String name, int depth) throws Exception {
        PartPose pose = (PartPose) field(part, "partPose");

        System.out.printf(
            "PART\t%s\t%.2f\t%.2f\t%.2f\t%.3f\t%.3f\t%.3f%n",
            name, pose.x(), pose.y(), pose.z(), pose.xRot(), pose.yRot(), pose.zRot());

        for (CubeDefinition cube : cubes(part)) {
            UVPair uv = (UVPair) field(cube, "texCoord");
            CubeDeformation grow = (CubeDeformation) field(cube, "grow");
            boolean mirror = (Boolean) field(cube, "mirror");
            System.out.printf("CUBE\t%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.1f\t%.1f\t%.2f\t%.2f\t%.2f\t%s%n",
                name,
                vector(cube, "origin", 0), vector(cube, "origin", 1), vector(cube, "origin", 2),
                vector(cube, "dimensions", 0), vector(cube, "dimensions", 1), vector(cube, "dimensions", 2),
                uv.u(), uv.v(),
                floatField(grow, "growX"), floatField(grow, "growY"), floatField(grow, "growZ"),
                mirror ? "mirror" : "");
        }

        for (Map.Entry<String, PartDefinition> child : part.getChildren()) {
            dump(child.getValue(), name + "/" + child.getKey(), depth + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<CubeDefinition> cubes(PartDefinition part) throws Exception {
        return (List<CubeDefinition>) field(part, "cubes");
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

    /** Reads one component of a joml vector through its public interface. */
    private static float vector(CubeDefinition cube, String name, int component) throws Exception {
        Object value = field(cube, name);
        return (Float) value.getClass().getMethod(component == 0 ? "x" : component == 1 ? "y" : "z")
            .invoke(value);
    }

    private villager_dump() {
    }
}
