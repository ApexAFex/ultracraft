import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Выгружает зелья и их цвет из серверного jar: имя, номер и цвет 0xRRGGBB.
 *
 * Клиенту эти реестры не присылаются — они встроены в игру, поэтому таблица делается один раз
 * здесь и читается клиентом как обычный TSV.
 */
public final class PotionTable {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        for (int id = 0; id < BuiltInRegistries.POTION.size(); id++) {
            Potion potion = BuiltInRegistries.POTION.byId(id);
            String name = BuiltInRegistries.POTION.getKey(potion).getPath();
            int color = 0;
            boolean first = true;
            int red = 0, green = 0, blue = 0, count = 0;
            for (MobEffectInstance effect : potion.getEffects()) {
                var key = effect.getEffect().unwrapKey();
                if (key.isEmpty()) continue;
                var holder = effect.getEffect();
                int effectColor = holder.value().getColor();
                red += (effectColor >> 16) & 0xFF;
                green += (effectColor >> 8) & 0xFF;
                blue += effectColor & 0xFF;
                count++;
            }
            if (count == 0) {
                color = 0x385DC6; // вода
            } else {
                color = ((red / count) << 16) | ((green / count) << 8) | (blue / count);
            }
            System.out.printf("%d\t%s\t%06X%n", id, name, color);
        }
    }
}
