package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock bore's step, run each held tick of the stream: it cuts a 3x3 tunnel
 * along the look out to the stream's reach. At each block the eye line
 * crosses past the eye's own, a 3x3 slice stands square to the look's main
 * axis; within a slice the ring of eight breaks in turn and the middle last,
 * the nearest slice first, the JSON's count of breaks a tick, which keeps
 * pace with walking. A solid block outside the tag on the eye line stops the
 * bore there (decision bore-vortex-with-a-worldspace-shake).
 *
 * @param breaks the block tag naming the blocks bore may break
 * @param count  the most blocks one tick breaks
 */
public record BoreStep(TagKey<Block> breaks, int count) implements Step {

    private static final String NAME = "bore";
    private static final String FIELD_BREAKS = "breaks";
    private static final String FIELD_COUNT = "count";
    /** The distance between samples along the look, fine enough to visit every block it crosses. */
    private static final double SAMPLE_STEP = 0.05;
    /** A slice's ring, in turn around the middle, then the middle: ring in. */
    private static final int[][] RING_IN = {
        {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {0, 0}
    };

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<BoreStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_BREAKS).forGetter(BoreStep::breaks),
            Codec.INT.fieldOf(FIELD_COUNT).forGetter(BoreStep::count)
    ).apply(inst, BoreStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BoreStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<BoreStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> boreAlong(host, host.eye(), aim.aimPoint()));
        return true;
    }

    /**
     * Breaks the next blocks of the tunnel in its order, up to the count.
     *
     * @param host the channel host
     * @param eye  the eye the tunnel runs from
     * @param end  the end of the reach along the look
     */
    private void boreAlong(ChannelHost host, Vec3 eye, Vec3 end) {
        int broken = 0;
        for (BlockPos pos : tunnelOrder(host, eye, end)) {
            if (broken >= count) {
                return;
            }
            if (host.blockIn(pos, breaks)) {
                host.breakBlock(pos);
                broken++;
            }
        }
    }

    /**
     * The tunnel's blocks in the order they break: each slice ring in, the
     * nearest slice first, ending at the first solid block outside the tag
     * on the eye line.
     *
     * @param host the channel host
     * @param eye  the eye the tunnel runs from
     * @param end  the end of the reach along the look
     * @return the blocks, each once
     */
    private List<BlockPos> tunnelOrder(ChannelHost host, Vec3 eye, Vec3 end) {
        Direction.Axis main = Direction.getApproximateNearest(end.subtract(eye)).getAxis();
        Set<BlockPos> order = new LinkedHashSet<>();
        List<BlockPos> line = blocksAlong(eye, end);
        for (BlockPos middle : line.subList(1, line.size())) {
            if (!host.airAt(middle) && !host.blockIn(middle, breaks)) {
                break;
            }
            order.addAll(sliceRingIn(middle, main));
        }
        return List.copyOf(order);
    }

    /**
     * A 3x3 slice square to an axis, ring in: the eight around the middle in
     * turn, then the middle.
     *
     * @param middle the slice's middle
     * @param main   the axis the slice stands square to
     * @return the nine blocks in breaking order
     */
    static List<BlockPos> sliceRingIn(BlockPos middle, Direction.Axis main) {
        List<BlockPos> slice = new ArrayList<>();
        for (int[] at : RING_IN) {
            slice.add(switch (main) {
                case X -> middle.offset(0, at[1], at[0]);
                case Y -> middle.offset(at[0], 0, at[1]);
                case Z -> middle.offset(at[0], at[1], 0);
            });
        }
        return slice;
    }

    /**
     * The blocks a line crosses, nearest its start first, each once.
     *
     * @param from the line's start
     * @param to   the line's end
     * @return the blocks crossed
     */
    static List<BlockPos> blocksAlong(Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        int samples = (int) Math.ceil(line.length() / SAMPLE_STEP);
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i <= samples; i++) {
            BlockPos cell = BlockPos.containing(samples == 0 ? from : from.add(line.scale((double) i / samples)));
            if (cells.isEmpty() || !cells.getLast().equals(cell)) {
                cells.add(cell);
            }
        }
        return cells;
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
