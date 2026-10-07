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
 * Rock flatten's step, run each held tick of the channel: one block a tick
 * breaks, with its drops, from the cursor's 3x3 above the plane the hold
 * remembered from the cursor, up to 3 blocks high, top down, the first that
 * belongs to the mundane set the ability's JSON names and stands in reach.
 * decision flatten-disc-cursor-breaks-above-the-plane
 *
 * @param breaks the block tag naming the blocks flatten may break
 */
public record FlattenStep(TagKey<Block> breaks) implements Step {

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
     * Breaks the swath's next block top down: the first in the tag and in reach.
     *
     * @param host the channel host
     * @param aim  the hold's aim this tick
     */
    private void breakUnderCursor(ChannelHost host, ChannelAim aim) {
        for (BlockPos pos : aim.swathTopDown(aim.aimedBlock(host.eye()))) {
            if (host.blockIn(pos, breaks) && host.reaches(pos)) {
                host.breakBlock(pos);
                return;
            }
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
