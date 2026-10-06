package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.throwing.TargetingHint;
import org.jspecify.annotations.Nullable;

/**
 * The mark the aim draws for the selected ability, chosen by its badge.
 * target-kind-configured-per-ability
 */
enum AimIndicator {
    /** Draws nothing: a self ability, or none selected. */
    NONE,
    /** The aimed entity's outline, and the touch ring for a mob ability flying a line. */
    ENTITY_OUTLINE,
    /** The aimed block's outline and the bullseye on its struck face. */
    BLOCK_OUTLINE,
    /** A small reticule at the aimed point. */
    RETICULE;

    /**
     * The mark a badge draws: mob the entity outline, world the block outline,
     * self nothing, and every badge aiming the ray's point a reticule there,
     * until an ability customizes its own.
     *
     * @param badge the selected ability's badge, or null when none is synced
     * @return the indicator
     */
    static AimIndicator of(@Nullable AbilityBadge badge) {
        if (badge == null) {
            return NONE;
        }
        return switch (TargetingHint.of(badge)) {
            case NONE -> NONE;
            case ENTITY -> ENTITY_OUTLINE;
            case BLOCK -> BLOCK_OUTLINE;
            case POINT -> RETICULE;
        };
    }
}
