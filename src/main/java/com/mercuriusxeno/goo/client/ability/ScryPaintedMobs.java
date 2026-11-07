package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HeldSweepPace;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/**
 * The mobs Scry's fronts have painted on the caster's client: each mob a
 * front crosses is outlined through walls in the sweep's color, as vanilla
 * outlines a glowing mob, for the six seconds the walls glow, and let go after
 * it; a front crossing it again paints it afresh (operator ruling
 * 2026-11-06: Scry paints and highlights mobs, a glowing outline through walls).
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision held-sweeps-share-one-fast-shell-visual
 */
public final class ScryPaintedMobs {

    /** The caster's client's painted mobs, read by the glowing outline's mixins. */
    public static final ScryPaintedMobs CLIENT = new ScryPaintedMobs();

    private final Map<Integer, Painted> painted = new HashMap<>();

    /**
     * Whether a front swept over a point between two of its radii.
     *
     * @param center the front's center
     * @param from   the front's radius before
     * @param to     its radius now
     * @param at     the point, a mob's center
     * @return true when the front passed the point between the two radii
     */
    static boolean crossedBy(Vec3 center, double from, double to, Vec3 at) {
        double distance = at.distanceTo(center);
        return distance > from && distance <= to;
    }

    /**
     * Paints a mob a front crossed.
     *
     * @param entityId the mob's id
     * @param hitAt    the game time with its partial tick the front crossed it
     * @param tintRgb  the sweep's color, 0xRRGGBB
     */
    void paint(int entityId, double hitAt, int tintRgb) {
        painted.put(entityId, new Painted(hitAt, tintRgb));
    }

    /**
     * Whether a mob is outlined at a moment: from the front crossing it until
     * the linger runs out.
     *
     * @param entityId the mob's id
     * @param now      the game time with its partial tick
     * @return true while painted
     */
    boolean isPainted(int entityId, double now) {
        Painted mob = painted.get(entityId);
        return mob != null && now - mob.hitAt() < HeldSweepPace.LINGER_TICKS;
    }

    /**
     * A painted mob's outline color at a moment.
     *
     * @param entityId the mob's id
     * @param now      the game time with its partial tick
     * @return the opaque outline color, the sweep's
     */
    int outlineAt(int entityId, double now) {
        Painted mob = painted.get(entityId);
        return mob == null ? 0 : ARGB.opaque(mob.tintRgb());
    }

    /**
     * Whether a mob is outlined now, for the outline mixins.
     *
     * @param entityId the mob's id
     * @return true while painted
     */
    public boolean isPaintedNow(int entityId) {
        return isPainted(entityId, HeldSweepVisual.clientTime());
    }

    /**
     * A painted mob's outline color now, for the outline mixins.
     *
     * @param entityId the mob's id
     * @return the opaque outline color
     */
    public int outlineNow(int entityId) {
        return outlineAt(entityId, HeldSweepVisual.clientTime());
    }

    /**
     * Lets go every mob whose linger has run out.
     *
     * @param now the game time with its partial tick
     */
    void forgetFaded(double now) {
        painted.values().removeIf(mob -> now - mob.hitAt() >= HeldSweepPace.LINGER_TICKS);
    }

    /** Lets every mob go, as a disconnect does. */
    void clear() {
        painted.clear();
    }

    /**
     * A painted mob: when a front crossed it and in which color.
     *
     * @param hitAt   the game time with its partial tick the front crossed it
     * @param tintRgb the sweep's color
     */
    private record Painted(double hitAt, int tintRgb) {
    }
}
