package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * A filter a selection's {@code where} list may name. The set grows one
 * filter per migration that needs it; the vocabulary doc lists the target
 * set. The host applies the filter, since what an entity is belongs to
 * the world side of the seam.
 */
public enum EntityFilter {
    /**
     * Keeps living entities.
     */
    LIVING,
    /**
     * Keeps anything that is not a dropped item.
     */
    NOT_ITEM,
    /**
     * Keeps anything that is not a wither or an ender dragon.
     */
    NOT_BOSS,
    /**
     * Keeps entities with AI.
     */
    MOB,
    /**
     * Keeps entities that burn.
     */
    NOT_FIRE_IMMUNE,
    /**
     * Keeps living entities that heal from harm and are harmed by healing.
     */
    UNDEAD,
    /**
     * Keeps entities still alive, after a prior effect may have killed them.
     */
    ALIVE,
    /**
     * Keeps anything but the entity the selection centers on, the struck
     * entity on the entity host.
     */
    NOT_TARGET,
    /**
     * Keeps anything that is not a sneaking player; the metal trap spares
     * a player who sneaks through it.
     */
    NOT_SNEAKING,
    /**
     * Keeps entities moving horizontally above rest; the crystal cloud
     * shreds only what moves through it.
     */
    MOVING;

    private static final String WHAT = "entity filter";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<EntityFilter> CODEC = LowerCaseEnumCodec.of(EntityFilter.class, WHAT);
}
