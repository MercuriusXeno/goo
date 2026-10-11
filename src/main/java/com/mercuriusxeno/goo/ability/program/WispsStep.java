package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.block.ability.WispBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Glow's Radiant: leaves wisps of light in dark air, each fading out after
 * its life. The channel floods ({@link WispFlood}): from the holder's eyes
 * out through the connected air they see, nearest first, a budget of cells
 * a tick, a wisp in each dark cell no wisp already lights, the flood
 * starting over when the hold starts, and carried on from where the holder
 * moves to, its reach and its wisps kept, as they walk. The drip
 * tries the one cell above the block below the tap, a wisp there when it
 * is dark air.
 * decisions radiant-wisps-where-light-is-low, radiant-drip-places-a-wisp
 * operator rulings 2026-10-10: wisps flood out from the eyes nearest first to 64 blocks, the edge at 16 blocks a second
 *
 * @param radius how far from the host a cell may be, in blocks; zero for the host's own cell
 * @param count  how many cells to walk each run
 * @param life   how many ticks a wisp lasts
 * @param above  how many blocks above the host the drip's cell sits
 * @param flood  whether the step floods out from the holder's eyes; otherwise it tries the host's own cell
 * @param growth how many blocks the flood's edge grows each tick; zero reaches the radius at once
 */
public record WispsStep(double radius, int count, int life, int above, boolean flood, double growth)
        implements Step {

    private static final String NAME = "wisps";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LIFE = "life";
    private static final String FIELD_ABOVE = "above";
    private static final String FIELD_FLOOD = "flood";
    private static final String FIELD_GROWTH = "growth";
    private static final float CHIME_VOLUME = 0.4f;
    private static final float CHIME_PITCH = 1.6f;
    private static final float CHIME_PITCH_SPREAD = 0.4f;
    /** The most wisps that chime in one tick, so a flood placing many rings a few. */
    private static final int CHIMES_PER_TICK = 3;
    /** How far the holder's eyes may move from a flood's origin, in blocks, before it starts over. */
    static final int RESTART_STEPS = 2;
    /** Ticks a flood stays remembered after it last walked, before a later flood drops it. */
    static final int FORGET_AFTER_TICKS = 20;
    /** Each holder's flood under way. */
    private static final Map<UUID, WispFlood> FLOODS = new ConcurrentHashMap<>();

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WispsStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(WispsStep::radius),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_COUNT).forGetter(WispsStep::count),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_LIFE).forGetter(WispsStep::life),
            Codec.INT.optionalFieldOf(FIELD_ABOVE, 0).forGetter(WispsStep::above),
            Codec.BOOL.optionalFieldOf(FIELD_FLOOD, false).forGetter(WispsStep::flood),
            Codec.DOUBLE.optionalFieldOf(FIELD_GROWTH, 0.0).forGetter(WispsStep::growth)
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
        if (flood && context.host() instanceof TargetHost holder) {
            floodFrom(level, holder.target(), heldTicks(context));
        } else {
            BlockPos cell = host.position().above(above);
            if (takesAWisp(level, cell)) {
                placeWisp(level, cell, true);
            }
        }
        return true;
    }

    private void floodFrom(ServerLevel level, LivingEntity holder, int heldTicks) {
        BlockPos eyes = BlockPos.containing(holder.getEyePosition());
        WispFlood under = FLOODS.get(holder.getUUID());
        if (startsOver(under, eyes, heldTicks)) {
            long now = level.getGameTime();
            FLOODS.values().removeIf(stale -> now - stale.lastWalked() > FORGET_AFTER_TICKS);
            // a wisp's light keeps a cell lit until it drops to the threshold, one level a step
            under = floodFor(under, eyes, heldTicks, WispBlock.LIGHT - GooConfig.radiantLightThreshold());
            FLOODS.put(holder.getUUID(), under);
        }
        under.walkedAt(level.getGameTime());
        under.walk(count, new LevelCells(level, holder));
    }

    /**
     * Whether a holder's flood starts over: none under way, the hold just
     * begun, or the eyes moved on from where it started.
     *
     * @param under     the flood under way, or null
     * @param eyes      the holder's eye cell
     * @param heldTicks the hold's age, 1 on its first tick
     * @return true to start a new flood
     */
    static boolean startsOver(@Nullable WispFlood under, BlockPos eyes, int heldTicks) {
        return under == null || heldTicks == ChannelAim.FIRST_TICK
                || under.origin().distManhattan(eyes) > RESTART_STEPS;
    }

    /**
     * The flood a holder's eyes start: a fresh one as the hold begins or
     * where none is under way, otherwise one from where the eyes moved to.
     *
     * @param under     the flood under way, or null
     * @param eyes      the holder's eye cell
     * @param heldTicks the hold's age, 1 on its first tick
     * @param litSteps  how many steps out a placed wisp's light keeps a cell lit
     * @return the flood to walk
     */
    WispFlood floodFor(@Nullable WispFlood under, BlockPos eyes, int heldTicks, int litSteps) {
        return under == null || heldTicks == ChannelAim.FIRST_TICK ? new WispFlood(eyes, radius, growth, litSteps)
                : under.movedTo(eyes);
    }

    private static int heldTicks(StepContext context) {
        return context.host() instanceof ChannelHost channel
                ? channel.channelAim().map(ChannelAim::held).orElse(ChannelAim.FIRST_TICK)
                : ChannelAim.FIRST_TICK;
    }

    private static boolean takesAWisp(ServerLevel level, BlockPos cell) {
        return level.getBlockState(cell).isAir()
                && isDark(level.getMaxLocalRawBrightness(cell), GooConfig.radiantLightThreshold());
    }

    private void placeWisp(ServerLevel level, BlockPos cell, boolean chimes) {
        level.setBlock(cell, GooBlocks.WISP.get().defaultBlockState(), Block.UPDATE_ALL);
        level.scheduleTick(cell, GooBlocks.WISP.get(), WispBlock.ticksBeforeFading(life));
        if (chimes) {
            // operator ruling 2026-10-09: each wisp chimes as it appears
            RandomSource random = level.getRandom();
            level.playSound(null, cell, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME,
                    CHIME_PITCH + random.nextFloat() * CHIME_PITCH_SPREAD);
        }
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

    /** The level's cells as a flood asks them, from one holder's eyes. */
    private final class LevelCells implements WispFlood.Cells {

        private final ServerLevel level;
        private final LivingEntity holder;
        private int chimed;

        LevelCells(ServerLevel level, LivingEntity holder) {
            this.level = level;
            this.holder = holder;
        }

        @Override
        public boolean open(BlockPos cell) {
            if (!level.isLoaded(cell)) {
                return false;
            }
            BlockState state = level.getBlockState(cell);
            return state.isAir() || state.is(GooBlocks.WISP.get());
        }

        @Override
        public boolean dark(BlockPos cell) {
            return takesAWisp(level, cell);
        }

        @Override
        public boolean seen(BlockPos cell) {
            return inSight(level, holder, cell);
        }

        @Override
        public void place(BlockPos cell) {
            placeWisp(level, cell, chimed++ < CHIMES_PER_TICK);
        }
    }
}
