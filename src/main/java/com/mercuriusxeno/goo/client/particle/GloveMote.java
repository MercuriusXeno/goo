package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

/**
 * A glove stream mote, which swings with its caster's glove over its
 * opening ticks and then flies free.
 * glove-stream-motes-swing-with-the-glove-at-launch
 */
public abstract class GloveMote extends SingleQuadParticle {

    private GloveSwing.@Nullable Tracker swing;
    private @Nullable Supplier<Vec3> leavesFrom;

    /**
     * Places a mote that swings with no glove until {@link #swingWith} names its caster.
     *
     * @param level  the client level
     * @param x      where the mote starts on x
     * @param y      where the mote starts on y
     * @param z      where the mote starts on z
     * @param sprite the mote's sprite
     */
    protected GloveMote(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
    }

    /**
     * Sets the mote swinging with the glove of the player who sprayed it.
     *
     * @param swing the mote's swing, reading its caster's pose each tick
     */
    public void swingWith(GloveSwing.Tracker swing) {
        this.swing = swing;
    }

    /**
     * Holds the mote back until its first tick, which places it where its
     * caster's glove stands then: the particle engine takes a new mote in
     * only after ticking the ones already flying, so a mote placed where the
     * stream's tick found the glove stood still there a tick or more while
     * its caster ran on.
     * glove-stream-motes-carry-the-players-travel-velocity: a mote leaves the glove where it stands
     *
     * @param glove where the caster's glove stands, read on the mote's first tick
     */
    public void leaveFrom(Supplier<Vec3> glove) {
        this.leavesFrom = glove;
    }

    /** Places a held-back mote at its caster's glove on its first tick, before it moves. */
    protected void leaveTheGlove() {
        if (leavesFrom == null) {
            return;
        }
        Vec3 glove = leavesFrom.get();
        this.setPos(glove.x, glove.y, glove.z);
        leavesFrom = null;
    }

    /**
     * Draws the mote once it has left the glove, and nothing while it waits for its first tick.
     *
     * @param reusedState the reusable render state for quad particles
     * @param camera      the active camera
     * @param rotation    the camera-facing rotation
     * @param partialTick the partial tick for interpolation
     */
    @Override
    protected void extractRotatedQuad(QuadParticleRenderState reusedState, Camera camera, Quaternionf rotation,
                                      float partialTick) {
        if (leavesFrom == null) {
            super.extractRotatedQuad(reusedState, camera, rotation, partialTick);
        }
    }

    /** Swings the mote with its caster's glove while its window lasts, then lets it fly free. */
    protected void swingWithTheGlove() {
        if (swing == null) {
            return;
        }
        GloveSwing.Swung swung = swing.step(new Vec3(this.x, this.y, this.z), new Vec3(this.xd, this.yd, this.zd),
                this.age);
        if (swung == null) {
            swing = null;
            return;
        }
        this.setPos(swung.position().x, swung.position().y, swung.position().z);
        this.xd = swung.velocity().x;
        this.yd = swung.velocity().y;
        this.zd = swung.velocity().z;
    }
}
