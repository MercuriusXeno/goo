package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Rock flatten's step, run each held tick of the channel: one block a tick
 * breaks, with its drops, from the cursor's 3x3 out from the face the hold
 * began on, up to 3 blocks out, the outermost first, never behind the face,
 * the first that belongs to the set the ability's JSON names, the earth and
 * its builds, and stands in reach.
 * decision flatten-disc-cursor-breaks-above-the-plane
 *
 * @param breaks the block tag naming the blocks flatten may break
 */
public record FlattenStep(TagKey<Block> breaks) implements TakesNoEntity {

    private static final String NAME = "flatten";
    private static final String FIELD_BREAKS = "breaks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FlattenStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_BREAKS).forGetter(FlattenStep::breaks)
    ).apply(inst, FlattenStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FlattenStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FlattenStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> breakUnderCursor(host, aim));
        return true;
    }

    /**
     * Breaks the swath's next block outermost first: the first in the tag and in reach.
     *
     * @param host the channel host
     * @param aim  the hold's aim this tick
     */
    private void breakUnderCursor(ChannelHost host, ChannelAim aim) {
        breaksAhead(aim, aim.aimedBlock(host.eye()), pos -> host.blockIn(pos, breaks) && host.reaches(pos), 1)
                .forEach(host::breakBlock);
    }

    /**
     * The blocks the swath breaks over the coming ticks while the cursor
     * rests, one a tick, outermost first: the same pick its tick breaks by,
     * so a client shakes the blocks the server is about to break.
     * decision only-the-blocks-about-to-break-shake-briefly-and-gently
     *
     * @param aim       the hold's aim
     * @param aimed     the block the cursor rests on
     * @param breakable whether a cell holds a block in the tag and in reach
     * @param ticks     how many ticks ahead to read
     * @return the blocks, in breaking order
     */
    public static List<BlockPos> breaksAhead(ChannelAim aim, BlockPos aimed, Predicate<BlockPos> breakable,
                                             int ticks) {
        return aim.swathOutermostFirst(aimed).stream().filter(breakable).limit(ticks).toList();
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
