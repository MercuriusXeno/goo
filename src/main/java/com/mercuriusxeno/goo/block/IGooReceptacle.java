package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;

/**
 * A block entity goo can be poured into from above, such as a tap's drip
 * landing on it (decision landing-goo-enters-any-holder): takes a type and a
 * volume and answers the volume it kept.
 */
@FunctionalInterface
public interface IGooReceptacle {
    /**
     * Keeps up to the given volume of one goo type.
     *
     * @param type   the goo type offered
     * @param volume the volume offered, in mB
     * @return the volume kept, 0 when the receptacle refuses the type or is full
     */
    int insertGoo(ResourceKey<GooTypeDefinition> type, int volume);
}
