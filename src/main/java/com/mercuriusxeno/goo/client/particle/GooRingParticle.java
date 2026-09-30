package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.ability.GooRingMesh;
import com.mercuriusxeno.goo.registry.GooRingParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Goo's swirling ring particle (decision goo-swirl-ring-particle): a disc
 * square to its face's axis at its radius, still where it was sent, drawn by
 * {@link GooRingParticleGroup} through the goo ring pipeline, since a quad
 * particle cannot run a shader of its own.
 */
public final class GooRingParticle extends Particle {

    /** The group every ring particle draws in, registered by RegisterParticleGroupsEvent. */
    public static final ParticleRenderType GROUP = new ParticleRenderType("GOO_RING");

    /** Ticks the ring plays: the layer walk's preview delay, then two ticks into the break. */
    static final int LIFETIME_TICKS = 10;

    private final Direction face;
    private final float radius;
    private final int color;

    private GooRingParticle(ClientLevel level, double x, double y, double z, GooRingParticleOptions options) {
        super(level, x, y, z);
        this.face = options.face();
        this.radius = options.radius();
        this.color = options.color();
        this.lifetime = LIFETIME_TICKS;
        this.hasPhysics = false;
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public ParticleRenderType getGroup() {
        return GROUP;
    }

    /**
     * The ring as its group draws it this frame, relative to the camera.
     *
     * @param camera      the camera's world position
     * @param partialTick the frame's partial tick
     * @return the ring's draw state
     */
    GooRingParticleGroup.Ring toRing(Vec3 camera, float partialTick) {
        float progress = GooRingMesh.progress(this.age, partialTick, this.lifetime);
        return new GooRingParticleGroup.Ring(this.x - camera.x, this.y - camera.y, this.z - camera.z,
                this.face, this.radius, GooRingMesh.vertexColor(this.color, progress));
    }

    /** Provider building a ring from its typed options. */
    public static final class Provider implements ParticleProvider<GooRingParticleOptions> {
        @Override
        public Particle createParticle(GooRingParticleOptions options, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       RandomSource random) {
            return new GooRingParticle(level, x, y, z, options);
        }
    }
}
