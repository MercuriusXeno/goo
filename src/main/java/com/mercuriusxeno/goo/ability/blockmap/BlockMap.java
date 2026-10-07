package com.mercuriusxeno.goo.ability.blockmap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import java.util.Map;
import java.util.Optional;

/**
 * A data map stepping each block it names to another, one rung at a time,
 * which an ability transforms blocks by: calcify's main sequence runs sand,
 * dirt, coarse dirt, gravel, cobblestone, stone, each entry the operator can
 * edit in {@code data/<ns>/block_maps/<id>.json}
 * (decision petrify-stone-encasement-and-calcify-map).
 *
 * @param steps each block's next block
 */
public record BlockMap(Map<Block, Block> steps) {

    private static final String FIELD_STEPS = "steps";

    /** Codec for a block map file: block ids to block ids under {@code steps}. */
    public static final Codec<BlockMap> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(BuiltInRegistries.BLOCK.byNameCodec(), BuiltInRegistries.BLOCK.byNameCodec())
                    .fieldOf(FIELD_STEPS).forGetter(BlockMap::steps)
    ).apply(inst, BlockMap::new));

    /**
     * Copies the steps so the record holds them unmodifiable.
     */
    public BlockMap {
        steps = Map.copyOf(steps);
    }

    /**
     * The block one rung on from a block.
     *
     * @param from the block standing
     * @return the next block, empty where the map names none
     */
    public Optional<Block> next(Block from) {
        return Optional.ofNullable(steps.get(from));
    }
}
