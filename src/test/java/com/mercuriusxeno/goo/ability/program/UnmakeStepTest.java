package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An unmake dissolves a block after work proportional to its crucible value
 * and leaves a share of that value; the stream's block pass carries it apart
 * from the entity program (decision unmake-waves-dissolve-by-crucible-cost).
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

        private UnmakeHost holding(GooValue value, int progress) {
            UnmakeHost host = mock(UnmakeHost.class);
            when(host.unmadeValue()).thenReturn(value);
            when(host.unmakeProgress()).thenReturn(progress);
            return host;
        }

        private void tick(UnmakeHost host) {
            assertTrue(UNMAKE.tick(new StepContext(host, 0, 0)));
        }

        @Test
        void shortOfTheWorkTheDissolveShowsItsShare() {
            UnmakeHost host = holding(COBBLESTONE, 10);

            tick(host);

            verify(host).showUnmaking(10f / 29);
            verify(host, never()).unmake(any());
        }

        @Test
        void atTheWorkTheBlockGoesAndLeavesItsYield() {
            UnmakeHost host = holding(COBBLESTONE, 29);

            tick(host);

            verify(host).unmake(new GooContents(Map.of(GooTypes.ROCK, 576)));
        }

        @Test
        void theDearerBlockStandsWhereTheCheaperGoes() {
            UnmakeHost dear = holding(DIAMONDISH, 29);

            tick(dear);

            verify(dear, never()).unmake(any());
        }

        @Test
        void anUnvaluedBlockStands() {
            UnmakeHost host = holding(null, 1000);

            tick(host);

            verify(host, never()).unmake(any());
            verify(host, never()).showUnmaking(anyFloat());
        }
    }

    @Nested
    class BlockPass {

        private final DamageStep damage = new DamageStep(Expr.literal(1), DamageKind.MAGIC);
        private final List<Step> program =
                List.of(damage, new BlocksStep(List.of(UNMAKE)));

        @Test
        void thePassHoldsTheBlockStepsAndTheEntityProgramTheRest() {
            assertEquals(List.of(UNMAKE), BlocksStep.passOf(program));
            assertEquals(List.of(damage), BlocksStep.withoutPass(program));
        }

        @Test
        void theStreamedBlockHostsAnUnmakeAndTheEntityHostDoesNot() {
            assertDoesNotThrow(() -> ProgramBehavior.forHost(List.of(UNMAKE), HostKind.STREAMED_BLOCK));
            assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(List.of(UNMAKE), HostKind.ENTITY));
            assertDoesNotThrow(() -> ProgramBehavior.forHost(program, HostKind.ENTITY));
        }
    }
}
