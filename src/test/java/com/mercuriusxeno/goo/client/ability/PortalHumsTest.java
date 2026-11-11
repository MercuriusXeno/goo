package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.network.HolesSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which portal hums play: one starts at the centre of every portal and
 * wormhole mouth newly standing, linked or inert, and one stops for each that
 * fell or closed; the gates standing are those the client draws in the local
 * player's level.
 * decision standing-portals-loop-a-soft-ambient-hum
 * decision wormhole-mouths-take-the-whoosh-and-the-hum
 */
class PortalHumsTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    private static final Portal ENTRY = wall(OVERWORLD, new BlockPos(0, 64, 0), Direction.SOUTH);
    private static final Portal EXIT = wall(OVERWORLD, new BlockPos(10, 64, 0), Direction.SOUTH);
    private static final Portal INERT = wall(OVERWORLD, new BlockPos(20, 64, 0), Direction.SOUTH);
    /** Another player's linked pair, told of to every client in the level. */
    private static final Portal THEIR_ENTRY = wall(OVERWORLD, new BlockPos(30, 64, 0), Direction.NORTH);
    private static final Portal THEIR_EXIT = wall(OVERWORLD, new BlockPos(40, 64, 0), Direction.NORTH);
    private static final Portal IN_THE_NETHER = wall(NETHER, new BlockPos(0, 64, 0), Direction.SOUTH);
    private static final Gate STRUCK_MOUTH = wall(OVERWORLD, new BlockPos(0, 70, 5), Direction.NORTH).gate();
    private static final Gate FAR_MOUTH = wall(OVERWORLD, new BlockPos(0, 70, 8), Direction.SOUTH).gate();
    private static final Set<Gate> PORTALS = Set.of(ENTRY.gate(), EXIT.gate(), INERT.gate());

    private static Portal wall(ResourceKey<Level> dimension, BlockPos lower, Direction face) {
        return new Portal(GlobalPos.of(dimension, lower), face, Direction.UP);
    }

    private static Set<Vec3> centresOf(Set<Gate> gates) {
        return gates.stream().map(Gate::center).collect(Collectors.toSet());
    }

    @Nested
    class Changes {

        @Test
        void eachPortalNewlyStandingStartsAHumAtItsCentre() {
            PortalHums.Changes changes = PortalHums.changes(PORTALS, Set.of());
            assertEquals(centresOf(PORTALS), centresOf(changes.starts()));
            assertEquals(Set.of(), changes.stops());
        }

        @Test
        void aPortalFallingStopsItsHumAndStartsNone() {
            PortalHums.Changes changes = PortalHums.changes(Set.of(ENTRY.gate(), EXIT.gate()), PORTALS);
            assertEquals(Set.of(), changes.starts());
            assertEquals(Set.of(INERT.gate()), changes.stops());
        }

        @Test
        void anOpenHolesMouthsStartTwoHumsAtTheirCentres() {
            PortalHums.Changes changes = PortalHums.changes(Set.of(STRUCK_MOUTH, FAR_MOUTH), Set.of());
            assertEquals(Set.of(STRUCK_MOUTH.center(), FAR_MOUTH.center()), centresOf(changes.starts()));
        }

        @Test
        void aHoleClosingStopsBothMouthsHums() {
            Set<Gate> humming = Set.of(ENTRY.gate(), STRUCK_MOUTH, FAR_MOUTH);
            PortalHums.Changes changes = PortalHums.changes(Set.of(ENTRY.gate()), humming);
            assertEquals(Set.of(), changes.starts());
            assertEquals(Set.of(STRUCK_MOUTH, FAR_MOUTH), changes.stops());
        }
    }

    @Nested
    class Standing {

        @Test
        void everyPortalDrawnInTheLevelStandsLinkedOrInertAndEveryMouth() {
            Set<Gate> standing = PortalHums.standing(List.of(ENTRY, EXIT, INERT, IN_THE_NETHER),
                    List.of(new QuantumPortals.Link(ENTRY, EXIT), new QuantumPortals.Link(THEIR_ENTRY, THEIR_EXIT)),
                    List.of(new HolesSyncPayload.Pair(STRUCK_MOUTH, FAR_MOUTH)), OVERWORLD);
            assertEquals(Set.of(ENTRY.gate(), EXIT.gate(), INERT.gate(), THEIR_ENTRY.gate(), THEIR_EXIT.gate(),
                    STRUCK_MOUTH, FAR_MOUTH), standing);
        }
    }
}
