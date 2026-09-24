package com.mercuriusxeno.goo.lab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The Goo Lab as a pure list of placements relative to the lab origin
 * (decision lab-built-from-code): a floor plate holding, north to south, the
 * machine row of {@link LabBays}, the supply row of {@link LabSupply}, the
 * mob pens of {@link LabPens} and the
 * target range of {@link LabTargetRange}. Each zone adds its placements to
 * one plan, so the build, the rebuild and the tests read one source.
 */
public final class LabLayout {

    /**
     * Where the lab origin stands in the Goo Lab world: the top layer of the
     * template's superflat floor, so the plate replaces the surface block.
     */
    public static final LabOffset WORLD_ORIGIN = new LabOffset(0, -61, 0);
    /**
     * Blocks of floor between the outermost zone and the plate's edge.
     */
    static final int FLOOR_MARGIN = 2;
    /**
     * Blocks of walkway between one zone and the next, north to south.
     */
    static final int ZONE_GAP = 3;
    /**
     * Headroom above the floor that the machine plots claim for their bays.
     */
    static final int PLOT_HEADROOM = LabBays.PLOT_HEADROOM;
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
     * The machine row's north-west corner.
     */
    private static final LabOffset ROW_CORNER = new LabOffset(FLOOR_MARGIN, 0, FLOOR_MARGIN);
    /**
     * The supply row's north-west corner, a zone gap south of the machine row.
     */
    private static final LabOffset SUPPLY_CORNER = ROW_CORNER.shifted(0, 0, LabBays.PLOT_SIZE + ZONE_GAP);
    /**
     * The pens' north-west corner, a zone gap south of the supply row, leaving room for the pens' signs.
     */
    private static final LabOffset PEN_CORNER = SUPPLY_CORNER.shifted(0, 0, LabSupply.STATION_DEPTH + ZONE_GAP + 1);
    /**
     * The firing line's west end, a zone gap south of the pens.
     */
    private static final LabOffset RANGE_CORNER = PEN_CORNER.shifted(0, 0, LabPens.OUTER_SIZE + ZONE_GAP + 1);

    private LabLayout() {
    }

    /**
     * Answers the lab plan with no supply stations, for the parts that read no registry.
     *
     * @return the placements, zones, spawns and bounds of one build
     */
    public static LabPlan plan() {
        return plan(List.of());
    }

    /**
     * Answers the whole lab plan, with one supply station per goo type id.
     *
     * @param gooTypeIds the goo type ids the build read from the registry, in station order
     * @return the placements, zones, spawns and bounds of one build
     */
    public static LabPlan plan(List<String> gooTypeIds) {
        List<LabPlot> plots = LabBays.plots(ROW_CORNER);
        LabSupply.Row supply = LabSupply.row(SUPPLY_CORNER, gooTypeIds);
        List<LabPen> pens = LabPens.pens(PEN_CORNER);
        LabRange range = LabTargetRange.range(RANGE_CORNER);
        Map<LabOffset, LabPlacement> byOffset = new LinkedHashMap<>();
        LabBox floor = floorAround(zoneBoxes(plots, supply, pens, range));
        floorPlacements(floor, plots).forEach(p -> byOffset.put(p.offset(), p));
        plots.forEach(plot -> put(byOffset, bayAndSign(plot)));
        put(byOffset, LabSupply.rowBlocks(supply));
        pens.forEach(pen -> put(byOffset, LabPens.penBlocks(pen, SIGN_BLOCK)));
        put(byOffset, LabTargetRange.rangeBlocks(range));
        List<LabSpawn> spawns = pens.stream().flatMap(pen -> LabPens.spawns(pen).stream()).toList();
        List<LabPlacement> placements = new ArrayList<>(byOffset.values());
        LabBox bounds = enclose(placements, zoneBoxes(plots, supply, pens, range));
        return new LabPlan(placements, plots, supply, pens, range, spawns, bounds);
    }

    /**
     * Answers the floor plate's extent under a plan.
     *
     * @param plan the plan
     * @return the floor box at y 0
     */
    static LabBox floorBox(LabPlan plan) {
        return floorAround(zoneBoxes(plan.plots(), plan.supply(), plan.pens(), plan.range()));
    }

    /**
     * Answers every zone's box.
     *
     * @param plots  the machine plots
     * @param supply the supply row
     * @param pens   the mob pens
     * @param range  the target range
     * @return the boxes
     */
    private static List<LabBox> zoneBoxes(List<LabPlot> plots, LabSupply.Row supply, List<LabPen> pens, LabRange range) {
        List<LabBox> boxes = new ArrayList<>();
        plots.forEach(plot -> boxes.add(plot.bounds()));
        boxes.add(supply.bounds());
        pens.forEach(pen -> boxes.add(pen.bounds()));
        boxes.add(range.bounds());
        return boxes;
    }

    /**
     * Answers the floor plate's extent: every zone plus a margin all round, one block thick, from the origin.
     *
     * @param zones the zones' boxes
     * @return the floor box at y 0
     */
    private static LabBox floorAround(List<LabBox> zones) {
        int maxX = zones.stream().mapToInt(box -> box.max().x()).max().orElse(0) + FLOOR_MARGIN;
        int maxZ = zones.stream().mapToInt(box -> box.max().z()).max().orElse(0) + FLOOR_MARGIN;
        return new LabBox(new LabOffset(0, 0, 0), new LabOffset(maxX, 0, maxZ));
    }

    /**
     * Adds placements, each replacing whatever an earlier zone set at its offset.
     *
     * @param byOffset   the plan so far, keyed by offset
     * @param placements the placements to add
     */
    private static void put(Map<LabOffset, LabPlacement> byOffset, List<LabPlacement> placements) {
        placements.forEach(p -> byOffset.put(p.offset(), p));
    }

    /**
     * Answers a plot's bay blocks and the sign naming its machine.
     *
     * @param plot the plot
     * @return the plot's placements above the floor
     */
    private static List<LabPlacement> bayAndSign(LabPlot plot) {
        List<LabPlacement> placements = new ArrayList<>(LabBays.bayBlocks(plot));
        placements.add(new LabPlacement(plot.signOffset(), SIGN_BLOCK, plot.machine().displayName()));
        return placements;
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
     * Answers the smallest box holding every placement and zone.
     *
     * @param placements the plan's placements
     * @param zones      the zones' boxes
     * @return the plan's bounds
     */
    private static LabBox enclose(List<LabPlacement> placements, List<LabBox> zones) {
        List<LabOffset> corners = Stream.concat(
                placements.stream().map(LabPlacement::offset),
                zones.stream().flatMap(box -> Stream.of(box.min(), box.max()))).toList();
        LabOffset min = new LabOffset(
                corners.stream().mapToInt(LabOffset::x).min().orElse(0),
                corners.stream().mapToInt(LabOffset::y).min().orElse(0),
                corners.stream().mapToInt(LabOffset::z).min().orElse(0));
        LabOffset max = new LabOffset(
                corners.stream().mapToInt(LabOffset::x).max().orElse(0),
                corners.stream().mapToInt(LabOffset::y).max().orElse(0),
                corners.stream().mapToInt(LabOffset::z).max().orElse(0));
        return new LabBox(min, max);
    }
}
