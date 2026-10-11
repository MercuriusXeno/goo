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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The tap's unmake melts the block under it in the unstable crucible's own
 * time for it and gives back the full goo
 * (decision unmake-drip-dissolves-the-block-below).
 */
class UnmakeStepTest {

    /** Cobblestone's value: one mundane block of rock. */
    private static final GooValue COBBLESTONE = new GooValue(Map.of(GooTypes.ROCK, 1152));
    private static final GooValue DIAMONDISH = new GooValue(Map.of(GooTypes.CRYSTAL, 13824, GooTypes.AEON, 4608));
    /** The unstable fuel's melt exponent: an item melts in ceil(mB ^ 0.5) ticks. */
    private static final double UNSTABLE_EXPONENT = 0.5;
    /** The unstable crucible's ticks for cobblestone, ceil(sqrt(1152)). */
    private static final int COBBLESTONE_TICKS = 34;
    private static final UnmakeStep UNMAKE = new UnmakeStep(Expr.literal(1));

    @Nested
    class Rule {

        @Test
        void anUnmakeTakesTheUnstableCruciblesTime() {
            assertEquals(COBBLESTONE_TICKS, UnmakeRule.workToUnmake(1152, UNSTABLE_EXPONENT, 1));
            assertEquals(136, UnmakeRule.workToUnmake(18432, UNSTABLE_EXPONENT, 1));
        }

        @Test
        void aFasterUnmakeTakesItsShareOfTheTime() {
            assertEquals(17, UnmakeRule.workToUnmake(1152, UNSTABLE_EXPONENT, 2));
        }

        @Test
        void nothingTakesLessThanOneTick() {
            assertEquals(1, UnmakeRule.workToUnmake(1, UNSTABLE_EXPONENT, 100));
        }
    }

    @Nested
    class Dissolve {

        private static final BlockPos CHEAP = new BlockPos(1, 2, 3);
        private static final BlockPos DEAR = new BlockPos(1, 2, 4);

        private UnmakeHost holding(BlockPos pos, GooValue value, int progress) {
            UnmakeHost host = mock(UnmakeHost.class);
            when(host.meltExponent()).thenReturn(UNSTABLE_EXPONENT);
            when(host.unmadeBlocks()).thenReturn(List.of(pos));
            when(host.unmadeValue(pos)).thenReturn(value);
            when(host.countUnmakeWork(eq(pos), anyInt())).thenReturn(progress);
            return host;
        }

        private void tick(UnmakeHost host) {
            assertTrue(UNMAKE.tick(new StepContext(host, 0, 0)));
        }

        @Test
        void shortOfTheWorkTheMeltShowsItsShare() {
            UnmakeHost host = holding(CHEAP, COBBLESTONE, 10);

            tick(host);

            verify(host).showUnmaking(CHEAP, 10f / COBBLESTONE_TICKS);
            verify(host, never()).unmake(any(BlockPos.class), any());
        }

        @Test
        void atTheWorkTheBlockGoesAndGivesBackItsFullGoo() {
            UnmakeHost host = holding(CHEAP, COBBLESTONE, COBBLESTONE_TICKS);

            tick(host);

            verify(host).unmake(CHEAP, new GooContents(Map.of(GooTypes.ROCK, 1152)));
        }

        @Test
        void eachHeldBlockIsWorkedOnItsOwn() {
            UnmakeHost host = holding(CHEAP, COBBLESTONE, COBBLESTONE_TICKS);
            when(host.unmadeBlocks()).thenReturn(List.of(CHEAP, DEAR));
            when(host.unmadeValue(DEAR)).thenReturn(DIAMONDISH);
            when(host.countUnmakeWork(eq(DEAR), anyInt())).thenReturn(COBBLESTONE_TICKS);

            tick(host);

            verify(host).unmake(eq(CHEAP), any(GooContents.class));
            verify(host, never()).unmake(eq(DEAR), any(GooContents.class));
            verify(host).showUnmaking(eq(DEAR), anyFloat());
        }

        @Test
        void anUnvaluedBlockStands() {
            UnmakeHost host = holding(CHEAP, null, 1000);

            tick(host);

            verify(host, never()).unmake(any(BlockPos.class), any());
            verify(host, never()).showUnmaking(any(BlockPos.class), anyFloat());
        }
    }

    @Nested
    class Hosts {

        @Test
        void onlyTheTapHostsAnUnmake() {
            List<Step> unmake = List.of(UNMAKE);

            assertDoesNotThrow(() -> ProgramBehavior.forHost(unmake, HostKind.TAP));
            assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(unmake, HostKind.PLAYER));
            assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(unmake, HostKind.ENTITY));
        }
    }
}
