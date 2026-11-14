package com.mercuriusxeno.goo.block.ability;

import net.minecraft.world.entity.Entity;

/**
 * Slick conserves the momentum of what moves across it: a thing that stood on
 * slick within the last half second keeps slick's ice friction on the bare
 * ground it crosses, so a short unpainted gap does not scrub its speed, and
 * steps up onto a raised cell in the air as on the ground, so meeting a
 * raised slick cell's side after a drop does not stop it dead. Vanilla steps
 * a thing up only from the ground and reads the friction of the block under
 * its feet the tick it lands, and both take the momentum slick built.
 * operator ruling 2026-11-14: conserve the momentum over raised slick cells and short bare gaps
 * slick-coats-the-ground-with-kinetic-speed
 */
public final class SlickMomentum {

    /** The ticks after leaving slick a thing still carries slick's momentum: half a second. */
    static final long CARRY_TICKS = 10;

    private SlickMomentum() {
    }

    /**
     * Whether a thing carries slick's momentum this tick.
     *
     * @param entity the thing moving
     * @return true where it stood on slick within the last half second
     */
    public static boolean carries(Entity entity) {
        long now = entity.level().getGameTime();
        return SlickBuild.lastOnSlickAt(entity).stream().anyMatch(at -> carries(at, now));
    }

    /**
     * Whether a thing last on slick at a game time carries slick's momentum at another.
     *
     * @param lastOnSlickAt the game time it last stood on slick
     * @param now           the game time it moves at
     * @return true within CARRY_TICKS of leaving slick
     */
    static boolean carries(long lastOnSlickAt, long now) {
        return now - lastOnSlickAt <= CARRY_TICKS;
    }

    /**
     * The friction a thing meets on the ground under it: at least slick's,
     * where it carries slick's momentum.
     *
     * @param entity   the thing moving
     * @param friction the friction of the block under its feet
     * @return the friction it moves with
     */
    public static float frictionFor(Entity entity, float friction) {
        float slick = CellSurface.RAMP.friction();
        return friction < slick && carries(entity) ? slick : friction;
    }
}
