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
        HeldSweepPulse pulse = pulses.computeIfAbsent(payload.pulse(), id -> new HeldSweepPulse(fieldAt(center),
                payload.range(), HeldSweepVisual.tint(payload.gooType(), typeColor), payload.paintsMobs(), walls));
        pulse.heard(payload.radius(), gameTime);
    }

    /**
     * The walls about a spot: the field already there, or a new one that
     * takes what the fields standing now found rather than walk the cave
     * again, since a moving caster sends each pulse from a new spot.
     * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
     *
     * @param center the spot, at the caster's body
     * @return the field
     */
    private EchoField fieldAt(Vec3 center) {
        EchoField field = fields.get(center);
        if (field == null) {
            field = new EchoField(center, fields.values());
            fields.put(center, field);
        }
        return field;
    }

    /** Drops each field no live pulse leaves from any more, letting go of the fields it took walls from. */
    void dropUnusedFields() {
        fields.values().removeIf(field -> {
            boolean unused = pulses.values().stream().noneMatch(pulse -> pulse.field() == field);
            if (unused) {
                field.retire();
            }
            return unused;
        });
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
