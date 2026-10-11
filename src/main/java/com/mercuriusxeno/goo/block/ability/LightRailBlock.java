package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/**
 * One cell of the light rail between two linked reflector prisms: replaceable
 * so it counts as air for placement, shapeless and without collision, lit at
 * the level its network carries, and burning the undead that cross it with
 * a Sunbeam's radiant hit. The prisms' renderer draws the beam; the rail
 * itself draws nothing.
 * decision reflector-rails-carry-the-brightest-light
 */
public class LightRailBlock extends Block {

    /** The brightest a rail carries: the max light. */
    public static final int BRIGHTEST = 15;
    /** The light the rail carries, its network's brightest. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, BRIGHTEST);
    /** A Sunbeam's direct hit on the undead: 4 doubled (decision sunbeam-splits-at-the-prism-with-a-glisten). */
    public static final float RADIANT_DAMAGE = 8f;
    /** Seconds an undead crossing a rail burns. */
    private static final int BURN_SECONDS = 1;

    /**
     * Creates the light rail block.
     *
     * @param properties the block properties
     */
    public LightRailBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, BRIGHTEST));
    }

    /**
     * The light a rail gives: its network's level.
     *
     * @param state the block state
     * @return the light emission level
     */
    public static int lightLevel(BlockState state) {
        return state.getValue(LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * Burns an undead crossing the rail with a Sunbeam's radiant hit, at most
     * once per its own hurt immunity.
     *
     * @param state   the rail's state
     * @param level   the level
     * @param pos     the rail's position
     * @param entity  the entity inside
     * @param effects the inside-block effect applier
     * @param pastEdges whether the entity crossed the block's edges this move
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living
                && living.isInvertedHealAndHarm() && living.invulnerableTime == 0) {
            living.hurtServer(server, living.damageSources().magic(), RADIANT_DAMAGE);
            living.igniteForSeconds(BURN_SECONDS);
        }
    }
}
