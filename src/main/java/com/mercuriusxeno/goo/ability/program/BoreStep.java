package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock bore's step, run each held tick of the stream: walking the look from
 * the eye to the stream's reach, the nearest blocks of the mundane set the
 * ability's JSON names break with their drops, up to the JSON's count, and
 * the first other solid block stops the bore.
 * decision bore-vortex-with-a-worldspace-shake
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
        host.channelAim().ifPresent(aim -> boreAlong(host, blocksAlong(host.eye(), aim.aimPoint())));
        return true;
    }

    /**
     * Breaks the nearest breakable blocks along the line, up to the count,
     * stopping at the first solid block outside the tag.
     *
     * @param host  the channel host
     * @param cells the blocks the line crosses, nearest first
     */
    private void boreAlong(ChannelHost host, List<BlockPos> cells) {
        int broken = 0;
        for (BlockPos pos : cells) {
            if (broken >= count) {
                return;
            }
            if (host.blockIn(pos, breaks)) {
                host.breakBlock(pos);
                broken++;
            } else if (!host.airAt(pos)) {
                return;
            }
        }
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
