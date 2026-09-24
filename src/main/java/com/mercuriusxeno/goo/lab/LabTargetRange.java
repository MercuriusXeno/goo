package com.mercuriusxeno.goo.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The target range: a marked firing line and, at throwing distance south of
 * it, one target of each block type a thrown goo meets (decision
 * lab-holds-bays-supply-pens-kit). Solid targets stand on the floor; the
 * liquids sit as sources in the floor over a basin block, so they cannot flow.
 */
public final class LabTargetRange {

    /**
     * Blocks from the firing line to the target row.
     */
    static final int THROW_DISTANCE = 12;
    /**
     * Blocks between neighbouring targets.
     */
    static final int TARGET_SPACING = 2;
    /**
     * The block marking the firing line in the floor.
     */
    static final String FIRING_LINE_BLOCK = "minecraft:red_concrete";
    /**
     * The block under a liquid target, holding it in the floor.
     */
    static final String BASIN_BLOCK = "minecraft:stone";
    /**
     * Solid target block types, ice first so the lava at the far end cannot melt it.
     */
    static final List<String> SOLID_TARGETS = List.of(
            "minecraft:stone", "minecraft:ice", "minecraft:dirt", "minecraft:oak_log",
            "minecraft:glass", "minecraft:obsidian");
    /**
     * Liquid target block types.
     */
    static final List<String> LIQUID_TARGETS = List.of("minecraft:water", "minecraft:lava");
    /**
     * The liquid target states, for the basin check.
     */
    private static final Set<String> LIQUID_STATES = Set.copyOf(LIQUID_TARGETS);
    /**
     * One block down, where a liquid target's basin sits.
     */
    private static final int BELOW = -1;

    private LabTargetRange() {
    }

    /**
     * Lays the range with its firing line starting at the given corner.
     *
     * @param corner the west end of the firing line, in the floor
     * @return the range
     */
    static LabRange range(LabOffset corner) {
        int targetCount = SOLID_TARGETS.size() + LIQUID_TARGETS.size();
        int length = (targetCount - 1) * TARGET_SPACING + 1;
        LabBox firingLine = new LabBox(corner, corner.shifted(length - 1, 0, 0));
        List<LabPlacement> targets = new ArrayList<>();
        LabOffset row = corner.shifted(0, 0, THROW_DISTANCE);
        for (int index = 0; index < SOLID_TARGETS.size(); index++) {
            targets.add(LabPlacement.block(row.shifted(index * TARGET_SPACING, 1, 0), SOLID_TARGETS.get(index)));
        }
        for (int index = 0; index < LIQUID_TARGETS.size(); index++) {
            int x = (SOLID_TARGETS.size() + index) * TARGET_SPACING;
            targets.add(LabPlacement.block(row.shifted(x, 0, 0), LIQUID_TARGETS.get(index)));
        }
        LabBox bounds = new LabBox(corner.shifted(0, BELOW, 0), row.shifted(length - 1, 1, 0));
        return new LabRange(firingLine, targets, bounds);
    }

    /**
     * Answers the range's placements: the firing line in the floor, the targets and each liquid's basin.
     *
     * @param range the range
     * @return the range's placements
     */
    static List<LabPlacement> rangeBlocks(LabRange range) {
        List<LabPlacement> placements = new ArrayList<>();
        LabOffset start = range.firingLine().min();
        for (int x = start.x(); x <= range.firingLine().max().x(); x++) {
            placements.add(LabPlacement.block(new LabOffset(x, start.y(), start.z()), FIRING_LINE_BLOCK));
        }
        for (LabPlacement target : range.targets()) {
            if (LIQUID_STATES.contains(target.blockState())) {
                placements.add(LabPlacement.block(target.offset().shifted(0, BELOW, 0), BASIN_BLOCK));
            }
            placements.add(target);
        }
        return placements;
    }
}
