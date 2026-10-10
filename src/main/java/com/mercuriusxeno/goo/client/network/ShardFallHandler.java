package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.FallingShards;
import com.mercuriusxeno.goo.network.ShardFallPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for a glass shard a crystal tap dropped nearby: it
 * falls from the spigot to the point it struck.
 * decision shards-drip-falls-as-a-glass-shard
 */
public final class ShardFallHandler {

    private ShardFallHandler() {}

    /**
     * Handles the shard payload on the client thread.
     *
     * @param payload the shard payload
     * @param context the network context
     */
    public static void handle(ShardFallPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                FallingShards.CLIENT.drop(mc.level, payload.from(), payload.to());
            }
        });
    }
}
