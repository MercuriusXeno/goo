package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Optional;

/**
 * Draws SkyFunnel once a level pass in goo:pocket, rising from the room's own
 * doorway, with goo's copy of the End portal's shader: its stars read at
 * each corner's coordinates on the surface, as the doorway's cuboid wears
 * them, and each vertex's brightness fading with the log of its distance
 * from the funnel's nearest point to its farthest, so the near pillar stands
 * in front of the far ceiling. Each face draws both ways round, so the
 * funnel shows from inside and out. It draws in the level pass rather than
 * the sky pass, so the blocks before it hide it as they hide the doorway,
 * and past the far plane SkyFunnel draws each vertex back inside it. The funnel is its own draw, outside the doorway's block entity
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
        List<List<SkyFunnel.Corner>> quads = SkyFunnel.quads(bounds);
        DoubleSummaryStatistics distances = quads.stream().flatMap(List::stream)
                .mapToDouble(corner -> corner.place().distanceTo(camera)).summaryStatistics();
        collector.submitCustomGeometry(poses, GooRenderTypes.SKY_FUNNEL_TYPE, (pose, buffer) -> {
            for (List<SkyFunnel.Corner> quad : quads) {
                for (SkyFunnel.Corner corner : quad) {
                    addCorner(buffer, pose, corner, camera, farthest, distances);
                }
                for (int index = quad.size() - 1; index >= 0; index--) {
                    addCorner(buffer, pose, quad.get(index), camera, farthest, distances);
                }
            }
        });
    }

    /**
     * Writes one corner: drawn along its line of sight within the pass's
     * reach, its stars' coordinates, and its brightness by distance.
     *
     * @param buffer    the funnel's vertex consumer
     * @param pose      the pass's pose, standing at the camera
     * @param corner    the corner
     * @param camera    the pass's camera
     * @param farthest  the farthest depth the pass draws
     * @param distances the funnel's corners' true distances from the camera
     */
    private static void addCorner(VertexConsumer buffer, PoseStack.Pose pose, SkyFunnel.Corner corner, Vec3 camera,
            double farthest, DoubleSummaryStatistics distances) {
        float brightness = SkyFunnel.brightnessAt(corner.place().distanceTo(camera), distances.getMin(),
                distances.getMax());
        buffer.addVertex(pose, SkyFunnel.fromCamera(camera, corner.place(), farthest).toVector3f())
                .setUv((float) corner.around(), (float) corner.up())
                .setColor(brightness, brightness, brightness, 1F);
    }
}
