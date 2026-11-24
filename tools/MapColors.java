import net.minecraft.world.level.material.MapColor;

/**
 * Выгружает палитру карт: 256 значений упакованного цвета → RGB.
 *
 * Карта приходит байтами, где старшие биты — номер цвета, младшие два — оттенок яркости.
 * Клиенту незачем повторять эти константы руками: таблица берётся из самого jar.
 */
public final class MapColors {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        StringBuilder out = new StringBuilder();
        for (int packed = 0; packed < 256; packed++) {
            int argb = MapColor.getColorFromPackedId(packed);
            out.append(packed).append('\t')
                .append(String.format("%06X", argb & 0xFFFFFF))
                .append('\n');
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), out.toString());
        System.out.println("цветов карты: 256");
    }
}
