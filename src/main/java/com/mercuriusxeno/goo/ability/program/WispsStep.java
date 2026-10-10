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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Glow's Radiant: tries a number of air cells within a zone around the host
 * and leaves a wisp of light in each whose light level is under the
 * configured threshold, the wisp fading out after its life. The channel's
 * zone centers on the player's eyes and grows outward each tick it is held,
 * half its tries landing near the zone's growing edge, and takes only cells
 * the player's eyes see, so holding it sweeps light out through a cave; the
 * drip tries the one cell above the block below the tap.
 * decisions radiant-wisps-where-light-is-low, radiant-drip-places-a-wisp
 * operator ruling 2026-10-09: the zone expands while channeled and fills only air in sight
 *
 * @param radius how far from the host a cell may be, in blocks; zero for the host's own cell
 * @param count  how many cells to try each run
 * @param life   how many ticks a wisp lasts
 * @param above  how many blocks above the host the cells are centered, when the zone is not on the eyes
 * @param aura   whether a player holding the channel shows the held aura to the clients tracking them
 * @param growth how many blocks the zone grows each held tick, up to the radius; zero holds it at the radius
 * @param sight  whether the zone centers on the holder's eyes and takes only the cells they see
 */
public record WispsStep(double radius, int count, int life, int above, boolean aura, double growth,
                        boolean sight) implements Step {

    private static final String NAME = "wisps";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LIFE = "life";
    private static final String FIELD_ABOVE = "above";
    private static final String FIELD_AURA = "aura";
    private static final String FIELD_GROWTH = "growth";
    private static final String FIELD_SIGHT = "sight";
    private static final float CHIME_VOLUME = 0.4f;
    private static final float CHIME_PITCH = 1.6f;
    private static final float CHIME_PITCH_SPREAD = 0.4f;
    private static final double DIAMETER_PER_RADIUS = 2;
    private static final double HALF = 0.5;
    /** The zone's radius on the hold's first tick, before it grows. */
    static final double ZONE_START = 1;
    /** How deep the band at the zone's edge is, where half the tries land. */
    static final double EDGE_BAND = 3;
    /** Tries alternate between the whole zone and its edge. */
    private static final int TRIES_PER_ALTERNATION = 2;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WispsStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(WispsStep::radius),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_COUNT).forGetter(WispsStep::count),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_LIFE).forGetter(WispsStep::life),
            Codec.INT.optionalFieldOf(FIELD_ABOVE, 0).forGetter(WispsStep::above),
            Codec.BOOL.optionalFieldOf(FIELD_AURA, false).forGetter(WispsStep::aura),
            Codec.DOUBLE.optionalFieldOf(FIELD_GROWTH, 0.0).forGetter(WispsStep::growth),
            Codec.BOOL.optionalFieldOf(FIELD_SIGHT, false).forGetter(WispsStep::sight)
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
        LivingEntity viewer = viewerOf(context);
        BlockPos center = viewer == null ? host.position().above(above) : BlockPos.containing(viewer.getEyePosition());
        double reach = zoneRadius(heldTicks(context));
        int threshold = GooConfig.radiantLightThreshold();
        for (int tried = 0; tried < count; tried++) {
            BlockPos cell = pickForTry(tried, center, reach, level.getRandom());
            if (takesAWisp(level, cell, threshold) && seenBy(level, viewer, cell)) {
                placeWisp(level, cell);
            }
        }
        if (aura && context.host() instanceof TargetHost holder) {
            EntityVisuals.sendToWatchers(holder.target(), new RadiantAuraPayload(holder.target().getId()));
        }
        return true;
    }

    private @Nullable LivingEntity viewerOf(StepContext context) {
        return sight && context.host() instanceof TargetHost holder ? holder.target() : null;
    }

    /** Even tries pick anywhere in the zone, odd tries near its growing edge. */
    private static BlockPos pickForTry(int tried, BlockPos center, double reach, RandomSource random) {
        return tried % TRIES_PER_ALTERNATION == 0 ? pick(center, reach, random)
                : pickNearTheEdge(center, reach, random);
    }

    private static boolean seenBy(ServerLevel level, @Nullable LivingEntity viewer, BlockPos cell) {
        return viewer == null || inSight(level, viewer, cell);
    }

    private static int heldTicks(StepContext context) {
        return context.host() instanceof ChannelHost channel
                ? channel.channelAim().map(ChannelAim::heldTicks).orElse(ChannelAim.FIRST_TICK)
                : ChannelAim.FIRST_TICK;
    }

    private static boolean takesAWisp(ServerLevel level, BlockPos cell, int threshold) {
        return level.getBlockState(cell).isAir() && isDark(level.getMaxLocalRawBrightness(cell), threshold);
    }

    private void placeWisp(ServerLevel level, BlockPos cell) {
        level.setBlock(cell, GooBlocks.WISP.get().defaultBlockState(), Block.UPDATE_ALL);
        level.scheduleTick(cell, GooBlocks.WISP.get(), WispBlock.ticksBeforeFading(life));
        // operator ruling 2026-10-09: each wisp chimes as it appears
        level.playSound(null, cell, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME,
                CHIME_PITCH + level.getRandom().nextFloat() * CHIME_PITCH_SPREAD);
    }

    /**
     * Whether the viewer's eyes see a cell's center, no block's collision between.
     *
     * @param level  the level
     * @param viewer the entity looking
     * @param cell   the cell
     * @return true when the line is clear
     */
    static boolean inSight(ServerLevel level, LivingEntity viewer, BlockPos cell) {
        return level.clip(new ClipContext(viewer.getEyePosition(), Vec3.atCenterOf(cell), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
    }

    /**
     * The zone's radius on a tick of the hold: from its start, grown each
     * held tick, never past the radius; with no growth, the radius.
     *
     * @param heldTicks the hold's age, 1 on its first tick
     * @return the zone's radius in blocks
     */
    double zoneRadius(int heldTicks) {
        if (growth <= 0) {
            return radius;
        }
        return Math.min(radius, ZONE_START + growth * (heldTicks - 1));
    }

    /**
     * Picks a cell within a reach of a center, uniformly within the ball.
     *
     * @param center the center cell
     * @param reach  the ball's radius
     * @param random the random source
     * @return the cell
     */
    static BlockPos pick(BlockPos center, double reach, RandomSource random) {
        if (reach <= 0) {
            return center;
        }
        while (true) {
            double x = (random.nextDouble() - HALF) * reach * DIAMETER_PER_RADIUS;
            double y = (random.nextDouble() - HALF) * reach * DIAMETER_PER_RADIUS;
            double z = (random.nextDouble() - HALF) * reach * DIAMETER_PER_RADIUS;
            if (x * x + y * y + z * z <= reach * reach) {
                return center.offset((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
            }
        }
    }

    /**
     * Picks a cell in the band at a ball's edge: any direction, at a distance
     * within the edge band inside the reach.
     *
     * @param center the center cell
     * @param reach  the ball's radius
     * @param random the random source
     * @return the cell
     */
    static BlockPos pickNearTheEdge(BlockPos center, double reach, RandomSource random) {
        if (reach <= 0) {
            return center;
        }
        Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
        double distance = reach - random.nextDouble() * Math.min(reach, EDGE_BAND);
        Vec3 offset = direction.scale(distance);
        return center.offset((int) Math.round(offset.x), (int) Math.round(offset.y), (int) Math.round(offset.z));
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
