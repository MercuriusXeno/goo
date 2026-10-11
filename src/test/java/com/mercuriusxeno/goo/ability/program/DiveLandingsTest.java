package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A dive lands on a standable cell within its sphere, never the diver's own
 * cell, a cell below the feet weighing four times one above them
 * (decision dive-drops-you-to-a-cave-below).
 */
class DiveLandingsTest {

    private static final BlockPos FEET = new BlockPos(10, 64, -5);
    private static final BlockPos BELOW = FEET.offset(1, -3, 0);
    private static final BlockPos ABOVE = FEET.offset(0, 3, 1);
    private static final int RADIUS = 4;
    /** One below cell at four shares and one above cell at one share. */
    private static final int SHARES = DiveLandings.BELOW_WEIGHT + 1;

    @Test
    void noStandableCellLandsNowhere() {
        assertEquals(Optional.empty(), DiveLandings.pick(FEET, RADIUS, cell -> false, RandomSource.create(1)));
    }

    @Test
    void aLandingIsStandableInsideTheSphereAndNeverTheFeet() {
        RandomSource random = RandomSource.create(7);
        for (int roll = 0; roll < 200; roll++) {
            BlockPos landing = DiveLandings.pick(FEET, RADIUS, cell -> true, random).orElseThrow();
            assertTrue(!landing.equals(FEET) && landing.distSqr(FEET) <= RADIUS * RADIUS, landing.toString());
        }
        BlockPos corner = FEET.offset(RADIUS, RADIUS, RADIUS);
        assertEquals(Optional.empty(), DiveLandings.pick(FEET, RADIUS, corner::equals, random));
    }

    @Test
    void aCellBelowTakesFourSharesToOneAbove() {
        Set<BlockPos> standable = Set.of(BELOW, ABOVE);
        RandomSource random = mock(RandomSource.class);
        when(random.nextInt(SHARES)).thenReturn(0, DiveLandings.BELOW_WEIGHT - 1, DiveLandings.BELOW_WEIGHT);
        assertEquals(Optional.of(BELOW), DiveLandings.pick(FEET, RADIUS, standable::contains, random));
        assertEquals(Optional.of(BELOW), DiveLandings.pick(FEET, RADIUS, standable::contains, random));
        assertEquals(Optional.of(ABOVE), DiveLandings.pick(FEET, RADIUS, standable::contains, random));
    }

    @Test
    void theShippedRadiusReachesALoneCaveCellBelow() {
        BlockPos cave = FEET.below(4);
        assertEquals(Optional.of(cave), DiveLandings.pick(FEET, 16, cave::equals, RandomSource.create(3)));
    }

    @Test
    void aCellLevelWithTheFeetTakesOneShare() {
        BlockPos level = FEET.east(2);
        Set<BlockPos> standable = Set.of(BELOW, level);
        RandomSource random = mock(RandomSource.class);
        when(random.nextInt(SHARES)).thenReturn(DiveLandings.BELOW_WEIGHT);
        assertEquals(Optional.of(level), DiveLandings.pick(FEET, RADIUS, standable::contains, random));
    }
}
