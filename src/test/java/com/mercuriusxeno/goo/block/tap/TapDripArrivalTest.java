package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.IGooReceptacle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A drip's landing: a receptacle keeping any of its goo swallows it and no
 * program runs; a landing keeping none runs the type's tap ability, and a
 * type carrying no tap-tagged ability runs no program.
 */
class TapDripArrivalTest {

    private static final BlockPos TAP_POS = new BlockPos(0, 64, 0);
    private static final BlockPos LANDING = new BlockPos(0, 62, 0);
    private static final int ONE_PROGRAM = 1;
    /** A 1:4 drip's volume, so the landing is seen to offer the drip's own mB. */
    private static final int DRIP_MB = 4;

    private static TapDripScheduler.PendingDrip blazeDrip() {
        return new TapDripScheduler.PendingDrip(null, TAP_POS, LANDING, Direction.UP, GooTypes.BLAZE, DRIP_MB, 0);
    }

    @Test
    void dripOfATypeWithNoTapAbilityRunsNoProgram() {
        assertNull(AbilityRegistry.tapAbilityFor(GooTypes.ROCK));

        TapDripScheduler.PendingDrip drip = new TapDripScheduler.PendingDrip(
                null, TAP_POS, LANDING, Direction.UP, GooTypes.ROCK, 1, 0);

        assertEquals(0, TapDripScheduler.land(drip));
    }

    @Nested
    class OnAReceptacle {

        @Test
        void receptacleKeepingTheDripRunsNoProgram() {
            AtomicInteger offered = new AtomicInteger();
            AtomicInteger abilityRuns = new AtomicInteger();
            IGooReceptacle keepsAll = (type, volume) -> {
                offered.addAndGet(volume);
                return volume;
            };

            int programs = TapDripScheduler.land(blazeDrip(), keepsAll, drip -> abilityRuns.incrementAndGet());

            assertEquals(0, programs);
            assertEquals(0, abilityRuns.get());
            assertEquals(DRIP_MB, offered.get());
        }

        @Test
        void receptacleKeepingNoneRunsTheProgram() {
            AtomicInteger abilityRuns = new AtomicInteger();
            IGooReceptacle refuses = (type, volume) -> 0;

            int programs = TapDripScheduler.land(blazeDrip(), refuses, drip -> {
                abilityRuns.incrementAndGet();
                return ONE_PROGRAM;
            });

            assertEquals(ONE_PROGRAM, programs);
            assertEquals(1, abilityRuns.get());
        }

        @Test
        void landingWithNoReceptacleRunsTheProgram() {
            AtomicInteger abilityRuns = new AtomicInteger();

            int programs = TapDripScheduler.land(blazeDrip(), null, drip -> {
                abilityRuns.incrementAndGet();
                return ONE_PROGRAM;
            });

            assertEquals(ONE_PROGRAM, programs);
            assertEquals(1, abilityRuns.get());
        }
    }
}
