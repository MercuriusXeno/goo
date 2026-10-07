package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooSubmitter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;

/**
 * A fullbright, translucent quad that drifts with the velocity it was given
 * and floats free of gravity and collision, the body vitality's restore motes
 * and fog puffs share; each subclass shapes its own alpha and size over life.
 * vitality-waves-regenerate-and-court
 */
abstract class DriftingGlowParticle extends SingleQuadParticle {

    /**
     * How a glow is made: its drag, size, life, tint and opening alpha.
     *
     * @param friction         the velocity kept each tick
     * @param collisionSize    the collision box's side, in blocks
     * @param baseLifetime     the fewest ticks it lives
     * @param lifetimeVariance the ticks of life it may add at random
     * @param baseQuadSize     the smallest quad it draws
     * @param quadSizeVariance the quad size it may add at random
     * @param tint             its red, green and blue, each from 0 to 1
     * @param startAlpha       the alpha it opens with
     */
    record Look(float friction, float collisionSize, int baseLifetime, int lifetimeVariance, float baseQuadSize,
                float quadSizeVariance, float[] tint, float startAlpha) {
    }

    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    /**
     * Makes a glow drifting from where it spawns.
     *
     * @param level    the client level
     * @param position the spawn point
     * @param velocity the spawn velocity
     * @param sprites  the glow's sprite set
     * @param look     how the glow is made
     */
    DriftingGlowParticle(ClientLevel level, double[] position, double[] velocity, SpriteSet sprites, Look look) {
        super(level, position[X], position[Y], position[Z], sprites.get(0, 1));
        this.xd = velocity[X];
        this.yd = velocity[Y];
        this.zd = velocity[Z];
        this.setSize(look.collisionSize(), look.collisionSize());
        this.gravity = 0f;
        this.friction = look.friction();
        this.hasPhysics = false;
        RandomSource random = level.getRandom();
        this.lifetime = look.baseLifetime() + random.nextInt(look.lifetimeVariance());
        this.quadSize = look.baseQuadSize() + random.nextFloat() * look.quadSizeVariance();
        this.rCol = look.tint()[RED];
        this.gCol = look.tint()[GREEN];
        this.bCol = look.tint()[BLUE];
        this.alpha = look.startAlpha();
    }

    /**
     * How far through its life the glow is this tick.
     *
     * @return from 0 at spawn to 1 at its last tick
     */
    final float lifeProgress() {
        return (float) this.age / this.lifetime;
    }

    /**
     * Glows blend over what lies behind them.
     *
     * @return the translucent particle render layer
     */
    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Glows at full brightness whatever the light where it floats.
     *
     * @param partialTick the partial tick
     * @return full block and sky light
     */
    @Override
    protected int getLightCoords(float partialTick) {
        return GooSubmitter.fullbrightLight();
    }
}
