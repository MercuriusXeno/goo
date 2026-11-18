package com.mercuriusxeno.goo.client.zone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Draws Zone's and End's door as a pixel unzipping into a slash and
 * widening into a doorway, its outline filled with the End portal's
 * starfield, seen from either side, facing its caster.
 * decision crack-and-doorway-wear-the-end-portal-shader
 * decision door-opens-from-a-thin-crack-in-the-air
 * decision door-unzips-from-a-starfield-pixel-then-widens-into-you
 */
public final class StarfieldDoorDrawing implements GateDrawing {

    /** The drawing. */
    public static final StarfieldDoorDrawing INSTANCE = new StarfieldDoorDrawing();

    private StarfieldDoorDrawing() {
    }

    /**
     * Submits one door's outline under the End portal render type, whose
     * vertices carry their position alone, as the End gate submits its square.
     *
     * @param collector the collector the frame submits to
     * @param poseStack the frame's pose
     * @param door      the door drawn
     * @param camera    the camera's world position
     * @param time      the client game tick and the frame's partial tick, which the starfield needs not
     */
    @Override
    public void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door,
                       Vec3 camera, double time) {
        Vec3[] corners = cornersOf(door, camera);
        collector.submitCustomGeometry(poseStack, RenderTypes.endPortal(),
                (pose, consumer) -> emitBothWaysRound(pose, consumer, corners));
    }

    /**
     * The door's outline corners, stood upright at its foot across the way
     * its caster faced, relative to the camera.
     *
     * @param door   the door drawn
     * @param camera the camera's world position
     * @return the corners in order around the outline
     */
    private static Vec3[] cornersOf(ClientConjuredDoors.Drawn door, Vec3 camera) {
        double radians = Math.toRadians(door.yaw());
        Vec3 across = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        Vec3 foot = door.at().subtract(camera);
        List<DoorCrack.Corner> outline = DoorCrack.outline(door.unzipped(), door.widened());
        Vec3[] corners = new Vec3[outline.size()];
        for (int index = 0; index < corners.length; index++) {
            DoorCrack.Corner corner = outline.get(index);
            corners[index] = foot.add(across.scale(corner.across())).add(0, corner.up(), 0);
        }
        return corners;
    }

    private static void emitBothWaysRound(PoseStack.Pose pose, VertexConsumer consumer, Vec3[] corners) {
        for (Vec3 corner : corners) {
            vertex(pose, consumer, corner);
        }
        for (int index = corners.length - 1; index >= 0; index--) {
            vertex(pose, consumer, corners[index]);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z);
    }
}
