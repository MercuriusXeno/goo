package com.mercuriusxeno.goo.lunar;

/**
 * The depth bands lunar's underground changes through, from its surface down to the molten iron sea over its core:
 * the shallow zone's rubble caves, the middle zone's bare rock caverns, the eldritch warrens, and the sea
 * cavern. Each zone runs from the top of the zone under it to its own top. Lunar's noise settings and surface rule
 * name the same numbers.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 */
public final class LunarDepths {

    /** The world's floor, where bedrock lies. */
    public static final int WORLD_FLOOR_Y = -256;
    /** The world's height in blocks from its floor, the surface standing where it stood. */
    public static final int WORLD_HEIGHT = 576;
    /** The top of the shallow zone, the rubble caves' band under the surface. */
    public static final int SHALLOW_ZONE_TOP_Y = 40;
    /** The top of the middle zone, the bare rock caverns' band. */
    public static final int CAVERN_ZONE_TOP_Y = 0;
    /** The top of the warren zone, the eldritch warrens' band. */
    public static final int WARREN_ZONE_TOP_Y = -64;
    /** The top of the sea zone, the highest the sea cavern's roof reaches. */
    public static final int SEA_ZONE_TOP_Y = -128;
    /** The molten iron sea's level: molten iron stands below it and open air over it. */
    public static final int SEA_LEVEL_Y = -176;

    private LunarDepths() {
    }
}
