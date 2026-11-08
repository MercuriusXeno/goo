package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.BlockTransforms;
import com.mercuriusxeno.goo.network.BlockExposurePayload;
import com.mercuriusxeno.goo.network.BlockTransformPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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

    /**
     * Handles a block's calcify share on the client thread
     * (decision petrify-stone-encasement-and-calcify-map).
     *
     * @param payload the exposure payload
     * @param context the network context
     */
    public static void handleExposure(BlockExposurePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                BlockState toward = Block.stateById(payload.toward());
                if (payload.tint() == BlockExposurePayload.MELTS_AWAY) {
                    // flames-melts-vanilla-meltable-ice-and-snow-over-the-hold: the client's copy shows the melt
                    BlockTransforms.CLIENT.meltSwap(payload.pos(), toward, payload.share(),
                            mc.level.getBlockState(payload.pos())).ifPresent(state ->
                            mc.level.setBlock(payload.pos(), state, Block.UPDATE_ALL_IMMEDIATE));
                }
                BlockTransforms.CLIENT.expose(payload.pos(), toward, payload.share(), mc.level.getGameTime(),
                        payload.tint());
            }
        });
    }
}
