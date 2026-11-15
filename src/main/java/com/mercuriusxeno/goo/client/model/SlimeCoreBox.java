package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import java.util.Optional;

/**
 * The box a volume cell's goo slime core fills, in fifteenths of a block,
 * the unit a cell's layers count in, and the rule turning a cell into it:
 * the core keeps a padding of goo between itself and wherever the goo ends,
 * the ground, the surface and each side, so a lone cell shows no core until
 * its layers outgrow two paddings, and each layer after adds one to the
 * core's height.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * slime-core-keeps-a-two-layer-gap-and-grows-with-fill
 *
 * @param minX the west bound
 * @param minY the floor
 * @param minZ the north bound
 * @param maxX the east bound
 * @param maxY the top
 * @param maxZ the south bound
 */
public record SlimeCoreBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** The layers of goo standing between the core and wherever the goo ends: the tuning a thicker shell turns. */
    public static final int PADDING_LAYERS = 2;
    /** A whole block, in fifteenths. */
    static final int WHOLE = VolumeCellBlock.MAX_LAYERS;

    /**
     * The core a cell read alone holds.
     *
     * @param layers the layers the cell holds
     * @return the core's box, empty where the padding leaves no room for one
     */
    public static Optional<SlimeCoreBox> of(int layers) {
        int top = layers - PADDING_LAYERS;
        if (top <= PADDING_LAYERS) {
            return Optional.empty();
        }
        int far = WHOLE - PADDING_LAYERS;
        return Optional.of(new SlimeCoreBox(PADDING_LAYERS, PADDING_LAYERS, PADDING_LAYERS, far, top, far));
    }
}
