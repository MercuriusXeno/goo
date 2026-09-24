package com.mercuriusxeno.goo.lab;

import java.util.List;

/**
 * A fenced, roofed mob pen.
 *
 * @param displayName the name the pen's sign shows
 * @param bounds      the pen's footprint from fence ring to roof
 * @param interior    the air inside the fence ring and under the roof, where the mobs stand
 * @param mobs        the entity type ids the pen holds, one mob each
 */
public record LabPen(String displayName, LabBox bounds, LabBox interior, List<String> mobs) {

    /**
     * Copies the mob list so a pen stays fixed once made.
     *
     * @param displayName the name the pen's sign shows
     * @param bounds      the pen's footprint
     * @param interior    the air inside the pen
     * @param mobs        the entity type ids
     */
    public LabPen {
        mobs = List.copyOf(mobs);
    }
}
