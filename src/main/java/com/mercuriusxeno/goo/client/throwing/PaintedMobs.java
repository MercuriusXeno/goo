package com.mercuriusxeno.goo.client.throwing;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * The mobs a Knives hold has painted, in the order first hovered: each mob
 * the outline picks while the hold forms joins once and stays painted after
 * the outline leaves it, until the knives are thrown or the hold is let go,
 * or the mob dies or unloads. The outline's pick already spares the caster's
 * minions and downed mobs, so the paint spares them too.
 * decision knives-paint-mobs-by-hovering-while-forming
 */
public final class PaintedMobs {

    private final Set<Integer> painted = new LinkedHashSet<>();

    /**
     * Paints the mob the outline picks this tick.
     *
     * @param hovered the outlined mob's entity id, empty for none
     */
    public void hovered(OptionalInt hovered) {
        hovered.ifPresent(painted::add);
    }

    /**
     * @return the painted mobs' entity ids
     */
    public List<Integer> painted() {
        return List.copyOf(painted);
    }

    /**
     * @param entityId an entity id
     * @return whether the mob is painted
     */
    public boolean paints(int entityId) {
        return painted.contains(entityId);
    }

    /** Clears the list as the knives are thrown. */
    public void thrown() {
        painted.clear();
    }

    /** Clears the list as the hold is let go. */
    public void dismissed() {
        painted.clear();
    }

    /**
     * Drops each painted mob no longer standing.
     *
     * @param stands whether the mob of an id still stands
     */
    public void dropGone(IntPredicate stands) {
        painted.removeIf(id -> !stands.test(id));
    }
}
