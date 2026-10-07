package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.BlockTransforms;
import com.mercuriusxeno.goo.network.BlockTransformPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client side of a block transform: starts the old block mingling into the
 * new (decision petrify-stone-encasement-and-calcify-map).
 */
public final class BlockTransformHandler {

    private BlockTransformHandler() {}

    /**
     * Handles the transform payload on the client thread.
     *
     * @param payload the transform payload
     * @param context the network context
     */
    public static void handle(BlockTransformPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                BlockTransforms.CLIENT.start(payload.pos(), Block.stateById(payload.fromState()),
                        mc.level.getGameTime());
            }
        });
    }
}
