package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock petrify's tap step, run as each drip lands on the block below the
 * tap, calcifying like the fog: each drip builds the block's exposure toward
 * the next rung of the block map by one drip's share, the next block
 * mingling in a little, and the drips the JSON names step the rung; once
 * the drips stop, unfinished progress recedes. A block in the
 * dripstone-capable tag grows pointed dripstone downward instead, once the
 * JSON's drips have accumulated (decision petrify-drip-calcifies-and-grows-dripstone).
 *
 * @param map   the id of the block map the block steps along
 * @param drips the drips that step one rung, or grow one tip
 * @param grows the block tag naming the blocks that grow dripstone instead
 */
public record PetrifyDripStep(Identifier map, int drips, TagKey<Block> grows) implements Step {

    private static final String NAME = "petrify_drip";
    private static final String FIELD_MAP = "map";
    private static final String FIELD_DRIPS = "drips";
    private static final String FIELD_GROWS = "grows";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PetrifyDripStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_MAP).forGetter(PetrifyDripStep::map),
            Codec.INT.fieldOf(FIELD_DRIPS).forGetter(PetrifyDripStep::drips),
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_GROWS).forGetter(PetrifyDripStep::grows)
    ).apply(inst, PetrifyDripStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PetrifyDripStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PetrifyDripStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        DripHost host = context.hostAs(DripHost.class);
        BlockPos below = host.position();
        if (host.blockIn(below, grows)) {
            if (host.countDrip() >= drips) {
                host.resetDrips();
                host.growStalactite();
            }
            return true;
        }
        BlockMaps.get(map).flatMap(steps -> steps.next(host.blockAt(below)))
                .map(Block::defaultBlockState)
                .filter(next -> host.exposeBlock(below, next, 1f / drips) >= 1f)
                .ifPresent(next -> host.transformBlock(below, next));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.DRIP);
    }
}
