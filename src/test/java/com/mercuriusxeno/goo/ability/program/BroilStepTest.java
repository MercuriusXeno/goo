package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Broil cooks a block into its furnace result where that result is a block,
 * pulses on the hold's first tick and every few after, and burns the living
 * through fire, setting alight only a hit that kills
 * (decision broil-heat-wave-ring-cooks-ahead).
 */
class BroilStepTest {

    @Nested
    class CookedBlock {

        private final Block sand = mock(Block.class);
        private final Item sandItem = mock(Item.class);
        private final Block empty = mock(Block.class);

        CookedBlock() {
            when(sand.asItem()).thenReturn(sandItem);
        }

        @Test
        void aBlockWhoseSmeltIsABlockCooksIntoIt() {
            Block glass = mock(Block.class);
            BlockItem glassItem = mock(BlockItem.class);
            when(glassItem.getBlock()).thenReturn(glass);

            assertEquals(Optional.of(glass), BroilStep.cookedBlock(sand,
                    item -> item == sandItem ? Optional.of(glassItem) : Optional.empty(), empty));
        }

        // broil-heat-wave-ring-cooks-ahead: a log cooks away, popping its charcoal out
        @Test
        void aBlockWhoseSmeltIsAnItemCooksIntoTheEmptyBlock() {
            Item charcoal = mock(Item.class);

            assertEquals(Optional.of(empty), BroilStep.cookedBlock(sand, item -> Optional.of(charcoal), empty));
        }

        @Test
        void aBlockNoRecipeSmeltsDoesNotCook() {
            assertEquals(Optional.empty(), BroilStep.cookedBlock(sand, item -> Optional.empty(), empty));
        }

        @Test
        void aBlockSmeltingIntoItselfDoesNotCook() {
            BlockItem sameItem = mock(BlockItem.class);
            when(sameItem.getBlock()).thenReturn(sand);

            assertEquals(Optional.empty(), BroilStep.cookedBlock(sand, item -> Optional.of(sameItem), empty));
        }
    }

    @Nested
    class AimLine {

        @Test
        void theLineCrossesEachCellItRunsThroughOnceNearestFirst() {
            List<BlockPos> cells = PaintedHold.cellsAlong(new Vec3(0.5, 0.5, 0.5), new Vec3(3.5, 0.5, 0.5));
            assertEquals(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0),
                    new BlockPos(3, 0, 0)), cells);
        }

        @Test
        void aSlantedLineStepsDownThroughTheCellsBelow() {
            List<BlockPos> cells = PaintedHold.cellsAlong(new Vec3(0.5, 2.5, 0.5), new Vec3(2.5, 0.5, 0.5));
            assertTrue(cells.contains(new BlockPos(1, 1, 0)));
            assertFalse(cells.contains(new BlockPos(2, 2, 0)));
        }

        @Test
        void aLineWithNoLengthHoldsItsOwnCell() {
            Vec3 here = new Vec3(0.5, 0.5, 0.5);
            assertEquals(List.of(new BlockPos(0, 0, 0)), PaintedHold.cellsAlong(here, here));
        }
    }

    @Nested
    class Pulses {

        @Test
        void theFirstTickAndEveryFourthAfterPulse() {
            assertTrue(BroilStep.pulsesOn(1, 4));
            assertFalse(BroilStep.pulsesOn(2, 4));
            assertFalse(BroilStep.pulsesOn(4, 4));
            assertTrue(BroilStep.pulsesOn(5, 4));
            assertTrue(BroilStep.pulsesOn(9, 4));
        }

        @Test
        void aPulseEveryTickPulsesEachTick() {
            assertTrue(BroilStep.pulsesOn(2, 1));
        }
    }

    @Nested
    class Scald {

        private final ServerLevel level = mock(ServerLevel.class);
        private final LivingEntity mob = mock(LivingEntity.class);
        private final DamageSource inFire = mock(DamageSource.class);

        Scald() {
            DamageSources sources = mock(DamageSources.class);
            when(sources.inFire()).thenReturn(inFire);
            when(mob.damageSources()).thenReturn(sources);
        }

        @Test
        void aKillingHitSetsTheMobAlightSoItsLootCooks() {
            when(mob.getHealth()).thenReturn(3f);

            BroilStep.scald(level, mob, 3f);

            verify(mob).setRemainingFireTicks(1);
            verify(mob).hurtServer(level, inFire, 3f);
        }

        @Test
        void aHitTheMobSurvivesBurnsWithoutSettingItAlight() {
            when(mob.getHealth()).thenReturn(10f);

            BroilStep.scald(level, mob, 3f);

            verify(mob, never()).setRemainingFireTicks(anyInt());
            verify(mob).hurtServer(level, inFire, 3f);
        }

        @Test
        void absorptionKeepsAHitFromKilling() {
            when(mob.getHealth()).thenReturn(3f);
            when(mob.getAbsorptionAmount()).thenReturn(2f);

            BroilStep.scald(level, mob, 3f);

            verify(mob, never()).setRemainingFireTicks(anyInt());
        }

        @Test
        void aHitLandsThroughTheMobsDamageImmunity() {
            when(mob.getHealth()).thenReturn(10f);
            mob.invulnerableTime = 10;

            BroilStep.scald(level, mob, 3f);

            assertEquals(0, mob.invulnerableTime);
        }

        @Test
        void aFireImmuneMobIsLeftAlone() {
            when(mob.fireImmune()).thenReturn(true);

            BroilStep.scald(level, mob, 3f);

            verify(mob, never()).hurtServer(any(), any(), anyFloat());
        }
    }
}
