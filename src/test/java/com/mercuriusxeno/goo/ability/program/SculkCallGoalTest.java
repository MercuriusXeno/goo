package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A called mob's walk holds for the ticks the shipped Shriek step lasts,
 * counted from the shriek, and ends on the last of them, with no block
 * standing at the landing to hold it
 * (decision shriek-blob-cries-once-and-calls-earshot).
 */
class SculkCallGoalTest {

    private static final BlockPos BLOB = new BlockPos(3, 64, -7);
    private static final long SHRIEK_TICK = 12_000;

    /** The ticks the walk lasts, read from the Shriek step xeno_shriek.json runs on its landing. */
    private static int shippedLasts() {
        return AbilityJson.decode("xeno_shriek").behaviors().stream().filter(ShriekStep.class::isInstance)
                .map(ShriekStep.class::cast).findFirst().orElseThrow().lasts().evaluateInt(Variables.NONE);
    }

    private static SculkCallGoal walkFromTheShriek() {
        return new SculkCallGoal(mock(Mob.class), BLOB, SHRIEK_TICK, shippedLasts());
    }

    @Test
    void theWalkHoldsOneTickBeforeTheShriekLastsOut() {
        assertTrue(walkFromTheShriek().holdsAt(SHRIEK_TICK + shippedLasts() - 1));
    }

    @Test
    void theWalkEndsOnTheTickTheShriekLastsOut() {
        assertFalse(walkFromTheShriek().holdsAt(SHRIEK_TICK + shippedLasts()));
    }
}
