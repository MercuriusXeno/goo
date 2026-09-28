package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import net.minecraft.core.Direction;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Where the crystallizer's two canisters stand in the canister block on its top,
 * over plain values so a unit test reaches it without a registry. Operator ruling:
 * the canisters stand back left and back right at model pixels (4, 4) and (12, 4),
 * the dial on the south face, off the canister grid; each takes the grid corner slot
 * nearest its turned center, so a placement aimed at that quarter lands in it.
 */
public final class CrystallizerLayout {

    /** The canisters the crystallizer reads, back left then back right. */
    public static final int CANISTER_COUNT = 2;

    /** The body's top, and the model's width, in pixels. */
    static final double TOP = 16;

    /** The canisters' centers in model space, the dial on the south face: back left then back right. */
    private static final double[][] MODEL_CENTERS = {{4, 4}, {12, 4}};

    /** The canister grid's corner slots, the only slots a turned center sits nearest. */
    private static final int[] CORNER_SLOTS = {0, 2, 6, 8};

    private static final Map<Direction, int[]> SLOTS = new EnumMap<>(Direction.class);
    private static final Map<Direction, Set<Integer>> ALLOWED = new EnumMap<>(Direction.class);
    private static final Map<Direction, float[][]> CENTERS = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            layOut(facing);
        }
    }

    private CrystallizerLayout() {
    }

    /**
     * Fills the slot indices, allowed set and centers for one facing.
     *
     * @param facing the face the dial sits on
     */
    private static void layOut(Direction facing) {
        float[][] centers = new float[CanisterSlotLayout.SLOT_COUNT][];
        for (int i = 0; i < centers.length; i++) {
            centers[i] = CanisterSlotLayout.SLOT_CENTERS[i].clone();
        }
        int[] slots = new int[CANISTER_COUNT];
        for (int role = 0; role < CANISTER_COUNT; role++) {
            double[] turned = modelToWorld(facing, MODEL_CENTERS[role][0], MODEL_CENTERS[role][1]);
            slots[role] = nearestCorner(turned[0], turned[1]);
            centers[slots[role]] = new float[] {(float) turned[0], (float) turned[1]};
        }
        SLOTS.put(facing, slots);
        ALLOWED.put(facing, Set.of(slots[0], slots[1]));
        CENTERS.put(facing, centers);
    }

    /**
     * @param x world x, in pixels
     * @param z world z, in pixels
     * @return the grid corner slot whose center lies nearest
     */
    private static int nearestCorner(double x, double z) {
        int best = CORNER_SLOTS[0];
        double bestDistSq = Double.MAX_VALUE;
        for (int corner : CORNER_SLOTS) {
            double dx = x - CanisterSlotLayout.SLOT_CENTERS[corner][0];
            double dz = z - CanisterSlotLayout.SLOT_CENTERS[corner][1];
            if (dx * dx + dz * dz < bestDistSq) {
                bestDistSq = dx * dx + dz * dz;
                best = corner;
            }
        }
        return best;
    }

    /**
     * Turns a model-space point, the dial on the south face, to the world by the
     * blockstate's y rotation for the facing (south 0, west 90, north 180, east 270).
     *
     * @param facing the face the dial sits on
     * @param x      model x, in pixels
     * @param z      model z, in pixels
     * @return world {x, z}, in pixels
     */
    public static double[] modelToWorld(Direction facing, double x, double z) {
        return switch (facing) {
            case WEST -> new double[] {TOP - z, x};
            case NORTH -> new double[] {TOP - x, TOP - z};
            case EAST -> new double[] {z, TOP - x};
            default -> new double[] {x, z};
        };
    }

    /**
     * The canister block slot a canister role reads.
     *
     * @param facing the face the dial sits on
     * @param role   0 back left, 1 back right
     * @return the canister block slot index
     */
    public static int slot(Direction facing, int role) {
        return SLOTS.get(facing)[role];
    }

    /**
     * @param facing the face the dial sits on
     * @return the two canister block slots the crystallizer reads
     */
    public static Set<Integer> allowedSlots(Direction facing) {
        return ALLOWED.get(facing);
    }

    /**
     * The canister block's slot centers on a crystallizer: the fixed grid with the
     * two slots it reads moved to the turned canister centers. One stable array
     * per facing.
     *
     * @param facing the face the dial sits on
     * @return pixel centers per slot
     */
    public static float[][] centers(Direction facing) {
        return CENTERS.get(facing);
    }
}
