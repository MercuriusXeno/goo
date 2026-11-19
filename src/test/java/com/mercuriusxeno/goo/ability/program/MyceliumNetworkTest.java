package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored mycelium network: each chunk's mycelial blocks stored as
 * components with the connectors where they cross the chunk's border, read
 * from the world once into a cache, encoded and decoded equal, and joined
 * chunk to chunk through those connectors; blocks join at faces, edges and
 * corners (decision mycelium-networks-are-cached-dags-joined-by-chunk-connectors).
 */
class MyceliumNetworkTest {

    private static final double RANGE = 64;
    private static final int CHUNK_SPAN = 16;

    /** A world holding a set of mycelial blocks, read a chunk at a time and counting each read. */
    private static final class CountingWorld implements MyceliumNetwork.Source {
        private final Set<BlockPos> mycelial;
        private int reads;

        CountingWorld(Set<BlockPos> mycelial) {
            this.mycelial = mycelial;
        }

        @Override
        public @Nullable ChunkMycelium read(ChunkPos chunk) {
            reads++;
            return ChunkMycelium.of(mycelial.stream().filter(pos -> ChunkPos.containing(pos).equals(chunk)).toList());
        }
    }

    private static Set<BlockPos> line(int fromX, int toX) {
        Set<BlockPos> blocks = new HashSet<>();
        for (int x = fromX; x <= toX; x++) {
            blocks.add(new BlockPos(x, 0, 0));
        }
        return blocks;
    }

    private static boolean connects(Set<BlockPos> mycelial, BlockPos from, BlockPos to) {
        return new MyceliumNetwork(new CountingWorld(mycelial)).connects(List.of(from), to, RANGE);
    }

    @Nested
    class AcrossChunks {

        @Test
        void twoChunkNetworksSharingAConnectorJoinIntoOne() {
            Set<BlockPos> line = line(CHUNK_SPAN - 4, CHUNK_SPAN + 3);
            assertTrue(connects(line, new BlockPos(CHUNK_SPAN - 4, 0, 0), new BlockPos(CHUNK_SPAN + 3, 0, 0)));
        }

        @Test
        void aGapAtTheBorderPartsTheChunks() {
            Set<BlockPos> line = line(CHUNK_SPAN - 4, CHUNK_SPAN + 3);
            line.remove(new BlockPos(CHUNK_SPAN, 0, 0));
            assertFalse(connects(line, new BlockPos(CHUNK_SPAN - 4, 0, 0), new BlockPos(CHUNK_SPAN + 3, 0, 0)));
        }

        @Test
        void aStepAcrossTheBorderCornerJoins() {
            Set<BlockPos> step = Set.of(new BlockPos(CHUNK_SPAN - 1, 0, CHUNK_SPAN - 1),
                    new BlockPos(CHUNK_SPAN, 1, CHUNK_SPAN));
            assertTrue(connects(step, new BlockPos(CHUNK_SPAN - 1, 0, CHUNK_SPAN - 1),
                    new BlockPos(CHUNK_SPAN, 1, CHUNK_SPAN)));
        }

        @Test
        void aChunkPastTheRangeFromTheDestinationIsNotWalked() {
            Set<BlockPos> line = line(0, 5 * CHUNK_SPAN);
            MyceliumNetwork network = new MyceliumNetwork(new CountingWorld(line));
            assertFalse(network.connects(List.of(BlockPos.ZERO), new BlockPos(5 * CHUNK_SPAN, 0, 0), 2 * CHUNK_SPAN));
        }
    }

    @Nested
    class WithinAChunk {

        @Test
        void aStepUpASlopeJoinsAtTheEdge() {
            Set<BlockPos> slope = Set.of(BlockPos.ZERO, new BlockPos(1, 1, 0), new BlockPos(2, 2, 0));
            assertTrue(connects(slope, BlockPos.ZERO, new BlockPos(2, 2, 0)));
        }

        @Test
        void oneMissingBlockPartsALine() {
            Set<BlockPos> line = line(0, CHUNK_SPAN - 1);
            line.remove(new BlockPos(7, 0, 0));
            assertFalse(connects(line, BlockPos.ZERO, new BlockPos(CHUNK_SPAN - 1, 0, 0)));
        }

        @Test
        void anOriginThatIsNotMycelialJoinsNothing() {
            Set<BlockPos> line = line(1, CHUNK_SPAN - 1);
            assertFalse(connects(line, BlockPos.ZERO, new BlockPos(CHUNK_SPAN - 1, 0, 0)));
        }
    }

    @Nested
    class Cache {

        @Test
        void aSecondQuestionOnAnUnchangedNetworkReadsNoBlock() {
            CountingWorld world = new CountingWorld(line(CHUNK_SPAN - 4, CHUNK_SPAN + 3));
            MyceliumNetwork network = new MyceliumNetwork(world);
            List<BlockPos> from = List.of(new BlockPos(CHUNK_SPAN - 4, 0, 0));
            BlockPos to = new BlockPos(CHUNK_SPAN + 3, 0, 0);

            network.connects(from, to, RANGE);
            int afterFirst = world.reads;
            network.connects(from, to, RANGE);

            assertEquals(0, world.reads - afterFirst);
        }

        @Test
        void aForgottenChunkIsReadAgainAndFollowsTheWorld() {
            Set<BlockPos> line = line(0, CHUNK_SPAN - 1);
            MyceliumNetwork network = new MyceliumNetwork(new CountingWorld(line));
            List<BlockPos> from = List.of(BlockPos.ZERO);
            BlockPos to = new BlockPos(CHUNK_SPAN - 1, 0, 0);
            boolean before = network.connects(from, to, RANGE);

            line.remove(new BlockPos(7, 0, 0));
            network.forget(ChunkPos.containing(to));

            assertEquals(List.of(true, false), List.of(before, network.connects(from, to, RANGE)));
        }
    }

    @Test
    void aChunksNetworkEncodesAndDecodesEqualConnectorsIncluded() {
        Set<BlockPos> blocks = line(2, CHUNK_SPAN - 1);
        blocks.add(new BlockPos(0, -40, 0));
        blocks.add(new BlockPos(5, 200, 15));
        ChunkMycelium network = ChunkMycelium.of(blocks);

        ChunkMycelium decoded = ChunkMycelium.CODEC.codec().parse(JsonOps.INSTANCE,
                ChunkMycelium.CODEC.codec().encodeStart(JsonOps.INSTANCE, network).getOrThrow()).getOrThrow();

        assertEquals(List.of(network, 3, true), List.of(decoded, decoded.components().size(),
                decoded.components().stream().allMatch(component -> !component.connectors().isEmpty())));
    }
}
