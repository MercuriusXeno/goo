package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;

/**
 * The Void portable holes the client draws in the local player's level, as
 * the server last told them, each growing open from nothing when first seen
 * and shrinking shut once the server stops listing it.
 * decision hole-is-an-in-world-portable-hole-for-items
 * decision hole-animates-open-and-closed
 */
public final class ClientPortableHoles {

    private static final HoleSightings SIGHTINGS = new HoleSightings();
    /** The level the holes were last told for, so a hole left behind in another is not drawn shrinking here. */
    private static Level toldFor;

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
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != toldFor) {
                SIGHTINGS.forget();
                toldFor = mc.level;
            }
            SIGHTINGS.seen(payload.mouths(), mc.level == null ? 0 : mc.level.getGameTime());
        });
    }

    /**
     * @param time the client's game time, partial tick included
     * @return each mouth to draw, open or shrinking shut
     */
    public static List<PortableHolesSyncPayload.Mouth> drawn(float time) {
        return SIGHTINGS.drawn(time);
    }

    /**
     * @param mouth a drawn mouth
     * @param time  the client's game time, partial tick included
     * @return the share of its full size it stands open at, in [0, 1]
     */
    public static float openShare(PortableHolesSyncPayload.Mouth mouth, float time) {
        return SIGHTINGS.openShare(mouth, time);
    }
}
