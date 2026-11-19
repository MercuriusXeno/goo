package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * One chunk's stored mycelium network: its mycelial blocks grouped into the
 * components they form inside the chunk, each with its connectors, the cells
 * on the chunk's border where the network may cross into a neighbouring
 * chunk. Blocks join at faces, edges and corners (operator ruling
 * 2026-11-19). Saved with the chunk and read back into the cache, so a
 * question walks component to component and reads no block.
 * mycelium-networks-are-cached-dags-joined-by-chunk-connectors
 *
 * @param components the chunk's components, each cell packed by {@link #pack(BlockPos)}
 */
public record ChunkMycelium(List<Component> components) {

    /** A chunk holding no mycelium. */
    public static final ChunkMycelium NONE = new ChunkMycelium(List.of());
    private static final int LOCAL_MASK = 15;
    private static final int Z_SHIFT = 4;
    private static final int Y_SHIFT = 8;
    private static final int AROUND = 1;
    private static final Codec<IntList> CELLS = Codec.INT_STREAM.xmap(
            stream -> IntArrayList.wrap(stream.toArray()), IntList::intStream);
    /** Codec for a chunk's network, written with the chunk. */
    public static final MapCodec<ChunkMycelium> CODEC = Component.CODEC.listOf().fieldOf("components")
            .xmap(ChunkMycelium::new, ChunkMycelium::components);

    /**
     * One component of a chunk's network.
     *
     * @param cells      every cell of the component, packed
     * @param connectors the cells of the component on the chunk's border, packed
     */
    public record Component(IntList cells, IntList connectors) {

        /** Codec for a component. */
        public static final Codec<Component> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                CELLS.fieldOf("cells").forGetter(Component::cells),
                CELLS.fieldOf("connectors").forGetter(Component::connectors)
        ).apply(inst, Component::new));
    }

    /**
     * A network of the components given, held unchanged.
     *
     * @param components the chunk's components
     */
    public ChunkMycelium {
        components = List.copyOf(components);
    }

    /**
     * Groups one chunk's mycelial blocks into its network.
     *
     * @param mycelial the mycelial blocks, all in one chunk
     * @return the network
     */
    public static ChunkMycelium of(Collection<BlockPos> mycelial) {
        IntSet left = new IntOpenHashSet();
        mycelial.forEach(pos -> left.add(pack(pos)));
        List<Component> components = new ArrayList<>();
        while (!left.isEmpty()) {
            components.add(grow(left.iterator().nextInt(), left));
        }
        return components.isEmpty() ? NONE : new ChunkMycelium(components);
    }

    /**
     * Takes the component holding a cell out of the cells left, walking its
     * neighbours inside the chunk.
     *
     * @param seed a cell of the component
     * @param left the cells no component holds yet, changed in place
     * @return the component
     */
    private static Component grow(int seed, IntSet left) {
        IntList cells = new IntArrayList();
        IntList connectors = new IntArrayList();
        IntList next = IntArrayList.of(seed);
        left.remove(seed);
        while (!next.isEmpty()) {
            int cell = next.removeInt(next.size() - 1);
            cells.add(cell);
            if (onBorder(cell)) {
                connectors.add(cell);
            }
            for (int beside : besideInChunk(cell)) {
                if (left.remove(beside)) {
                    next.add(beside);
                }
            }
        }
        return new Component(cells, connectors);
    }

    private static IntList besideInChunk(int cell) {
        IntList beside = new IntArrayList();
        for (int dx = -AROUND; dx <= AROUND; dx++) {
            for (int dz = -AROUND; dz <= AROUND; dz++) {
                if (inChunk(localX(cell) + dx) && inChunk(localZ(cell) + dz)) {
                    addColumn(cell, dx, dz, beside);
                }
            }
        }
        return beside;
    }

    /** Adds the three cells stacked at one column offset from a cell, the cell itself left out. */
    private static void addColumn(int cell, int dx, int dz, IntList beside) {
        for (int dy = -AROUND; dy <= AROUND; dy++) {
            int near = pack(localX(cell) + dx, localY(cell) + dy, localZ(cell) + dz);
            if (near != cell) {
                beside.add(near);
            }
        }
    }

    private static boolean inChunk(int local) {
        return (local & ~LOCAL_MASK) == 0;
    }

    private static boolean onBorder(int cell) {
        return onEdge(localX(cell)) || onEdge(localZ(cell));
    }

    private static boolean onEdge(int local) {
        return local == 0 || local == LOCAL_MASK;
    }

    /**
     * A block's cell packed within its chunk: x and z in the low eight bits, y above.
     *
     * @param pos the block
     * @return the packed cell
     */
    static int pack(BlockPos pos) {
        return pack(pos.getX() & LOCAL_MASK, pos.getY(), pos.getZ() & LOCAL_MASK);
    }

    private static int pack(int x, int y, int z) {
        return y << Y_SHIFT | z << Z_SHIFT | x;
    }

    static int localX(int cell) {
        return cell & LOCAL_MASK;
    }

    static int localY(int cell) {
        return cell >> Y_SHIFT;
    }

    static int localZ(int cell) {
        return cell >> Z_SHIFT & LOCAL_MASK;
    }
}
