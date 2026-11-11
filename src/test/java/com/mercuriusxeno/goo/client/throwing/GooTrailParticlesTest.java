package com.mercuriusxeno.goo.client.throwing;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The drips and fog puffs a thrown blob sheds launch with the blob's own
 * velocity, so the trail moves with the blob rather than hanging where it
 * spawned (decision thrown-blob-trail-takes-the-blobs-velocity).
 */
class GooTrailParticlesTest {

    private static final Vec3 BLOB_VELOCITY = new Vec3(0.9, 0.3, -0.4);
    private static final double DELTA = 1e-12;

    @Test
    void aDripLaunchesWithTheBlobsVelocityPlusItsDownwardPull() {
        Vec3 drip = GooTrailParticles.dripLaunch(BLOB_VELOCITY);

        assertEquals(BLOB_VELOCITY.x, drip.x, DELTA);
        assertEquals(BLOB_VELOCITY.y + GooTrailParticles.DRIP_DOWN_VEL, drip.y, DELTA);
        assertEquals(BLOB_VELOCITY.z, drip.z, DELTA);
    }

    @Test
    void aFogPuffLaunchesWithTheBlobsVelocityPlusItsJitter() {
        Vec3 jitter = new Vec3(0.01, -0.005, 0.002);

        assertEquals(BLOB_VELOCITY.add(jitter), GooTrailParticles.fogLaunch(BLOB_VELOCITY, jitter));
    }
}
