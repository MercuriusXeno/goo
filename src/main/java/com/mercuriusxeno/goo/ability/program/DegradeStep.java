package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.blockmap.BlockMap;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nether decay's block step, run each held tick of the stream: every block
 * the gnat swarm reaches builds exposure toward its degraded block in the
 * map the JSON names and steps to it once it has stood in the swarm the
 * JSON's ticks; a position the hold has stepped once is passed over until
 * the hold ends, so stone becomes cobblestone and goes no further in one
 * activation (decision decay-gnats-degrade-each-block-once).
 *
 * @param map   the id of the block map the blocks step along
 * @param ticks the ticks of swarm a block takes to step
 */
public record DegradeStep(Identifier map, int ticks) implements Step {

    private static final String NAME = "degrade";
    private static final String FIELD_MAP = "map";
    private static final String FIELD_TICKS = "ticks";

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
        host.channelAim().ifPresent(aim -> steps.ifPresent(found -> degradeCone(host, aim, found, 1f / ticks)));
        return true;
    }

    /**
     * Builds each reached block's exposure toward its degraded block,
     * stepping it once the share is full, and only once a hold.
     *
     * @param host  the channel host
     * @param aim   the stream's aim, its reach along the look
     * @param steps the block map
     * @param rate  the share one tick of swarm builds
     */
    private static void degradeCone(ChannelHost host, ChannelAim aim, BlockMap steps, float rate) {
        for (BlockPos pos : CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees())) {
            if (!host.airAt(pos) && !host.steppedThisHold(pos)) {
                steps.next(host.blockAt(pos)).ifPresent(next -> degradeBlock(host, pos, next, rate));
            }
        }
    }

    /**
     * Builds one exposed block's share toward its degraded block, stepping it
     * and noting the step for the hold once the share is full.
     *
     * @param host the channel host
     * @param pos  the block
     * @param next the block it degrades to
     * @param rate the share one tick of swarm builds
     */
    private static void degradeBlock(ChannelHost host, BlockPos pos, Block next, float rate) {
        if (CalcifyStep.facesAir(host, pos) && host.exposeBlock(pos, next.defaultBlockState(), rate) >= 1f) {
            host.transformBlock(pos, next.defaultBlockState());
            host.noteSteppedThisHold(pos);
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
