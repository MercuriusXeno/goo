package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups the gem ore blocks Glitter's sphere found into veins: blocks of one
 * ore touching at a face, an edge or a corner form one vein, revealed
 * together as the front reaches its centroid.
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class OreVeins {

    /** Blocks a touching block lies from another along each axis, corners included. */
    private static final int TOUCH = 1;

    private OreVeins() {
    }

    /**
     * One vein: the ore it holds, the centroid the front reveals it at and
     * its blocks.
     *
     * @param ore      the ore block's id
     * @param centroid the mean of its blocks' centers
     * @param blocks   its ore blocks
     */
    public record Vein(Identifier ore, Vec3 centroid, List<BlockPos> blocks) {

        /** @return the ore blocks it holds */
        public int count() {
            return blocks.size();
        }
    }

    /**
     * Groups found ore blocks into veins of touching blocks of one ore.
     *
     * @param found each found ore block's position and ore id
     * @return the veins, nearest the first found block's order
     */
    public static List<Vein> group(Map<BlockPos, Identifier> found) {
        Map<BlockPos, Identifier> left = new HashMap<>(found);
        List<Vein> veins = new ArrayList<>();
        for (Map.Entry<BlockPos, Identifier> start : found.entrySet()) {
            if (left.remove(start.getKey()) != null) {
                veins.add(floodVein(start.getKey(), start.getValue(), left));
            }
        }
        veins.sort(Comparator.comparing((Vein vein) -> vein.ore().toString())
                .thenComparingDouble(vein -> vein.centroid().x).thenComparingDouble(vein -> vein.centroid().z));
        return veins;
    }

    private static Vein floodVein(BlockPos start, Identifier ore, Map<BlockPos, Identifier> left) {
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(start);
        Vec3 sum = Vec3.ZERO;
        List<BlockPos> blocks = new ArrayList<>();
        while (!frontier.isEmpty()) {
            BlockPos block = frontier.poll();
            sum = sum.add(Vec3.atCenterOf(block));
            blocks.add(block);
            for (BlockPos touching : BlockPos.betweenClosed(block.offset(-TOUCH, -TOUCH, -TOUCH), block.offset(TOUCH, TOUCH, TOUCH))) {
                if (ore.equals(left.get(touching))) {
                    BlockPos kept = touching.immutable();
                    left.remove(kept);
                    frontier.add(kept);
                }
            }
        }
        return new Vein(ore, sum.scale(1.0 / blocks.size()), List.copyOf(blocks));
    }
}
