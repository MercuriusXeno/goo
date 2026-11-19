package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Optional;

/**
 * Where a Creep cell places its next fungal bud, and what it pays: one of
 * its edge spaces, open and where a fungal bud can stand, chosen by a roll
 * among those, spending the fixed layers a bud costs; nowhere, the layers
 * kept, where no edge space is open and valid or the goo holds fewer layers
 * than a bud costs. No roll decides whether it places: a valid space always
 * takes a bud. It places at age 0 and never changes a block already
 * standing, so the goo grows to buds and stops, and the buds grow on their
 * own.
 * spores-goo-spends-on-edge-growth-and-grows-to-buds
 * spores-is-shrooms-residual-a-passive-mycosis
 */
public final class CreepGrowth {

    private CreepGrowth() {
    }

    /**
     * One space beside the goo.
     *
     * @param pos       the space
     * @param open      whether it holds nothing, no goo and no block
     * @param budStands whether a fungal bud can stand there
     */
    public record Edge(BlockPos pos, boolean open, boolean budStands) {
    }

    /**
     * Where the goo places its next bud and the layers it keeps.
     *
     * @param spot       the space the bud is placed in at age 0, empty where none is
     * @param layersLeft the layers the goo holds once it has placed it
     */
    public record Budding(Optional<BlockPos> spot, int layersLeft) {
    }

    /**
     * Where the goo places its next bud.
     *
     * @param edges        the spaces beside the goo
     * @param roll         a random number choosing among the valid spaces, any value
     * @param layers       the layers the goo holds
     * @param layersPerBud the layers one bud costs
     * @return the space to place in and the layers left
     */
    public static Budding nextBud(List<Edge> edges, int roll, int layers, int layersPerBud) {
        List<BlockPos> valid = edges.stream().filter(edge -> edge.open() && edge.budStands()).map(Edge::pos).toList();
        if (valid.isEmpty() || layers < layersPerBud) {
            return new Budding(Optional.empty(), layers);
        }
        return new Budding(Optional.of(valid.get(Math.floorMod(roll, valid.size()))), layers - layersPerBud);
    }
}
