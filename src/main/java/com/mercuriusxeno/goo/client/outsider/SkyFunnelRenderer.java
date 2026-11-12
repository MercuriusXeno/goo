package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;
import java.util.List;
import java.util.Optional;

/**
 * Draws SkyFunnel once a level pass in goo:pocket, over the room's own
 * doorway, with the End portal's shader the doorway wears: position-only
 * vertices, its starfield read in screen space, each face both ways round so
 * the horn shows from inside and out. It draws in the level pass rather than
 * the sky pass, so near the doorway the blocks before it hide it as they
 * hide the doorway, and past the far plane SkyFunnel draws each vertex back
 * inside it. The funnel is its own draw, outside the doorway's block entity
 * and outside the room copies, so one funnel draws however many copies the
 * frame shows; the room's own doorway stands at its center, never farther
 * than a copy's. A view through a linked portal draws it around that view's
 * own camera, at the same true place, as RoomCopyRenderer draws the copies.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SkyFunnelRenderer {

    private SkyFunnelRenderer() {
    }

    /**
     * Submits the funnel for the pass drawing now, in the room the player stands in.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Optional<RoomExtent.Bounds> bounds = ClientRoomEdges.bounds();
        if (mc.level == null || mc.level.dimension() != OutsiderRooms.POCKET || bounds.isEmpty()) {
            return;
        }
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        submit(event.getSubmitNodeCollector(), event.getPoseStack(), bounds.get(), camera.pos, camera.depthFar);
    }

    /**
     * Submits one funnel over a room's own doorway, each quad's corners in
     * order and then reversed.
     *
     * @param collector the pass's submit collector
     * @param poses     the pass's poses, standing at the camera
     * @param bounds    the room's boundary
     * @param camera    the pass's camera
     * @param farthest  the farthest depth the pass draws
     */
    static void submit(SubmitNodeCollector collector, PoseStack poses, RoomExtent.Bounds bounds, Vec3 camera,
            double farthest) {
        List<List<Vec3>> quads = SkyFunnel.quads(bounds);
        collector.submitCustomGeometry(poses, RenderTypes.endPortal(), (pose, buffer) -> {
            for (List<Vec3> quad : quads) {
                Vector3f[] corners = quad.stream().map(corner -> SkyFunnel.fromCamera(camera, corner, farthest))
                        .map(Vec3::toVector3f).toArray(Vector3f[]::new);
                for (Vector3f corner : corners) {
                    buffer.addVertex(pose, corner);
                }
                for (int index = corners.length - 1; index >= 0; index--) {
                    buffer.addVertex(pose, corners[index]);
                }
            }
        });
    }
}
