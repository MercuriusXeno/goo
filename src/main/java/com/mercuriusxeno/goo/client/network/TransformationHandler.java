package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.Transformations;
import com.mercuriusxeno.goo.network.ModelShrinkPayload;
import com.mercuriusxeno.goo.network.TransformationPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for a model transformation: starts the blob's hop
 * and morph into the entity or block the payload names.
 * Decision model-transformation-is-one-animation.
 */
public final class TransformationHandler {

    private TransformationHandler() {}

    /**
     * Handles the transformation payload on the client thread.
     *
     * @param payload the transformation payload
     * @param context the network context
     */
    public static void handle(TransformationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Transformations.CLIENT.add(payload.gooType(), payload.from(), payload.to(), payload.targetEntityId(),
                    payload.targetBlock(), mc.level.getGameTime(), payload.ticks());
        });
    }

    /**
     * Handles a model shrink on the client thread.
     * rewind-shrinks-adult-to-baby-to-egg
     *
     * @param payload the shrink payload
     * @param context the network context
     */
    public static void handleShrink(ModelShrinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Transformations.CLIENT.shrink(payload.entityId(), payload.fromScale(), payload.toScale(),
                    payload.babyModel(), mc.level.getGameTime(), payload.ticks());
        });
    }
}
