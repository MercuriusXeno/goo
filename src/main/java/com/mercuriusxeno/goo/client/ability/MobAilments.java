package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The status ailments entities wear on this client, keyed by entity id:
 * each ailment holds until the tick it ends, a fresh landing of the same
 * ailment stretches it to the later end, and its overlay stands whole until
 * that end and fades over the second after, so an overlay its source keeps
 * refreshing never dims while the source stands.
 * Decision ailment-overlay-shader-per-ailment.
 * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
 */
public final class MobAilments {

    /** Game ticks the overlay takes to fade out after its ailment ends or is cleared: a second. */
    public static final int FADE_TICKS = Afterglow.FADE_TICKS;

    /** The client's ailments. */
    public static final MobAilments CLIENT = new MobAilments();

    /** The game tick each ailment ends, by entity id. */
    private final Map<Integer, EnumMap<AilmentKind, Long>> endTicks = new HashMap<>();

    /**
     * One ailment an entity wears, as a frame reads it.
     *
     * @param kind      the ailment
     * @param ticksLeft game ticks until it ends, zero or below once it has ended and its overlay fades
     */
    public record Worn(AilmentKind kind, long ticksLeft) {

        /**
         * Whether the ailment still stands, rather than fading after its end.
         *
         * @return true before the tick it ends
         */
        public boolean stands() {
            return ticksLeft > 0;
        }
    }

    /**
     * How strongly the overlay draws with ticks left on its ailment: whole
     * until it ends, then falling to nothing over the FADE_TICKS after.
     *
     * @param ticksLeft ticks until the ailment ends, with the partial tick taken off, negative past the end
     * @return 0 to 1
     */
    public static float strength(float ticksLeft) {
        return Math.clamp(1f + ticksLeft / FADE_TICKS, 0f, 1f);
    }

    /**
     * An entity starts wearing an ailment, or wears it longer; a landing of
     * no duration ends a worn ailment where it lands, so its overlay falls to
     * nothing over the second after rather than snapping off.
     *
     * @param entityId      the entity's id
     * @param kind          the ailment
     * @param tick          the game tick it lands
     * @param durationTicks how long it lasts, zero to end it
     */
    public void afflict(int entityId, AilmentKind kind, long tick, int durationTicks) {
        dropFaded(tick);
        if (durationTicks <= 0) {
            Map<AilmentKind, Long> worn = endTicks.get(entityId);
            if (worn != null) {
                worn.computeIfPresent(kind, (ailment, end) -> Math.min(end, tick));
            }
            return;
        }
        endTicks.computeIfAbsent(entityId, id -> new EnumMap<>(AilmentKind.class))
                .merge(kind, tick + durationTicks, Math::max);
    }

    /**
     * The ailments an entity wears at a tick, in AilmentKind order, each
     * standing or fading after its end; empty where it wears none.
     *
     * @param entityId the entity's id
     * @param tick     the game tick
     * @return each ailment drawn with its ticks left
     */
    public List<Worn> ailmentsOf(int entityId, long tick) {
        Map<AilmentKind, Long> worn = endTicks.get(entityId);
        if (worn == null) {
            return List.of();
        }
        return worn.entrySet().stream()
                .filter(ending -> ending.getValue() + FADE_TICKS > tick)
                .map(ending -> new Worn(ending.getKey(), ending.getValue() - tick))
                .toList();
    }

    private void dropFaded(long tick) {
        endTicks.values().forEach(worn -> worn.values().removeIf(end -> end + FADE_TICKS <= tick));
        endTicks.values().removeIf(Map::isEmpty);
    }

    /**
     * Drops every ailment, as a disconnect does.
     */
    public void clear() {
        endTicks.clear();
    }
}
