package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The eldritch on lunar's two sides. The monstrosity spawns under the open sky while the sky reads as night there,
 * so at every hour on the dark side and by night alone on the light side's open ground, and anywhere under cover, as
 * the warrens and caves hold it; and the dark side spawns it in larger groups, by the factor the dark_side_eldritch
 * data map names for the biome.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 * decision eldritch-spawn-in-higher-volumes-on-lunar
 */
public final class LunarEldritch {

    /** The dark_side_eldritch data map's field naming the dark side's group factor. */
    public static final String GROUP_FACTOR_FIELD = "group_factor";

    private LunarEldritch() {
    }

    /**
     * A biome's dark-side eldritch, as the dark_side_eldritch data map names it.
     *
     * @param groupFactor how many times larger the dark side's groups and clusters run than the light side's
     */
    public record DarkSideGroups(int groupFactor) {

        private static final int LARGEST_FACTOR = 16;

        /** The data codec. */
        public static final Codec<DarkSideGroups> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, LARGEST_FACTOR).fieldOf(GROUP_FACTOR_FIELD).forGetter(DarkSideGroups::groupFactor))
                .apply(i, DarkSideGroups::new));
    }

    /**
     * Whether the hour lets a monstrosity spawn at a lunar spot: under cover at any hour, and under the open sky while
     * the sky reads as night there.
     *
     * @param x               the spot's x, in blocks
     * @param lightSideDarken the darkening the overworld's clock gives the light side now
     * @param seesSky         whether the spot stands under the open sky
     * @return true where the hour lets it spawn
     */
    public static boolean spawnHourAllows(double x, int lightSideDarken, boolean seesSky) {
        return !seesSky || LunarNight.isNightAt(x, lightSideDarken);
    }

    /**
     * The eldritch volume at a lunar x: a group's count or a cluster's cap, the light side's own there, and that times
     * the dark side's factor past it.
     *
     * @param x               the x, in blocks
     * @param lightSideNumber the light side's count or cap
     * @param darkSideFactor  the dark side's factor
     * @return the number there
     */
    public static int volumeAt(double x, int lightSideNumber, int darkSideFactor) {
        return Lunar.isDarkSide(x) ? lightSideNumber * darkSideFactor : lightSideNumber;
    }
}
