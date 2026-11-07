package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.network.HolesSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Quantum holes standing open in the local player's level as the server
 * last told them, each as its two mouths.
 * hole-closes-the-space-to-an-opening-ahead
 */
public final class ClientHoles {

    private static List<HolesSyncPayload.Pair> holes = List.of();
    /** The game time each open hole was first told of, which its mouths grow open from. */
    private static final Map<HolesSyncPayload.Pair, Long> OPENED = new HashMap<>();
    /** Ticks a mouth takes to grow open. */
    private static final float OPENING_TICKS = 12F;
    /** How far past full size a mouth swells as it opens. */
    private static final float OVERSHOOT = 1.7F;

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
            long now = mc.level == null ? 0 : mc.level.getGameTime();
            OPENED.keySet().retainAll(holes);
            holes.forEach(hole -> OPENED.putIfAbsent(hole, now));
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
        if (mc.level == null) {
            return 1F;
        }
        for (Map.Entry<HolesSyncPayload.Pair, Long> opened : OPENED.entrySet()) {
            if (opened.getKey().entry().equals(gate) || opened.getKey().exit().equals(gate)) {
                float age = (mc.level.getGameTime() - opened.getValue() + partialTick) / OPENING_TICKS;
                return age >= 1F ? 1F : easeOutBack(Math.max(0F, age));
            }
        }
        return 1F;
    }

    private static float easeOutBack(float t) {
        float shifted = t - 1F;
        return 1F + (OVERSHOOT + 1F) * shifted * shifted * shifted + OVERSHOOT * shifted * shifted;
    }

    /**
     * @return each open hole's two mouths
     */
    public static List<HolesSyncPayload.Pair> holes() {
        return holes;
    }
}
