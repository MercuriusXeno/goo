package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.growth.VineGrowth;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Growth's block step, run each held tick of the stream: every plant in the
 * cone that grows on random ticks, a crop, anything bone meal grows, or a
 * plant that climbs or stacks on its own (vines, sugar cane, cactus, kelp
 * and the other growing heads, bamboo, chorus), takes a random tick, a vine
 * growing on {@link VineGrowth}'s rule instead, and now and then throws vanilla's bone-meal sparkle so the
 * breeze shows what it grows.
 * growth-breeze-ticks-plants
 */
public record TickPlantsStep() implements Step {

    private static final String NAME = "tick_plants";
    /** One in this many ticks a plant takes throws a sparkle, so the cone glints without smothering. */
    private static final int SPARKLE_ODDS = 6;
    private static final int SPARKLE_COUNT = 2;
    private static final double SPARKLE_SPREAD = 0.3;
    private static final double PLANT_MIDDLE = 0.5;
    /** The kinds of block Growth grows: crops, what bone meal grows, and what climbs or stacks on its own. */
    private static final List<Class<?>> GROWING_PLANTS = List.of(CropBlock.class, BonemealableBlock.class,
            VineBlock.class, SugarCaneBlock.class, CactusBlock.class, GrowingPlantHeadBlock.class,
            BambooStalkBlock.class, ChorusFlowerBlock.class);

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<TickPlantsStep> CODEC = MapCodec.unit(TickPlantsStep::new);

    /**
     * The registered type.
     */
    public static final StepType<TickPlantsStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TickPlantsStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            ServerLevel level = host.level();
            for (BlockPos pos : CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees())) {
                tickPlant(level, pos, level.getRandom());
            }
        });
        return true;
    }

    /**
     * Grows the block in a cell where it is a plant that grows: a vine
     * grows on Growth's own rule, every other plant takes a random tick.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param random the random source
     */
    private static void tickPlant(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (!grows(state)) {
            return;
        }
        if (state.getBlock() instanceof VineBlock) {
            VineGrowth.grow(level, pos, state, random);
        } else {
            state.randomTick(level, pos, random);
        }
        if (random.nextInt(SPARKLE_ODDS) == 0) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + PLANT_MIDDLE, pos.getY() + PLANT_MIDDLE,
                    pos.getZ() + PLANT_MIDDLE, SPARKLE_COUNT, SPARKLE_SPREAD, SPARKLE_SPREAD, SPARKLE_SPREAD, 0);
        }
    }

    /**
     * Whether a block is a plant Growth grows: one that takes random ticks
     * and is a crop, grows under bone meal, or climbs or stacks on its own.
     *
     * @param state the block
     * @return true for a growing plant
     */
    static boolean grows(BlockState state) {
        // growth-breeze-ticks-plants: nether wart is a fungus, shroom goo's to grow
        return state.isRandomlyTicking() && !(state.getBlock() instanceof NetherWartBlock)
                && GROWING_PLANTS.stream().anyMatch(kind -> kind.isInstance(state.getBlock()));
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
