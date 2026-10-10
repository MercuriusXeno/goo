package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A plain colored latitude-longitude sphere of quads, the shell Scry's sweep
 * and Radiant's wisps draw through glow's additive shell pipeline.
 * decisions scry-sphere-reveals-faces-and-glistens-mobs, radiant-wisps-where-light-is-low
 */
public final class ColorSphere {

    private static final int STACKS = 24;
    private static final int SLICES = 48;

    private ColorSphere() {
    }

    /**
     * Emits the sphere about a center.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer, position and color
     * @param center   the sphere's center in the pose's space
     * @param r        the radius in blocks
     * @param color    the packed ARGB color
     */
    public static void emit(PoseStack.Pose pose, VertexConsumer consumer, Vec3 center, float r, int color) {
        for (int stack = 0; stack < STACKS; stack++) {
            float lat0 = Mth.PI * stack / STACKS;
            float lat1 = Mth.PI * (stack + 1) / STACKS;
            for (int slice = 0; slice < SLICES; slice++) {
                float lon0 = Mth.TWO_PI * slice / SLICES;
                float lon1 = Mth.TWO_PI * (slice + 1) / SLICES;
                point(pose, consumer, center, r, lat0, lon0, color);
                point(pose, consumer, center, r, lat1, lon0, color);
                point(pose, consumer, center, r, lat1, lon1, color);
                point(pose, consumer, center, r, lat0, lon1, color);
            }
        }
    }

    private static void point(PoseStack.Pose pose, VertexConsumer consumer, Vec3 center, float r,
                              float lat, float lon, int color) {
        float ring = Mth.sin(lat) * r;
        consumer.addVertex(pose, (float) center.x + ring * Mth.cos(lon), (float) center.y + Mth.cos(lat) * r,
                (float) center.z + ring * Mth.sin(lon)).setColor(color);
    }
}
