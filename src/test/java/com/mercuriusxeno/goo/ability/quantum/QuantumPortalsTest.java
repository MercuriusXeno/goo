package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A player holds one pair of portals: a new portal of a color takes that
 * color's place, the blue and the orange link while both stand in one
 * dimension, a fallen portal takes its link down, and a save from before one
 * pair per player loads the newest portal of each color
 * (decisions player-holds-one-pair-of-portals, portals-link-by-looking-through-portal-vision).
 */
class QuantumPortalsTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    private static final UUID OWNER = new UUID(1L, 2L);
    private static final Portal HERE = wall(OVERWORLD, new BlockPos(0, 64, 0), PortalColor.BLUE);
    private static final Portal THERE = wall(OVERWORLD, new BlockPos(9, 64, 0), PortalColor.ORANGE);
    private static final Portal ELSEWHERE = wall(NETHER, new BlockPos(0, 64, 9), PortalColor.ORANGE);
    private static final Portal BLUE_MOVED = wall(OVERWORLD, new BlockPos(0, 64, 20), PortalColor.BLUE);

    private static Portal wall(ResourceKey<Level> dimension, BlockPos block, PortalColor color) {
        return new Portal(GlobalPos.of(dimension, block), Direction.SOUTH, Direction.UP, color);
    }

    private static QuantumPortals paired() {
        QuantumPortals portals = new QuantumPortals();
        portals.place(OWNER, HERE);
        portals.place(OWNER, THERE);
        return portals;
    }

    @Test
    void theBlueAndTheOrangeLinkTheBlueTheEntry() {
        QuantumPortals portals = paired();
        assertEquals(Optional.of(new QuantumPortals.Link(HERE, THERE)), portals.pairOf(OWNER));
        assertEquals(Optional.of(THERE), portals.partnerOf(HERE));
        assertEquals(Optional.of(HERE), portals.partnerOf(THERE));
    }

    @Test
    void aNewBlueMovesTheBlueAndLinksToTheOrange() {
        QuantumPortals portals = paired();
        portals.place(OWNER, BLUE_MOVED);
        assertEquals(List.of(BLUE_MOVED, THERE), portals.portalsOf(OWNER));
        assertEquals(Optional.of(BLUE_MOVED), portals.partnerOf(THERE));
    }

    @Test
    void aPortalAlreadyStandingPlacesNothingNew() {
        QuantumPortals portals = paired();
        assertEquals(false, portals.place(OWNER, HERE));
    }

    @Test
    void portalsInTwoDimensionsLinkNot() {
        QuantumPortals portals = new QuantumPortals();
        portals.place(OWNER, HERE);
        portals.place(OWNER, ELSEWHERE);
        assertEquals(Optional.empty(), portals.pairOf(OWNER));
        assertEquals(List.of(), portals.links());
    }

    @Test
    void aFallenPortalTakesItsLinkDown() {
        QuantumPortals portals = paired();
        assertEquals(Optional.of(new QuantumPortals.Linked(OWNER, new QuantumPortals.Link(HERE, THERE))),
                portals.fall(HERE));
        assertEquals(List.of(THERE), portals.portalsOf(OWNER));
        assertEquals(Optional.empty(), portals.partnerOf(THERE));
    }

    /**
     * A save from before one pair per player held every portal placed, oldest
     * first, and every pair linked, the exit the orange one: it loads as the
     * newest blue and the newest orange.
     */
    @Test
    void aSaveOfManyPortalsKeepsTheNewestOfEachColor() {
        Portal firstEntry = HERE.withColor(PortalColor.BLUE);
        Portal firstExit = THERE.withColor(PortalColor.BLUE);
        Portal secondEntry = BLUE_MOVED;
        Portal secondExit = wall(OVERWORLD, new BlockPos(9, 64, 20), PortalColor.BLUE);
        QuantumPortals portals = new QuantumPortals(List.of(),
                List.of(new QuantumPortals.Placed(OWNER, List.of(firstEntry, firstExit, secondEntry, secondExit),
                        Optional.of(secondExit))),
                List.of(new QuantumPortals.Linked(OWNER, new QuantumPortals.Link(firstEntry, firstExit)),
                        new QuantumPortals.Linked(OWNER, new QuantumPortals.Link(secondEntry, secondExit))));
        assertEquals(List.of(secondEntry, secondExit.withColor(PortalColor.ORANGE)), portals.portalsOf(OWNER));
    }
}
