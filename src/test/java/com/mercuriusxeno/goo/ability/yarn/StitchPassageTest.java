package com.mercuriusxeno.goo.ability.yarn;

import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what Stitch does to the kind of mob in it: a passive passes, a hostile tangles.
 * passives-move-through-stitch-and-hostiles-tangle-in-it
 */
class StitchPassageTest {

    @Test
    void aPassiveCreaturePasses() {
        assertEquals(StitchPassage.PASS, StitchPassage.of(MobCategory.CREATURE, false));
    }

    @Test
    void aMonsterTangles() {
        assertEquals(StitchPassage.TANGLE, StitchPassage.of(MobCategory.MONSTER, true));
    }

    @Test
    void anEnemyOutsideTheMonsterCategoryTangles() {
        assertEquals(StitchPassage.TANGLE, StitchPassage.of(MobCategory.MISC, true));
    }

    @Test
    void aPlayerPasses() {
        assertEquals(StitchPassage.PASS, StitchPassage.of(MobCategory.MISC, false));
    }
}
