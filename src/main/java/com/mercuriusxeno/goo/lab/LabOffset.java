package com.mercuriusxeno.goo.lab;

/**
 * A block position relative to the lab origin, kept free of Minecraft types
 * so a {@link LabPlan} reads and tests without a level.
 *
 * @param x east offset from the lab origin
 * @param y upward offset from the lab origin
 * @param z south offset from the lab origin
 */
public record LabOffset(int x, int y, int z) {

    /**
     * Answers this offset moved by the given amounts.
     *
     * @param dx east shift
     * @param dy upward shift
     * @param dz south shift
     * @return the shifted offset
     */
    public LabOffset shifted(int dx, int dy, int dz) {
        return new LabOffset(x + dx, y + dy, z + dz);
    }
}
