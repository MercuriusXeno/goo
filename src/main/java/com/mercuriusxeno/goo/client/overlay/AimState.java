package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.jspecify.annotations.Nullable;
import java.util.function.Predicate;

/**
 * The glove's aim as one frame resolved it, read by everything that needs
 * it until the next frame: the frame's highlight, the throw and the
 * entity outline. One resolve per frame
 * means one sticky seed, the hit that resolve found (decisions
 * render-context-is-the-one-emitter, aim-target-follows-client-aim).
 * {@link AimTracker} updates it.
 */
public final class AimState {

    private TargetResult target = TargetResult.NONE;
    private AimAssistResolver.@Nullable AimHit hit;
    private int outlineColor;
    private boolean mobsOutline = true;

    /**
     * Resolves one frame's aim from the previous frame's aim-assist hit.
     */
    @FunctionalInterface
    interface AimResolver {
        /**
         * Resolves the aim.
         *
         * @param seed the previous frame's aim-assist hit, or null
         * @return the target and the aim-assist hit behind it
         */
        Resolution resolve(AimAssistResolver.@Nullable AimHit seed);
    }

    /**
     * One frame's resolved aim.
     *
     * @param target the target the aim names
     * @param hit    the aim-assist hit behind it, the next frame's sticky seed, or null
     */
    record Resolution(TargetResult target, AimAssistResolver.@Nullable AimHit hit) {
        /** No aim at all. */
        static final Resolution NOTHING = new Resolution(TargetResult.NONE, null);
    }

    AimState() {
    }

    /**
     * The target this frame's resolve found.
     *
     * @return the target, NONE when nothing is aimed at
     */
    TargetResult target() {
        return target;
    }

    /**
     * Stores one frame's aim, resolved from the last frame's hit.
     *
     * @param resolver           resolves the aim
     * @param entityOutlineColor the outline an aimed-at entity takes, 0 for none
     */
    void update(AimResolver resolver, int entityOutlineColor) {
        update(resolver, entityOutlineColor, true);
    }

    /**
     * Stores one frame's aim, resolved from the last frame's hit, for a held
     * ability that may outline no mob.
     * decision knives-do-not-highlight-mobs
     *
     * @param resolver           resolves the aim
     * @param entityOutlineColor the outline an aimed-at entity takes, 0 for none
     * @param mobsOutline        whether the held ability outlines the mob its aim rests on; Knives outlines none
     */
    void update(AimResolver resolver, int entityOutlineColor, boolean mobsOutline) {
        Resolution resolution = resolver.resolve(hit);
        target = resolution.target();
        hit = resolution.hit();
        // A locked item keeps its outline while the aim rests on a block, so the color stands whatever the target.
        outlineColor = entityOutlineColor;
        this.mobsOutline = mobsOutline;
    }

    /**
     * Drops the aim when no glove aims.
     */
    void clear() {
        target = TargetResult.NONE;
        hit = null;
        outlineColor = 0;
    }

    /**
     * The outline an entity takes: the aimed-at one's or a locked item's.
     *
     * @param entity    the entity being rendered
     * @param lockedNow whether a Grab locks an entity now, as this client sees its ailment
     * @return the opaque outline color, or 0 when the glove outlines the entity not
     */
    int outlineFor(Entity entity, Predicate<Entity> lockedNow) {
        return outlines(entity, aimedMob(), lockedNow, mobsOutline) ? outlineColor : 0;
    }

    /**
     * The mob the aim holds: the one a mob badge or a lock-on aims at, or the
     * one a Grab press holds.
     *
     * @return the mob, or null when the aim holds none
     */
    @Nullable Entity aimedMob() {
        return target instanceof TargetResult.EntityTarget && hit instanceof AimAssistResolver.AimHit.EntityHit eh
                ? eh.entity() : null;
    }

    /**
     * Whether the glove outlines an entity: the one the aim rests on, under a
     * held ability that outlines mobs, and every item a Grab press locked
     * while its lock stands, so a sweep shows each item it took though the
     * aim moved on.
     * decision sweeping-grab-locks-every-item-the-highlight-passes
     * decision knives-do-not-highlight-mobs
     *
     * @param entity      the entity being rendered
     * @param aimed       the entity the aim rests on, or null
     * @param lockedNow   whether a Grab locks an entity now
     * @param mobsOutline whether the held ability outlines the mob its aim rests on
     * @return true where the glove outlines it
     */
    static boolean outlines(Entity entity, @Nullable Entity aimed, Predicate<Entity> lockedNow,
                            boolean mobsOutline) {
        return mobsOutline && entity == aimed || entity instanceof ItemEntity && lockedNow.test(entity);
    }

    /**
     * Whether a held ability's aim outlines the mob it rests on: a held throw
     * that stacks, Knives' or Dart's, outlines none.
     * decision knives-do-not-highlight-mobs
     * decision dart-does-not-seek-or-highlight
     *
     * @param delivery the held ability's delivery
     * @return true where the aimed mob draws the outline
     */
    static boolean mobsOutlineFor(Delivery delivery) {
        return !delivery.charge().stacks();
    }

    /**
     * The mark an entity carries beside its outline.
     * active-target-indicators-carry-depth-and-animation
     *
     * @param entity   the entity being drawn
     * @param gameTime the game time including the partial tick
     * @return the mark, NONE for an entity the aim does not hold
     */
    AimedMobMark markFor(Entity entity, float gameTime) {
        Entity aimed = aimedMob();
        return AimedMobMark.at(mobsOutline && aimed != null && aimed == entity, gameTime);
    }
}
