package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.ICanisterClickTaker;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.item.CanisterPlacementResolver.CanisterPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** CanisterPlacementResolver answers where a canister use lands, the answer both the server's placement and the green preview read. */
class CanisterPlacementResolverTest {

    private static final double PIXEL = 1.0 / 16.0;
    private static final BlockPos AIMED = new BlockPos(0, 64, 0);
    private static final BlockPos ABOVE = AIMED.above();
    private static final int SLOT_ZERO = 0;
    private static final int CENTRE_SLOT = 4;

    private Level level;
    private Player player;
    private ItemStack canister;

    @BeforeEach
    void standTheScene() {
        level = mock(Level.class);
        player = mock(Player.class);
        when(player.level()).thenReturn(level);
        canister = heldCanister();
        standBlock(AIMED, Shapes.block(), false);
        standBlock(ABOVE, Shapes.empty(), true);
        when(level.isUnobstructed(any(), any(), any())).thenReturn(true);
    }

    private static ItemStack heldCanister() {
        Block canisterBlock = mock(Block.class);
        when(canisterBlock.defaultBlockState()).thenReturn(mock(BlockState.class));
        BlockItem item = mock(BlockItem.class);
        when(item.getBlock()).thenReturn(canisterBlock);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        return stack;
    }

    private void standBlock(BlockPos pos, VoxelShape supportShape, boolean replaceable) {
        BlockState state = mock(BlockState.class);
        when(state.getBlockSupportShape(level, pos)).thenReturn(supportShape);
        when(state.canBeReplaced(any(BlockPlaceContext.class))).thenReturn(replaceable);
        when(level.getBlockState(pos)).thenReturn(state);
    }

    private BlockHitResult topHitAtSlot(int slot) {
        double x = AIMED.getX() + (slot % 3 * 5 + 3) * PIXEL;
        double z = AIMED.getZ() + (slot / 3 * 5 + 3) * PIXEL;
        return new BlockHitResult(new Vec3(x, AIMED.getY() + 1.0, z), Direction.UP, AIMED, false);
    }

    private CanisterPlacement resolve(BlockHitResult hit, boolean sneaking) {
        return CanisterPlacementResolver.resolve(
                new BlockPlaceContext(player, InteractionHand.MAIN_HAND, canister, hit), sneaking);
    }

    private CanisterBlockEntity canisterBlockAt(BlockPos pos, boolean full) {
        CanisterBlockEntity be = mock(CanisterBlockEntity.class);
        when(be.getBlockPos()).thenReturn(pos);
        when(be.getLevel()).thenReturn(level);
        ItemStack slotContent = mock(ItemStack.class);
        when(slotContent.isEmpty()).thenReturn(!full);
        when(be.getCanister(anyInt())).thenReturn(slotContent);
        when(level.getBlockEntity(pos)).thenReturn(be);
        return be;
    }

    @Nested
    class NewBlock {

