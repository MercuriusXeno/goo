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
    NOT_BOSS;

    private static final String WHAT = "entity filter";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<EntityFilter> CODEC = LowerCaseEnumCodec.of(EntityFilter.class, WHAT);
}
