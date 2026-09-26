package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.Direction;

/**
 * A host placed on a block face (capability {@link HostCapability#PLACED_FACE}).
 */
public interface PlacedFaceHost extends StepHost {

    /**
     * Returns the face the marker was placed on; the blast direction is
     * its opposite.
     *
     * @return the placed face
     */
    Direction placedFace();
}
