package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.blockmap.BlockMap;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock petrify's block step, run each held tick of the stream: every block
 * the fog reaches that faces open air builds exposure toward the next rung of
 * the block map the JSON names, and steps that rung once it has stood in the
 * fog the JSON's ticks, the next block mingling in as it builds; a block the
 * fog leaves decays back to its last rung
 * (decision petrify-stone-encasement-and-calcify-map).
 *
 * @param map   the id of the block map the blocks step along
 * @param ticks the ticks of fog a block takes to step one rung
 */
public record CalcifyStep(Identifier map, int ticks) implements Step {

    private static final String NAME = "calcify";
    private static final String FIELD_MAP = "map";
    private static final String FIELD_TICKS = "ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CalcifyStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_MAP).forGetter(CalcifyStep::map),
            Codec.INT.fieldOf(FIELD_TICKS).forGetter(CalcifyStep::ticks)
    ).apply(inst, CalcifyStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CalcifyStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CalcifyStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        Optional<BlockMap> steps = BlockMaps.get(map);
        host.channelAim().ifPresent(aim -> steps.ifPresent(found -> calcifyCone(host, aim, found, 1f / ticks)));
        return true;
    }

    /**
     * Builds each fogged block's exposure toward its next rung, stepping the
     * rung once the share is full.
     *
     * @param host  the channel host
     * @param aim   the stream's aim, its reach along the look
     * @param steps the block map
     * @param rate  the share one tick of fog builds
     */
    private static void calcifyCone(ChannelHost host, ChannelAim aim, BlockMap steps, float rate) {
        for (BlockPos pos : blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees())) {
            Optional<Block> next = host.airAt(pos) ? Optional.empty() : steps.next(host.blockAt(pos));
            if (next.isPresent() && facesAir(host, pos)
                    && host.exposeBlock(pos, next.get().defaultBlockState(), rate) >= 1f) {
                host.transformBlock(pos, next.get().defaultBlockState());
            }
        }
    }

    /**
     * Whether a block faces open air on any side, so the stream can reach it.
     *
     * @param host the channel host
     * @param pos  the block
     * @return true where a neighbor is air
     */
    static boolean facesAir(ChannelHost host, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (host.airAt(pos.relative(side))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The blocks whose centers stand inside a cone, nearest the apex first.
     *
     * @param apex        the cone's apex
     * @param reachPoint  the end of the cone's axis, its reach along the look
     * @param coneDegrees the cone, apex to rim, in degrees
     * @return the blocks inside the cone
     */
    static List<BlockPos> blocksInCone(Vec3 apex, Vec3 reachPoint, double coneDegrees) {
        Vec3 line = reachPoint.subtract(apex);
        double range = line.length();
        List<BlockPos> inside = new ArrayList<>();
        if (range == 0) {
            return inside;
        }
        Vec3 axis = line.scale(1 / range);
        BlockPos from = BlockPos.containing(apex.subtract(range, range, range));
        BlockPos to = BlockPos.containing(apex.add(range, range, range));
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (StreamCone.contains(apex, axis, range, coneDegrees, Vec3.atCenterOf(pos))) {
                inside.add(pos.immutable());
            }
        }
        inside.sort((a, b) -> Double.compare(Vec3.atCenterOf(a).distanceToSqr(apex),
                Vec3.atCenterOf(b).distanceToSqr(apex)));
        return inside;
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
