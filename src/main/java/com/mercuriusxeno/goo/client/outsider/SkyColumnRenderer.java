package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Draws a SkyColumn once a level pass in goo:pocket over the room's own
 * doorway and over every copy's doorway the room tiles to at the render
 * distance, each copy's the room's own moved by its offset, so the columns
 * hold their places as the player crosses the room's edge. Each wears goo's
 * copy of the End portal's shader as a window into space, as the doorway's
 * cuboid wears it, its stars read by the direction each point is seen
 * through, at full brightness as the doorway draws. Only the sides the
 * camera sees draw, so a column costs at most two quads however many
 * copies stand. Each column culls by its own box, so a column in view draws
 * where its copy's room stands off screen. It draws in the level pass rather
 * than the sky pass, so the blocks before a column hide it as they hide the
 * doorway, and past the far plane SkyColumn draws each vertex back inside
 * it. A view through a linked portal draws the columns around that view's
 * own camera, at the same true places, as RoomCopyRenderer draws the copies.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SkyColumnRenderer {

    private static final RoomCopies.Offset OWN = new RoomCopies.Offset(0, 0);
    /** A corner's packed places: east, up, south. */
    private static final int AXES = 3;
    private static final int UP = 1;
    private static final int SOUTH = 2;

    private SkyColumnRenderer() {
    }

    /**
     * Submits the columns for the pass drawing now, in the room the player stands in.
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
        Frustum frustum = PortalView.drawing() ? PortalView.camera().getCullFrustum()
                : mc.gameRenderer.getMainCamera().getCullFrustum();
        List<RoomCopies.Offset> copies = RoomCopies.offsets(bounds.get(),
                RoomCopies.reachOf(mc.options.getEffectiveRenderDistance()));
        submit(event.getSubmitNodeCollector(), event.getPoseStack(), bounds.get(), copies, camera.pos,
                camera.depthFar, frustum::isVisible);
    }

    /**
     * Submits a column over a room's own doorway and over each copy's whose
     * column box the camera sees, each column the sides the camera sees.
     *
     * @param collector the pass's submit collector
     * @param poses     the pass's poses, standing at the camera
     * @param bounds    the room's boundary
     * @param copies    the room's copies
     * @param camera    the pass's camera
     * @param farthest  the farthest depth the pass draws
     * @param seen      whether the camera sees a box
     */
    static void submit(SubmitNodeCollector collector, PoseStack poses, RoomExtent.Bounds bounds,
            List<RoomCopies.Offset> copies, Vec3 camera, double farthest, Predicate<AABB> seen) {
        AABB column = SkyColumn.boxOf(bounds);
        DrawnCorners corners = new DrawnCorners(camera, farthest);
        for (RoomCopies.Offset offset : withOwn(copies)) {
            if (seen.test(column.move(offset.east(), 0, offset.south()))) {
                SkyColumn.eachCorner(bounds, offset.east(), offset.south(), camera, corners);
            }
        }
        if (corners.count == 0) {
            return;
        }
        collector.submitCustomGeometry(poses, GooRenderTypes.SKY_FUNNEL_TYPE, (pose, buffer) -> {
            Vector3f corner = new Vector3f();
            for (int at = 0; at < corners.count; at += AXES) {
                corner.set(corners.places[at], corners.places[at + UP], corners.places[at + SOUTH]);
                buffer.addVertex(pose, corner).setColor(1F, 1F, 1F, 1F);
            }
        });
    }

    /**
     * The columns' corners as they draw from the camera, each along its line
     * of sight within the pass's reach, packed east, up, south.
     */
    private static final class DrawnCorners implements SkyColumn.CornerSink {

        private static final int FIRST_CAPACITY = 1024;
        private final Vec3 camera;
        private final double farthest;
        private float[] places = new float[FIRST_CAPACITY];
        private int count;

        DrawnCorners(Vec3 camera, double farthest) {
            this.camera = camera;
            this.farthest = farthest;
        }

        @Override
        public void corner(double x, double y, double z) {
            if (count + AXES > places.length) {
                places = Arrays.copyOf(places, places.length + places.length);
            }
            double east = x - camera.x();
            double up = y - camera.y();
            double south = z - camera.z();
            double scale = SkyColumn.reachScale(east, up, south, farthest);
            places[count] = (float) (east * scale);
            places[count + UP] = (float) (up * scale);
            places[count + SOUTH] = (float) (south * scale);
            count += AXES;
        }
    }

    private static List<RoomCopies.Offset> withOwn(List<RoomCopies.Offset> copies) {
        List<RoomCopies.Offset> all = new ArrayList<>(copies.size() + 1);
        all.add(OWN);
        all.addAll(copies);
        return all;
    }
}
