package com.mercuriusxeno.goo.lab;

import java.util.List;

/**
 * A fenced mob pen, roofed or open to the sky.
 *
 * @param displayName the name the pen's sign shows
 * @param bounds      the pen's footprint from fence ring to roof height
 * @param interior    the air inside the fence ring and under roof height, where the mobs stand
 * @param mobs        the entity type ids the pen holds, one mob each
 * @param roofed      whether tinted glass closes the pen at roof height, or open air stands there
 */
public record LabPen(String displayName, LabBox bounds, LabBox interior, List<String> mobs, boolean roofed) {

    /**
     * Copies the mob list so a pen stays fixed once made.
     *
     * @param displayName the name the pen's sign shows
     * @param bounds      the pen's footprint
     * @param interior    the air inside the pen
     * @param mobs        the entity type ids
     * @param roofed      whether the pen is roofed
     */
    public LabPen {
        mobs = List.copyOf(mobs);
    }
}
