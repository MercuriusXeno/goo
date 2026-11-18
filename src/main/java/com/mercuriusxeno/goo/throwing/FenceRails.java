package com.mercuriusxeno.goo.throwing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The clip a thrown projectile flies through, which passes the gaps between
 * a fence's rails. No vanilla clip mode holds those gaps: a fence's collider
 * stands solid to a block and a half, and its outline's arms span the whole
 * block's height. So a block in the minecraft:fences tag clips against the
 * shape its model draws, its post and the two rails of each side it joins,
 * built from its connection state, since the server holds no baked models;
 * every other block clips by its collider, fluids passed through.
 * decision knives-and-dart-function-as-projectiles
 */
public final class FenceRails {

    /** The post the fence_post model draws: 4 pixels square, the block's full height. */
    private static final VoxelShape POST = Block.column(4, 0, 16);
    /** The lower and top rails the fence_side model draws on the north side: 2 pixels wide, 6 to 9 and 12 to 15 up. */
    private static final Map<Direction, VoxelShape> RAILS = Shapes.rotateHorizontal(
            Shapes.or(Block.boxZ(2, 6, 9, 0, 8), Block.boxZ(2, 12, 15, 0, 8)));
    private static final Map<BlockState, VoxelShape> DRAWN = new ConcurrentHashMap<>();

    private FenceRails() {
    }

    /**
     * The shape a fence's model draws: its post and the rails of each side it joins.
     *
     * @param fence the fence's state
     * @return the shape, in the block's own space
     */
    public static VoxelShape drawnShapeOf(BlockState fence) {
        return DRAWN.computeIfAbsent(fence, state -> {
            VoxelShape shape = POST;
            for (Map.Entry<Direction, BooleanProperty> side : CrossCollisionBlock.PROPERTY_BY_DIRECTION.entrySet()) {
                if (state.hasProperty(side.getValue()) && state.getValue(side.getValue())) {
                    shape = Shapes.or(shape, RAILS.get(side.getKey()));
                }
            }
            return shape;
        });
    }

    /**
     * The clip a level's blocks make for a thrown projectile: a fence by its
     * post and rails, every other block by its collider.
     *
     * @param level the blocks
     * @return the clip
     */
    public static CoatRay.BlockClip clipping(BlockGetter level) {
        return (from, to) -> Optional.ofNullable(BlockGetter.<CoatRay.@Nullable BlockStrike, BlockGetter>traverseBlocks(
                from, to, level, (blocks, pos) -> strikeIn(blocks, pos, from, to), blocks -> null));
    }

    // Where a line meets one block's shape, null where it passes the block by, so the traversal walks on.
    private static CoatRay.@Nullable BlockStrike strikeIn(BlockGetter level, BlockPos pos, Vec3 from, Vec3 to) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.is(BlockTags.FENCES) ? drawnShapeOf(state)
                : state.getCollisionShape(level, pos, CollisionContext.empty());
        BlockHitResult hit = shape.clip(from, to, pos);
        return hit == null ? null : new CoatRay.BlockStrike(hit.getLocation(), hit.getBlockPos(), hit.getDirection());
    }
}
