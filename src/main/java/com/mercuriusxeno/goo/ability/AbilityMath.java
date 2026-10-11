package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pure math functions for world effect calculations. Framework-free so
 * they can be unit tested without bootstrapping Minecraft.
 */
public final class AbilityMath {

    /**
     * Goo types counted toward rock majority (rock and crystal from quartz ancestry).
     */
    private static final Set<ResourceKey<GooTypeDefinition>> ROCK_FAMILY = Set.of(GooTypes.ROCK, GooTypes.CRYSTAL);

    /**
     * Majority threshold multiplier: rockTotal * 2 > total means >50%.
     */
    private static final int MAJORITY_MULTIPLIER = 2;

    /** An inner radius squared below every offset's, so a shell from the center holds the center. */
    public static final double HOLDS_THE_CENTER = -1;

    private AbilityMath() {
    }

    /**
     * Iterates all block positions within a sphere and applies the action to each.
     *
     * @param center the center of the sphere
     * @param radius the sphere radius
     * @param action the action to apply to each position in the sphere
     */
    public static void forEachInSphere(BlockPos center, int radius, Consumer<BlockPos> action) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        action.accept(center.offset(dx, dy, dz));
                    }
                }
            }
        }
    }

    /**
     * Iterates the block positions between two spheres about a center: each
     * whose offset from the center lies farther than the inner radius and
     * no farther than the outer, so bands that meet end to end hold each
     * position exactly once.
     *
     * @param center the spheres' center
     * @param inner  the inner radius, exclusive
     * @param outer  the outer radius, inclusive
     * @param action the action to apply to each position in the band
     */
    public static void forEachInShell(BlockPos center, double inner, double outer, Consumer<BlockPos> action) {
        double outerSquared = outer * outer;
        double innerSquared = inner > 0 ? inner * inner : HOLDS_THE_CENTER;
        int reach = (int) Math.floor(outer);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                int flat = dx * dx + dy * dy;
                if (flat <= outerSquared) {
                    shellColumn(center.offset(dx, dy, 0), flat, innerSquared, outerSquared, action);
                }
            }
        }
    }

    /**
     * Iterates one column of a shell along z, both ways from its foot.
     *
     * @param foot         the column's block at the center's z
     * @param flat         the column's squared offset across x and y
     * @param innerSquared the inner radius squared, exclusive
     * @param outerSquared the outer radius squared, inclusive
     * @param action       the action to apply to each position in the band
     */
    private static void shellColumn(BlockPos foot, int flat, double innerSquared, double outerSquared,
                                    Consumer<BlockPos> action) {
        int farthest = (int) Math.floor(Math.sqrt(outerSquared - flat));
        int nearest = innerSquared < flat ? 0 : (int) Math.floor(Math.sqrt(innerSquared - flat));
        for (int dz = nearest; dz <= farthest; dz++) {
            int squared = flat + dz * dz;
            if (squared > innerSquared && squared <= outerSquared) {
                action.accept(foot.offset(0, 0, dz));
                if (dz != 0) {
                    action.accept(foot.offset(0, 0, -dz));
                }
            }
        }
    }

    /**
     * Returns true if rock + crystal make up strictly more than half of
     * the block's total goo. This lets mixed-composition blocks
     * like bricks or polished stone qualify while keeping metal-heavy
     * or organic blocks out.
     *
     * @param value the block's goo composition, or null if unknown
     * @return true if rock family is the majority
     */
    public static boolean isRockCompatible(GooValue value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int rockTotal = 0;
        for (ResourceKey<GooTypeDefinition> type : ROCK_FAMILY) {
            rockTotal += value.get(type);
        }
        return rockTotal * MAJORITY_MULTIPLIER > value.totalGoo();
    }
}
