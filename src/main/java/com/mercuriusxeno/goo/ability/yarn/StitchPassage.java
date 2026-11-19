package com.mercuriusxeno.goo.ability.yarn;

import net.minecraft.world.entity.MobCategory;

/**
 * What Stitch does to the kind of mob in it: a passive passes as if the goo
 * were not there, and a hostile, a monster or any enemy, is tangled in it.
 * passives-move-through-stitch-and-hostiles-tangle-in-it
 */
public enum StitchPassage {

    /** The mob moves through the goo as over bare ground. */
    PASS,
    /** The mob is tangled in the goo's strands. */
    TANGLE;

    /**
     * What the goo does to a mob of a kind.
     *
     * @param category the mob's spawn category
     * @param enemy    whether the mob is an enemy
     * @return TANGLE for a monster or an enemy, PASS for every other mob
     */
    public static StitchPassage of(MobCategory category, boolean enemy) {
        return enemy || category == MobCategory.MONSTER ? TANGLE : PASS;
    }
}
