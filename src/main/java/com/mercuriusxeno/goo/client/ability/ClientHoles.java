package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.network.HolesSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Quantum holes standing open in the local player's level as the server
 * last told them, each as its two mouths.
 * hole-closes-the-space-to-an-opening-ahead
 */
public final class ClientHoles {

    private static List<HolesSyncPayload.Pair> holes = List.of();
    /** How far each mouth has grown open since it was first told of. */
    private static final GrowOpen<Gate> OPENING = new GrowOpen<>();
    /** The level the holes were last told of in, so a mouth standing as a level is joined shows full size. */
    private static ClientLevel toldIn;

    private ClientHoles() {
    }

    /**
     * Takes the holes the server sent.
     *
     * @param payload the sync payload
     * @param context the network context
     */
    public static void handle(HolesSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            holes = List.copyOf(payload.holes());
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                OPENING.seen(holes.stream().flatMap(hole -> Stream.of(hole.entry(), hole.exit())).toList(),
                        mc.level.getGameTime(), mc.level != toldIn);
                toldIn = mc.level;
            }
            ClientGates.refresh();
        });
    }

    /**
     * How far a hole's mouth has grown open, from nothing as it opens to its
     * full size a moment later, swelling a little past full before it settles.
     *
     * @param gate        one of the hole's mouths
     * @param partialTick the frame's partial tick
     * @return the mouth's share of its full size, one once open
     */
    public static float grownOpen(Gate gate, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 1F : OPENING.grown(gate, mc.level.getGameTime(), partialTick);
    }

    /**
     * @return each open hole's two mouths
     */
    public static List<HolesSyncPayload.Pair> holes() {
        return holes;
    }
}
