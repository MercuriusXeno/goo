package com.mercuriusxeno.goo.block.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One cell of the two-tall, door-shaped dimension doorway standing alone in
 * each Outsider room: a sheet running east to west that a player walks into
 * to be carried back to the exact place they cast Outsider from. Nothing
 * breaks it, and the pocket stands it again if anything removes it.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * The sheet draws as the End portal's starfield, through the cell's block
 * entity, as the conjured door does; the block's model draws nothing.
 * decision room-door-wears-the-end-portal-starfield
 */
public class DimensionDoorBlock extends Block implements Portal, EntityBlock {

    /** The sheet: the full width east to west, a quarter block thick at the cell's middle. */
    private static final VoxelShape SHEET = Block.box(0, 0, 6, 16, 16, 10);
    /** How far either side of the sheet's middle a mote rises. */
    private static final double PARTICLE_SPREAD = 0.4;
    private static final double CELL_MIDDLE = 0.5;

    /**
     * @param properties the block's properties
     */
    public DimensionDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHEET;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                       Entity entity) {
        return SHEET;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity instanceof ServerPlayer && entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    /**
     * Each cell holds the block entity its renderer draws as the End portal's starfield.
     * decision room-door-wears-the-end-portal-starfield
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DimensionDoorBlockEntity(pos, state);
    }

    /** Carries a player back where they cast Outsider from; nothing else passes through. */
    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity,
                                                             BlockPos portalEntryPos) {
        return entity instanceof ServerPlayer player ? OutsiderRooms.wayHome(player) : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double across = (random.nextDouble() - CELL_MIDDLE) / CELL_MIDDLE * PARTICLE_SPREAD;
        level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + CELL_MIDDLE + across,
                pos.getY() + random.nextDouble(), pos.getZ() + CELL_MIDDLE, 0.0, 0.0, 0.0);
    }
}
