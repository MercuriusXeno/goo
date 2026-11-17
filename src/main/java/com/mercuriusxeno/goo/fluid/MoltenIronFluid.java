package com.mercuriusxeno.goo.fluid;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import org.jspecify.annotations.NonNull;
import java.util.function.Supplier;

/**
 * Molten iron, lunar's core liquid. It spreads as lava does, slowly and a short way; water cannot wash it away but
 * cools it (FluidInteractionRegistry: a source to iron core, flowing molten iron to anorthosite), and molten iron
 * falling onto water cools that water to anorthosite. It burns hotter than lava: what stands in it takes
 * {@link #SCALD_DAMAGE} a hit to lava's 4 and burns {@link #FIRE_SECONDS} seconds to lava's 15, dropped items burn
 * away, and it lights flammable blocks near it as lava does. Its hit is a damage type of its own, so a death in it
 * reads its own message.
 * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
 * decision molten-iron-death-reads-wanted-a-cast-of-molten-metal
 */
public abstract class MoltenIronFluid extends BaseFlowingFluid {

    /**
     * Molten iron's own damage type, fire as lava's is, so its death message is its own.
     * molten-iron-death-reads-wanted-a-cast-of-molten-metal
     */
    public static final ResourceKey<DamageType> MOLTEN_IRON_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Goo.MODID, "molten_iron"));
    /** The damage a hit of molten iron deals, past lava's 4. */
    static final float SCALD_DAMAGE = 6.0F;
    /** The seconds molten iron sets a thing alight for, past lava's 15. */
    static final float FIRE_SECONDS = 20.0F;
    /** The level event vanilla plays as lava fizzes against water. */
    private static final int FIZZ_EVENT = 1501;
    /** Lava's fire spread: up to two hops upward, or three tries beside it. */
    private static final int FIRE_PASSES = 3;
    private static final int SPREAD_REACH = 3;
    private static final int SPREAD_OFFSET = 1;
    /** A source block's amount, a full block. */
    private static final int SOURCE_AMOUNT = 8;
    /** The fullest flowing level, one step from a source. */
    private static final int FULLEST_FLOW = 7;

    /** The block water cooled by molten iron falling onto it becomes. */
    private final Supplier<BlockState> cooledFlow;

    /**
     * @param properties the shared molten iron properties
     * @param cooledFlow the block water becomes where molten iron falls onto it
     */
    protected MoltenIronFluid(Properties properties, Supplier<BlockState> cooledFlow) {
        super(properties);
        this.cooledFlow = cooledFlow;
    }

    @Override
    protected void entityInside(@NonNull Level level, @NonNull BlockPos pos, @NonNull Entity entity,
                                @NonNull InsideBlockEffectApplier effectApplier) {
        effectApplier.apply(InsideBlockEffectType.CLEAR_FREEZE);
        effectApplier.apply(InsideBlockEffectType.LAVA_IGNITE);
        effectApplier.runAfter(InsideBlockEffectType.LAVA_IGNITE, MoltenIronFluid::scald);
    }

    /**
     * Burns a thing standing in molten iron: a longer fire than lava's and a harder hit, through molten iron's own
     * damage, which names no entity since molten iron is environmental and credits nobody.
     * molten-iron-death-reads-wanted-a-cast-of-molten-metal
     *
     * @param entity the thing standing in it
     */
    static void scald(Entity entity) {
        if (entity.fireImmune() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        entity.igniteForSeconds(FIRE_SECONDS);
        DamageSource moltenIron = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(MOLTEN_IRON_DAMAGE));
        entity.hurtServer(level, moltenIron, SCALD_DAMAGE);
    }

    /**
     * Water cannot flow into molten iron and wash it away; meeting it cools the molten iron instead.
     */
    @Override
    protected boolean canBeReplacedWith(@NonNull FluidState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                                        @NonNull Fluid other, @NonNull Direction direction) {
        return false;
    }

    /**
     * Molten iron falling onto water cools the water to anorthosite, as lava cools it to stone.
     */
    @Override
    protected void spreadTo(@NonNull LevelAccessor level, @NonNull BlockPos pos, @NonNull BlockState state,
                            @NonNull Direction direction, @NonNull FluidState target) {
        if (direction == Direction.DOWN && level.getFluidState(pos).is(FluidTags.WATER)) {
            if (state.getBlock() instanceof LiquidBlock) {
                level.setBlock(pos, EventHooks.fireFluidPlaceBlockEvent(level, pos, pos, cooledFlow.get()),
                        Block.UPDATE_ALL);
            }
            level.levelEvent(FIZZ_EVENT, pos, 0);
            return;
        }
        super.spreadTo(level, pos, state, direction, target);
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    /**
     * Lights flammable blocks near the molten iron as lava does.
     */
    @Override
    public void randomTick(@NonNull ServerLevel level, @NonNull BlockPos pos, @NonNull FluidState fluidState,
                           @NonNull RandomSource random) {
        if (!level.canSpreadFireAround(pos)) {
            return;
        }
        int passes = random.nextInt(FIRE_PASSES);
        if (passes > 0) {
            lightAbove(level, pos, random, passes);
        } else {
            lightBeside(level, pos, random);
        }
    }

    private static void lightAbove(ServerLevel level, BlockPos pos, RandomSource random, int passes) {
        BlockPos test = pos;
        for (int pass = 0; pass < passes; pass++) {
            test = test.offset(random.nextInt(SPREAD_REACH) - SPREAD_OFFSET, 1, random.nextInt(SPREAD_REACH) - SPREAD_OFFSET);
            if (!level.isLoaded(test)) {
                return;
            }
            BlockState found = level.getBlockState(test);
            if (found.isAir()) {
                if (hasFlammableNeighbour(level, test)) {
                    level.setBlockAndUpdate(test, EventHooks.fireFluidPlaceBlockEvent(level, test, pos,
                            BaseFireBlock.getState(level, test)));
                    return;
                }
            } else if (!found.getCollisionShape(level, test).isEmpty()) {
                return;
            }
        }
    }

    private static void lightBeside(ServerLevel level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < FIRE_PASSES; i++) {
            BlockPos test = pos.offset(random.nextInt(SPREAD_REACH) - SPREAD_OFFSET, 0,
                    random.nextInt(SPREAD_REACH) - SPREAD_OFFSET);
            if (!level.isLoaded(test)) {
                return;
            }
            if (level.isEmptyBlock(test.above()) && isFlammable(level, test, Direction.UP)) {
                level.setBlockAndUpdate(test.above(), EventHooks.fireFluidPlaceBlockEvent(level, test.above(), pos,
                        BaseFireBlock.getState(level, test)));
            }
        }
    }

    private static boolean hasFlammableNeighbour(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isFlammable(level, pos.relative(direction), direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFlammable(ServerLevel level, BlockPos pos, Direction face) {
        if (level.isInsideBuildHeight(pos.getY()) && !level.isLoaded(pos)) {
            return false;
        }
        return level.getBlockState(pos).ignitedByLava(level, pos, face);
    }

    /**
     * A molten iron source block.
     */
    public static class Source extends MoltenIronFluid {

        /**
         * @param properties the shared molten iron properties
         * @param cooledFlow the block water becomes where molten iron falls onto it
         */
        public Source(Properties properties, Supplier<BlockState> cooledFlow) {
            super(properties, cooledFlow);
        }

        @Override
        public int getAmount(@NonNull FluidState state) {
            return SOURCE_AMOUNT;
        }

        @Override
        public boolean isSource(@NonNull FluidState state) {
            return true;
        }
    }

    /**
     * Molten iron flowing out from a source, one to seven levels.
     */
    public static class Flowing extends MoltenIronFluid {

        /**
         * @param properties the shared molten iron properties
         * @param cooledFlow the block water becomes where molten iron falls onto it
         */
        public Flowing(Properties properties, Supplier<BlockState> cooledFlow) {
            super(properties, cooledFlow);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, FULLEST_FLOW));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.@NonNull Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(@NonNull FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(@NonNull FluidState state) {
            return false;
        }
    }
}
