package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Blaze's Melt: the struck stone becomes a lava source as the blob sinks
 * into it, and the aim refuses any block the tag leaves out, so a throw at
 * one is never sent nor paid for: {@code melt tag=goo:meltable}.
 * melt-blob-absorbs-into-lava
 * <p>
 * The operator's rulings (idea melt-rulings): the natural stones melt; the
 * blob sinks in while the stone mingles into lava, one transition and no
 * burnout; the lava bucket's pour sounds.
 *
 * @param meltable the blocks that melt
 */
public record MeltStep(TagKey<Block> meltable) implements TargetGatedStep {

    private static final String NAME = "melt";
    private static final String FIELD_TAG = "tag";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<MeltStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_TAG).forGetter(MeltStep::meltable)
    ).apply(inst, MeltStep::new));

    /**
     * The registered type.
     */
    public static final StepType<MeltStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<MeltStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(MeltHost.class).melt(meltable);
        return true;
    }

    @Override
    public boolean admitsStruck(BlockState struck) {
        return struck.is(meltable);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.MELT);
    }
}
