package com.mercuriusxeno.goo.block.plexer;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;

/**
 * The goo a plexer fabricates from: the canister above it in the world, a map in a test.
 */
public interface PlexerGooSupply {

    /**
     * The volume of a type the supply holds.
     *
     * @param type the goo type
     * @return the volume in mB
     */
    int available(ResourceKey<GooTypeDefinition> type);

    /**
     * Removes a volume of a type the supply holds.
     *
     * @param type   the goo type
     * @param amount the volume in mB, no more than {@link #available}
     */
    void consume(ResourceKey<GooTypeDefinition> type, int amount);
}
