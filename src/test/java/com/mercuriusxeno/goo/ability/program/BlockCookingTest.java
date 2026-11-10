package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A block cooks toward its furnace result, and a block whose result is an
 * item finishes through the same finish as one whose result is a block, its
 * item left at rest in the cell (decision blocks-that-cook-into-items-cook-like-blocks).
 */
class BlockCookingTest {

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

            assertEquals(Optional.of(glass), BlockCooking.cookedBlock(sand,
                    item -> item == sandItem ? Optional.of(glassItem) : Optional.empty(), empty));
        }

        @Test
        void aBlockWhoseSmeltIsAnItemCooksIntoTheEmptyBlock() {
            Item charcoal = mock(Item.class);

            assertEquals(Optional.of(empty), BlockCooking.cookedBlock(sand, item -> Optional.of(charcoal), empty));
        }

        @Test
        void aBlockNoRecipeSmeltsDoesNotCook() {
            assertEquals(Optional.empty(), BlockCooking.cookedBlock(sand, item -> Optional.empty(), empty));
        }

        @Test
        void aBlockSmeltingIntoItselfDoesNotCook() {
            BlockItem sameItem = mock(BlockItem.class);
            when(sameItem.getBlock()).thenReturn(sand);

            assertEquals(Optional.empty(), BlockCooking.cookedBlock(sand, item -> Optional.of(sameItem), empty));
        }
    }

    /**
     * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
     */
    @Nested
    class MeltedBlock {

        private final Block ice = mock(Block.class);
        private final Block frostedIce = mock(Block.class);
        private final Block snowLayer = mock(Block.class);
        private final Block water = mock(Block.class);
        private final Block air = mock(Block.class);
        private final BlockCooking.Meltables meltables =
                new BlockCooking.Meltables(ice, frostedIce, snowLayer, water, air);

        @Test
        void iceMeltsToWater() {
            assertEquals(Optional.of(water), BlockCooking.meltedBlock(ice, meltables, false));
        }

        @Test
        void frostedIceMeltsToWater() {
            assertEquals(Optional.of(water), BlockCooking.meltedBlock(frostedIce, meltables, false));
        }

        @Test
        void iceMeltsToAirWhereUltrawarm() {
            assertEquals(Optional.of(air), BlockCooking.meltedBlock(ice, meltables, true));
            assertEquals(Optional.of(air), BlockCooking.meltedBlock(frostedIce, meltables, true));
        }

        @Test
        void snowLayerMeltsToAir() {
            assertEquals(Optional.of(air), BlockCooking.meltedBlock(snowLayer, meltables, false));
        }

        @Test
        void aBlockThatNeitherMeltsNorSmeltsIsLeft() {
            Block stone = mock(Block.class);
            when(stone.asItem()).thenReturn(mock(Item.class));

            assertEquals(Optional.empty(), BlockCooking.meltedBlock(stone, meltables, false));
            assertEquals(Optional.empty(), FlamesStep.heatedInto(meltables, false, item -> Optional.empty(), air)
                    .apply(stone));
        }
    }

    @Nested
    class FinishCooked {

        private final BlockBreakHost host = mock(BlockBreakHost.class);
        private final BlockPos pos = new BlockPos(3, 1, 2);
        private final BlockState emptied = mock(BlockState.class);
        private final ItemStack result = mock(ItemStack.class);

        @Test
        void anItemResultEmptiesTheCellAndRestsTheItemAtItsCenter() {
            when(result.getItem()).thenReturn(mock(Item.class));

            BlockCooking.finishCooked(host, pos, result, emptied);

            verify(host).transformBlock(pos, emptied);
            verify(host).restItem(Vec3.atCenterOf(pos), result);
        }

        @Test
        void aBlockResultBecomesThatBlockAndRestsNoItem() {
            BlockItem glassItem = mock(BlockItem.class);
            Block glass = mock(Block.class);
            BlockState glassState = mock(BlockState.class);
            when(glassItem.getBlock()).thenReturn(glass);
            when(glass.defaultBlockState()).thenReturn(glassState);
            when(result.getItem()).thenReturn(glassItem);

            BlockCooking.finishCooked(host, pos, result, emptied);

            verify(host).transformBlock(pos, glassState);
            verify(host, never()).restItem(any(), any());
        }
    }
}
