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
        LabBox floor = LabLayout.floorBox(plan);
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
            if (placement.offset().y() != 0 || plan.range().bounds().contains(placement.offset())) {
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
    void pensStandClearOfPlotsAndEachOther() {
        List<LabBox> zones = new java.util.ArrayList<>();
        plan.plots().forEach(plot -> zones.add(plot.bounds()));
        plan.pens().forEach(pen -> zones.add(pen.bounds()));
        zones.add(plan.range().bounds());
        for (int i = 0; i < zones.size(); i++) {
            for (int j = i + 1; j < zones.size(); j++) {
                assertFalse(zones.get(i).intersects(zones.get(j)), zones.get(i) + " / " + zones.get(j));
            }
        }
    }

    @Test
    void everyPenRingIsFencedAndRoofed() {
        for (LabPen pen : plan.pens()) {
            LabBox bounds = pen.bounds();
            for (int x = bounds.min().x(); x <= bounds.max().x(); x++) {
                for (int z = bounds.min().z(); z <= bounds.max().z(); z++) {
                    boolean ring = LabPens.isRing(x - bounds.min().x(), z - bounds.min().z());
                    for (int y = bounds.min().y() + 1; y < bounds.max().y(); y++) {
                        String expected = ring ? LabPens.FENCE_BLOCK : null;
                        assertEquals(expected, stateAt(new LabOffset(x, y, z)), pen.displayName());
                    }
                    assertEquals(LabPens.ROOF_BLOCK, stateAt(new LabOffset(x, bounds.max().y(), z)));
                }
            }
        }
    }

    @Test
    void everyPenMobSpawnsInsideItsPen() {
        int planned = plan.pens().stream().mapToInt(pen -> pen.mobs().size()).sum();
        assertEquals(planned, plan.spawns().size());
        for (LabPen pen : plan.pens()) {
            List<String> inside = plan.spawns().stream()
                    .filter(spawn -> pen.interior().contains(spawn.offset()))
                    .map(LabSpawn::entityId).toList();
            assertEquals(pen.mobs(), inside);
        }
        assertEquals(Set.of("Passive", "Hostile", "Undead", "Blaze"),
                Set.copyOf(plan.pens().stream().map(LabPen::displayName).toList()));
    }

    @Test
    void rangeTargetsStandAtThrowingDistanceFromTheFiringLine() {
        LabRange range = plan.range();
        int lineZ = range.firingLine().min().z();
        Set<String> types = new HashSet<>();
        for (LabPlacement target : range.targets()) {
            assertEquals(LabTargetRange.THROW_DISTANCE, target.offset().z() - lineZ);
            assertTrue(target.offset().x() >= range.firingLine().min().x());
            assertTrue(target.offset().x() <= range.firingLine().max().x());
            assertEquals(target.blockState(), stateAt(target.offset()));
            types.add(target.blockState());
        }
        Set<String> planned = new HashSet<>(LabTargetRange.SOLID_TARGETS);
        planned.addAll(LabTargetRange.LIQUID_TARGETS);
        assertEquals(planned, types);
        for (int x = range.firingLine().min().x(); x <= range.firingLine().max().x(); x++) {
            assertEquals(LabTargetRange.FIRING_LINE_BLOCK, stateAt(new LabOffset(x, 0, lineZ)));
        }
    }

    @Test
    void liquidTargetsSitInTheFloorOverABasin() {
        for (LabPlacement target : plan.range().targets()) {
            if (LabTargetRange.LIQUID_TARGETS.contains(target.blockState())) {
                assertEquals(0, target.offset().y());
                assertEquals(LabTargetRange.BASIN_BLOCK, stateAt(target.offset().shifted(0, -1, 0)));
            }
        }
    }

    @Test
    void everyBaySetsItsMachineBlock() {
        for (LabPlot plot : plan.plots()) {
            String state = stateAt(plot.machineOffset());
            assertTrue(state != null && state.startsWith("goo:" + plot.machine().blockPath()), plot.machine().name());
        }
    }

    @Test
    void supplyRowTakesOneStationPerTypeIdItIsGiven() {
        List<String> ids = List.of("goo:rock", "goo:blaze", "gootest:seventeenth");
        LabPlan supplied = LabLayout.plan(ids);
        assertEquals(ids, supplied.supply().stations().stream().map(LabSupply.Station::gooTypeId).toList());
        for (LabSupply.Station station : supplied.supply().stations()) {
            assertEquals(LabSupply.CANISTER_BLOCK, stateIn(supplied, station.canisterOffset()));
            assertEquals(LabSupply.CHEST_BLOCK, stateIn(supplied, station.chestOffset()));
            LabPlacement sign = supplied.placements().stream()
                    .filter(p -> p.offset().equals(station.signOffset())).findFirst().orElseThrow();
            assertEquals(station.gooTypeId(), sign.signText());
            assertTrue(supplied.supply().bounds().contains(station.chestOffset()));
        }
        assertTrue(plan.supply().stations().isEmpty());
    }

    @Test
    void supplyRowStandsClearOfTheOtherZones() {
        List<String> ids = java.util.stream.IntStream.range(0, 24).mapToObj(i -> "goo:type" + i).toList();
        LabPlan supplied = LabLayout.plan(ids);
        LabBox row = supplied.supply().bounds();
        supplied.plots().forEach(plot -> assertFalse(row.intersects(plot.bounds())));
        supplied.pens().forEach(pen -> assertFalse(row.intersects(pen.bounds())));
        assertFalse(row.intersects(supplied.range().bounds()));
        assertTrue(LabLayout.floorBox(supplied).contains(row.max().shifted(0, -1, 0)));
    }

    private static String stateIn(LabPlan target, LabOffset offset) {
        return target.placements().stream().filter(p -> p.offset().equals(offset))
                .map(LabPlacement::blockState).findFirst().orElse(null);
    }

    private String stateAt(LabOffset offset) {
        return plan.placements().stream().filter(p -> p.offset().equals(offset))
                .map(LabPlacement::blockState).findFirst().orElse(null);
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
