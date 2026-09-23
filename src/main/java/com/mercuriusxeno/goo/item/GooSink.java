package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;

/**
 * Where a held or cursor stack's goo goes: takes a type and a volume and answers the volume it kept.
 */
@FunctionalInterface
public interface GooSink {
    /**
     * Accepts up to the given volume of one goo type.
     *
     * @param type   the goo type offered
     * @param volume the volume offered, in mB
     * @return the volume accepted
     */
    int accept(ResourceKey<GooTypeDefinition> type, int volume);
}
