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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unmake's soup drinks the face under the cursor a block at a time, in the
 * face's order, each block burning twice the unstable crucible's fuel
 * for it (decision unmake-waves-dissolve-by-crucible-cost).
 */
class SiphonStepTest {

    /** Cobblestone's value: one mundane block of rock. */
    private static final GooValue COBBLESTONE = new GooValue(Map.of(GooTypes.ROCK, 1152));
    /** The unstable crucible's ticks for cobblestone, ceil(sqrt(1152)). */
    private static final int COBBLESTONE_TICKS = 34;
    private static final double UNSTABLE_EXPONENT = 0.5;
    private static final BlockPos AIMED = new BlockPos(4, 2, 3);
    private static final BlockPos BESIDE = new BlockPos(4, 3, 3);
    private static final SiphonStep SIPHON = new SiphonStep(Expr.literal(1), Expr.literal(1));

    private static SiphonHost facing(List<BlockPos> face, boolean ready, boolean pays) {
        SiphonHost host = mock(SiphonHost.class);
        when(host.readyToSiphon()).thenReturn(ready);
        when(host.siphonFace(anyInt())).thenReturn(face);
        when(host.meltExponent()).thenReturn(UNSTABLE_EXPONENT);
        when(host.ticksPerMb()).thenReturn(1);
        when(host.burnUnstable(anyInt())).thenReturn(pays);
        return host;
    }

    private static void tick(SiphonHost host) {
        SIPHON.tick(new StepContext(host, 0, 0));
    }

    @Nested
    class Drinking {

        @Test
        void theFirstOnTheFaceStartsAndBurnsTwiceTheCruciblesFuel() {
            SiphonHost host = facing(List.of(AIMED, BESIDE), true, true);
            when(host.siphonValue(AIMED)).thenReturn(COBBLESTONE);
            when(host.siphonValue(BESIDE)).thenReturn(COBBLESTONE);

            tick(host);

            verify(host).holdSoup();
            verify(host).burnUnstable(2 * COBBLESTONE_TICKS);
            verify(host).siphon(AIMED, new GooContents(Map.of(GooTypes.ROCK, 1152)), SiphonRule.SIPHON_TICKS,
                    SiphonRule.START_INTERVAL_TICKS);
            verify(host, never()).siphon(eq(BESIDE), any(), anyInt(), anyInt());
        }

        @Test
        void aBlockWithNoGooIsPassedOver() {
            SiphonHost host = facing(List.of(AIMED, BESIDE), true, true);
            when(host.siphonValue(BESIDE)).thenReturn(COBBLESTONE);

            tick(host);

            verify(host).siphon(eq(BESIDE), any(), anyInt(), anyInt());
        }

        @Test
        void aBlockThePlayerCannotPayForStands() {
            SiphonHost host = facing(List.of(AIMED), true, false);
            when(host.siphonValue(AIMED)).thenReturn(COBBLESTONE);

            tick(host);

            verify(host, never()).siphon(any(), any(), anyInt(), anyInt());
        }

        @Test
        void aSoupNotYetReadyStartsNothingButStaysHeld() {
            SiphonHost host = facing(List.of(AIMED), false, true);
            when(host.siphonValue(AIMED)).thenReturn(COBBLESTONE);

            tick(host);

            verify(host).holdSoup();
            verify(host, never()).burnUnstable(anyInt());
            verify(host, never()).siphon(any(), any(), anyInt(), anyInt());
        }

        @Test
        void theRadiusNamesTheSquare() {
            SiphonHost host = facing(List.of(), true, true);

            new SiphonStep(Expr.literal(2), Expr.literal(1)).tick(new StepContext(host, 0, 0));

            verify(host).siphonFace(2);
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
        void aChargedSiphonDrinksTwiceAsFast() {
            assertEquals(SiphonRule.SIPHON_TICKS, SiphonRule.siphonTicks(1));
            assertEquals(3, SiphonRule.siphonTicks(2));
            assertEquals(1, SiphonRule.startInterval(2));
        }

        @Test
        void nothingTakesLessThanOneTick() {
            assertEquals(1, SiphonRule.siphonTicks(100));
            assertEquals(1, SiphonRule.startInterval(100));
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
