// Печатает цвета красителей из клиента 26.3: имя, номер и цвет текстуры.
//
// Цвет нужен там, где ваниль красит текстуру: шерсть овцы по красителю, кожаная броня, флаги.
// Числа берутся у самой игры, а не из вики.
//
//   .\tools\dye-colors.ps1            # таблица в stdout
//
// Нужен JDK 25: классы 26.3 собраны под class file version 69.

import net.minecraft.world.item.DyeColor;

final class dye_colors {

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        for (DyeColor color : DyeColor.values()) {
            // В 26.3 цвет один: `getTextureDiffuseColor()` отдаёт упакованный 0xRRGGBB.
            int rgb = color.getTextureDiffuseColor() & 0xFFFFFF;
            System.out.printf(
                "%s\t%d\t0x%06X%n", color.getName(), color.getId(), rgb);
        }
    }
}
