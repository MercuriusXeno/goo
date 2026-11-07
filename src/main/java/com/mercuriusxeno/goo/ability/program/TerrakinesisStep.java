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
import net.minecraft.resources.Identifier;
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
import java.util.HashSet;
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
 * The hunk tears free with a low stone-break at the hunk and a burst of its
 * own dust off each seam, the faces where it parted from the ground it left.
 * decision terrakinesis-tears-free-with-a-stone-tear-and-seam-dust
 *
 * Before the tear the hold strains the ground for {@link #WINDUP_TICKS},
 * rumbling ever higher at the struck block while the client shakes the cells
 * it would lift; a release before then lifts nothing and charges nothing.
 * decision terrakinesis-shakes-the-blocks-before-the-tear
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
    /** The ticks a hold strains the ground before the hunk tears free: half a second. */
    public static final int WINDUP_TICKS = 10;
    /** Ticks between the wind-up's rumbles. */
    static final int RUMBLE_EVERY = 3;
    /** The first rumble's pitch, each one after rising by {@link #RUMBLE_RISE}. */
    private static final float RUMBLE_LOW = 0.5f;
    private static final float RUMBLE_RISE = 0.15f;
    private static final Identifier RUMBLE = Identifier.withDefaultNamespace("block.gravel.break");
    /** The most blocks one hunk carries, whatever the radius. */
    static final int MOST_BLOCKS = 64;
    /** Chips each seam face throws as the hunk tears free, dense enough to read as torn earth. */
    static final int CHIPS_PER_SEAM = 10;
    /** How far across a seam face its chips scatter from the face's middle, in blocks. */
    private static final double CHIP_SCATTER = 0.45;
    /** How far out from a seam face its chips start, so none starts inside a block. */
    private static final double CHIP_LIFT = 0.55;
    /** How fast a chip leaves its seam face, in blocks a tick. */
    private static final double CHIP_SPEED = 0.18;
    private static final double HALF = 0.5;
    /** A scatter reaches both ways from the face's middle. */
    private static final double BOTH_WAYS = 2;
    /** The tear: a deepslate break pitched an octave down, at full volume. */
    public static final SoundCue TEAR = new SoundCue(Identifier.withDefaultNamespace("block.deepslate.break"),
            SoundKind.BLOCKS, 1f, 0.5f);

    /**
     * One face where a lifted cell parted from a block still standing in the world.
     *
     * @param cell the lifted cell
     * @param face the side of it the standing block met
     */
    record Seam(BlockPos cell, Direction face) {
    }

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
            int holdTicks = context.hostAs(ChannelHost.class).channelAim().map(ChannelAim::held).orElse(WINDUP_TICKS);
            if (windsUp(holdTicks)) {
                rumble(player, holdTicks);
                return true;
            }
            hunk = lift(player);
        }
        if (hunk != null) {
            GrabEvents.hold(player, hunk, holdPoint(player, hunk), throwSpeed);
        }
        return true;
    }

    /**
     * Whether a hold is still winding up: the hunk tears on the tick the
     * wind-up completes and not before.
     *
     * @param holdTicks the hold's tick count, 1 on its first tick
     * @return true while the ground still strains
     */
    public static boolean windsUp(int holdTicks) {
        return holdTicks < WINDUP_TICKS;
    }

    /**
     * The rumble a winding-up hold plays this tick: one every {@link #RUMBLE_EVERY}
     * ticks from the first, each pitched higher than the last.
     *
     * @param holdTicks the hold's tick count, 1 on its first tick
     * @return the rumble, or null on a quiet tick or past the wind-up
     */
    static @Nullable SoundCue rumbleAt(int holdTicks) {
        if (!windsUp(holdTicks) || (holdTicks - 1) % RUMBLE_EVERY != 0) {
            return null;
        }
        float pitch = RUMBLE_LOW + RUMBLE_RISE * ((holdTicks - 1) / RUMBLE_EVERY);
        return new SoundCue(RUMBLE, SoundKind.BLOCKS, 1f, pitch);
    }

    private void rumble(ServerPlayer player, int holdTicks) {
        SoundCue rumble = rumbleAt(holdTicks);
        BlockPos struck = rumble == null ? null : struckBlock(player);
        if (struck != null) {
            SoundPlays.play(player.level(), Vec3.atCenterOf(struck), rumble);
        }
    }

    /**
     * The block the look strikes within range, where the hunk could lift it.
     *
     * @param player the channeling player
     * @return the block, or null where the look strikes none the hunk lifts
     */
    private @Nullable BlockPos struckBlock(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || !liftable(level, hit.getBlockPos()) ? null
                : hit.getBlockPos();
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
        BlockPos struck = struckBlock(player);
        if (struck == null) {
            return null;
        }
        List<BlockPos> cells = hunkCells(struck, radius, cell -> liftable(level, cell));
        if (!pay(player, rockValueOf(level, cells))) {
            return null;
        }
        List<BlockHunk.HunkBlock> blocks = new ArrayList<>(cells.size());
        for (BlockPos cell : cells) {
            blocks.add(new BlockHunk.HunkBlock(cell.subtract(struck), level.getBlockState(cell)));
        }
        for (Seam seam : seamFaces(cells, cell -> !level.getBlockState(cell).isAir())) {
            throwSeamChips(level, seam, level.getBlockState(seam.cell()));
        }
        cells.forEach(cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        BlockHunk hunk = BlockHunk.lift(level, struck, blocks);
        SoundPlays.play(level, hunk.getBoundingBox().getCenter(), TEAR);
        return hunk;
    }

    /**
     * The seams a hunk tears along: each face of a lifted cell whose neighbor
     * is no lifted cell and still holds a block; a face between two lifted
     * cells, or one open to air, is no seam.
     *
     * @param cells    the lifted cells
     * @param standing whether a cell outside the hunk holds a block
     * @return the seams
     */
    static List<Seam> seamFaces(List<BlockPos> cells, Predicate<BlockPos> standing) {
        Set<BlockPos> lifted = new HashSet<>(cells);
        List<Seam> seams = new ArrayList<>();
        for (BlockPos cell : cells) {
            for (Direction face : Direction.values()) {
                BlockPos neighbor = cell.relative(face);
                if (!lifted.contains(neighbor) && standing.test(neighbor)) {
                    seams.add(new Seam(cell, face));
                }
            }
        }
        return seams;
    }

    /**
     * Throws chips of a lifted block off one seam face, scattered across the
     * face and flying out from it.
     *
     * @param level the server level
     * @param seam  the seam
     * @param state the lifted block
     */
    private static void throwSeamChips(ServerLevel level, Seam seam, BlockState state) {
        BlockParticleOption chip = new BlockParticleOption(ParticleTypes.BLOCK, state);
        Vec3 out = Vec3.atLowerCornerOf(seam.face().getUnitVec3i());
        Vec3 faceMiddle = Vec3.atCenterOf(seam.cell()).add(out.scale(CHIP_LIFT));
        for (int index = 0; index < CHIPS_PER_SEAM; index++) {
            Vec3 at = faceMiddle.add(acrossFace(level, seam.face()));
            // With a count of zero the offsets are the chip's direction and the speed its scale.
            level.sendParticles(chip, at.x, at.y, at.z, 0, out.x, out.y, out.z, CHIP_SPEED);
        }
    }

    private static Vec3 acrossFace(ServerLevel level, Direction face) {
        double first = (level.getRandom().nextDouble() - HALF) * BOTH_WAYS * CHIP_SCATTER;
        double second = (level.getRandom().nextDouble() - HALF) * BOTH_WAYS * CHIP_SCATTER;
        return switch (face.getAxis()) {
            case X -> new Vec3(0, first, second);
            case Y -> new Vec3(first, 0, second);
            case Z -> new Vec3(first, second, 0);
        };
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
    public static List<BlockPos> hunkCells(BlockPos struck, double radius, Predicate<BlockPos> liftable) {
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
        return liftableState(state, state.getDestroySpeed(level, cell), ValuedBlocks.valueAt(level, cell));
    }

    /**
     * Whether the hunk lifts a block, read from what the server and the
     * client both know of it, so the client shakes the cells the server lifts.
     *
     * @param state    the block
     * @param hardness its hardness where it stands, negative for one no hand breaks
     * @param value    its goo value, or null for none
     * @return true where the hunk lifts it
     */
    public static boolean liftableState(BlockState state, float hardness, @Nullable GooValue value) {
        boolean breakable = !state.isAir() && !state.hasBlockEntity() && hardness >= 0;
        return breakable && carriesRock(value);
    }

    private static boolean carriesRock(@Nullable GooValue value) {
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
