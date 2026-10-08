package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.ClientGooValues;
import com.mercuriusxeno.goo.client.ability.MingledGoo;
import com.mercuriusxeno.goo.data.GooValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The goo a block melts into on this client: its item's goo value as the
 * client knows it, mingled by type.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class MeltMeshGoo {

    private MeltMeshGoo() {
    }

    /**
     * @param state the block
     * @return the goo it melts into, none where the client holds no value for it
     */
    public static MingledGoo of(BlockState state) {
        GooValue value = ClientGooValues.current().lookup(BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()));
        return value == null ? MingledGoo.NONE : MingledGoo.of(value.getAll());
    }
}
