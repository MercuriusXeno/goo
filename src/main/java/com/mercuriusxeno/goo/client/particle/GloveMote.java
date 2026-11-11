package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A glove stream mote, which swings with its caster's glove over its
 * opening ticks and then flies free.
 * glove-stream-motes-swing-with-the-glove-at-launch
 */
public abstract class GloveMote extends SingleQuadParticle {

    private GloveSwing.@Nullable Tracker swing;

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
