package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.Optional;

/**
 * Draws the local player's portals on the faces they cover: the entry portal
 * blue, its partner orange, and an unlinked portal its idle face. While the
 * player chooses a partner, every portal of theirs in this level draws
 * through walls (portal vision), the one the look picks brightest.
 * portals-link-by-looking-through-portal-vision
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PortalFaces {

    /** The blue entry portal's color. */
    static final int ENTRY_RGB = 0x3A7BFF;
    /** The orange exit portal's color. */
    static final int EXIT_RGB = 0xFF8A1E;
    /** An unlinked portal's idle color, Quantum's violet. */
    static final int IDLE_RGB = 0x9B6BFF;
    private static final int FACE_ALPHA = 190;
    private static final int VISION_ALPHA = 120;
    private static final int PICKED_ALPHA = 235;

    private PortalFaces() {
    }

    /**
     * The color a portal's face draws in: blue for the entry, orange for its
     * partner, violet for an unlinked portal.
     *
     * @param portal the portal
     * @param link   the player's linked pair, empty for none
     * @return 0xRRGGBB
     */
    static int colorOf(Portal portal, Optional<QuantumPortals.Link> link) {
        if (link.isEmpty()) {
            return IDLE_RGB;
        }
        if (link.get().entry().equals(portal)) {
            return ENTRY_RGB;
        }
        return link.get().exit().equals(portal) ? EXIT_RGB : IDLE_RGB;
    }

    /**
     * Draws the portals once the world has drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || ClientPortals.portals().isEmpty()) {
            return;
        }
        Optional<Portal> choosing = ClientPortals.choosing();
        Optional<Portal> picked = choosing.flatMap(from -> ClientPortals.pickedOnLook(ClientPortals.portals(), from,
                player.getEyePosition(), player.getLookAngle()));
        RenderType type = choosing.isPresent() ? GooRenderTypes.SPORE_SHELL_THROUGH_BLOCKS_TYPE
                : GooRenderTypes.SPORE_SHELL_TYPE;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawFaces(event.getPoseStack().last(), buffers.getBuffer(type), mc.gameRenderer.getMainCamera().position(),
                mc.level.dimension(), choosing, picked);
        buffers.endBatch(type);
    }

    private static void drawFaces(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
            ResourceKey<Level> dimension, Optional<Portal> choosing, Optional<Portal> picked) {
        for (Portal portal : ClientPortals.portals()) {
            if (portal.block().dimension().equals(dimension)) {
                ScrySweep.emitBoxFace(pose, consumer, new AABB(portal.block().pos()).move(camera.reverse()),
                        portal.face(), ARGB.color(alphaOf(portal, choosing, picked),
                                colorOf(portal, ClientPortals.link())));
            }
        }
    }

    private static int alphaOf(Portal portal, Optional<Portal> choosing, Optional<Portal> picked) {
        if (choosing.isEmpty() || choosing.get().equals(portal)) {
            return FACE_ALPHA;
        }
        return picked.isPresent() && picked.get().equals(portal) ? PICKED_ALPHA : VISION_ALPHA;
    }
}
