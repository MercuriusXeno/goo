package com.mercuriusxeno.goo.ability.aging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The blocks aging in one chunk, saved with the chunk so an aging survives
 * the chunk unloading and loading again.
 * old-blob-ages-valuables-slowly
 *
 * @param aging each aging block
 */
public record AgingBlocks(List<Aging> aging) {

    /** A chunk with nothing aging. */
    public static final AgingBlocks NONE = new AgingBlocks(List.of());

    /** Codec the chunk saves the blocks by. */
    public static final MapCodec<AgingBlocks> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Aging.CODEC.listOf().fieldOf("aging").forGetter(AgingBlocks::aging)
    ).apply(inst, AgingBlocks::new));

    /**
     * One aging block: where it stands, the block its row ages and the
     * in-game ticks it has aged.
     *
     * @param pos     the block's position
     * @param source  the block its row ages
     * @param elapsed the ticks aged so far
     */
    public record Aging(BlockPos pos, Identifier source, int elapsed) {

        /** Codec for one aging block. */
        public static final Codec<Aging> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Aging::pos),
                Identifier.CODEC.fieldOf("source").forGetter(Aging::source),
                Codec.INT.fieldOf("elapsed").forGetter(Aging::elapsed)
        ).apply(inst, Aging::new));
    }

    /**
     * Whether nothing ages here.
     *
     * @return true for a chunk with nothing aging
     */
    public boolean isEmpty() {
        return aging.isEmpty();
    }

    /**
     * These blocks with one more starting, replacing any aging already at
     * its position.
     *
     * @param pos    the block's position
     * @param source the block its row ages
     * @return the blocks with the new one
     */
    public AgingBlocks starting(BlockPos pos, Identifier source) {
        List<Aging> next = new ArrayList<>(aging.stream().filter(block -> !block.pos().equals(pos)).toList());
        next.add(new Aging(pos.immutable(), source, 0));
        return new AgingBlocks(List.copyOf(next));
    }

    /**
     * These blocks each aged some more ticks.
     *
     * @param ticks the ticks to age
     * @return the aged blocks
     */
    public AgingBlocks aged(int ticks) {
        return new AgingBlocks(aging.stream()
                .map(block -> new Aging(block.pos(), block.source(), block.elapsed() + ticks)).toList());
    }

    /**
     * These blocks without the finished ones.
     *
     * @param finished the blocks to drop
     * @return the blocks left aging
     */
    public AgingBlocks without(List<Aging> finished) {
        return new AgingBlocks(aging.stream().filter(block -> !finished.contains(block)).toList());
    }
}