        @Test
        void supportedTopPlacesInTheAimedSlot() {
            assertEquals(new CanisterPlacement(ABOVE, SLOT_ZERO, false), resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void supportedTopFollowsTheAimedSlot() {
            assertEquals(new CanisterPlacement(ABOVE, CENTRE_SLOT, false), resolve(topHitAtSlot(CENTRE_SLOT), false));
        }

        @Test
        void signTopRefuses() {
            standBlock(AIMED, Shapes.empty(), false);
            assertNull(resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void crucibleRimRefuses() {
            VoxelShape rim = Shapes.or(Shapes.box(0, 0, 0, 1, 13 * PIXEL, 1),
                    Shapes.box(2 * PIXEL, 13 * PIXEL, 2 * PIXEL, 14 * PIXEL, 1, 4 * PIXEL));
            standBlock(AIMED, rim, false);
            assertNull(resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void occupiedCellRefuses() {
            standBlock(ABOVE, Shapes.empty(), false);
            assertNull(resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void entityInTheCellRefuses() {
            when(level.isUnobstructed(any(), any(), any())).thenReturn(false);
            assertNull(resolve(topHitAtSlot(SLOT_ZERO), false));
        }
    }

    @Nested
    class InsertIntoExisting {

        @Test
        void canisterBlockWhereTheNewBlockWouldGoTakesAnInsert() {
            canisterBlockAt(ABOVE, false);
            assertEquals(new CanisterPlacement(ABOVE, SLOT_ZERO, true), resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void fullCanisterBlockWhereTheNewBlockWouldGoRefuses() {
            canisterBlockAt(ABOVE, true);
            assertNull(resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void sneakingOnACanisterBlockInsertsIntoIt() {
            CanisterBlockEntity aimed = canisterBlockAt(AIMED, false);
            when(aimed.takesCanisterAt(any(), any(Boolean.class))).thenReturn(true);
            assertEquals(new CanisterPlacement(AIMED, SLOT_ZERO, true), resolve(topHitAtSlot(SLOT_ZERO), true));
        }

        @Test
        void standingOnACanisterBlockIsItsOwnPickup() {
            CanisterBlockEntity aimed = canisterBlockAt(AIMED, false);
            when(aimed.takesCanisterAt(any(), any(Boolean.class))).thenReturn(true);
            assertNull(resolve(topHitAtSlot(SLOT_ZERO), false));
        }
    }

    @Nested
    class AimedMachineClick {

        @Test
        void standingOnAHolderThatTakesTheUseRefuses() {
            BlockEntity hub = mock(BlockEntity.class, withSettings().extraInterfaces(ICanisterHolder.class));
            when(((ICanisterHolder) hub).takesCanisterAt(any(), any(Boolean.class))).thenReturn(true);
            when(level.getBlockEntity(AIMED)).thenReturn(hub);
            assertNull(resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void standingOnAVatThatMovesGooRefuses() {
            clickTakerAimed(true);
            assertNull(resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void standingOnAVatThatMovesNoGooPlaces() {
            clickTakerAimed(false);
            assertEquals(new CanisterPlacement(ABOVE, SLOT_ZERO, false), resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void sneakingSkipsTheVatsOwnClick() {
            clickTakerAimed(true);
            assertEquals(new CanisterPlacement(ABOVE, SLOT_ZERO, false), resolve(topHitAtSlot(SLOT_ZERO), true));
        }

        private void clickTakerAimed(boolean takes) {
            BlockEntity vat = mock(BlockEntity.class, withSettings().extraInterfaces(ICanisterClickTaker.class));
            when(((ICanisterClickTaker) vat).takesCanisterClick(canister)).thenReturn(takes);
            when(level.getBlockEntity(AIMED)).thenReturn(vat);
        }
    }

    @Nested
    class BlockWithItsOwnClick {

        @Test
        void standingOnABlockWithItsOwnClickRefuses() {
            aimAtBlockOfClass(ClickableBlock.class);
            assertNull(resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        @Test
        void sneakingOnABlockWithItsOwnClickPlaces() {
            aimAtBlockOfClass(ClickableBlock.class);
            assertEquals(new CanisterPlacement(ABOVE, SLOT_ZERO, false), resolve(topHitAtSlot(SLOT_ZERO), true));
        }

        @Test
        void standingOnAPlainBlockPlaces() {
            aimAtBlockOfClass(Block.class);
            assertEquals(new CanisterPlacement(ABOVE, SLOT_ZERO, false), resolve(topHitAtSlot(SLOT_ZERO), false));
        }

        private void aimAtBlockOfClass(Class<? extends Block> blockClass) {
            Block block = mock(blockClass);
            when(level.getBlockState(AIMED).getBlock()).thenReturn(block);
        }
    }

    /** A block that defines its own right click, as a chest or a door does. */
    abstract static class ClickableBlock extends Block {
        ClickableBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            return InteractionResult.SUCCESS;
        }
    }
}
