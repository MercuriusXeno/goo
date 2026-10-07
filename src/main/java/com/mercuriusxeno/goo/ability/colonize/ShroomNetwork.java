package com.mercuriusxeno.goo.ability.colonize;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The shroom networks Colonize grows: each is the blocks its family tag
 * under {@code goo:shroom_network} holds, the ground block it spreads as,
 * and the ground it can take over. Crimson and warped nylium take
 * netherrack, sculk takes what vanilla sculk spreads over, and mycelium
 * takes dirt and grass. A block on no network grows none
 * (decision colonize-blob-grows-the-network).
 */
public enum ShroomNetwork {
    /** Crimson nylium, its fungi and its trees. */
    CRIMSON("shroom_network/crimson", Blocks.CRIMSON_NYLIUM, tag("colonizable/nylium")),
    /** Warped nylium, its fungi and its trees. */
    WARPED("shroom_network/warped", Blocks.WARPED_NYLIUM, tag("colonizable/nylium")),
    /** Sculk and its growths. */
    SCULK("shroom_network/sculk", Blocks.SCULK, BlockTags.SCULK_REPLACEABLE),
    /** Mycelium and the mushrooms on it. */
    MYCELIUM("shroom_network/mycelium", Blocks.MYCELIUM, tag("colonizable/mycelium"));

    private final TagKey<Block> members;
    private final Block ground;
    private final TagKey<Block> colonizable;

    ShroomNetwork(String members, Block ground, TagKey<Block> colonizable) {
        this.members = tag(members);
        this.ground = ground;
        this.colonizable = colonizable;
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Goo.MODID, path));
    }

    /**
     * The network a block belongs to.
     *
     * @param landed the block the blob landed on
     * @return the network that spreads from it, or empty for a block of none
     */
    public static Optional<ShroomNetwork> of(BlockState landed) {
        for (ShroomNetwork network : values()) {
            if (landed.is(network.members)) {
                return Optional.of(network);
            }
        }
        return Optional.empty();
    }

    /**
     * Grows this network over the ground around a center: each block it can
     * take over within the radius, open to the air above it, becomes its
     * ground.
     *
     * @param level  the server level
     * @param center the block the spread grows from
     * @param radius the spread's reach in blocks
     * @return the blocks taken over
     */
    public int spread(ServerLevel level, BlockPos center, int radius) {
        Predicate<BlockPos> takes = pos -> level.getBlockState(pos).is(colonizable)
                && !level.getBlockState(pos.above()).isSolidRender();
        int taken = 0;
        for (BlockPos pos : BlockPos.withinManhattan(center, radius, radius, radius)) {
            if (pos.distSqr(center) <= (double) radius * radius && takes.test(pos)) {
                level.setBlock(pos, ground.defaultBlockState(), Block.UPDATE_ALL);
                taken++;
            }
        }
        return taken;
    }
}
