package com.mercuriusxeno.goo.lab;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the Goo Lab plan without a level: a floor plate, one signed plot per
 * machine block, and no two plots sharing ground.
 */
class LabLayoutTest {

    private static final Set<String> MACHINE_BLOCKS = Set.of(
            "tap", "vat", "crucible", "hub", "plexer", "reactor", "choral_gasket");

    private final LabPlan plan = LabLayout.plan();

    @Test
    void everyMachineBlockOwnsOnePlot() {
        Set<LabMachine> plotted = EnumSet.noneOf(LabMachine.class);
        plan.plots().forEach(plot -> assertTrue(plotted.add(plot.machine()), plot.machine().name()));
        assertEquals(EnumSet.allOf(LabMachine.class), plotted);
        Set<String> paths = new HashSet<>();
        plotted.forEach(machine -> paths.add(machine.blockPath()));
        assertEquals(MACHINE_BLOCKS, paths);
    }

    @Test
    void everyPlotHoldsASignNamingItsMachine() {
        for (LabPlot plot : plan.plots()) {
            List<LabPlacement> signs = plan.placements().stream()
                    .filter(LabPlacement::isSign)
                    .filter(p -> plot.bounds().contains(p.offset()))
                    .toList();
            assertEquals(1, signs.size(), plot.machine().name());
            LabPlacement sign = signs.getFirst();
            assertEquals(plot.signOffset(), sign.offset());
            assertEquals(plot.machine().displayName(), sign.signText());
            assertTrue(sign.blockState().startsWith(LabLayout.SIGN_BLOCK));
        }
    }

    @Test
    void noTwoPlotsIntersect() {
        List<LabPlot> plots = plan.plots();
        for (int i = 0; i < plots.size(); i++) {
            for (int j = i + 1; j < plots.size(); j++) {
                LabBox a = plots.get(i).bounds();
                LabBox b = plots.get(j).bounds();
                assertFalse(a.intersects(b), plots.get(i).machine() + " / " + plots.get(j).machine());
            }
        }
    }

    @Test
    void plotOffsetsAreDistinct() {
        Set<LabOffset> corners = new HashSet<>();
        plan.plots().forEach(plot -> assertTrue(corners.add(plot.bounds().min())));
    }

    @Test
    void floorPlateCoversTheWholeFootprint() {
        LabBox floor = LabLayout.floorBox();
        Set<LabOffset> floorCells = new HashSet<>();
        plan.placements().stream().filter(p -> p.offset().y() == 0).forEach(p -> floorCells.add(p.offset()));
        int width = floor.max().x() - floor.min().x() + 1;
        int depth = floor.max().z() - floor.min().z() + 1;
        assertEquals(width * depth, floorCells.size());
        plan.plots().forEach(plot -> assertTrue(floor.contains(plot.bounds().min())));
        plan.plots().forEach(plot -> assertTrue(floor.contains(plot.bounds().max().shifted(0, -LabLayout.PLOT_HEADROOM, 0))));
    }

    @Test
    void floorMarksPlotGroundAndWalkwayApart() {
        for (LabPlacement placement : plan.placements()) {
            if (placement.offset().y() != 0) {
                continue;
            }
            boolean underPlot = plan.plots().stream().anyMatch(plot -> plot.bounds().contains(placement.offset()));
            assertEquals(underPlot ? LabLayout.PLOT_BLOCK : LabLayout.FLOOR_BLOCK, placement.blockState());
        }
    }

    @Test
    void boundsHoldEveryPlacementAndPlot() {
        plan.placements().forEach(p -> assertTrue(plan.bounds().contains(p.offset()), p.toString()));
        plan.plots().forEach(plot -> assertTrue(plan.bounds().contains(plot.bounds().max())));
    }

    @Test
    void boxesTouchingOnlyAtAGapDoNotIntersect() {
        LabBox left = new LabBox(new LabOffset(0, 0, 0), new LabOffset(4, 4, 4));
        LabBox adjacent = new LabBox(new LabOffset(5, 0, 0), new LabOffset(9, 4, 4));
        LabBox sharingEdge = new LabBox(new LabOffset(4, 0, 0), new LabOffset(9, 4, 4));
        assertFalse(left.intersects(adjacent));
        assertTrue(left.intersects(sharingEdge));
        assertTrue(sharingEdge.intersects(left));
    }
}
