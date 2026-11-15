package com.mercuriusxeno.goo.item;

/**
 * Pure math for the empty-cursor right-click on a goo in a slot, which
 * halves it at every volume (decision right-click-halves-the-stack).
 */
public final class GooSplit {

    private static final int HALF_DIVISOR = 2;

    private GooSplit() {
    }

    /**
     * Halves a volume between the slot and the cursor: the cursor takes the
     * floored half and the slot keeps the larger half, except that a volume
     * whose floored half is 0 goes to the cursor whole.
     *
     * @param volume the goo's volume, above zero
     * @return the volume the slot keeps and the volume the cursor takes
     */
    public static Halves halve(int volume) {
        int cursor = volume / HALF_DIVISOR;
        if (cursor == 0) {
            cursor = volume;
        }
        return new Halves(volume - cursor, cursor);
    }

    /**
     * Halves a goo holding a part under 1 mB, keeping the sum: the whole mB halves as
     * {@link #halve(int)} does, the part stays with the slot's half, and a goo holding only
     * a part goes to the cursor whole. No halve mints a new fraction.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     * decision right-click-halves-the-stack
     *
     * @param volume the goo's exact value, above zero
     * @return the value the slot keeps and the value the cursor takes
     */
    public static MixedHalves halve(MixedVolume volume) {
        if (volume.whole() <= 0) {
            return new MixedHalves(MixedVolume.ZERO, volume);
        }
        Halves wholeHalves = halve(volume.whole());
        return new MixedHalves(new MixedVolume(wholeHalves.slotVolume(), volume.part()),
                MixedVolume.ofWhole(wholeHalves.cursorVolume()));
    }

    /**
     * One right-click's split of a goo holding a part under 1 mB.
     *
     * @param slot   the value left in the slot, empty when the slot empties
     * @param cursor the value placed on the cursor
     */
    public record MixedHalves(MixedVolume slot, MixedVolume cursor) {
    }

    /**
     * One right-click's split of a goo.
     *
     * @param slotVolume   the volume left in the slot, 0 when the slot empties
     * @param cursorVolume the volume placed on the cursor
     */
    public record Halves(int slotVolume, int cursorVolume) {
    }
}
