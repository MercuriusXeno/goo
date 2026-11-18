package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where Mycosis goo grows its next fungal bud, and what it pays: the first
 * valid spot bearing no fungus yet, the cell's own space before the spots in
 * its reach, spending the layers a bud costs; nowhere, the layers kept, where
 * every valid spot bears fungus or the goo holds fewer layers than a bud
 * costs, so saturated goo idles and keeps its goo.
 * mycosis-goo-disperses-spores-and-grows-shrooms-nearby-depleting
 * mycosis-pays-per-mob-infected-and-fungus-tended
 */
public final class MycosisGrowth {

    private MycosisGrowth() {
    }

    /**
     * Where the goo grows its next bud and the layers it keeps.
     *
     * @param spot       the spot the bud grows in, empty where none grows
     * @param layersLeft the layers the goo holds once it has grown it
     */
    public record Growth(Optional<BlockPos> spot, int layersLeft) {
    }

    /**
     * Where the goo grows its next bud.
     *
     * @param valid       the spots a bud could stand in, the cell's own space first, in growing order
     * @param grown       the spots already bearing fungus
     * @param layers      the layers the goo holds
     * @param layersPerBud the layers one bud costs
     * @return the spot to grow in and the layers left
     */
    public static Growth nextGrowth(List<BlockPos> valid, Set<BlockPos> grown, int layers, int layersPerBud) {
        if (layers < layersPerBud) {
            return new Growth(Optional.empty(), layers);
        }
        Optional<BlockPos> next = valid.stream().filter(spot -> !grown.contains(spot)).findFirst();
        return new Growth(next, next.isPresent() ? layers - layersPerBud : layers);
    }
}
