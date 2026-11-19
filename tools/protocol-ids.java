// Prints every packet id of the game protocol, straight from the server jar.
//
// The numeric ids are not written down anywhere: they are the order in which the server
// registers its packets. This program asks the server's own protocol builder for that list,
// so the table can never drift from the real game the way a wiki page can.
//
//   java -cp tools/mcd/server-26.3.jar tools/protocol-ids.java
//
// The server jar 26.x is shipped unobfuscated, which is what makes this possible:
// class and method names here are the real ones. Extract the version jar first:
//   7z e tools/server/server.jar META-INF/versions/26.3/server-26.3.jar -otools/mcd

import net.minecraft.SharedConstants;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.Bootstrap;

public class ProtocolIds {

    public static void main(String[] args) {
        // The game needs its own version (from version.json inside the jar) before the
        // data fixer can answer schema questions, and only then can registries bootstrap.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        print("clientbound", GameProtocols.CLIENTBOUND_TEMPLATE.details());
        print("serverbound", GameProtocols.SERVERBOUND_TEMPLATE.details());
    }

    private static void print(String direction, ProtocolInfo.Details details) {
        System.out.println("# " + direction);
        details.listPackets((type, id) ->
                System.out.println(String.format("%-4d 0x%02X %s", id, id, type.id())));
    }
}
