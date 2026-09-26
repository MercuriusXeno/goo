package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The glove's aim as one tick resolved it, read by everything that needs
 * it until the next tick: the frame's highlight and arc, the throw, the
 * entity outline and the chain marker's targeted look. One resolve per tick
 * means one sticky seed, the hit that resolve found (decision
 * render-context-is-the-one-emitter). {@link AimTracker} ticks it.
 */
public final class AimState {

    /**
     * Maximum range for blob throwing in blocks.
     */
    public static final double MAX_RANGE = 64.0;

    private TargetResult target = TargetResult.NONE;
    private AimAssistResolver.@Nullable AimHit hit;
    private int outlineColor;

    /**
     * Resolves one tick's aim from the previous tick's aim-assist hit.
     */
    @FunctionalInterface
    interface AimResolver {
        /**
         * Resolves the aim.
         *
         * @param seed the previous tick's aim-assist hit, or null
         * @return the target and the aim-assist hit behind it
         */
        Resolution resolve(AimAssistResolver.@Nullable AimHit seed);
    }

    /**
     * One tick's resolved aim.
     *
     * @param target the target the aim names
     * @param hit    the aim-assist hit behind it, the next tick's sticky seed, or null
     */
    record Resolution(TargetResult target, AimAssistResolver.@Nullable AimHit hit) {
        /** No aim at all. */
        static final Resolution NOTHING = new Resolution(TargetResult.NONE, null);
    }

    AimState() {
    }

    /**
     * The target this tick's resolve found.
     *
     * @return the target, NONE when nothing is aimed at
     */
    TargetResult target() {
        return target;
    }

    /**
     * Stores one tick's aim, resolved from the last tick's hit.
     *
     * @param resolver           resolves the aim
     * @param entityOutlineColor the outline an aimed-at entity takes, 0 for none
     */
    void update(AimResolver resolver, int entityOutlineColor) {
        Resolution resolution = resolver.resolve(hit);
        target = resolution.target();
        hit = resolution.hit();
        outlineColor = target instanceof TargetResult.EntityTarget ? entityOutlineColor : 0;
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
     * Whether the aim assist is locked onto the chain marker at the given position.
     *
     * @param pos the chain marker block position
     * @return true if this tick's hit is that marker
     */
    boolean isAimedAtMarker(BlockPos pos) {
        return hit instanceof AimAssistResolver.AimHit.ChainMarkerHit cmh && cmh.pos().equals(pos);
    }

    /**
     * The outline an entity takes when it is the aimed-at one.
     *
     * @param entity the entity being rendered
     * @return the opaque outline color, or 0 when the entity is not aimed at
     */
    int outlineFor(Entity entity) {
        boolean aimed = hit instanceof AimAssistResolver.AimHit.EntityHit eh && eh.entity() == entity;
        return aimed ? outlineColor : 0;
    }
}
