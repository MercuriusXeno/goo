package com.mercuriusxeno.goo.lab;

import java.util.ArrayList;
import java.util.List;

/**
 * The Goo Lab as a pure list of placements: a floor plate and one signed plot
 * per machine in a row along +x, all relative to the lab origin
 * (decision lab-built-from-code). Later lab tasks add their placements to the
 * same plan, so the build, the rebuild and the tests read one source.
 */
public final class LabLayout {

    /**
     * Where the lab origin stands in the Goo Lab world: the top layer of the
     * template's superflat floor, so the plate replaces the surface block.
     */
    public static final LabOffset WORLD_ORIGIN = new LabOffset(0, -61, 0);
    /**
     * Width and depth of one machine plot.
     */
    static final int PLOT_SIZE = 5;
    /**
     * Blocks of walkway between neighbouring plots.
     */
    static final int PLOT_GAP = 2;
    /**
     * Blocks of floor between the outermost plots and the plate's edge.
     */
    static final int FLOOR_MARGIN = 2;
    /**
     * Headroom above the floor that a plot claims for its bay.
     */
    static final int PLOT_HEADROOM = 4;
    /**
     * The block the floor plate is made of.
     */
    static final String FLOOR_BLOCK = "minecraft:smooth_stone";
    /**
     * The block that marks a plot's ground inside the floor plate.
     */
    static final String PLOT_BLOCK = "minecraft:polished_andesite";
    /**
     * A standing sign turned to face north, toward a player on the walkway.
     */
    static final String SIGN_BLOCK = "minecraft:oak_sign[rotation=8]";
    /**
     * Plot origin to sign: centred on the plot's north edge, one block above the floor.
     */
    private static final int SIGN_INSET = PLOT_SIZE / 2;

    private LabLayout() {
    }

    /**
     * Answers the whole lab plan.
     *
     * @return the placements, plots and bounds of one build
     */
    public static LabPlan plan() {
        List<LabPlot> plots = machinePlots();
        LabBox floor = floorBox();
        List<LabPlacement> placements = new ArrayList<>(floorPlacements(floor, plots));
        plots.forEach(plot -> placements.add(signPlacement(plot)));
        LabBox bounds = new LabBox(floor.min(), floor.max().shifted(0, PLOT_HEADROOM, 0));
        return new LabPlan(placements, plots, bounds);
    }

    /**
     * Lays one plot per machine in a row along +x, walkway between them.
     *
     * @return the plots in machine order
     */
    static List<LabPlot> machinePlots() {
        List<LabPlot> plots = new ArrayList<>();
        LabMachine[] machines = LabMachine.values();
        for (int index = 0; index < machines.length; index++) {
            int minX = FLOOR_MARGIN + index * (PLOT_SIZE + PLOT_GAP);
            LabOffset min = new LabOffset(minX, 0, FLOOR_MARGIN);
            LabOffset max = min.shifted(PLOT_SIZE - 1, PLOT_HEADROOM, PLOT_SIZE - 1);
            LabOffset sign = min.shifted(SIGN_INSET, 1, 0);
            plots.add(new LabPlot(machines[index], new LabBox(min, max), sign));
        }
        return plots;
    }

    /**
     * Answers the floor plate's extent: the plot row plus a margin all round, one block thick.
     *
     * @return the floor box at y 0
     */
    static LabBox floorBox() {
        int machineCount = LabMachine.values().length;
        int rowLength = machineCount * PLOT_SIZE + (machineCount - 1) * PLOT_GAP;
        int maxX = rowLength + FLOOR_MARGIN + FLOOR_MARGIN - 1;
        int maxZ = PLOT_SIZE + FLOOR_MARGIN + FLOOR_MARGIN - 1;
        return new LabBox(new LabOffset(0, 0, 0), new LabOffset(maxX, 0, maxZ));
    }

    /**
     * Fills the floor plate, marking each plot's ground with the plot block.
     *
     * @param floor the floor box
     * @param plots the plots whose ground the plate marks
     * @return one placement per floor block
     */
    private static List<LabPlacement> floorPlacements(LabBox floor, List<LabPlot> plots) {
        List<LabPlacement> placements = new ArrayList<>();
        for (int x = floor.min().x(); x <= floor.max().x(); x++) {
            for (int z = floor.min().z(); z <= floor.max().z(); z++) {
                LabOffset offset = new LabOffset(x, 0, z);
                placements.add(LabPlacement.block(offset, groundBlock(offset, plots)));
            }
        }
        return placements;
    }

    /**
     * Answers the plot block under a plot and the floor block elsewhere.
     *
     * @param offset the floor position
     * @param plots  the plots to test against
     * @return the block state for that floor position
     */
    private static String groundBlock(LabOffset offset, List<LabPlot> plots) {
        boolean underPlot = plots.stream().anyMatch(plot -> plot.bounds().contains(offset));
        return underPlot ? PLOT_BLOCK : FLOOR_BLOCK;
    }

    /**
     * Answers the sign naming a plot's machine.
     *
     * @param plot the plot the sign stands in
     * @return the sign placement
     */
    private static LabPlacement signPlacement(LabPlot plot) {
        return new LabPlacement(plot.signOffset(), SIGN_BLOCK, plot.machine().displayName());
    }
}
