package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A value for each side of the moon, as a worldgen JSON names it under light_side and dark_side: the light side's
 * within 3.75 million of x = 0 and the dark side's past it, so what the data concentrates on the dark side reads
 * there and the light side keeps its own.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 *
 * @param lightSide the light side's value
 * @param darkSide  the dark side's value
 * @param <T>       the value
 */
public record Sided<T>(T lightSide, T darkSide) {

    /** The JSON fields naming each side's value. */
    private static final String LIGHT_SIDE_FIELD = "light_side";
    private static final String DARK_SIDE_FIELD = "dark_side";
    private static final String SPACING_WITHIN_SEPARATION = "Spacing has to be larger than separation";
    /** A chunk's middle column stands this many blocks past its first. */
    private static final int CHUNK_MIDDLE = 8;
    /** A chunk's x steps its first block's x by this many bits. */
    private static final int CHUNK_SHIFT = 4;

    /**
     * The fields a JSON names a sided value by.
     *
     * @param value the codec of each side's value
     * @param <T>   the value
     * @return the map codec, its fields light_side and dark_side
     */
    public static <T> MapCodec<Sided<T>> mapCodec(Codec<T> value) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                value.fieldOf(LIGHT_SIDE_FIELD).forGetter(Sided::lightSide),
                value.fieldOf(DARK_SIDE_FIELD).forGetter(Sided::darkSide)).apply(i, Sided::new));
    }

    /**
     * A structure set's random spread on one side: one placement a grid cell spacing chunks wide, kept separation
     * chunks from its neighbours.
     *
     * @param spacing    the grid cell's width, in chunks
     * @param separation the least gap between two placements, in chunks
     */
    public record Spread(int spacing, int separation) {

        private static final int MOST_CHUNKS = 4096;

        /** The data codec, refusing a spacing no wider than its separation as vanilla's random spread does. */
        public static final Codec<Spread> CODEC = RecordCodecBuilder.<Spread>create(i -> i.group(
                Codec.intRange(0, MOST_CHUNKS).fieldOf("spacing").forGetter(Spread::spacing),
                Codec.intRange(0, MOST_CHUNKS).fieldOf("separation").forGetter(Spread::separation))
                .apply(i, Spread::new)).validate(Spread::validate);

        private static DataResult<Spread> validate(Spread spread) {
            return spread.spacing() <= spread.separation()
                    ? DataResult.error(() -> SPACING_WITHIN_SEPARATION) : DataResult.success(spread);
        }
    }

    /**
     * @param x the x, in blocks
     * @return the dark side's value past the light side, else the light side's
     */
    public T at(double x) {
        return Lunar.isDarkSide(x) ? darkSide : lightSide;
    }

    /**
     * @param chunkX the chunk's x
     * @return the value at the chunk's middle column
     */
    public T atChunk(int chunkX) {
        return at((chunkX << CHUNK_SHIFT) + CHUNK_MIDDLE);
    }
}
