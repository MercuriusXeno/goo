package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player's portals link within one dimension alone: a link to a portal in
 * another dimension is refused, and choosing holds
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
        assertTrue(portals.linkOf(OWNER).isEmpty());
        assertEquals(Optional.of(HERE), portals.choosing(OWNER));
    }

    @Test
    void linkWithinTheDimensionLands() {
        QuantumPortals portals = choosingFromHere();
        assertEquals(Optional.of(new QuantumPortals.Link(HERE, THERE)),
                portals.link(OWNER, THERE).map(QuantumPortals.Relink::now));
    }
}
