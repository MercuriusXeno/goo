package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.blockmap.BlockMap;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nether decay's block step, run each held tick of the stream: the gnat
 * swarm paints every block it reaches, and every block the hold has painted
 * builds exposure toward its degraded block in the map the JSON names while
 * the hold lasts, whether or not the aim is still on it, stepping once it
 * has built the JSON's ticks. A painted block past half its step is left to
 * finish alone, even after the hold ends; a position the hold has stepped
 * once it passes over, so stone becomes cobblestone and goes no further in
 * one activation (decision decay-gnats-degrade-each-block-once).
 *
 * @param map   the id of the block map the blocks step along
 * @param ticks the ticks of swarm a block takes to step
 */
public record DegradeStep(Identifier map, int ticks) implements Step {

    private static final String NAME = "degrade";
    private static final String FIELD_MAP = "map";
    private static final String FIELD_TICKS = "ticks";
    /** The share past which a painted block finishes its step alone. */
    static final float FINISHES_ALONE = 0.5f;
    /**
     * The tint Decay's mingled block wears, nether goo's #C03434, so the
     * block's own grays multiply down to a dark maroon.
     */
    public static final int NETHER_MAROON = 0xC03434;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DegradeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_MAP).forGetter(DegradeStep::map),
            Codec.INT.fieldOf(FIELD_TICKS).forGetter(DegradeStep::ticks)
    ).apply(inst, DegradeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DegradeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DegradeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        Optional<BlockMap> steps = BlockMaps.get(map);
        host.channelAim().ifPresent(aim -> steps.ifPresent(found -> {
            HoldMarks marks = host.holdMarks();
            if (aim.reaching()) {
                paintCone(host, aim, found, marks);
            }
            buildPainted(host, found, marks, 1f / ticks);
        }));
        return true;
    }

    /**
     * Paints each block in the cone that faces air and has a degraded block.
     *
     * @param host  the channel host
     * @param aim   the stream's aim, its reach along the look
     * @param steps the block map
     * @param marks the hold's marks
     */
    private static void paintCone(ChannelHost host, ChannelAim aim, BlockMap steps, HoldMarks marks) {
        List<BlockPos> reached = new ArrayList<>(CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(),
                aim.coneDegrees()));
        // the operator's ruling on Decay: the block under the crosshair is painted wherever on its face the aim rests
        crosshairBlock(host, aim).ifPresent(reached::add);
        for (BlockPos pos : reached) {
            if (!host.airAt(pos) && steps.next(host.blockAt(pos)).isPresent() && CalcifyStep.facesAir(host, pos)) {
                marks.paint(pos, host.blockAt(pos));
            }
        }
    }

    /**
     * The block the crosshair rests on within the stream's reach.
     *
     * @param host the channel host
     * @param aim  the stream's aim, its reach along the look
     * @return the block, or empty where the look meets none within reach
     */
    private static Optional<BlockPos> crosshairBlock(ChannelHost host, ChannelAim aim) {
        BlockHitResult hit = host.level().clip(new ClipContext(host.eye(), aim.aimPoint(), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit.getBlockPos()) : Optional.empty();
    }

    /**
     * Builds every painted block one tick toward its degraded block.
     *
     * @param host  the channel host
     * @param steps the block map
     * @param marks the hold's marks
     * @param rate  the share one tick builds
     */
    private static void buildPainted(ChannelHost host, BlockMap steps, HoldMarks marks, float rate) {
        for (Map.Entry<BlockPos, Block> painted : marks.painted().entrySet()) {
            BlockPos pos = painted.getKey();
            if (host.blockAt(pos) != painted.getValue()) {
                // it finished alone, or something else replaced it
                marks.noteStepped(pos);
            } else if (!host.finishingAlone(pos)) {
                steps.next(painted.getValue()).ifPresentOrElse(next -> build(host, marks, pos, next, rate),
                        () -> marks.unpaint(pos));
            }
        }
    }

    /**
     * Builds one painted block's share, stepping it when full and leaving it
     * to finish alone once past half.
     *
     * @param host  the channel host
     * @param marks the hold's marks
     * @param pos   the block
     * @param next  the block it degrades to
     * @param rate  the share one tick builds
     */
    private static void build(ChannelHost host, HoldMarks marks, BlockPos pos, Block next, float rate) {
        // the operator's ruling on Decay: its overlay wears the nether family's dark maroon
        float share = host.exposeBlock(pos, next.defaultBlockState(), rate, NETHER_MAROON);
        if (share >= 1f) {
            host.transformBlock(pos, next.defaultBlockState());
            marks.noteStepped(pos);
        } else if (share > FINISHES_ALONE) {
            host.finishBlockAlone(pos, rate);
        }
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
