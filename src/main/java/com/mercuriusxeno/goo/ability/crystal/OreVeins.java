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
 * ore touching at a face, an edge or a corner form one vein, which shows as
 * one icon at its centroid; and merges the veins of one ore lying close
 * together into one icon, so a cluster of small veins reads as one.
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class OreVeins {

    /** The answer of a search finding no vein. */
    private static final int NONE = -1;
    /** Blocks a touching block lies from another along each axis, corners included. */
    private static final int TOUCH = 1;

    private OreVeins() {
    }

    /**
     * One vein, or several close veins merged: the ore it holds, the
     * centroid its icon shows at and the blocks it counts.
     *
     * @param ore      the ore block's id
     * @param centroid the mean of its blocks' centers
     * @param count    the ore blocks it holds
     */
    public record Vein(Identifier ore, Vec3 centroid, int count) {
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
        int count = 0;
        while (!frontier.isEmpty()) {
            BlockPos block = frontier.poll();
            sum = sum.add(Vec3.atCenterOf(block));
            count++;
            for (BlockPos touching : BlockPos.betweenClosed(block.offset(-TOUCH, -TOUCH, -TOUCH), block.offset(TOUCH, TOUCH, TOUCH))) {
                if (ore.equals(left.get(touching))) {
                    BlockPos kept = touching.immutable();
                    left.remove(kept);
                    frontier.add(kept);
                }
            }
        }
        return new Vein(ore, sum.scale(1.0 / count), count);
    }

    /**
     * Merges the veins of one ore whose centroids lie within a distance of
     * a vein already merged into, into one icon at their blocks' joint
     * centroid; veins of different ores never merge.
     *
     * @param veins    the veins
     * @param distance the most blocks apart two veins' centroids lie and still merge
     * @return the icons to show
     */
    public static List<Vein> merge(List<Vein> veins, double distance) {
        double reach = distance * distance;
        List<Vein> merged = new ArrayList<>();
        for (Vein vein : veins) {
            int into = closeVeinOfTheSameOre(merged, vein, reach);
            if (into < 0) {
                merged.add(vein);
            } else {
                merged.set(into, joined(merged.get(into), vein));
            }
        }
        return merged;
    }

    private static int closeVeinOfTheSameOre(List<Vein> merged, Vein vein, double reach) {
        for (int index = 0; index < merged.size(); index++) {
            Vein other = merged.get(index);
            if (other.ore().equals(vein.ore()) && other.centroid().distanceToSqr(vein.centroid()) <= reach) {
                return index;
            }
        }
        return NONE;
    }

    private static Vein joined(Vein first, Vein second) {
        int count = first.count() + second.count();
        Vec3 centroid = first.centroid().scale(first.count()).add(second.centroid().scale(second.count()))
                .scale(1.0 / count);
        return new Vein(first.ore(), centroid, count);
    }
}
