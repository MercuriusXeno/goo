package com.mercuriusxeno.goo.client.zone;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * How one look of conjured door is drawn: Zone's and End's with the
 * starfield, Tartarus' with its own texel doorway.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 */
@FunctionalInterface
public interface GateDrawing {

    /**
     * Submits one door as a frame draws it.
     *
     * @param collector the collector the frame submits to
     * @param poseStack the frame's pose
     * @param door      the door drawn
     * @param camera    the camera's world position
     * @param time      the client game tick and the frame's partial tick
     */
    void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door, Vec3 camera,
                double time);
}
