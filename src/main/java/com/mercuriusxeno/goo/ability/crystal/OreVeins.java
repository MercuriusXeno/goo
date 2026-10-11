package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.AbilityMath;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Groups the gem ore blocks Glitter's sphere found into veins: blocks of one
 * ore touching at a face, an edge or a corner form one vein, revealed
 * together as the front reaches its centroid.
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class OreVeins {

    /** Blocks a touching block lies from another along each axis, corners included. */
    private static final int TOUCH = 1;
    /** The most blocks one vein a growing front finds holds, so a vast deposit floods no further. */
    static final int MOST_VEIN_BLOCKS = 256;
    /** Veins in a steady order: by ore, then across the ground. */
    private static final Comparator<Vein> VEIN_ORDER = Comparator.comparing((Vein vein) -> vein.ore().toString())
            .thenComparingDouble(vein -> vein.centroid().x).thenComparingDouble(vein -> vein.centroid().z);

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
        veins.sort(VEIN_ORDER);
        return veins;
    }

    /**
     * The veins a growing front first reaches as it crosses one band: each
     * ore block in the band floods out to its whole vein, kept within the
     * sphere the front may reach, and the vein belongs to the band holding
     * its nearest block, so a hold's bands find each vein once.
     *
     * @param center the front's center block
     * @param inner  the front's radius before the band, exclusive
     * @param outer  the front's radius after the band, inclusive
     * @param most   the radius the front may reach at most
     * @param oreAt  each block's ore id, or null where no ore stands
     * @return the veins first reached in the band, sorted as {@link #group} sorts
     */
    public static List<Vein> firstReachedIn(BlockPos center, double inner, double outer, double most,
                                            Function<BlockPos, @Nullable Identifier> oreAt) {
        Set<BlockPos> visited = new HashSet<>();
        List<Vein> veins = new ArrayList<>();
        double innerSquared = inner > 0 ? inner * inner : AbilityMath.HOLDS_THE_CENTER;
        AbilityMath.forEachInShell(center, inner, outer, pos -> {
            Identifier ore = visited.contains(pos) ? null : oreAt.apply(pos);
            if (ore == null) {
                return;
            }
            Vein vein = floodReachable(pos.immutable(), ore, center, most, oreAt, visited);
            double nearest = vein.blocks().stream().mapToDouble(block -> block.distSqr(center)).min().orElse(0);
            if (nearest > innerSquared) {
                veins.add(vein);
            }
        });
        veins.sort(VEIN_ORDER);
        return veins;
    }

    private static Vein floodReachable(BlockPos start, Identifier ore, BlockPos center, double most,
                                       Function<BlockPos, @Nullable Identifier> oreAt, Set<BlockPos> visited) {
        double mostSquared = most * most;
        List<BlockPos> blocks = new ArrayList<>();
        Vec3 sum = Vec3.ZERO;
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(start);
        visited.add(start);
        while (!frontier.isEmpty() && blocks.size() < MOST_VEIN_BLOCKS) {
            BlockPos block = frontier.poll();
            blocks.add(block);
            sum = sum.add(Vec3.atCenterOf(block));
            for (BlockPos touching : BlockPos.betweenClosed(block.offset(-TOUCH, -TOUCH, -TOUCH),
                    block.offset(TOUCH, TOUCH, TOUCH))) {
                if (joins(touching, ore, center, mostSquared, oreAt, visited)) {
                    BlockPos kept = touching.immutable();
                    visited.add(kept);
                    frontier.add(kept);
                }
            }
        }
        return new Vein(ore, sum.scale(1.0 / blocks.size()), List.copyOf(blocks));
    }

    private static boolean joins(BlockPos touching, Identifier ore, BlockPos center, double mostSquared,
                                 Function<BlockPos, @Nullable Identifier> oreAt, Set<BlockPos> visited) {
        return !visited.contains(touching) && touching.distSqr(center) <= mostSquared
                && ore.equals(oreAt.apply(touching));
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
