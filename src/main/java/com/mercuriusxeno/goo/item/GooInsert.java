package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The in-world goo insert the vat, tap, hub and canister blocks share (decision block-insert-shared).
 */
public final class GooInsert {

    private GooInsert() { }

    /**
     * Pours a held goo or goo into a sink and depletes the stack by what the sink accepted.
     * Any other stack, or a sink accepting nothing, leaves the stack whole.
     *
     * @param stack  the held stack
     * @param player the player holding it
     * @param sink   where the goo goes
     * @return the volume accepted, 0 when nothing moved
     */
    public static int pour(ItemStack stack, Player player, GooSink sink) {
        ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(stack);
        if (type == null) { return 0; }

        int accepted = sink.accept(type, GooStacks.volumeOf(stack));
        if (accepted <= 0) { return 0; }

        GooStacks.deplete(stack, accepted, player);
        return accepted;
    }
}
