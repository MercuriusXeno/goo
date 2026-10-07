package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityBadge;

/**
 * The aim mode the selected ability's badge asks for; the badge is the one
 * source of an ability's target kind.
 * target-kind-configured-per-ability
 */
public enum TargetingHint {
    /**
     * Aims nothing: no ability selected, or a self ability.
     */
    NONE,
    /**
     * Takes the entity under the ray through the aim assist, and aims nothing
     * where no entity is near: a mob ability is never thrown at the world.
     */
    ENTITY,
    /**
     * Favors the block face the ray meets.
     */
    BLOCK,
    /**
     * Locks onto a mob near the ray through the aim assist, and aims the ray's
     * point where no mob is near, favoring no block.
     */
    POINT;

    /**
     * The aim mode a badge asks for: mob favors entities, world favors blocks,
     * self and brew aim nothing, and every other badge aims the ray's point.
     *
     * @param badge the selected ability's badge
     * @return the aim mode
     */
    public static TargetingHint of(AbilityBadge badge) {
        if (badge.aimsAPoint()) {
            return POINT;
        }
        return switch (badge) {
            case MOB -> ENTITY;
            case WORLD -> BLOCK;
            default -> NONE;
        };
    }
}
