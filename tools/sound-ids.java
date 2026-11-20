// Prints every sound event of the game with its registry id, straight from the server jar.
//
// The `Sound` packet does not carry a sound name: it carries an id in the
// `minecraft:sound_event` registry (or an inline definition for datapack sounds).
// The ids are the order in which the server registers its sounds, so the only honest
// source is the server itself:
//
//   java -cp "tools/mcd/server-26.3.jar;tools/mcd/libs/*" tools/sound-ids.java data/sound-events-777.tsv
//
// The output is a TSV: id, name. It is read by SoundEventTable at run time.

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvent;

public class SoundIds {

    public static void main(String[] args) throws Exception {
        String output = args.length > 0 ? args[0] : "data/sound-events-777.tsv";
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Registry<SoundEvent> registry = BuiltInRegistries.SOUND_EVENT;
        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<?, SoundEvent> entry : registry.entrySet()) {
            SoundEvent event = entry.getValue();
            rows.add(new String[] { Integer.toString(registry.getId(event)), event.location().toString() });
        }
        rows.sort((a, b) -> Integer.compare(Integer.parseInt(a[0]), Integer.parseInt(b[0])));
        File file = new File(output);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
            writer.println("# id\tname");
            for (String[] row : rows) writer.println(row[0] + "\t" + row[1]);
        }
        System.out.println("Записано звуков: " + rows.size() + " → " + file.getAbsolutePath());
    }
}
