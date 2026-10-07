package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An unmake works every block its host holds, dissolving each after work
 * proportional to its crucible value and leaving a share of that value
 * (decisions unmake-waves-dissolve-by-crucible-cost, unmake-drip-dissolves-the-block-below).
 */
class UnmakeStepTest {

    /** Cobblestone's value: one mundane block of rock. */
    private static final GooValue COBBLESTONE = new GooValue(Map.of(GooTypes.ROCK, 1152));
    private static final GooValue DIAMONDISH = new GooValue(Map.of(GooTypes.CRYSTAL, 13824, GooTypes.AEON, 4608));
    private static final double WORK_PER_GOO = 0.025;
    private static final double YIELD = 0.5;
    private static final UnmakeStep UNMAKE = new UnmakeStep(Expr.literal(WORK_PER_GOO), Expr.literal(YIELD));

    @Nested
    class Rule {

        @Test
        void workGrowsWithTheCrucibleValue() {
            assertEquals(29, UnmakeRule.workToUnmake(1152, WORK_PER_GOO));
            assertEquals(461, UnmakeRule.workToUnmake(18432, WORK_PER_GOO));
        }

        @Test
        void aNearlyWorthlessBlockStillTakesOneTick() {
            assertEquals(1, UnmakeRule.workToUnmake(1, WORK_PER_GOO));
        }

        @Test
        void theYieldKeepsItsShareOfEachTypeRoundedDown() {
            GooContents kept = UnmakeRule.yieldOf(new GooValue(Map.of(GooTypes.ROCK, 1153, GooTypes.AEON, 1)), YIELD);

            assertEquals(new GooContents(Map.of(GooTypes.ROCK, 576)), kept);
        }
    }

    @Nested
    class Dissolve {

        private static final BlockPos CHEAP = new BlockPos(1, 2, 3);
        private static final BlockPos DEAR = new BlockPos(1, 2, 4);

        private UnmakeHost holding(BlockPos pos, GooValue value, int progress) {
            UnmakeHost host = mock(UnmakeHost.class);
            when(host.unmadeBlocks()).thenReturn(List.of(pos));
            when(host.unmadeValue(pos)).thenReturn(value);
            when(host.countUnmakeWork(pos)).thenReturn(progress);
            return host;
        }

        private void tick(UnmakeHost host) {
            assertTrue(UNMAKE.tick(new StepContext(host, 0, 0)));
        }

        @Test
        void shortOfTheWorkTheDissolveShowsItsShare() {
            UnmakeHost host = holding(CHEAP, COBBLESTONE, 10);

            tick(host);

            verify(host).showUnmaking(CHEAP, 10f / 29);
            verify(host, never()).unmake(any(BlockPos.class), any());
        }

        @Test
        void atTheWorkTheBlockGoesAndLeavesItsYield() {
            UnmakeHost host = holding(CHEAP, COBBLESTONE, 29);

            tick(host);

            verify(host).unmake(CHEAP, new GooContents(Map.of(GooTypes.ROCK, 576)));
        }

        @Test
        void eachHeldBlockIsWorkedOnItsOwn() {
            UnmakeHost host = holding(CHEAP, COBBLESTONE, 29);
            when(host.unmadeBlocks()).thenReturn(List.of(CHEAP, DEAR));
            when(host.unmadeValue(DEAR)).thenReturn(DIAMONDISH);
            when(host.countUnmakeWork(DEAR)).thenReturn(29);

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
    class Mobs {

        private final LivingEntity chicken = mock(LivingEntity.class);

        private UnmakeHost holdingMob(GooValue loot, int progress) {
            UnmakeHost host = mock(UnmakeHost.class);
            when(host.unmadeBlocks()).thenReturn(List.of());
            when(host.unmadeMobs()).thenReturn(List.of(chicken));
            when(host.unmadeValue(chicken)).thenReturn(loot);
            when(host.countUnmakeWork(chicken)).thenReturn(progress);
            return host;
        }

        @Test
        void aMobMeltsOnceItsLootsWorkIsDoneAndLeavesThatGoo() {
            UnmakeHost host = holdingMob(COBBLESTONE, 29);

            assertTrue(UNMAKE.tick(new StepContext(host, 0, 0)));

            verify(host).unmake(chicken, new GooContents(Map.of(GooTypes.ROCK, 576)));
        }

        @Test
        void shortOfTheWorkTheMobShowsItsShare() {
            UnmakeHost host = holdingMob(COBBLESTONE, 10);

            assertTrue(UNMAKE.tick(new StepContext(host, 0, 0)));

            verify(host).showUnmaking(chicken, 10f / 29);
            verify(host, never()).unmake(any(LivingEntity.class), any());
        }

        @Test
        void aMobWhoseLootIsWorthNothingStands() {
            UnmakeHost host = holdingMob(null, 1000);

            assertTrue(UNMAKE.tick(new StepContext(host, 0, 0)));

            verify(host, never()).unmake(any(LivingEntity.class), any());
            verify(host, never()).showUnmaking(any(LivingEntity.class), anyFloat());
        }
    }

    @Nested
    class Hosts {

        @Test
        void theStreamingPlayerAndTheTapHostAnUnmakeAndTheStruckEntityDoesNot() {
            List<Step> unmake = List.of(UNMAKE);

            assertDoesNotThrow(() -> ProgramBehavior.forHost(unmake, HostKind.PLAYER));
            assertDoesNotThrow(() -> ProgramBehavior.forHost(unmake, HostKind.TAP));
            assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(unmake, HostKind.ENTITY));
        }
    }
}
