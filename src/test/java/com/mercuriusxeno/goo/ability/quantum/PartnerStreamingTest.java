package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The server streams a viewer the partner's chunk and its eight neighbors
 * while the viewer looks into a linked portal, however far the pair stands
 * apart, and nothing while it looks elsewhere
 * (decision linked-partner-chunks-stream-to-any-viewer).
 */
class PartnerStreamingTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A wall frame whose face looks south, onto z 1. */
    private static final Portal ENTRY = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(0, 64, 0)), Direction.SOUTH,
            Direction.UP);
    /** Its partner, two thousand blocks east, far past any view distance. */
    private static final Portal EXIT = new Portal(GlobalPos.of(OVERWORLD, new BlockPos(2000, 64, 0)),
            Direction.SOUTH, Direction.UP);
    private static final List<QuantumPortals.Link> LINKS = List.of(new QuantumPortals.Link(ENTRY, EXIT));
    /** An eye three blocks before the entry's face. */
    private static final Vec3 EYE = new Vec3(0.5, 65.6, 4);

    @Test
    void aViewerLookingIntoTheEntryIsStreamedTheNineChunksAroundThePartner() {
        Set<ChunkPos> expected = new HashSet<>();
        ChunkPos partner = ChunkPos.containing(EXIT.block().pos());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                expected.add(new ChunkPos(partner.x() + dx, partner.z() + dz));
            }
        }
        assertEquals(expected, PartnerStreaming.chunksFor(OVERWORLD, EYE, new Vec3(0, 0, -1), LINKS));
    }

    @Test
    void aViewerLookingAwayIsStreamedNothing() {
        assertEquals(Set.of(), PartnerStreaming.chunksFor(OVERWORLD, EYE, new Vec3(0, 0, 1), LINKS));
    }
}
