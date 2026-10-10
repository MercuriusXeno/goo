package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The cells a pulse stream reaches: every cell the aim line itself passes
 * through, so the device under the crosshair is always reached, and every
 * cell whose center stands inside the cone around it. A narrow cone far out
 * holds few cell centers, and the aimed device's center sits off the aim
 * line, so the cone alone missed the device being aimed at.
 * pulser-toggles-rapidly-while-held
 * signal-wave-toggles-each-device-once
 */
public final class AimedCells {

    /** Samples per block along the aim line, fine enough to cross every cell it passes. */
    private static final int SAMPLES_PER_BLOCK = 10;

    private AimedCells() {
    }

    /**
     * The cells along the aim and inside its cone, nearest first.
     *
     * @param apex        the eye, where the aim starts
     * @param reach       the end of the aim
     * @param coneDegrees the cone, apex to rim, in degrees
     * @return the cells, the aim line's first, then the cone's
     */
    public static List<BlockPos> along(Vec3 apex, Vec3 reach, double coneDegrees) {
        Set<BlockPos> cells = new LinkedHashSet<>(onTheLine(apex, reach));
        cells.addAll(CalcifyStep.blocksInCone(apex, reach, coneDegrees));
        return new ArrayList<>(cells);
    }

    /**
     * The cells a line passes through, from its start.
     *
     * @param from the line's start
     * @param to   the line's end
     * @return the cells in order
     */
    static List<BlockPos> onTheLine(Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        int samples = Math.max(1, (int) Math.ceil(line.length() * SAMPLES_PER_BLOCK));
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int i = 0; i <= samples; i++) {
            cells.add(BlockPos.containing(from.add(line.scale((double) i / samples))));
        }
        return new ArrayList<>(cells);
    }
}
