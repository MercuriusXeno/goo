package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Void portable holes standing open in the local player's level as the
 * server last told them, each with the game time the client first saw it, so
 * its mouth grows open from nothing.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
public final class ClientPortableHoles {

    private static List<PortableHolesSyncPayload.Mouth> mouths = List.of();
    private static final Map<PortableHolesSyncPayload.Mouth, Long> FIRST_SEEN = new HashMap<>();

    private ClientPortableHoles() {
    }

    /**
     * Takes the holes the server sent.
     *
     * @param payload the sync payload
     * @param context the network context
     */
    public static void handle(PortableHolesSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            mouths = List.copyOf(payload.mouths());
            FIRST_SEEN.keySet().retainAll(mouths);
            Minecraft mc = Minecraft.getInstance();
            long now = mc.level == null ? 0 : mc.level.getGameTime();
            mouths.forEach(mouth -> FIRST_SEEN.putIfAbsent(mouth, now));
        });
    }

    /**
     * @return each open hole's mouth
     */
    public static List<PortableHolesSyncPayload.Mouth> mouths() {
        return mouths;
    }

    /**
     * @param mouth an open mouth
     * @return the game time the client first saw it
     */
    public static long firstSeen(PortableHolesSyncPayload.Mouth mouth) {
        return FIRST_SEEN.getOrDefault(mouth, 0L);
    }
}
