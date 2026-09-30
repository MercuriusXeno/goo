package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.GooRingMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Draws every live goo ring particle through the goo ring pipeline as
 * custom geometry (decision goo-swirl-ring-particle).
 */
public final class GooRingParticleGroup extends ParticleGroup<GooRingParticle> {

    /**
     * Creates the group for a particle engine.
     *
     * @param engine the particle engine
     */
    public GooRingParticleGroup(ParticleEngine engine) {
        super(engine);
    }

    @Override
    public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTickTime) {
        Vec3 position = camera.position();
        return new State(this.particles.stream().map(ring -> ring.toRing(position, partialTickTime)).toList());
    }

    /**
     * One ring's draw state, camera relative.
     *
     * @param x      the ring center's X relative to the camera
     * @param y      the ring center's Y relative to the camera
     * @param z      the ring center's Z relative to the camera
     * @param face   the face whose axis the disc lies square to
     * @param radius the disc's radius in blocks
     * @param color  the packed vertex color
     */
    record Ring(double x, double y, double z, Direction face, float radius, int color) {
    }

    /**
     * The frame's rings, each submitted as custom geometry on the goo ring render type.
     *
     * @param rings the rings
     */
    private record State(List<Ring> rings) implements ParticleGroupRenderState {
        @Override
        public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
            for (Ring ring : rings) {
                PoseStack poseStack = new PoseStack();
                poseStack.translate(ring.x(), ring.y(), ring.z());
                collector.submitCustomGeometry(poseStack, GooRenderTypes.GOO_RING_TYPE, (pose, buffer) ->
                        GooRingMesh.emit(pose, buffer, ring.face(), ring.radius(), ring.color()));
            }
        }
    }
}
