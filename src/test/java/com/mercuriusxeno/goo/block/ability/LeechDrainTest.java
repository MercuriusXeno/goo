package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.block.ability.LeechDrain.DrainSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the choice of the life a hit drains to its source through Leech
 * goo: the damage dealt for a direct living source striking a victim in the
 * goo, nothing otherwise.
 * damage-dealt-to-a-mob-in-leech-goo-becomes-drained-life-for-its-source
 */
class LeechDrainTest {

    private static final float DAMAGE = 4f;

    @Test
    void aLivingSourceStrikingAVictimInTheGooDrainsTheDamage() {
        assertEquals(DAMAGE, LeechDrain.drainedLife(DAMAGE, true, DrainSource.DIRECT_LIVING));
    }

    @Test
    void theSameHitOnAVictimOutsideTheGooDrainsNothing() {
        assertEquals(0f, LeechDrain.drainedLife(DAMAGE, false, DrainSource.DIRECT_LIVING));
    }

    @Test
    void sourcelessDamageToAVictimInTheGooDrainsNothing() {
        assertEquals(0f, LeechDrain.drainedLife(DAMAGE, true, DrainSource.NONE));
    }

    @Test
    void anIndirectHitOnAVictimInTheGooDrainsNothing() {
        assertEquals(0f, LeechDrain.drainedLife(DAMAGE, true, DrainSource.INDIRECT));
    }
}
