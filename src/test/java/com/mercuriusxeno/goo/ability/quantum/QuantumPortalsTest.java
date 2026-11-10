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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player's portals link within one dimension alone: a link to a portal in
 * another dimension is refused, and choosing holds. A portal links to one
 * other portal, and a player keeps as many linked pairs as they place:
 * linking a new pair leaves every other pair linked, and relinking a portal
 * breaks only the pairs that held it
 * (decision portals-link-by-looking-through-portal-vision).
 */
class QuantumPortalsTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    private static final UUID OWNER = new UUID(1L, 2L);
    private static final Portal HERE = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(0, 64, 0)), Direction.SOUTH, Direction.UP);
    private static final Portal THERE = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(9, 64, 0)), Direction.SOUTH, Direction.UP);
    private static final Portal ELSEWHERE = new Portal(GlobalPos.of(NETHER, new BlockPos(0, 64, 9)), Direction.SOUTH, Direction.UP);
    private static final Portal THIRD = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(0, 64, 20)), Direction.SOUTH, Direction.UP);
    private static final Portal FOURTH = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(9, 64, 20)), Direction.SOUTH, Direction.UP);

    private static QuantumPortals choosingFromHere() {
        QuantumPortals portals = new QuantumPortals();
        portals.place(OWNER, HERE);
        portals.place(OWNER, THERE);
        portals.place(OWNER, ELSEWHERE);
        portals.choose(OWNER, Optional.of(HERE));
        return portals;
    }

    @Test
    void linkToAPortalInAnotherDimensionIsRefused() {
        QuantumPortals portals = choosingFromHere();
        assertTrue(portals.link(OWNER, ELSEWHERE).isEmpty());
        assertTrue(portals.linksOf(OWNER).isEmpty());
        assertEquals(Optional.of(HERE), portals.choosing(OWNER));
    }

    @Test
    void linkWithinTheDimensionLands() {
        QuantumPortals portals = choosingFromHere();
        assertEquals(Optional.of(new QuantumPortals.Link(HERE, THERE)),
                portals.link(OWNER, THERE).map(QuantumPortals.Relink::now));
    }

    @Test
    void aSecondPairLeavesTheFirstLinked() {
        QuantumPortals portals = choosingFromHere();
        portals.link(OWNER, THERE);
        portals.place(OWNER, THIRD);
        portals.place(OWNER, FOURTH);
        portals.choose(OWNER, Optional.of(THIRD));

        portals.link(OWNER, FOURTH);

        assertEquals(List.of(new QuantumPortals.Link(HERE, THERE), new QuantumPortals.Link(THIRD, FOURTH)),
                portals.linksOf(OWNER), "Both pairs should stand linked");
        assertEquals(Optional.of(THERE), portals.partnerOf(HERE), "The first pair should still carry through");
        assertEquals(Optional.of(FOURTH), portals.partnerOf(THIRD), "The second pair should carry through");
    }

    @Test
    void relinkingAPortalBreaksOnlyThePairThatHeldIt() {
        QuantumPortals portals = choosingFromHere();
        portals.link(OWNER, THERE);
        portals.place(OWNER, THIRD);
        portals.place(OWNER, FOURTH);
        portals.choose(OWNER, Optional.of(THIRD));
        portals.link(OWNER, FOURTH);
        portals.choose(OWNER, Optional.of(HERE));

        Optional<QuantumPortals.Relink> relink = portals.link(OWNER, THIRD);

        assertEquals(List.of(new QuantumPortals.Link(HERE, THERE), new QuantumPortals.Link(THIRD, FOURTH)),
                relink.map(QuantumPortals.Relink::before).orElse(List.of()), "Both pairs holding the two should break");
        assertEquals(List.of(new QuantumPortals.Link(HERE, THIRD)), portals.linksOf(OWNER),
                "Only the new pair should stand");
        assertTrue(portals.partnerOf(THERE).isEmpty(), "THERE, its pair broken, should stand inert");
    }
}
