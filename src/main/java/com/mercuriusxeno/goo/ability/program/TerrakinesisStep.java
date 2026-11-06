package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Rock's Terrakinesis, run each tick of a held channel on the channeling
 * player, earth-bending: the first tick lifts a hunk out of the ground, the
 * block the look strikes within range and every block joined to it face to
 * face within the radius that carries rock goo value, charged their rock
 * value; every tick after holds the hunk on the look as Grab holds a body,
 * from the far distance with the look level drawn in to the near as it
 * pitches down. A left click while held hucks it at the throw speed
 * (GrabEvents.throwHeld), and the hunk crushes what it strikes.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 *
 * @param range      how far the look reaches for a block to lift, in blocks
 * @param radius     how far from the struck block's center the hunk reaches, in blocks
 * @param near       how far from the eye the hunk's middle hangs with the look pitched fully down
 * @param far        how far from the eye the hunk's middle hangs with the look level or above
 * @param throwSpeed the speed a huck launches the hunk at, in blocks per tick
 */
public record TerrakinesisStep(double range, double radius, double near, double far, double throwSpeed)
        implements Step {

    private static final String NAME = "terrakinesis";
    private static final String FIELD_RANGE = "range";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_NEAR = "near";
    private static final String FIELD_FAR = "far";
    private static final String FIELD_THROW_SPEED = "throw_speed";
    /** The most blocks one hunk carries, whatever the radius. */
    static final int MOST_BLOCKS = 64;
    private static final int DUST_PER_BLOCK = 4;
    private static final double DUST_SPREAD = 0.4;
    private static final double DUST_SPEED = 0.1;

    /** Codec for the step's params. */
    public static final MapCodec<TerrakinesisStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RANGE).forGetter(TerrakinesisStep::range),
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(TerrakinesisStep::radius),
            Codec.DOUBLE.fieldOf(FIELD_NEAR).forGetter(TerrakinesisStep::near),
            Codec.DOUBLE.fieldOf(FIELD_FAR).forGetter(TerrakinesisStep::far),
            Codec.DOUBLE.fieldOf(FIELD_THROW_SPEED).forGetter(TerrakinesisStep::throwSpeed)
    ).apply(inst, TerrakinesisStep::new));

    /** The registered type. */
    public static final StepType<TerrakinesisStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TerrakinesisStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (!(context.hostAs(TargetHost.class).target() instanceof ServerPlayer player)) {
            return true;
        }
        Entity held = GrabEvents.heldBy(player);
        BlockHunk hunk = held instanceof BlockHunk lifted ? lifted : null;
        if (held == null) {
            hunk = lift(player);
        }
        if (hunk != null) {
            GrabEvents.hold(player, hunk, holdPoint(player, hunk), throwSpeed);
        }
        return true;
    }

    /**
     * Where the hunk's position goes this tick: its middle on the look at
     * Grab's hold distance for the look's pitch.
     *
     * @param player the holding player
     * @param hunk   the held hunk
     * @return the hunk's position
     */
    private Vec3 holdPoint(ServerPlayer player, BlockHunk hunk) {
        double distance = GrabStep.holdDistance(player.getXRot(), near, far);
        Vec3 middle = player.getEyePosition().add(player.getLookAngle().scale(distance));
        return middle.subtract(hunk.getBoundingBox().getCenter().subtract(hunk.position()));
    }

    /**
     * Lifts the hunk the look strikes, where the player's rock goo pays for it.
     *
     * @param player the channeling player
     * @return the lifted hunk, or null where the look strikes no rock-valued block or the goo falls short
     */
    private @Nullable BlockHunk lift(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS || !liftable(level, hit.getBlockPos())) {
            return null;
        }
        BlockPos struck = hit.getBlockPos();
        List<BlockPos> cells = hunkCells(struck, radius, cell -> liftable(level, cell));
        if (!pay(player, rockValueOf(level, cells))) {
            return null;
        }
        List<BlockHunk.HunkBlock> blocks = new ArrayList<>(cells.size());
        for (BlockPos cell : cells) {
            BlockState state = level.getBlockState(cell);
            blocks.add(new BlockHunk.HunkBlock(cell.subtract(struck), state));
            Vec3 center = Vec3.atCenterOf(cell);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), center.x, center.y, center.z,
                    DUST_PER_BLOCK, DUST_SPREAD, DUST_SPREAD, DUST_SPREAD, DUST_SPEED);
        }
        cells.forEach(cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        return BlockHunk.lift(level, struck, blocks);
    }

    /**
     * The cells a hunk lifts: the struck block and every block joined to it
     * face to face, through blocks the hunk lifts, within the radius of the
     * struck block's center, nearest the struck block first, up to {@link #MOST_BLOCKS}.
     *
     * @param struck   the block the look strikes
     * @param radius   how far from the struck block's center the hunk reaches
     * @param liftable whether the hunk lifts a cell
     * @return the cells, the struck block first
     */
    static List<BlockPos> hunkCells(BlockPos struck, double radius, Predicate<BlockPos> liftable) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        found.add(struck);
        frontier.add(struck);
        double radiusSqr = radius * radius;
        while (!frontier.isEmpty() && found.size() < MOST_BLOCKS) {
            BlockPos cell = frontier.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = cell.relative(side);
                if (found.size() < MOST_BLOCKS && joins(found, next, struck, radiusSqr, liftable)) {
                    found.add(next);
                    frontier.add(next);
                }
            }
        }
        return List.copyOf(found);
    }

    private static boolean joins(Set<BlockPos> found, BlockPos next, BlockPos struck, double radiusSqr,
                                 Predicate<BlockPos> liftable) {
        return !found.contains(next) && next.distSqr(struck) <= radiusSqr && liftable.test(next);
    }

    /**
     * Whether the hunk lifts a block: one any hand can break, holding no block
     * entity, whose goo value carries rock.
     *
     * @param level the server level
     * @param cell  the block
     * @return true where the hunk lifts it
     */
    private static boolean liftable(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, cell) < 0) {
            return false;
        }
        GooValue value = ValuedBlocks.valueAt(level, cell);
        return value != null && value.exact(GooTypes.ROCK).isPositive();
    }

    private static GooFraction rockValueOf(ServerLevel level, List<BlockPos> cells) {
        GooFraction total = GooFraction.ZERO;
        for (BlockPos cell : cells) {
            GooValue value = ValuedBlocks.valueAt(level, cell);
            if (value != null) {
                total = total.plus(value.exact(GooTypes.ROCK));
            }
        }
        return total;
    }

    /**
     * Charges the hunk's rock value through the player's ledger, the
     * remainder paying first.
     *
     * @param player the lifting player
     * @param cost   the hunk's exact rock value
     * @return true where the player's rock goo paid it
     */
    private static boolean pay(ServerPlayer player, GooFraction cost) {
        int holdings = GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ROCK, 0);
        IRemainderLedger ledger = GooCapabilities.remainderLedgerOf(player);
        OptionalInt charged = ledger.debitExact(GooTypes.ROCK, cost, holdings);
        charged.ifPresent(mb -> GooSourceScanner.deplete(player, GooTypes.ROCK, mb));
        return charged.isPresent();
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL, HostCapability.TARGET);
    }
}
