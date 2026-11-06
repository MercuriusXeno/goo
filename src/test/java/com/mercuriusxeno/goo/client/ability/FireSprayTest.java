package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Held Flames and a heater prism both pour through the one particle fire
 * emitter, which throws licks and embers from the spout inside the cone, the
 * licks reaching out to the stream's range and no farther
 * (decision flames-draws-as-particle-style-fire).
 */
class FireSprayTest {

    private static final double RANGE = 6;
    private static final double CONE_DEGREES = 20;
    private static final double DELTA = 1e-9;
    /** Slack on the cone's half angle for float rounding. */
    private static final double ANGLE_SLACK = 1e-6;
    private static final int SEEDS = 40;
    private static final Vec3 HAND = new Vec3(3, 65, -2);
    private static final Vec3 AXIS = new Vec3(1, -0.5, 2).normalize();

    @Test
    void heldFlamesPoursFromTheGloveDownItsAimAtItsDeliveryReachAndCone() {
        Delivery delivery = new Delivery(DeliveryKind.STREAM, 0, RANGE, CONE_DEGREES, 0, false, Optional.empty(), 0);
        FireSpray.Spout spout = FireStream.spoutOf(delivery, HAND, AXIS);
        assertEquals(new FireSpray.Spout(HAND, AXIS, RANGE, CONE_DEGREES), spout);
    }

    @Test
    void aHeaterPoursFlamesOwnFireFromItsTipAtItsTarget() {
        FireSpray.Spout spout = HeaterFlames.spoutOf(new HeaterFlames.Heater(HAND, AXIS, 0));
        assertEquals(new FireSpray.Spout(HAND, AXIS, RANGE, CONE_DEGREES), spout);
    }

    @Test
    void theSprayThrowsBothLicksAndEmbers() {
        List<FireSpray.Spark> sparks = FireSpray.sparksOf(spout(), RandomSource.create(1));
        assertTrue(sparks.stream().anyMatch(spark -> spark.kind() == FireSpray.Kind.LICK));
        assertTrue(sparks.stream().anyMatch(spark -> spark.kind() == FireSpray.Kind.EMBER));
    }

    @Test
    void everySparkFliesInsideTheConeFromNearTheSpout() {
        double halfCone = Math.toRadians(CONE_DEGREES / 2);
        for (int seed = 0; seed < SEEDS; seed++) {
            for (FireSpray.Spark spark : FireSpray.sparksOf(spout(), RandomSource.create(seed))) {
                double angle = Math.acos(Math.min(1, spark.velocity().normalize().dot(AXIS)));
                assertTrue(angle <= halfCone + ANGLE_SLACK, "a spark flew " + Math.toDegrees(angle) + " degrees off");
                assertTrue(spark.at().distanceTo(HAND) <= spark.velocity().length() + DELTA,
                        "a spark started more than one tick's flight from the spout");
            }
        }
    }

    @Test
    void licksReachOutToTheRangeAndNoFarther() {
        double farthest = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            for (FireSpray.Spark spark : FireSpray.sparksOf(spout(), RandomSource.create(seed))) {
                if (spark.kind() == FireSpray.Kind.LICK) {
                    double reach = spark.velocity().length() * FireSpray.LICK_REACH_PER_SPEED;
                    assertTrue(reach <= RANGE + DELTA, "a lick would fly " + reach + " blocks");
                    farthest = Math.max(farthest, reach);
                }
            }
        }
        assertTrue(farthest > RANGE * FireSpray.LICK_SLOWEST, "no lick reached near the range: " + farthest);
    }

    private static FireSpray.Spout spout() {
        return new FireSpray.Spout(HAND, AXIS, RANGE, CONE_DEGREES);
    }
}
