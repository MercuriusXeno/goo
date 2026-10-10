package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.program.SlowTimeStep;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.item.ItemEntity;
import org.junit.jupiter.api.Test;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The chronosphere slows mobs and projectiles and spares players, its
 * slowness as deep as the motion it leaves, and its veil grows to its radius
 * over the expand ticks (decision chronosphere-hastes-players-slows-mobs).
 */
class TimeVeilTest {

    private static final double EPSILON = 1e-9;

    @Test
    void theVeilSlowsMobsAndProjectilesButNotPlayers() {
        assertTrue(TimeVeil.slows(mock(Zombie.class)));
        assertTrue(TimeVeil.slows(mock(Arrow.class)));
        assertFalse(TimeVeil.slows(mock(Player.class)));
        assertFalse(TimeVeil.slows(mock(ItemEntity.class)));
    }

    @Test
    void aTenthOfTheMotionWearsSixLevelsOfSlowness() {
        assertEquals(5, TimeVeil.slownessAmplifier(0.1));
        assertEquals(0, TimeVeil.slownessAmplifier(0.9));
    }

    @Test
    void theVeilGrowsFromTheImpactToItsRadius() {
        SlowTimeStep veil = new SlowTimeStep(5, 10, 200, 0.1);

        assertEquals(0, veil.radiusAt(0), EPSILON);
        assertEquals(2.5, veil.radiusAt(5), EPSILON);
        assertEquals(5, veil.radiusAt(40), EPSILON);
    }

    @Test
    void aVeiledMobThinksAtTheVeilsPace() {
        int period = TimeVeil.aiPeriod(0.1);
        long thinking = LongStream.range(0, 100).filter(tick -> TimeVeil.thinksAt(tick, period)).count();

        assertEquals(10, period);
        assertEquals(10, thinking);
    }

    @Test
    void anUnslowedMobThinksEveryTick() {
        assertEquals(1, TimeVeil.aiPeriod(1.0));
    }
}
