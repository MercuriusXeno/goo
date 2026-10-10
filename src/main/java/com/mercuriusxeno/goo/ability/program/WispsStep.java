package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.block.ability.WispBlock;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.RadiantAuraPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Glow's Radiant: tries a number of air cells within a radius of the host,
 * lifted a number of blocks, and leaves a wisp of light in each whose light
 * level is under the configured threshold, the wisp fading out after its
 * life. The channel tries two cells a tick within eight blocks of the
 * player; the drip tries the one cell above the block below the tap.
 * decisions radiant-wisps-where-light-is-low, radiant-drip-places-a-wisp
 *
 * @param radius how far from the host a cell may be, in blocks; zero for the host's own cell
 * @param count  how many cells to try each run
 * @param life   how many ticks a wisp lasts
 * @param above  how many blocks above the host the cells are centered
 * @param aura   whether a player holding the channel shows the held aura to the clients tracking them
 */
public record WispsStep(double radius, int count, int life, int above, boolean aura) implements Step {

    private static final String NAME = "wisps";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LIFE = "life";
    private static final String FIELD_ABOVE = "above";
    private static final String FIELD_AURA = "aura";
    private static final float CHIME_VOLUME = 0.4f;
    private static final float CHIME_PITCH = 1.6f;
    private static final float CHIME_PITCH_SPREAD = 0.4f;
    private static final double DIAMETER_PER_RADIUS = 2;
    private static final double HALF = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WispsStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(WispsStep::radius),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_COUNT).forGetter(WispsStep::count),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_LIFE).forGetter(WispsStep::life),
            Codec.INT.optionalFieldOf(FIELD_ABOVE, 0).forGetter(WispsStep::above),
            Codec.BOOL.optionalFieldOf(FIELD_AURA, false).forGetter(WispsStep::aura)
    ).apply(inst, WispsStep::new));

    /**
     * The registered type.
     */
    public static final StepType<WispsStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<WispsStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        BlockBreakHost host = context.hostAs(BlockBreakHost.class);
        ServerLevel level = host.level();
        BlockPos center = host.position().above(above);
        int threshold = GooConfig.radiantLightThreshold();
        for (int tried = 0; tried < count; tried++) {
            BlockPos cell = pick(center, level.getRandom());
            if (level.getBlockState(cell).isAir() && isDark(level.getMaxLocalRawBrightness(cell), threshold)) {
                level.setBlock(cell, GooBlocks.WISP.get().defaultBlockState(), Block.UPDATE_ALL);
                level.scheduleTick(cell, GooBlocks.WISP.get(), WispBlock.ticksBeforeFading(life));
                // operator ruling 2026-10-09: each wisp chimes as it appears
                level.playSound(null, cell, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME,
                        CHIME_PITCH + level.getRandom().nextFloat() * CHIME_PITCH_SPREAD);
            }
        }
        if (aura && context.host() instanceof TargetHost holder) {
            EntityVisuals.sendToWatchers(holder.target(), new RadiantAuraPayload(holder.target().getId()));
        }
        return true;
    }

    /**
     * Picks a cell within the radius of a center, uniformly within the ball.
     *
     * @param center the center cell
     * @param random the random source
     * @return the cell
     */
    BlockPos pick(BlockPos center, RandomSource random) {
        if (radius <= 0) {
            return center;
        }
        while (true) {
            double x = (random.nextDouble() - HALF) * radius * DIAMETER_PER_RADIUS;
            double y = (random.nextDouble() - HALF) * radius * DIAMETER_PER_RADIUS;
            double z = (random.nextDouble() - HALF) * radius * DIAMETER_PER_RADIUS;
            if (x * x + y * y + z * z <= radius * radius) {
                return center.offset((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
            }
        }
    }

    /**
     * Whether a light level is dark enough for a wisp.
     *
     * @param light     the cell's light level
     * @param threshold the configured threshold
     * @return true under the threshold
     */
    static boolean isDark(int light, int threshold) {
        return light < threshold;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.BREAK_BLOCKS);
    }
}
