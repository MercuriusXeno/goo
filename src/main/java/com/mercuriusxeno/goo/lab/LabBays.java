package com.mercuriusxeno.goo.lab;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The machine row: one plot per {@link LabMachine} along +x, each holding its
 * machine's blocks (decision lab-holds-bays-supply-pens-kit). The bays are a
 * hand-written list: the thread that lands a new machine adds its constant to
 * {@link LabMachine}, its blocks to {@link #bayBlocks} and its rig to
 * {@code LabRigs} (decision lab-iterates-the-registries).
 */
public final class LabBays {

    /**
     * Width and depth of one machine plot.
     */
    static final int PLOT_SIZE = 5;
    /**
     * Blocks of walkway between neighbouring plots.
     */
    static final int PLOT_GAP = 2;
    /**
     * Headroom above the floor that a plot claims for its bay.
     */
    static final int PLOT_HEADROOM = 4;
    /**
     * Plot corner to the plot's centre column.
     */
    static final int PLOT_CENTRE = PLOT_SIZE / 2;
    /**
     * Air blocks between the tap's spigot and the floor it drips on.
     */
    public static final int TAP_AIR_GAP = 1;
    /**
     * Blocks from the gasket plot's centre to each of its two paired canister blocks.
     */
    public static final int GASKET_SPREAD = 1;

    // --- block states ---
    private static final String TAP = "goo:tap[open=true,facing=north]";
    private static final String VAT = "goo:vat";
    private static final String CRUCIBLE = "goo:crucible";
    private static final String HUB = "goo:hub";
    private static final String PLEXER = "goo:plexer";
    private static final String REACTOR = "goo:reactor[facing=north]";
    private static final String CANISTER = "goo:canister";
    private static final String CHORAL_GASKET = "goo:choral_gasket";
    /**
     * Each machine's bay blocks, given its plot. LabLayoutTest reads a machine
     * block in every plot, so a machine this table leaves out fails there.
     */
    private static final Map<LabMachine, Function<LabPlot, List<LabPlacement>>> BAY_BLOCKS = bayBlockTable();

    private LabBays() {
    }

    /**
     * Builds the table of each machine's bay blocks.
     *
     * @return the table, one entry per machine
     */
    private static Map<LabMachine, Function<LabPlot, List<LabPlacement>>> bayBlockTable() {
        Map<LabMachine, Function<LabPlot, List<LabPlacement>>> table = new EnumMap<>(LabMachine.class);
        table.put(LabMachine.TAP, single(TAP));
        table.put(LabMachine.VAT, single(VAT));
        table.put(LabMachine.CRUCIBLE, single(CRUCIBLE));
        table.put(LabMachine.HUB, single(HUB));
        table.put(LabMachine.PLEXER, canisterTopped(PLEXER));
        table.put(LabMachine.REACTOR, canisterTopped(REACTOR));
        table.put(LabMachine.CHORAL_GASKET, LabBays::gasketRun);
        return table;
    }

    /**
     * Lays one plot per machine in a row along +x starting at the given corner.
     *
     * @param corner the north-west floor corner of the first plot
     * @return the plots in machine order
     */
    static List<LabPlot> plots(LabOffset corner) {
        List<LabPlot> plots = new ArrayList<>();
        LabMachine[] machines = LabMachine.values();
        for (int index = 0; index < machines.length; index++) {
            LabOffset min = corner.shifted(index * (PLOT_SIZE + PLOT_GAP), 0, 0);
            LabOffset max = min.shifted(PLOT_SIZE - 1, PLOT_HEADROOM, PLOT_SIZE - 1);
            LabOffset sign = min.shifted(PLOT_CENTRE, 1, 0);
            LabOffset machine = min.shifted(PLOT_CENTRE, machineHeight(machines[index]), PLOT_CENTRE);
            plots.add(new LabPlot(machines[index], new LabBox(min, max), sign, machine));
        }
        return plots;
    }

    /**
     * Answers the row's length along x.
     *
     * @return the blocks from the first plot's west edge to the last plot's east edge
     */
    static int rowLength() {
        int machineCount = LabMachine.values().length;
        return machineCount * PLOT_SIZE + (machineCount - 1) * PLOT_GAP;
    }

    /**
     * Answers how high above the floor a machine block stands: the tap hangs
     * over an air gap so its drip has a floor to land on, every other machine stands on the floor.
     *
     * @param machine the machine
     * @return the machine block's height above the floor
     */
    private static int machineHeight(LabMachine machine) {
        return machine == LabMachine.TAP ? 1 + TAP_AIR_GAP : 1;
    }

    /**
     * Answers the blocks a plot's bay sets above the floor, before its rig fills them.
     *
     * @param plot the plot
     * @return the bay's placements
     */
    static List<LabPlacement> bayBlocks(LabPlot plot) {
        return BAY_BLOCKS.get(plot.machine()).apply(plot);
    }

    /**
     * Answers a bay of one machine block.
     *
     * @param state the machine's block state
     * @return the bay's placements, given its plot
     */
    private static Function<LabPlot, List<LabPlacement>> single(String state) {
        return plot -> List.of(LabPlacement.block(plot.machineOffset(), state));
    }

    /**
     * Answers a bay of a machine block with a canister block on its top face.
     *
     * @param state the machine's block state
     * @return the bay's placements, given its plot
     */
    private static Function<LabPlot, List<LabPlacement>> canisterTopped(String state) {
        return plot -> List.of(LabPlacement.block(plot.machineOffset(), state),
                LabPlacement.block(plot.machineOffset().shifted(0, 1, 0), CANISTER));
    }

    /**
     * Answers the gasket run's bay: two canister blocks either side of a choral gasket block.
     *
     * @param plot the choral gasket plot
     * @return the bay's placements
     */
    private static List<LabPlacement> gasketRun(LabPlot plot) {
        return List.of(
                LabPlacement.block(gasketSource(plot), CANISTER),
                LabPlacement.block(plot.machineOffset(), CHORAL_GASKET),
                LabPlacement.block(gasketReceiver(plot), CANISTER));
    }

    /**
     * Answers where the gasket run's transmitting canister block stands.
     *
     * @param plot the choral gasket plot
     * @return the source canister block's offset
     */
    public static LabOffset gasketSource(LabPlot plot) {
        return plot.machineOffset().shifted(-GASKET_SPREAD, 0, 0);
    }

    /**
     * Answers where the gasket run's receiving canister block stands.
     *
     * @param plot the choral gasket plot
     * @return the receiver canister block's offset
     */
    public static LabOffset gasketReceiver(LabPlot plot) {
        return plot.machineOffset().shifted(GASKET_SPREAD, 0, 0);
    }
}
