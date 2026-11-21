import net.minecraft.network.syncher.EntityDataSerializers;

public class ProbeSerializer {
    public static void main(String[] args) throws Exception {
        var field = EntityDataSerializers.class.getDeclaredField("PAINTING_VARIANT");
        field.setAccessible(true);
        Object serializer = field.get(null);
        System.out.println("PAINTING_VARIANT id = " + EntityDataSerializers.getSerializedId((net.minecraft.network.syncher.EntityDataSerializer<?>) serializer));
    }
}
