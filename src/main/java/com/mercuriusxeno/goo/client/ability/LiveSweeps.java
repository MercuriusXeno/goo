package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.HeldSweepPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * What the caster's client holds of its held sweeps: the live pulses by id,
 * oldest first, the walls around each spot pulses leave from, and the walls
 * every live front holds lit.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
final class LiveSweeps {

    /** A pulse leaves from the caster's body, this far above where the caster stood. */
    private static final double BODY_CENTER = 0.9;

    private final Map<Integer, HeldSweepPulse> pulses = new LinkedHashMap<>();
    private final Map<Vec3, EchoField> fields = new HashMap<>();
    private final LitWalls walls = new LitWalls();

    /**
     * Takes one live pulse's tick: a pulse not seen before starts from its
     * origin, beside the walls any pulse from that spot already found, and
     * the front takes its new radius.
     *
     * @param payload   the pulse's tick
     * @param gameTime  the game time it arrived
     * @param typeColor answers a goo type's color
     */
    void hear(HeldSweepPayload payload, long gameTime, ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
        Vec3 center = payload.origin().add(0, BODY_CENTER, 0);
        HeldSweepPulse pulse = pulses.computeIfAbsent(payload.pulse(), id -> new HeldSweepPulse(
                fields.computeIfAbsent(center, EchoField::new), payload.range(),
                HeldSweepVisual.tint(payload.gooType(), typeColor), payload.paintsMobs(), walls));
        pulse.heard(payload.radius(), gameTime);
    }

    /** Drops every pulse, every field and every lit wall. */
    void clear() {
        pulses.clear();
        fields.clear();
        walls.clear();
    }

    Map<Integer, HeldSweepPulse> pulses() {
        return pulses;
    }

    Map<Vec3, EchoField> fields() {
        return fields;
    }

    LitWalls walls() {
        return walls;
    }
}
