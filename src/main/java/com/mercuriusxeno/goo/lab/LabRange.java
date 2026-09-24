package com.mercuriusxeno.goo.lab;

import java.util.List;

/**
 * The target range: a marked firing line and a row of target blocks at
 * throwing distance from it.
 *
 * @param firingLine the floor strip marking where a thrower stands
 * @param targets    the target blocks, one per block type
 * @param bounds     the range's footprint from the firing line to the targets
 */
public record LabRange(LabBox firingLine, List<LabPlacement> targets, LabBox bounds) {

    /**
     * Copies the target list so a range stays fixed once made.
     *
     * @param firingLine the floor strip marking where a thrower stands
     * @param targets    the target blocks
     * @param bounds     the range's footprint
     */
    public LabRange {
        targets = List.copyOf(targets);
    }
}
