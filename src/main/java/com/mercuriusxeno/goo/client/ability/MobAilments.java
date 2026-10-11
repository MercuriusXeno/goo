package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The status ailments entities wear on this client, keyed by entity id:
 * each ailment holds until the tick it ends, and a fresh landing of the same
 * ailment stretches it to the later end.
 * Decision ailment-overlay-shader-per-ailment.
 */
public final class MobAilments {

    /** Game ticks the overlay takes to fade out as its ailment runs out. */
    public static final int FADE_TICKS = 10;

    /** The client's ailments. */
    public static final MobAilments CLIENT = new MobAilments();

    /** The game tick each ailment ends, by entity id. */
    private final Map<Integer, EnumMap<AilmentKind, Long>> endTicks = new HashMap<>();

    /**
     * One ailment an entity wears, as a frame reads it.
     *
     * @param kind     the ailment
     * @param ticksLeft game ticks until it ends, above zero
     */
    public record Worn(AilmentKind kind, long ticksLeft) {
    }

    /**
     * How strongly the overlay draws with ticks left on its ailment: whole
     * until the last FADE_TICKS, then falling to nothing as it ends.
     *
     * @param ticksLeft ticks until the ailment ends, with the partial tick taken off
     * @return 0 to 1
     */
    public static float strength(float ticksLeft) {
        return Math.clamp(ticksLeft / FADE_TICKS, 0f, 1f);
    }

    /**
     * An entity starts wearing an ailment, or wears it longer; a landing of
     * no duration ends the ailment at once, as a stasis mob struck free does.
     * stasis-holds-mob-with-golden-shimmer
     *
     * @param entityId      the entity's id
     * @param kind          the ailment
     * @param tick          the game tick it lands
     * @param durationTicks how long it lasts, zero to end it
     */
    public void afflict(int entityId, AilmentKind kind, long tick, int durationTicks) {
        dropEnded(tick);
        if (durationTicks <= 0) {
            Map<AilmentKind, Long> worn = endTicks.get(entityId);
            if (worn != null) {
                worn.remove(kind);
            }
            return;
        }
        endTicks.computeIfAbsent(entityId, id -> new EnumMap<>(AilmentKind.class))
                .merge(kind, tick + durationTicks, Math::max);
    }

    /**
     * The ailments an entity wears at a tick, in AilmentKind order; empty
     * where it wears none.
     *
     * @param entityId the entity's id
     * @param tick     the game tick
     * @return each standing ailment with its ticks left
     */
    public List<Worn> ailmentsOf(int entityId, long tick) {
        Map<AilmentKind, Long> worn = endTicks.get(entityId);
        if (worn == null) {
            return List.of();
        }
        return worn.entrySet().stream()
                .filter(ending -> ending.getValue() > tick)
                .map(ending -> new Worn(ending.getKey(), ending.getValue() - tick))
                .toList();
    }

    private void dropEnded(long tick) {
        endTicks.values().forEach(worn -> worn.values().removeIf(end -> end <= tick));
        endTicks.values().removeIf(Map::isEmpty);
    }

    /**
     * Drops every ailment, as a disconnect does.
     */
    public void clear() {
        endTicks.clear();
    }
}
