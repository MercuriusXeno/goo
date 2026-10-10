package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlyTowardsPositionParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * An enchanting glyph in hex purple, flying in to the point it was spawned
 * at from the offset it was given, as the enchanting table's glyphs fly into
 * its book: Enchant's and Fuse's tomes draw them in.
 * enchant-book-with-a-purple-afterimage
 */
public final class HexGlyphParticle extends FlyTowardsPositionParticle {

    /** The darkest and brightest hex purple a glyph may take. */
    private static final float[] DEEP = {0.45f, 0.18f, 0.72f};
    private static final float[] BRIGHT = {0.85f, 0.62f, 1.0f};
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;

    private HexGlyphParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                             SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, xd, yd, zd, true, Particle.LifetimeAlpha.ALWAYS_OPAQUE, sprites.get(random));
        float shade = random.nextFloat();
        this.rCol = Mth.lerp(shade, DEEP[RED], BRIGHT[RED]);
        this.gCol = Mth.lerp(shade, DEEP[GREEN], BRIGHT[GREEN]);
        this.bCol = Mth.lerp(shade, DEEP[BLUE], BRIGHT[BLUE]);
    }

    /**
     * Creates hex glyphs: x, y and z name where a glyph lands, and the speeds
     * the offset it flies in from.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the glyphs' sprite set.
         *
         * @param sprites the sprite set from hex_glyph.json
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable HexGlyphParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                         double x, double y, double z,
                                                         double xSpeed, double ySpeed, double zSpeed,
                                                         RandomSource random) {
            return new HexGlyphParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, random);
        }
    }
}
