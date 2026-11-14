package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.throwing.TargetingHint;
import org.jspecify.annotations.Nullable;

/**
 * The mark the aim draws for the selected ability, chosen by its badge.
 * target-kind-configured-per-ability
 */
enum AimIndicator {
    /** Draws nothing, leaving the vanilla crosshair to aim. */
    NONE,
    /** The aimed entity's outline. */
    ENTITY_OUTLINE,
    /** The aimed block's outline and the bullseye on its struck face. */
    BLOCK_OUTLINE;

    /**
     * The mark a badge draws: mob the entity outline, world the block outline,
     * and every other badge nothing, the vanilla crosshair aiming the point. A
     * Grab press holding a mob draws the held mob's outline.
     * vanilla-crosshair-aims-everything-without-reticule-line-or-touch-ring
     * grab-hides-the-reticle-once-a-mob-is-held
     *
     * @param badge         the selected ability's badge, or null when none is synced
     * @param grabHoldsAMob whether the Grab press the frame resolved holds a mob
     * @return the indicator
     */
    static AimIndicator of(@Nullable AbilityBadge badge, boolean grabHoldsAMob) {
        if (badge == null) {
            return NONE;
        }
        if (grabHoldsAMob) {
            return ENTITY_OUTLINE;
        }
        return switch (TargetingHint.of(badge)) {
            case NONE, POINT -> NONE;
            case ENTITY -> ENTITY_OUTLINE;
            case BLOCK -> BLOCK_OUTLINE;
        };
    }

    /**
     * The mark drawn on the aimed face: for an ability growing a prism,
     * nothing on a face no prism can grow from, so the outline and its rings
     * mark only where a prism takes; every other ability's mark stands.
     * decision prism-highlight-draws-only-on-a-face-a-prism-grows-from
     *
     * @param indicator    the mark the badge chooses
     * @param growsAPrism  whether the selected ability grows a prism
     * @param growableFace whether a prism can grow from the aimed face
     * @return the mark drawn there
     */
    static AimIndicator onFace(AimIndicator indicator, boolean growsAPrism, boolean growableFace) {
        return growsAPrism && !growableFace ? NONE : indicator;
    }
}
