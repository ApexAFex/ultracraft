import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Выгружает эффекты и зелья из серверного jar.
 *
 * Клиенту реестры эффектов и зелий не присылаются — они встроены в игру. Печатает два файла:
 *   effects-777.tsv — номер, имя, цвет эффекта;
 *   potions-777.tsv — номер, имя, цвет зелья, список «эффект:длительность:уровень».
 */
public final class PotionTable {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        StringBuilder effects = new StringBuilder();
        for (int id = 0; id < BuiltInRegistries.MOB_EFFECT.size(); id++) {
            var effect = BuiltInRegistries.MOB_EFFECT.byId(id);
            String name = BuiltInRegistries.MOB_EFFECT.getKey(effect).getPath();
            // Тот же формат, что у tools/build-effect-table.js: номер, имя, категория.
            effects.append(id).append('\t').append(name).append('\t')
                .append(effect.getCategory().name()).append('\n');
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), effects.toString());

        StringBuilder potions = new StringBuilder();
        int potionCount = BuiltInRegistries.POTION.size();
        for (int id = 0; id < potionCount; id++) {
            Potion potion = BuiltInRegistries.POTION.byId(id);
            String name = BuiltInRegistries.POTION.getKey(potion).getPath();
            int red = 0, green = 0, blue = 0, count = 0;
            StringBuilder list = new StringBuilder();
            for (MobEffectInstance effect : potion.getEffects()) {
                var holder = effect.getEffect();
                int effectColor = holder.value().getColor();
                red += (effectColor >> 16) & 0xFF;
                green += (effectColor >> 8) & 0xFF;
                blue += effectColor & 0xFF;
                count++;
                int effectId = BuiltInRegistries.MOB_EFFECT.getId(holder.value());
                if (list.length() > 0) list.append(',');
                list.append(effectId).append(':').append(effect.getDuration())
                    .append(':').append(effect.getAmplifier());
            }
            int color = count == 0 ? 0x385DC6 : ((red / count) << 16) | ((green / count) << 8) | (blue / count);
            potions.append(id).append('\t').append(name).append('\t')
                .append(String.format("%06X", color)).append('\t').append(list).append('\n');
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[1]), potions.toString());
        System.out.println("эффектов " + BuiltInRegistries.MOB_EFFECT.size() + ", зелий " + potionCount);
    }
}
