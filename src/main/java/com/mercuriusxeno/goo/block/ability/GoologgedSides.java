package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A plant goologged into a goo cell, as a waterlogged block shares its space
 * with water: the plant hangs on a side the cell's goo coats and stands in
 * the cell's own block space, so growing it displaces no goo. A cell whose
 * goo grows a plant carries one of these flags for each of its four sides;
 * Vines' cell grows vines, and another plant can grow through the same sides.
 * vines-goo-on-walls-places-goologged-vines-and-depletes
 */
public final class GoologgedSides {

    /** Whether a plant hangs on the cell's north wall. */
    public static final BooleanProperty NORTH = BooleanProperty.create("plant_north");
    /** Whether a plant hangs on the cell's east wall. */
    public static final BooleanProperty EAST = BooleanProperty.create("plant_east");
    /** Whether a plant hangs on the cell's south wall. */
    public static final BooleanProperty SOUTH = BooleanProperty.create("plant_south");
    /** Whether a plant hangs on the cell's west wall. */
    public static final BooleanProperty WEST = BooleanProperty.create("plant_west");

    /** The sides a plant grows on, in the order the goo grows them. */
    public static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST);

    private static final Map<Direction, BooleanProperty> PLANTS = Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST,
            Direction.SOUTH, SOUTH, Direction.WEST, WEST);

    private GoologgedSides() {
    }

    /**
     * Where the goo grows its next plant and the layers it keeps.
     *
     * @param side       the side the plant grows on, empty where none grows
     * @param layersLeft the layers the goo holds once it has grown it
     */
    public record Planting(Optional<Direction> side, int layersLeft) {
    }

    /**
     * The state property flagging a plant on one side.
     *
     * @param side a horizontal side
     * @return the property
     */
    public static BooleanProperty plantProperty(Direction side) {
        return PLANTS.get(side);
    }

    /**
     * Whether a plant hangs on a side of a cell.
     *
     * @param state the cell's state
     * @param side  a horizontal side
     * @return true where the cell carries the side's flag and it is raised
     */
    public static boolean plantedOn(BlockState state, Direction side) {
        BooleanProperty plant = plantProperty(side);
        return state.hasProperty(plant) && state.getValue(plant);
    }

    /**
     * Where goo grows its next plant: the first coated side bearing none yet,
     * spending the layers a plant costs; nowhere, the layers kept, where
     * every coated side bears one or the goo holds fewer layers than a plant
     * costs.
     *
     * @param coated         the sides the goo coats where a plant could hang, in growing order
     * @param planted        the sides already bearing a plant
     * @param layers         the layers the goo holds
     * @param layersPerPlant the layers one plant costs
     * @return the side to grow on and the layers left
     */
    public static Planting nextPlanting(List<Direction> coated, Set<Direction> planted, int layers,
                                        int layersPerPlant) {
        if (layers < layersPerPlant) {
            return new Planting(Optional.empty(), layers);
        }
        Optional<Direction> next = coated.stream().filter(side -> !planted.contains(side)).findFirst();
        return new Planting(next, next.isPresent() ? layers - layersPerPlant : layers);
    }
}
