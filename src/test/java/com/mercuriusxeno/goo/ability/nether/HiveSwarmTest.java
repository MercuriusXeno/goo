package com.mercuriusxeno.goo.ability.nether;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.client.particle.GnatParticle;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hive's swarm hovers about its column with no prey in reach, and with
 * prey, gnats leave the column launched to come to rest at each mob they reach, as
 * far as the hive's program strikes
 * (decision hive-prism-pillar-eats-the-living).
 */
class HiveSwarmTest {

    private static final double DELTA = 1e-6;
    private static final long SEED = 7L;
    /** Decay, whose on_prism reaction is the hive (decision channeled-hosts-convert-prisms-over-the-hold). */
    private static final String HIVE = "/data/goo/goo_abilities/nether_decay.json";

    /** The gap of (3, 0.9, -2) times the tenth of its speed a gnat loses each tick, so it comes to rest at the mob. */
    @Test
    void aGnatLaunchesAtTheGapTimesTheShareOfSpeedItLoses() {
        Vec3 launch = HiveSwarm.launchToward(new Vec3(0.5, 0.5, 0.5), new Vec3(3.5, 1.4, -1.5));

        assertEquals(0.3, launch.x, DELTA);
        assertEquals(0.09, launch.y, DELTA);
        assertEquals(-0.2, launch.z, DELTA);
    }

    @Test
    void withNoPreyInReachOneIdleGnatHoversAtTheColumn() {
        Vec3 column = new Vec3(0.5, 0.5, 0.5);
        List<HiveSwarm.Gnat> gnats = HiveSwarm.gnatsFor(column, List.of(), RandomSource.create(SEED));
        assertEquals(1, gnats.size());
        HiveSwarm.Gnat idle = gnats.getFirst();
        assertTrue(Math.abs(idle.at().x - column.x) <= HiveSwarm.CLUSTER_RADIUS
                && Math.abs(idle.at().y - column.y) <= HiveSwarm.CLUSTER_RADIUS
                && Math.abs(idle.at().z - column.z) <= HiveSwarm.CLUSTER_RADIUS, "idle gnat at " + idle.at());
        assertTrue(Math.abs(idle.velocity().x) <= HiveSwarm.IDLE_DRIFT
                && Math.abs(idle.velocity().y) <= HiveSwarm.IDLE_DRIFT
                && Math.abs(idle.velocity().z) <= HiveSwarm.IDLE_DRIFT, "idle gnat drifts " + idle.velocity());
    }

    @Test
    void withPreyInReachGnatsLeaveTheColumnForEachMob() {
        Vec3 column = new Vec3(0.5, 0.5, 0.5);
        Vec3 near = new Vec3(3.5, 1.4, -1.5);
        Vec3 far = new Vec3(-2.5, 1.0, 4.5);
        List<HiveSwarm.Gnat> gnats = HiveSwarm.gnatsFor(column, List.of(near, far), RandomSource.create(SEED));
        assertEquals(2 * HiveSwarm.GNATS_PER_MOB, gnats.size());
        for (HiveSwarm.Gnat gnat : gnats) {
            assertEquals(column, gnat.at());
        }
        assertEquals(HiveSwarm.launchToward(column, near), gnats.getFirst().velocity());
        assertEquals(HiveSwarm.launchToward(column, far), gnats.getLast().velocity());
    }

    @Test
    void theSwarmSlowsAsAGnatDoes() {
        assertEquals(GnatParticle.SWARM_FRICTION, HiveSwarm.GNAT_FRICTION, DELTA);
    }

    @Test
    void theSwarmReachesAsFarAsTheHiveStrikes() throws IOException {
        try (InputStream in = HiveSwarmTest.class.getResourceAsStream(HIVE)) {
            assertNotNull(in, HIVE);
            JsonObject hive = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            double radius = hive.getAsJsonArray("on_prism").get(0).getAsJsonObject().get("radius").getAsDouble();
            assertEquals(radius, HiveSwarm.REACH, DELTA);
        }
    }
}
