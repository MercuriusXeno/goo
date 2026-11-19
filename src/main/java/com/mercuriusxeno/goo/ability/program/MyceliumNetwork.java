package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * One level's stored mycelium networks, cached a chunk at a time, and the walk
 * answering whether a path of mycelial blocks joins a destination to any
 * block a caster touches. The walk steps from component to component, and
 * across a chunk border only through a connector meeting a cell of the
 * neighbouring chunk's network, so it reads no block from the world once each
 * chunk it walks stands cached. It walks only the chunks within the range of
 * the destination.
 * mycelium-networks-are-cached-dags-joined-by-chunk-connectors
 */
public final class MyceliumNetwork {

    private static final int NO_COMPONENT = -1;
    private static final int LAST_LOCAL = 15;
    private static final int AROUND = 1;

    private final Source source;
    private final Map<ChunkPos, Indexed> chunks = new HashMap<>();

    /** Where the cache reads a chunk's stored network from. */
    @FunctionalInterface
    public interface Source {

        /**
         * A chunk's stored network.
         *
         * @param chunk the chunk
         * @return its network, or null where the chunk stands unloaded
         */
        @Nullable ChunkMycelium read(ChunkPos chunk);
    }

    /**
     * A chunk's network with each cell's component looked up.
     *
     * @param network     the network
     * @param componentOf each packed cell's component, NO_COMPONENT for a cell no component holds
     */
    private record Indexed(ChunkMycelium network, Int2IntMap componentOf) {

        static Indexed of(ChunkMycelium network) {
            Int2IntMap componentOf = new Int2IntOpenHashMap();
            componentOf.defaultReturnValue(NO_COMPONENT);
            List<ChunkMycelium.Component> components = network.components();
            for (int index = 0; index < components.size(); index++) {
                int component = index;
                components.get(index).cells().forEach(cell -> componentOf.put(cell, component));
            }
            return new Indexed(network, componentOf);
        }

        int componentAt(BlockPos pos) {
            return componentOf.get(ChunkMycelium.pack(pos));
        }

        IntList connectors(int component) {
            return network.components().get(component).connectors();
        }
    }

    /**
     * One component of one chunk's network, a step of the walk.
     *
     * @param chunk     the chunk
     * @param component the component's index in the chunk's network
     */
    private record Node(ChunkPos chunk, int component) {
    }

    /**
     * A cache reading each chunk's network from a source.
     *
     * @param source where each chunk's network is read from
     */
    public MyceliumNetwork(Source source) {
        this.source = source;
    }

    /**
     * Drops a chunk's cached network, so the next question reads it again.
     *
     * @param chunk the chunk
     */
    public void forget(ChunkPos chunk) {
        chunks.remove(chunk);
    }

    /**
     * Whether a path of mycelial blocks joins the destination to any origin.
     *
     * @param origins     the blocks the caster touches
     * @param destination the aimed block
     * @param range       how far from the destination, in blocks, a chunk the walk crosses may stand
     * @return true where a path joins them
     */
    public boolean connects(Collection<BlockPos> origins, BlockPos destination, double range) {
        Optional<Node> target = nodeAt(destination, destination, range);
        if (target.isEmpty()) {
            return false;
        }
        Set<Node> seen = new HashSet<>();
        Deque<Node> next = new ArrayDeque<>();
        origins.forEach(origin -> nodeAt(origin, destination, range).filter(seen::add).ifPresent(next::add));
        while (!next.isEmpty()) {
            Node node = next.poll();
            if (node.equals(target.get())) {
                return true;
            }
            acrossConnectors(node, destination, range).stream().filter(seen::add).forEach(next::add);
        }
        return false;
    }

    /**
     * The components a component's connectors meet in the neighbouring chunks.
     *
     * @param node        the component
     * @param destination the aimed block
     * @param range       how far from the destination a chunk may stand
     * @return the components met
     */
    private List<Node> acrossConnectors(Node node, BlockPos destination, double range) {
        List<Node> met = new ArrayList<>();
        Indexed indexed = chunks.get(node.chunk());
        indexed.connectors(node.component()).forEach(connector -> {
            int localX = ChunkMycelium.localX(connector);
            int localZ = ChunkMycelium.localZ(connector);
            BlockPos cell = node.chunk().getBlockAt(localX, ChunkMycelium.localY(connector), localZ);
            for (int dx = -AROUND; dx <= AROUND; dx++) {
                for (int dz = -AROUND; dz <= AROUND; dz++) {
                    if (leavesTheChunk(localX + dx) || leavesTheChunk(localZ + dz)) {
                        acrossTheBorder(cell, dx, dz, destination, range, met);
                    }
                }
            }
        });
        return met;
    }

    private void acrossTheBorder(BlockPos cell, int dx, int dz, BlockPos destination, double range, List<Node> met) {
        for (int dy = -AROUND; dy <= AROUND; dy++) {
            nodeAt(cell.offset(dx, dy, dz), destination, range).ifPresent(met::add);
        }
    }

    private static boolean leavesTheChunk(int local) {
        return local < 0 || local > LAST_LOCAL;
    }

    private Optional<Node> nodeAt(BlockPos pos, BlockPos destination, double range) {
        ChunkPos chunk = ChunkPos.containing(pos);
        Indexed indexed = withinRange(chunk, destination, range) ? indexed(chunk) : null;
        int component = indexed == null ? NO_COMPONENT : indexed.componentAt(pos);
        return component == NO_COMPONENT ? Optional.empty() : Optional.of(new Node(chunk, component));
    }

    /**
     * A chunk's cached network, read from the source the first time it is
     * asked for; an unloaded chunk stays uncached, so it is read once it loads.
     *
     * @param chunk the chunk
     * @return its network, or null where the chunk stands unloaded
     */
    private @Nullable Indexed indexed(ChunkPos chunk) {
        Indexed cached = chunks.get(chunk);
        if (cached == null) {
            ChunkMycelium read = source.read(chunk);
            if (read != null) {
                cached = Indexed.of(read);
                chunks.put(chunk, cached);
            }
        }
        return cached;
    }

    private static boolean withinRange(ChunkPos chunk, BlockPos destination, double range) {
        long dx = Math.max(0, Math.max(chunk.getMinBlockX() - destination.getX(),
                destination.getX() - chunk.getMaxBlockX()));
        long dz = Math.max(0, Math.max(chunk.getMinBlockZ() - destination.getZ(),
                destination.getZ() - chunk.getMaxBlockZ()));
        return dx * dx + dz * dz <= range * range;
    }
}
