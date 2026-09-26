package com.mercuriusxeno.goo.item;

/**
 * Pure math for the empty-cursor right-click on an omniblob in a slot, which
 * halves it at every volume (decision right-click-halves-the-stack).
 */
public final class OmniblobSplit {

    private static final int HALF_DIVISOR = 2;

    private OmniblobSplit() {
    }

    /**
     * Halves a volume between the slot and the cursor: the cursor takes the
     * floored half and the slot keeps the larger half, except that a volume
     * whose floored half is 0 goes to the cursor whole.
     *
     * @param volume the omniblob's volume in microblobs, above zero
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
     * One right-click's split of an omniblob.
     *
     * @param slotVolume   the volume left in the slot, 0 when the slot empties
     * @param cursorVolume the volume placed on the cursor
     */
    public record Halves(int slotVolume, int cursorVolume) {
    }
}
