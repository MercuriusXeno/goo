package com.mercuriusxeno.goo.block.gate;

import com.mercuriusxeno.goo.ability.gate.EndGates;
import com.mercuriusxeno.goo.ability.gate.GateSquare;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One cell of an End gate: a thin layer in the open cell in front of a
 * struck face, holding its share of the gate's two by two starfield square,
 * which is centred on the struck block and overlaps each of its eight
 * neighbours by half a block. The face it lies against never changes.
 * Whatever touches the starfield is carried to the gate's partner, from the
 * world to the End's platform and back, instantly as the End's own portal
 * does. Nothing collides with it, nothing mines it, and it drops nothing;
 * its gate closes on the gate's clock.
 * Decision end-clears-blocks-and-opens-a-portal.
 */
public class EndGateBlock extends BaseEntityBlock implements Portal {

    public static final MapCodec<EndGateBlock> CODEC = simpleCodec(EndGateBlock::new);

    /** The face the gate looks out of; the cell lies against the struck face behind it. */
    public static final EnumProperty<Direction> FACING = EnumProperty.create("facing", Direction.class);
    /** The cell's place across the gate on the plane's first axis: 0, 1 or 2, the middle 1. */
    public static final IntegerProperty ACROSS = IntegerProperty.create("across", 0, 2);
    /** The cell's place across the gate on the plane's second axis: 0, 1 or 2, the middle 1. */
    public static final IntegerProperty ALONG = IntegerProperty.create("along", 0, 2);

    /**
     * Creates the gate block.
     *
     * @param properties the block's properties
     */
    public EndGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ACROSS, 1)
                .setValue(ALONG, 1));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACROSS, ALONG);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EndGateBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.create(GateSquare.cellLayer(state.getValue(FACING), state.getValue(ACROSS),
                state.getValue(ALONG)));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                       Entity entity) {
        return state.getShape(level, pos);
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
        return EndGates.get(currentLevel).partnerOf(currentLevel.dimension(), portalEntryPos)
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
        Vec3 spot = GateSquare.cellLayer(state.getValue(FACING), state.getValue(ACROSS), state.getValue(ALONG))
                .move(pos).getCenter();
        level.addParticle(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y, spot.z, 0.0, 0.0, 0.0);
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
