package com.mercuriusxeno.goo.ability.program;

import net.minecraft.resources.Identifier;
import java.util.Map;

/**
 * A host with a block position it can write (capability {@link HostCapability#PLACE_BLOCK}).
 */
public interface PlaceBlockHost extends StepHost {

    /**
     * Writes a block at the anchor, replacing what stands there.
     *
     * @param block the block's registry id
     * @param state each state property to set, by its name, to the value's name
     */
    void placeBlock(Identifier block, Map<String, String> state);
}
