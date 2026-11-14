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
 * Rock's Level step, run each held tick of the channel: every block of the
 * cursor's 3x3 out from the face the hold began on, up to 3 blocks out, never
 * behind the face, that belongs to the set the ability's JSON names, the
 * earth and its builds, and stands in reach, progresses toward broken at
 * once through the hold's gradual break, which breaks each with its drops.
 * The outermost layer progresses at the pace its JSON names and each layer
 * nearer the face slower, so the swath clears from the outside in.
 * decision flatten-disc-cursor-breaks-above-the-plane
 * decision flatten-progresses-its-whole-swath-outermost-fastest
 * decision bore-and-flatten-json-carry-two-pace-values
 *
 * @param breaks the block tag naming the blocks Level may break
 * @param pace   how fast the swath's layers progress toward broken
 */
public record LevelStep(TagKey<Block> breaks, BreakPace pace) implements TakesNoEntity {

    private static final String NAME = "level";
    private static final String FIELD_BREAKS = "breaks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<LevelStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_BREAKS).forGetter(LevelStep::breaks),
            BreakPace.CODEC.forGetter(LevelStep::pace)
    ).apply(inst, LevelStep::new));

    /**
     * The registered type.
     */
    public static final StepType<LevelStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<LevelStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> breakUnderCursor(host, aim));
        return true;
    }

    /**
     * Hands the hold's gradual break every swath block in the tag and in
     * reach, each layer's blocks alike, the outermost layer holding any at
     * the full rate and each layer nearer the face slower.
     * decision flatten-progresses-its-whole-swath-outermost-fastest
     *
     * @param host the channel host
     * @param aim  the hold's aim this tick
     */
    private void breakUnderCursor(ChannelHost host, ChannelAim aim) {
        Predicate<BlockPos> breakable = pos -> host.blockIn(pos, breaks) && host.reaches(pos);
        List<List<BlockPos>> layers = progressingLayers(aim, aim.aimedBlock(host.eye()), breakable);
        host.gradualBreak().progress(pace.ratesByGroup(layers), host);
    }

    /**
     * The blocks the swath is progressing while the cursor rests: the pick
     * its tick hands the gradual break, so a client shakes exactly the blocks
     * the server is cracking.
     * decision only-the-blocks-about-to-break-shake-briefly-and-gently
     *
     * @param aim       the hold's aim
     * @param aimed     the block the cursor rests on
     * @param breakable whether a cell holds a block in the tag and in reach
     * @return the blocks, outermost first
     */
    public static List<BlockPos> progressing(ChannelAim aim, BlockPos aimed, Predicate<BlockPos> breakable) {
        return progressingLayers(aim, aimed, breakable).stream().flatMap(List::stream).toList();
    }

    private static List<List<BlockPos>> progressingLayers(ChannelAim aim, BlockPos aimed,
                                                          Predicate<BlockPos> breakable) {
        return aim.swathLayersOutermostFirst(aimed).stream()
                .map(layer -> layer.stream().filter(breakable).toList()).toList();
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
