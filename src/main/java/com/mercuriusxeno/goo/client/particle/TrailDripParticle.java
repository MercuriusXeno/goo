package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.particle.SpriteSet;

/**
 * The trail-drip: the 2x3 drip a thrown goo blob sheds mid-flight, keeping
 * the blob's lateral velocity (decision tap-drip-own-square-particles).
 */
public final class TrailDripParticle {

    private TrailDripParticle() {
    }

    /** Provider for the falling trail-drip, spawned by the blob flight trail. */
    public static class Provider extends DripParticle.FallProvider {

        /**
         * Creates a provider splatting as trail_drip_land.
         *
         * @param sprites the sprite set from the trail_drip definition
         */
        public Provider(SpriteSet sprites) {
            super(sprites, GooParticles.TRAIL_DRIP_LAND);
        }
    }

    /** Provider for the trail-drip's ground splat. */
    public static class LandProvider extends DripParticle.LandProvider {

        /**
         * Creates a land provider.
         *
         * @param sprites the sprite set from the trail_drip_land definition
         */
        public LandProvider(SpriteSet sprites) {
            super(sprites);
        }
    }
}
