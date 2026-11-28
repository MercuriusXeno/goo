package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import java.util.Optional;

/**
 * The size of a pocket of world goo on lunar's ground: a few cells laid on open ground tops around a spot, holding no
 * more layers together than the pocket's volume, so a pocket is worth gathering and never floods the ground around
 * it. Its cells level out as any goo cell does, holding their layers, and stand until something takes them.
 * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
 *
 * @param cells  how many cells the pocket tries to lay
 * @param layers the layers each cell tries to hold
 * @param volume the most layers the pocket's cells hold together
 * @param reach  the farthest a cell lies from the pocket's middle, in blocks
 */
public record GooPocketShape(IntProvider cells, IntProvider layers, int volume, int reach) {

    /** The most cells a pocket names, each holding at most a whole block. */
    static final int MOST_CELLS = 9;
    /** The farthest a pocket's cell may lie from its middle. */
    static final int MOST_REACH = 3;

    /** The shape's fields, read beside the fields of the feature laying it. */
    public static final MapCodec<GooPocketShape> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            IntProviders.codec(1, MOST_CELLS).fieldOf("cells").forGetter(GooPocketShape::cells),
            IntProviders.codec(1, VolumeCellBlock.MAX_LAYERS).fieldOf("layers").forGetter(GooPocketShape::layers),
            Codec.intRange(1, MOST_CELLS * VolumeCellBlock.MAX_LAYERS).fieldOf("volume")
                    .forGetter(GooPocketShape::volume),
            Codec.intRange(0, MOST_REACH).fieldOf("reach").forGetter(GooPocketShape::reach)
    ).apply(instance, GooPocketShape::new));

    /** A cell's ground is sought this far over and under the pocket's height. */
    static final int GROUND_SEARCH = 2;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /**
     * Lays the pocket's cells, the first at its middle and the rest on open ground around it, each holding its rolled
     * layers until the pocket's volume runs out.
     *
     * @param level  the level being generated
     * @param random the feature's random
     * @param origin the pocket's middle, an open spot over a ground top
     * @param cell   the world goo cell the pocket lays
     */
    public void lay(WorldGenLevel level, RandomSource random, BlockPos origin, Block cell) {
        int left = volume;
        int count = cells.sample(random);
        for (int laid = 0; laid < count && left > 0; laid++) {
            Optional<BlockPos> spot = laid == 0 ? Optional.of(origin) : groundNear(level, random, origin);
            if (spot.isPresent()) {
                int held = Math.min(layers.sample(random), left);
                level.setBlock(spot.get(), cell.defaultBlockState().setValue(VolumeCellBlock.LAYERS, held), PLACE_FLAGS);
                left -= held;
            }
        }
    }

    /**
     * An open ground top at a random spot within the pocket's reach, the one nearest the pocket's height.
     *
     * @param level  the level being generated
     * @param random the feature's random
     * @param origin the pocket's middle
     * @return the spot over the ground's top, empty where the search finds none
     */
    private Optional<BlockPos> groundNear(WorldGenLevel level, RandomSource random, BlockPos origin) {
        BlockPos column = origin.offset(random.nextIntBetweenInclusive(-reach, reach), 0,
                random.nextIntBetweenInclusive(-reach, reach));
        for (int dy = GROUND_SEARCH; dy >= -GROUND_SEARCH; dy--) {
            BlockPos spot = column.above(dy);
            if (opensOnGround(level, spot)) {
                return Optional.of(spot);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a spot is open over a sturdy ground top, where a cell can lie.
     *
     * @param level the level being generated
     * @param spot  the spot
     * @return true where the spot is air over a sturdy top
     */
    static boolean opensOnGround(WorldGenLevel level, BlockPos spot) {
        BlockPos under = spot.below();
        return level.getBlockState(spot).isAir() && level.getBlockState(under).isFaceSturdy(level, under, Direction.UP);
    }
}
