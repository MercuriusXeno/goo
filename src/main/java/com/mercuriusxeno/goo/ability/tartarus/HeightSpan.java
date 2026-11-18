package com.mercuriusxeno.goo.ability.tartarus;

/**
 * A level's height, from its lowest block to its highest under its logical
 * height, so the nether's stops under its bedrock roof.
 *
 * @param bottom the lowest Y
 * @param top    the highest Y
 */
public record HeightSpan(int bottom, int top) {

    /**
     * The span a level's dimension type gives.
     *
     * @param minY          the level's lowest Y
     * @param logicalHeight the level's logical height
     * @return the span from the lowest Y to the highest under the logical height
     */
    public static HeightSpan of(int minY, int logicalHeight) {
        return new HeightSpan(minY, minY + logicalHeight - 1);
    }

    /**
     * The height in another level standing as far down its span as a height
     * stands up this one, so the higher you are here, the lower you are there.
     * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
     *
     * @param y     a height in this level, held within its span
     * @param there the other level's span
     * @return the height there
     */
    public int mirroredInto(int y, HeightSpan there) {
        long up = Math.clamp(y, bottom, top) - (long) bottom;
        long down = Math.floorDiv(up * (there.top - there.bottom), Math.max(1, top - bottom));
        return (int) (there.top - down);
    }
}
