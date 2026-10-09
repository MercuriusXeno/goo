package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A stream's block pass that breaks every block of a tag inside its cone
 * each held tick; Cold kills the foliage its wind reaches:
 * {@code break_blocks tag=goo:foliage}.
 * cold-streams-wind-lines-and-snowflakes
 *
 * @param tag the blocks the stream breaks
 */
public record BreakBlocksStep(TagKey<Block> tag) implements Step {

    private static final String NAME = "break_blocks";
    private static final String FIELD_TAG = "tag";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<BreakBlocksStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_TAG).forGetter(BreakBlocksStep::tag)
    ).apply(inst, BreakBlocksStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BreakBlocksStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<BreakBlocksStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            for (BlockPos pos : CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees())) {
                if (host.blockIn(pos, tag)) {
                    host.breakBlock(pos);
                }
            }
        });
        return true;
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
