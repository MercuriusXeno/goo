package com.mercuriusxeno.goo.lab;

import java.util.ArrayList;
import java.util.List;

/**
 * The mob pens: a row of fenced pens along +x, each roofed with tinted glass
 * so undead keep out of the sun and fliers stay in, holding a spread of mobs
 * to throw goo at (decision lab-holds-bays-supply-pens-kit).
 */
public final class LabPens {

    /**
     * Width and depth of a pen's interior.
     */
    static final int INTERIOR_SIZE = 5;
    /**
     * Width and depth of a pen from fence to fence.
     */
    static final int OUTER_SIZE = INTERIOR_SIZE + 2;
    /**
     * Blocks of walkway between neighbouring pens.
     */
    static final int PEN_GAP = 2;
    /**
     * Height of the pen's roof above the floor; the fence ring stands below it.
     */
    static final int ROOF_HEIGHT = 3;
    /**
     * The fence the pen's ring is made of.
     */
    static final String FENCE_BLOCK = "minecraft:oak_fence";
    /**
     * The roof block: it keeps sky light off undead and lets a player see in.
     */
    static final String ROOF_BLOCK = "minecraft:tinted_glass";
    /**
     * Divisor that finds the middle of a span.
     */
    private static final int HALF = 2;
    /**
     * One block toward the walkway north of a pen, where its sign stands.
     */
    private static final int NORTH = -1;

    // --- the spread of mobs, by pen ---
    private static final String PASSIVE = "Passive";
    private static final String HOSTILE = "Hostile";
    private static final String UNDEAD = "Undead";
    private static final String BLAZE = "Blaze";
    private static final List<String> PASSIVE_MOBS =
            List.of("minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken");
    private static final List<String> HOSTILE_MOBS =
            List.of("minecraft:spider", "minecraft:creeper", "minecraft:witch");
    private static final List<String> UNDEAD_MOBS =
            List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:husk");
    private static final List<String> BLAZE_MOBS = List.of("minecraft:blaze");

    private LabPens() {
    }

    /**
     * Lays the pens in a row along +x starting at the given corner.
     *
     * @param corner the north-west floor corner of the first pen's fence ring
     * @return the pens in row order
     */
    static List<LabPen> pens(LabOffset corner) {
        List<String> names = List.of(PASSIVE, HOSTILE, UNDEAD, BLAZE);
        List<List<String>> mobs = List.of(PASSIVE_MOBS, HOSTILE_MOBS, UNDEAD_MOBS, BLAZE_MOBS);
        List<LabPen> pens = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            LabOffset min = corner.shifted(index * (OUTER_SIZE + PEN_GAP), 0, 0);
            LabBox bounds = new LabBox(min, min.shifted(OUTER_SIZE - 1, ROOF_HEIGHT, OUTER_SIZE - 1));
            LabBox interior = new LabBox(min.shifted(1, 1, 1), min.shifted(INTERIOR_SIZE, ROOF_HEIGHT - 1, INTERIOR_SIZE));
            pens.add(new LabPen(names.get(index), bounds, interior, mobs.get(index)));
        }
        return pens;
    }

    /**
     * Answers a pen's fence ring, its roof and the sign naming it outside its north fence.
     *
     * @param pen  the pen
     * @param sign the sign block state
     * @return the pen's placements above the floor
     */
    static List<LabPlacement> penBlocks(LabPen pen, String sign) {
        List<LabPlacement> placements = new ArrayList<>();
        LabOffset min = pen.bounds().min();
        for (int dx = 0; dx < OUTER_SIZE; dx++) {
            for (int dz = 0; dz < OUTER_SIZE; dz++) {
                placements.addAll(columnBlocks(min.shifted(dx, 0, dz), isRing(dx, dz)));
            }
        }
        LabOffset signAt = min.shifted(OUTER_SIZE / HALF, 1, NORTH);
        placements.add(new LabPlacement(signAt, sign, pen.displayName()));
        return placements;
    }

    /**
     * Answers one pen column: fence on the ring below the roof, then the roof.
     *
     * @param base the column's floor offset
     * @param ring whether the column lies on the fence ring
     * @return the column's placements
     */
    private static List<LabPlacement> columnBlocks(LabOffset base, boolean ring) {
        List<LabPlacement> column = new ArrayList<>();
        if (ring) {
            for (int dy = 1; dy < ROOF_HEIGHT; dy++) {
                column.add(LabPlacement.block(base.shifted(0, dy, 0), FENCE_BLOCK));
            }
        }
        column.add(LabPlacement.block(base.shifted(0, ROOF_HEIGHT, 0), ROOF_BLOCK));
        return column;
    }

    /**
     * Answers whether a column lies on the pen's outer ring.
     *
     * @param dx the column's x within the pen
     * @param dz the column's z within the pen
     * @return true on the ring
     */
    static boolean isRing(int dx, int dz) {
        int last = OUTER_SIZE - 1;
        return dx == 0 || dz == 0 || dx == last || dz == last;
    }

    /**
     * Answers where each of a pen's mobs spawns: spread along the interior's middle row.
     *
     * @param pen the pen
     * @return one spawn per mob
     */
    static List<LabSpawn> spawns(LabPen pen) {
        List<LabSpawn> spawns = new ArrayList<>();
        LabOffset row = pen.interior().min().shifted(0, 0, INTERIOR_SIZE / HALF);
        for (int index = 0; index < pen.mobs().size(); index++) {
            spawns.add(new LabSpawn(row.shifted(index + 1, 0, 0), pen.mobs().get(index)));
        }
        return spawns;
    }
}
