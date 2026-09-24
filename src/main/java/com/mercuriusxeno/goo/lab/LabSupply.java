package com.mercuriusxeno.goo.lab;

import java.util.ArrayList;
import java.util.List;

/**
 * The supply row: one station per registered goo type, each a sign naming the
 * type, a canister block the stocking fills with it and a chest of the items
 * that decompose into it (decision lab-iterates-the-registries). The row is
 * laid from whatever type ids the build reads from the registry, so a type a
 * datapack adds takes a station with no lab edit.
 */
public final class LabSupply {

    /**
     * Blocks between neighbouring stations.
     */
    static final int STATION_SPACING = 2;
    /**
     * A station's depth north to south: sign, canister block, chest.
     */
    static final int STATION_DEPTH = 3;
    /**
     * The canister block a station's type fills.
     */
    static final String CANISTER_BLOCK = "goo:canister";
    /**
     * The chest a station's items fill, facing north toward the walkway.
     */
    static final String CHEST_BLOCK = "minecraft:chest[facing=north]";
    /**
     * A standing sign turned to face north, toward a player on the walkway.
     */
    private static final String SIGN_BLOCK = "minecraft:oak_sign[rotation=8]";

    private LabSupply() {
    }

    /**
     * One supply station.
     *
     * @param gooTypeId      the goo type id the station serves
     * @param signOffset     where the sign naming the type stands
     * @param canisterOffset where the canister block of the type stands
     * @param chestOffset    where the chest of the type's items stands
     */
    public record Station(String gooTypeId, LabOffset signOffset, LabOffset canisterOffset, LabOffset chestOffset) {
    }

    /**
     * The row of stations.
     *
     * @param stations one station per goo type, in the order the ids arrived
     * @param bounds   the row's footprint, empty-width when no type arrived
     */
    public record Row(List<Station> stations, LabBox bounds) {

        /**
         * Copies the station list so a row stays fixed once made.
         *
         * @param stations the stations
         * @param bounds   the row's footprint
         */
        public Row {
            stations = List.copyOf(stations);
        }
    }

    /**
     * Lays one station per goo type along +x from the given corner.
     *
     * @param corner     the row's north-west floor corner
     * @param gooTypeIds the goo type ids, one station each
     * @return the row
     */
    static Row row(LabOffset corner, List<String> gooTypeIds) {
        List<Station> stations = new ArrayList<>();
        for (int index = 0; index < gooTypeIds.size(); index++) {
            LabOffset base = corner.shifted(index * STATION_SPACING, 1, 0);
            LabOffset chest = base.shifted(0, 0, STATION_DEPTH - 1);
            stations.add(new Station(gooTypeIds.get(index), base, base.shifted(0, 0, 1), chest));
        }
        int length = Math.max(1, (gooTypeIds.size() - 1) * STATION_SPACING + 1);
        LabBox bounds = new LabBox(corner, corner.shifted(length - 1, 1, STATION_DEPTH - 1));
        return new Row(stations, bounds);
    }

    /**
     * Answers the row's placements: each station's sign, canister block and chest.
     *
     * @param row the row
     * @return the placements, stocked afterwards
     */
    static List<LabPlacement> rowBlocks(Row row) {
        List<LabPlacement> placements = new ArrayList<>();
        for (Station station : row.stations()) {
            placements.add(new LabPlacement(station.signOffset(), SIGN_BLOCK, station.gooTypeId()));
            placements.add(LabPlacement.block(station.canisterOffset(), CANISTER_BLOCK));
            placements.add(LabPlacement.block(station.chestOffset(), CHEST_BLOCK));
        }
        return placements;
    }
}
