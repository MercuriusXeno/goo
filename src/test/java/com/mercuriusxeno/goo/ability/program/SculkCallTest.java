package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The convoke ring calls a hostile mob and passes every other mob over: a
 * zombie, a monster, is called, a cow, a creature, is not, and an enemy is
 * called whatever its category (decision convoke-ring-pulls-hostiles-only).
 */
class SculkCallTest {

    @Test
    void callsAZombie() {
        assertTrue(SculkCall.calls(MobCategory.MONSTER, true));
    }

    @Test
    void passesACowOver() {
        assertFalse(SculkCall.calls(MobCategory.CREATURE, false));
    }

    @Test
    void callsAnEnemyOutsideTheMonsterCategory() {
        assertTrue(SculkCall.calls(MobCategory.MISC, true));
    }
}
