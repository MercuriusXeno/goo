package com.mercuriusxeno.goo.lab;

/**
 * An inclusive box of lab offsets: the extent of a plot or of the whole lab.
 *
 * @param min the corner with the smallest coordinates
 * @param max the corner with the largest coordinates
 */
public record LabBox(LabOffset min, LabOffset max) {

    /**
     * Answers whether this box shares at least one block with another.
     *
     * @param other the box to compare against
     * @return true when the two boxes overlap
     */
    public boolean intersects(LabBox other) {
        return spansOverlap(min.x(), max.x(), other.min.x(), other.max.x())
                && spansOverlap(min.y(), max.y(), other.min.y(), other.max.y())
                && spansOverlap(min.z(), max.z(), other.min.z(), other.max.z());
    }

    /**
     * Answers whether an offset lies inside this box.
     *
     * @param offset the offset to test
     * @return true when the offset is within every axis span
     */
    public boolean contains(LabOffset offset) {
        return spanHolds(min.x(), max.x(), offset.x())
                && spanHolds(min.y(), max.y(), offset.y())
                && spanHolds(min.z(), max.z(), offset.z());
    }

    /**
     * Answers whether two inclusive integer spans overlap.
     *
     * @param lowA  start of the first span
     * @param highA end of the first span
     * @param lowB  start of the second span
     * @param highB end of the second span
     * @return true when the spans share a value
     */
    private static boolean spansOverlap(int lowA, int highA, int lowB, int highB) {
        return lowA <= highB && lowB <= highA;
    }

    /**
     * Answers whether a value lies within an inclusive span.
     *
     * @param low   start of the span
     * @param high  end of the span
     * @param value the value to test
     * @return true when low &lt;= value &lt;= high
     */
    private static boolean spanHolds(int low, int high, int value) {
        return low <= value && value <= high;
    }
}
