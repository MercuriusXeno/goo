package com.mercuriusxeno.goo.block.gate;

import com.mercuriusxeno.goo.ability.gate.DragonGates;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One cell of a Dragon Gate: an end portal laid over a block for a while,
 * which carries whatever steps into it to the cell's partner gate, from the
 * world to the End's platform and back, instantly as the End's own portal
 * does. Nothing collides with it, nothing mines it, and it drops nothing;
 * its gate closes back to the block it covered on the gate's clock.
 * Decision dragon-gate-banishes-blocks-and-opens-a-portal.
 */
public class DragonGateBlock extends BaseEntityBlock implements Portal {

    public static final MapCodec<DragonGateBlock> CODEC = simpleCodec(DragonGateBlock::new);

    /**
     * Creates the gate block.
     *
     * @param properties the block's properties
     */
    public DragonGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DragonGateBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                       Entity entity) {
        return Shapes.block();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    /**
     * Carries an entity to the gate partnered with this cell's, standing it
     * beside that gate with its look and motion kept.
     */
    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity,
                                                             BlockPos portalEntryPos) {
        return DragonGates.get(currentLevel).partnerOf(currentLevel.dimension(), portalEntryPos)
                .map(partner -> {
                    ServerLevel target = currentLevel.getServer().getLevel(partner.dimension());
                    return target == null ? null : new TeleportTransition(target, partner.arrival(),
                            entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                            Relative.union(Relative.DELTA, Relative.ROTATION),
                            TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
                })
                .orElse(null);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(),
                pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
