package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.IntFunction;

/**
 * A Goop or Slick spray drip: the mod's blocky goo drip, the trail-drip's
 * sprite in the ability's colors, thrown from the glove onto the end of its
 * coat ray, where it splats as the trail-drip does and is gone. Over its
 * opening ticks it rides its caster's glove, so it leaves the hand however
 * the caster walks or turns.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * glove-stream-motes-swing-with-the-glove-at-launch
 */
public final class SprayDripParticle extends GloveMote {

    private final Vec3 apex;
    private final GooRenderUtil.UvRect uv;
    private SprayDrip drip;
    private int flown;
    private Vec3 end;
    private @Nullable Ride ride;

    private SprayDripParticle(ClientLevel level, Vec3 apex, Vec3 end, TextureAtlasSprite sprite, SprayDrip drip) {
        super(level, apex.x, apex.y, apex.z, sprite);
        this.apex = apex;
        this.end = end;
        this.uv = GooSubmitter.spriteUv(sprite);
        this.gravity = 0f;
        this.hasPhysics = false;
        this.rCol = ARGB.redFloat(drip.rgb());
        this.gCol = ARGB.greenFloat(drip.rgb());
        this.bCol = ARGB.blueFloat(drip.rgb());
        fly(drip);
    }

    /**
     * The glove a drip rides over its opening ticks: its caster, whose pose
     * is read each tick, the pose at launch, the glove last tick, and where
     * the drip would stand had the caster kept still.
     */
    private static final class Ride {

        private final int casterId;
        private final IntFunction<GloveSwing.@Nullable CasterPose> poses;
        private final GloveSwing.CasterPose atLaunch;
        private Vec3 lastGlove;
        private Vec3 stillAt;
        private Vec3 stillVelocity;

        Ride(int casterId, IntFunction<GloveSwing.@Nullable CasterPose> poses, GloveSwing.CasterPose atLaunch,
             Vec3 apex, Vec3 velocity) {
            this.casterId = casterId;
            this.poses = poses;
            this.atLaunch = atLaunch;
            this.lastGlove = atLaunch.glove();
            this.stillAt = apex;
            this.stillVelocity = velocity;
        }
    }

    /**
     * Throws the drip onto its coat ray's end from its caster's glove: over
     * its opening ticks it rides the glove as the caster walks and turns, and
     * then flies on with the glove's velocity onto that end. A caster this
     * client holds no pose for throws it carrying the travel the server sent.
     * goop-and-slick-spray-throws-goo-drips-that-splat
     * glove-stream-motes-swing-with-the-glove-at-launch
     *
     * @param casterId the player who sprayed the drip
     * @param poses    each caster's pose this tick by id, null for one this client holds none of
     * @param coatEnd  the end of the drip's coat ray
     * @param travel   the caster's travel velocity the server sent, blocks per tick
     */
    public void rideWith(int casterId, IntFunction<GloveSwing.@Nullable CasterPose> poses, Vec3 coatEnd,
                         Vec3 travel) {
        this.end = coatEnd;
        SprayDrip still = SprayDrip.toward(apex, coatEnd, Vec3.ZERO, drip.rgb());
        GloveSwing.CasterPose atLaunch = poses.apply(casterId);
        if (atLaunch == null || still.holdTicks() == 0) {
            fly(SprayDrip.toward(apex, coatEnd, travel, drip.rgb()));
            return;
        }
        fly(still);
        this.ride = new Ride(casterId, poses, atLaunch, apex, still.velocity());
    }

    private void fly(SprayDrip flight) {
        this.drip = flight;
        this.flown = 0;
        this.xd = flight.velocity().x;
        this.yd = flight.velocity().y;
        this.zd = flight.velocity().z;
    }

    /** Carries the drip on the glove over its opening ticks, flies it on, and splats it at its end. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.age++;
        Ride held = this.ride;
        if (held != null && flown < drip.holdTicks()) {
            rideTheGlove(held);
            return;
        }
        SprayDrip.Step step = drip.step(new Vec3(this.x, this.y, this.z), new Vec3(this.xd, this.yd, this.zd),
                flown++);
        this.setPos(step.position().x, step.position().y, step.position().z);
        this.xd = step.velocity().x;
        this.yd = step.velocity().y;
        this.zd = step.velocity().z;
        if (step.splats()) {
            this.remove();
            this.level.addParticle(ColorParticleOption.create(GooParticles.TRAIL_DRIP_LAND.get(),
                    this.rCol, this.gCol, this.bCol), this.x, this.y, this.z, 0.0, 0.0, 0.0);
        }
    }

    /**
     * One tick on the glove: the drip stands where its flight from a still
     * glove would be, carried with the glove; on its last held tick the glove
     * lets it go with the glove's velocity. A caster gone from this client
     * lets it go where it stands.
     *
     * @param held the glove the drip rides
     */
    private void rideTheGlove(Ride held) {
        GloveSwing.CasterPose now = held.poses.apply(held.casterId);
        if (now == null) {
            this.ride = null;
            fly(SprayDrip.released(new Vec3(this.x, this.y, this.z), end, Vec3.ZERO, drip.rgb()));
            return;
        }
        SprayDrip.Step still = drip.step(held.stillAt, held.stillVelocity, flown++);
        held.stillAt = still.position();
        held.stillVelocity = still.velocity();
        Vec3 at = SprayDrip.heldAt(held.stillAt, held.atLaunch, now);
        this.setPos(at.x, at.y, at.z);
        Vec3 gloveVelocity = now.glove().subtract(held.lastGlove);
        held.lastGlove = now.glove();
        if (flown >= drip.holdTicks()) {
            this.ride = null;
            fly(SprayDrip.released(at, end, gloveVelocity, drip.rgb()));
        }
    }

    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected float getU0() {
        return uv.u0();
    }

    @Override
    protected float getU1() {
        return uv.u1();
    }

    @Override
    protected float getV0() {
        return uv.v0();
    }

    @Override
    protected float getV1() {
        return uv.v1();
    }

    /**
     * Makes an ability's spray drips from the stream's launches, in its palette.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;
        private final BlendPalette palette;

        /**
         * Creates a provider over the trail-drip sprite in an ability's palette.
         *
         * @param sprites the sprite set from the spray drip's definition
         * @param palette the colors its drips blend between
         */
        public Provider(SpriteSet sprites, BlendPalette palette) {
            this.sprites = sprites;
            this.palette = palette;
        }

        @Override
        public @Nullable SprayDripParticle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                                          double y, double z, double xSpeed, double ySpeed,
                                                          double zSpeed, RandomSource random) {
            Vec3 apex = new Vec3(x, y, z);
            Vec3 end = GooStreamHandler.launchEnd(apex, new Vec3(xSpeed, ySpeed, zSpeed));
            return new SprayDripParticle(level, apex, end, sprites.get(0, 1),
                    SprayDrip.launch(apex, end, Vec3.ZERO, palette, random));
        }
    }
}
