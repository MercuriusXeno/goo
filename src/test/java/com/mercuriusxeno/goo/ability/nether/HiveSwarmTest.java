package com.mercuriusxeno.goo.ability.nether;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.client.particle.GnatParticle;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A hive's gnats fly out from the column and come to rest at the mob they
 * reach, as far as the hive's program strikes
 * (decision hive-prism-pillar-eats-the-living).
 */
class HiveSwarmTest {

    private static final double DELTA = 1e-6;
    private static final String HIVE = "/data/goo/goo_abilities/nether_hive.json";
    private static final int TICKS_TO_REST = 400;

    @Test
    void aLaunchedGnatComesToRestAtTheMob() {
        Vec3 column = new Vec3(0.5, 0.5, 0.5);
        Vec3 mob = new Vec3(3.5, 1.4, -1.5);
        Vec3 velocity = HiveSwarm.launchToward(column, mob);
        Vec3 at = column;
        for (int tick = 0; tick < TICKS_TO_REST; tick++) {
            at = at.add(velocity);
            velocity = velocity.scale(HiveSwarm.GNAT_FRICTION);
        }
        assertEquals(0, at.distanceTo(mob), DELTA);
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
            double radius = hive.getAsJsonArray("behaviors").get(0).getAsJsonObject().get("radius").getAsDouble();
            assertEquals(radius, HiveSwarm.REACH, DELTA);
        }
    }
}
