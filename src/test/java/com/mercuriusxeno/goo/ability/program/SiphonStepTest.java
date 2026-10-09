package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unmake drinks every block in the cone before the eye together, each over
 * the unstable crucible's own time for it and burning twice the crucible's
 * fuel, and one the player cannot pay for holds up none of the others
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class SiphonStepTest {

    /** Cobblestone's value: one mundane block of rock. */
    private static final GooValue COBBLESTONE = new GooValue(Map.of(GooTypes.ROCK, 1152));
    /** The unstable crucible's ticks for cobblestone, ceil(sqrt(1152)). */
    private static final int COBBLESTONE_TICKS = 34;
    private static final double UNSTABLE_EXPONENT = 0.5;
    private static final double DELTA = 1e-6;
    private static final BlockPos AIMED = new BlockPos(4, 2, 3);
    private static final BlockPos BESIDE = new BlockPos(4, 3, 3);
    private static final SiphonStep SIPHON = new SiphonStep(Expr.literal(1), Expr.literal(1));

    private static SiphonHost facing(List<BlockPos> cone) {
        SiphonHost host = mock(SiphonHost.class);
        when(host.siphonCone(anyInt())).thenReturn(cone);
        when(host.meltExponent()).thenReturn(UNSTABLE_EXPONENT);
        when(host.ticksPerMb()).thenReturn(1);
        return host;
    }

    private static void tick(SiphonHost host) {
        SIPHON.tick(new StepContext(host, 0, 0));
    }

    @Nested
    class Drinking {

        @Test
        void everyBlockInTheConeStartsTogetherEachBurningTwiceTheCruciblesFuel() {
            SiphonHost host = facing(List.of(AIMED, BESIDE));
            when(host.siphonValue(any())).thenReturn(COBBLESTONE);
            when(host.burnUnstable(anyInt())).thenReturn(true);

            tick(host);

            verify(host).holdDrink();
            verify(host).siphon(AIMED, new GooContents(Map.of(GooTypes.ROCK, 1152)), COBBLESTONE_TICKS);
            verify(host).siphon(BESIDE, new GooContents(Map.of(GooTypes.ROCK, 1152)), COBBLESTONE_TICKS);
            verify(host, times(2)).burnUnstable(2 * COBBLESTONE_TICKS);
        }

        @Test
        void aBlockWithNoGooIsPassedOver() {
            SiphonHost host = facing(List.of(AIMED, BESIDE));
            when(host.siphonValue(BESIDE)).thenReturn(COBBLESTONE);
            when(host.burnUnstable(anyInt())).thenReturn(true);

            tick(host);

            verify(host).siphon(eq(BESIDE), any(), anyInt());
            verify(host, never()).siphon(eq(AIMED), any(), anyInt());
        }

        @Test
        void aBlockThePlayerCannotPayForStandsAndHoldsUpNoOther() {
            SiphonHost host = facing(List.of(AIMED, BESIDE));
            when(host.siphonValue(any())).thenReturn(COBBLESTONE);
            when(host.burnUnstable(anyInt())).thenReturn(false, true);

            tick(host);

            verify(host, never()).siphon(eq(AIMED), any(), anyInt());
            verify(host).siphon(eq(BESIDE), any(), anyInt());
        }

        @Test
        void aChargedSiphonDrinksEachBlockTwiceAsFast() {
            SiphonHost host = facing(List.of(AIMED));
            when(host.siphonValue(AIMED)).thenReturn(COBBLESTONE);
            when(host.burnUnstable(anyInt())).thenReturn(true);

            new SiphonStep(Expr.literal(1), Expr.literal(2)).tick(new StepContext(host, 0, 0));

            verify(host).siphon(eq(AIMED), any(), eq(COBBLESTONE_TICKS / 2));
        }

        @Test
        void theRadiusNamesTheCone() {
            SiphonHost host = facing(List.of());

            new SiphonStep(Expr.literal(2), Expr.literal(1)).tick(new StepContext(host, 0, 0));

            verify(host).siphonCone(2);
        }
    }

    @Nested
    class Rule {

        @Test
        void aBlockCostsTwiceTheUnstableCruciblesFuel() {
            assertEquals(2 * COBBLESTONE_TICKS, SiphonRule.fuelFor(1152, UNSTABLE_EXPONENT, 1));
        }

        @Test
        void richerFuelBuysMoreTicksPerMb() {
            assertEquals(2 * (COBBLESTONE_TICKS / 2), SiphonRule.fuelFor(1152, UNSTABLE_EXPONENT, 2));
        }

        @Test
        void aBlockStreamsInOverTheUnstableCruciblesTimeForIt() {
            assertEquals(COBBLESTONE_TICKS, SiphonRule.siphonTicks(1152, UNSTABLE_EXPONENT, 1));
            assertEquals(COBBLESTONE_TICKS / 2, SiphonRule.siphonTicks(1152, UNSTABLE_EXPONENT, 2));
        }

        @Test
        void nothingTakesLessThanOneTick() {
            assertEquals(1, SiphonRule.siphonTicks(1, UNSTABLE_EXPONENT, 100));
        }

        @Test
        void theConeIsOneBlockWideUpCloseAndItsSquareWideAtMidRange() {
            double halfAngle = Math.toRadians(SiphonRule.coneDegrees(1) / 2);

            assertEquals(1.5, Math.tan(halfAngle) * SiphonRule.MID_RANGE, DELTA);
            assertTrue(Math.tan(halfAngle) * 2 < 1, "two blocks out the cone is under a block wide");
            assertTrue(SiphonRule.coneDegrees(2) > SiphonRule.coneDegrees(1));
        }
    }

    @Nested
    class Hosts {

        @Test
        void onlyTheChannelingPlayerHostsASiphon() {
            List<Step> siphon = List.of(SIPHON);

            assertDoesNotThrow(() -> ProgramBehavior.forHost(siphon, HostKind.PLAYER));
            assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(siphon, HostKind.TAP));
            assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(siphon, HostKind.ENTITY));
        }
    }
}
