package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.Afterimages;
import com.mercuriusxeno.goo.client.ability.ViewportRipples;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.BlockAfterimagePayload;
import com.mercuriusxeno.goo.type.GooColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;

/**
 * Client-side handler for an afterimage: snapshots the entity's render
 * state as it stands now, so the echo keeps that pose, and leaves it at the
 * payload's point in the goo type's color.
 * Decision afterimage-is-one-shared-effect.
 */
public final class AfterimageHandler {

    private AfterimageHandler() {}

    /**
     * Handles the afterimage payload on the client thread.
     *
     * @param payload the afterimage payload
     * @param context the network context
     */
    public static void handle(AfterimagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            EntityRenderState snapshot = EntitySnapshots.of(mc, payload.entityId());
            if (snapshot == null) {
                return;
            }
            int rgb = GooColors.get(mc.level.registryAccess(), payload.gooType());
            Afterimages.CLIENT.add(snapshot, payload.position(), rgb, mc.level.getGameTime(), payload.lifeTicks());
            if (mc.player != null) {
                ViewportRipples.CLIENT.onAfterimage(payload.entityId(), mc.player.getId(), rgb, mc.level.getGameTime());
            }
        });
    }
    /**
     * Handles a block's afterimage on the client thread: takes the block's
     * shape as it stood and leaves its silhouettes rippling out of its cell
     * in the goo type's color.
     * reap-breeze-harvests-and-replants
     *
     * @param payload the block afterimage payload
     * @param context the network context
     */
    public static void handleBlock(BlockAfterimagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            BlockState stood = Block.stateById(payload.stateId());
            List<AABB> boxes = stood.getShape(mc.level, payload.pos()).toAabbs();
            if (boxes.isEmpty()) {
                return;
            }
            int rgb = GooColors.get(mc.level.registryAccess(), payload.gooType());
            Afterimages.BLOCKS.add(boxes, Vec3.atLowerCornerOf(payload.pos()), rgb, mc.level.getGameTime(),
                    payload.lifeTicks());
        });
    }
}
