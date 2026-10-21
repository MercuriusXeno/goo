package com.mercuriusxeno.goo.block.plexer;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.HashMap;
import java.util.Map;

/**
 * A fake plexer goo supply holding volumes per type in a map.
 */
public final class MapGooSupply implements PlexerGooSupply {

    private final Map<ResourceKey<GooTypeDefinition>, Integer> volumes = new HashMap<>();

    private MapGooSupply() {
    }

    /**
     * A supply holding one type.
     *
     * @param type   the goo type
     * @param volume the volume held
     * @return the supply
     */
    public static MapGooSupply holding(ResourceKey<GooTypeDefinition> type, int volume) {
        MapGooSupply supply = new MapGooSupply();
        supply.volumes.put(type, volume);
        return supply;
    }

    @Override
    public int available(ResourceKey<GooTypeDefinition> type) {
        return volumes.getOrDefault(type, 0);
    }

    @Override
    public void consume(ResourceKey<GooTypeDefinition> type, int amount) {
        volumes.merge(type, -amount, Integer::sum);
    }
}
