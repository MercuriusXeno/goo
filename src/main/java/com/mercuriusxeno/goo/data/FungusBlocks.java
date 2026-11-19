package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * The fungus blocks: every block whose block item's goo value holds sculk, which is every shroom, the nether's trees
 * and growth, and the sculk family. Each side's value table is read once into a set of blocks, kept until the side
 * stands a new table, so a check costs one set lookup; Transmit, its aim and Sculker's shroom vision all read it.
 * operator ruling 2026-11-18: a fungus block is a block whose block item contains sculk goo
 * decision shroom-is-a-universal-sculk-input
 * decision sculker-absorbs-sight-as-shroom-vision
 */
public final class FungusBlocks {

    /** Each value table's fungus blocks; a table stands unchanged for its life, so its set never goes stale. */
    private static final Map<IGooValueLookup, Set<Block>> BY_TABLE = Collections.synchronizedMap(new WeakHashMap<>());

    private FungusBlocks() {
    }

    /**
     * Whether a block is fungus by the values its level's side holds.
     *
     * @param level the level the block stands in
     * @param state the block
     * @return true for a block whose block item's goo value holds sculk
     */
    public static boolean is(Level level, BlockState state) {
        return of(GooValues.of(level)).contains(state.getBlock());
    }

    /**
     * The fungus blocks a value table names, read once per table.
     *
     * @param values the goo values
     * @return the fungus blocks
     */
    public static Set<Block> of(IGooValueLookup values) {
        return BY_TABLE.computeIfAbsent(values, table -> Set.copyOf(
                containingSculk(BuiltInRegistries.BLOCK, FungusBlocks::itemOf, table)));
    }

    /**
     * The blocks whose block item's goo value holds sculk.
     *
     * @param blocks the blocks to sort
     * @param itemOf each block's item id, null for a block with no item
     * @param values the goo values
     * @param <B>    what a block is
     * @return the fungus blocks
     */
    public static <B> Set<B> containingSculk(Iterable<B> blocks, Function<B, @Nullable Identifier> itemOf,
                                             IGooValueLookup values) {
        Set<B> fungus = new HashSet<>();
        for (B block : blocks) {
            Identifier item = itemOf.apply(block);
            GooValue value = item == null ? null : values.lookup(item);
            if (value != null && value.exact(GooTypes.SCULK).isPositive()) {
                fungus.add(block);
            }
        }
        return fungus;
    }

    private static @Nullable Identifier itemOf(Block block) {
        Item item = block.asItem();
        return item == Items.AIR ? null : BuiltInRegistries.ITEM.getKey(item);
    }
}
