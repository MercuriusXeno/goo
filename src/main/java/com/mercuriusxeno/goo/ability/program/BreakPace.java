package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How fast a gradual break progresses the blocks a step hands it: the ticks
 * the nearest block takes to break, and how many times slower each block
 * farther out progresses than the block before it. A step's JSON carries both
 * beside its other params so the pace tunes for feel.
 * decision bore-and-flatten-json-carry-two-pace-values
 *
 * @param breakTicks the ticks the nearest block takes to break
 * @param slowdown   how many times slower each block farther out progresses, 1 or more
 */
public record BreakPace(int breakTicks, double slowdown) {

    private static final String FIELD_BREAK_TICKS = "break_ticks";
    private static final String FIELD_SLOWDOWN = "slowdown";
    /** The least slowdown: every block farther out progresses no faster than the one before it. */
    private static final double LEAST_SLOWDOWN = 1;

    /**
     * Codec for the two pace fields, read flat among the step's own fields.
     */
    public static final MapCodec<BreakPace> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            ExtraCodecs.POSITIVE_INT.fieldOf(FIELD_BREAK_TICKS).forGetter(BreakPace::breakTicks),
            Codec.doubleRange(LEAST_SLOWDOWN, Double.MAX_VALUE).fieldOf(FIELD_SLOWDOWN)
                    .forGetter(BreakPace::slowdown)
    ).apply(inst, BreakPace::new));

    /**
     * Each cell's share of a break gained this tick: the first cell at the
     * full rate, each later cell the slowdown times slower than the one before.
     *
     * @param cellsNearestFirst the cells breaking, nearest first
     * @return each cell with its rate, in the cells' order
     */
    public Map<BlockPos, Double> ratesNearestFirst(List<BlockPos> cellsNearestFirst) {
        return ratesByGroup(cellsNearestFirst.stream().map(List::of).toList());
    }

    /**
     * Each cell's share of a break gained this tick, the cells of a group
     * alike: the first group holding a cell at the full rate, each later
     * group the slowdown times slower than the one before. An empty group
     * takes no rank, so the group after it gains its rate.
     *
     * @param groupsNearestFirst the cells breaking in groups, such as Level's layers, the nearest group first
     * @return each cell with its rate, in the groups' order
     */
    public Map<BlockPos, Double> ratesByGroup(List<List<BlockPos>> groupsNearestFirst) {
        Map<BlockPos, Double> rates = new LinkedHashMap<>();
        double rate = 1.0 / breakTicks;
        for (List<BlockPos> group : groupsNearestFirst) {
            if (group.isEmpty()) {
                continue;
            }
            for (BlockPos cell : group) {
                rates.put(cell, rate);
            }
            rate /= slowdown;
        }
        return rates;
    }
}
