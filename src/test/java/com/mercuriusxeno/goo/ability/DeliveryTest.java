package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.client.overlay.ArcRenderer;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ability's delivery block decodes to its kind and params, and the kind
 * decides a flight's travel ticks and peak (decision delivery-block-in-ability-json).
 */
class DeliveryTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("goo", "fixture");
    private static final String ABILITY_HEAD = """
            { "gooType": "glow", "displayName": "goo.ability.fixture", "badge": "world", "cost": 1000,
              "behaviors": [], """;
    private static final double TOLERANCE = 1e-9;
    private static final Vec3 START = new Vec3(3, 64, -2);
    private static final Vec3 TEN_AWAY = START.add(0, 0, 10);
    private static final double TEN_BLOCKS = 10;
    private static final float LEVITY = 0.7f;
    private static final int BASE_FLIGHT_TIME = 3;
    private static final int BULB_FULL_RANGE_TICK_BUDGET = 4;
    private static final String TOP_SPEED = "top_speed";

    private static DataResult<AbilityDefinition> decode(String deliveryField) {
        return AbilityDefinition.codecFor(ID)
                .parse(JsonOps.INSTANCE, JsonParser.parseString(ABILITY_HEAD + deliveryField + " \"order\": 0 }"));
    }

    private static Delivery deliveryOf(String deliveryField) {
        return decode(deliveryField).getOrThrow().delivery();
    }

    @Nested
    class Codec {

        @Test
        void beamBlockDecodesToBeamAtItsSpeed() {
            Delivery delivery = deliveryOf("\"delivery\": { \"kind\": \"beam\", \"blocks_per_tick\": 2.5 },");
            assertEquals(DeliveryKind.BEAM, delivery.kind());
            assertEquals(2.5, delivery.blocksPerTick(), TOLERANCE);
        }

        @Test
        void beamSpeedReadsTheJsonNotTheDefault() {
            assertEquals(4.0, deliveryOf("\"delivery\": { \"kind\": \"beam\", \"blocks_per_tick\": 4 },")
                    .blocksPerTick(), TOLERANCE);
        }

        /** A JSON naming no delivery refuses at load (decision standing-abilities-name-arc-or-beam). */
        @Test
        void abilityNamingNoDeliveryRefusesNamingTheField() {
            DataResult<AbilityDefinition> result = decode("");
            assertTrue(result.error().orElseThrow().message().contains("delivery"));
        }

        @Test
        void unknownKindRefusesNamingIt() {
            DataResult<AbilityDefinition> result = decode("\"delivery\": { \"kind\": \"lob\" },");
            assertTrue(result.error().orElseThrow().message().contains("lob"));
        }

        @Test
        void streamParamsReadTheJson() {
            Delivery delivery = deliveryOf("""
                    "delivery": { "kind": "stream", "range": 6, "cone": 30, "ticks_per_charge": 10 },""");
            assertEquals(new Delivery(DeliveryKind.STREAM, Delivery.DEFAULT_BLOCKS_PER_TICK, 6, 30, 10, true, java.util.Optional.empty(),
                    Delivery.DEFAULT_TRANSFORM_AT),
                    delivery);
        }

        // nova-ring-grows-with-the-hold
        @Test
        void chargeReadsItsMaxTicks() {
            Delivery delivery = deliveryOf("\"delivery\": { \"kind\": \"self\", \"charge\": { \"max_ticks\": 60 } },");
            assertTrue(delivery.charges());
            assertEquals(60, delivery.chargeTicks());
        }

        // shards-sling-then-morph-to-flechettes
        // knives-cone-five-to-forty-five-and-three-to-twenty-seven-knives
        @Test
        void crystalKnivesChargesARainOfKnives() {
            Delivery knives = AbilityJson.decode("crystal_knives").delivery();
            assertTrue(knives.charge().slings());
        }

        // knives-aims-a-spread-fan-not-a-blob-line
        @Test
        void knivesSlingsAFanAndAimsNoLineWhileABlobArcAndABeamAimALine() {
            Delivery knives = AbilityJson.decode("crystal_knives").delivery();
            assertTrue(knives.slingsAFan());
            assertFalse(knives.aimsALine());
            assertTrue(AbilityJson.decode("frost_orb").delivery().aimsALine());
            assertTrue(AbilityJson.decode("glow_crystal").delivery().aimsALine());
        }

        // traveling-form-transforms-in-flight
        @Test
        void javelinMorphsToADartAndAPlainArcStaysABlob() {
            assertEquals(TravelForm.DART, AbilityJson.decode("metal_javelin").delivery().form());
            assertEquals(TravelForm.BLOB, deliveryOf("\"delivery\": { \"kind\": \"arc\" },").form());
        }

        @Test
        void aDeliveryNamingNoChargeDoesNotCharge() {
            assertFalse(deliveryOf("\"delivery\": { \"kind\": \"self\" },").charges());
        }

        @Test
        void grannyReadsTheJson() {
            assertEquals(false, deliveryOf("\"delivery\": { \"kind\": \"arc\", \"granny\": false },")
                    .grannyAllowed());
        }
    }

    // nova-ring-grows-with-the-hold
    @Nested
    class Charge {

        private final Delivery nova = new Delivery(DeliveryKind.SELF, Delivery.DEFAULT_BLOCKS_PER_TICK, 0,
                Delivery.DEFAULT_CONE_DEGREES, Delivery.DEFAULT_TICKS_PER_CHARGE, true, java.util.Optional.empty(),
                Delivery.DEFAULT_TRANSFORM_AT, java.util.Optional.empty(), 60);

        @Test
        void aHoldChargesInProportionToItsTicks() {
            assertEquals(0.5f, nova.chargeShare(30));
            assertEquals(0f, nova.chargeShare(0));
        }

        @Test
        void aHoldPastTheMaxStaysFull() {
            assertEquals(1f, nova.chargeShare(600));
        }

        @Test
        void aDeliveryThatDoesNotChargeFiresWhole() {
            assertEquals(1f, Delivery.of(DeliveryKind.SELF).chargeShare(0));
        }
    }

    // orb-carries-a-swirling-nova
    @Nested
    class Rolls {

        private static final double SLOW = 0.3;
        private static final double RANGE = 40;

        private Delivery arc(double blocksPerTick, double range) {
            return new Delivery(DeliveryKind.ARC, blocksPerTick, range, Delivery.DEFAULT_CONE_DEGREES,
                    Delivery.DEFAULT_TICKS_PER_CHARGE, true, java.util.Optional.empty(), Delivery.DEFAULT_TRANSFORM_AT);
        }

        @Test
        void aSlowArcNamingARangeRolls() {
            assertTrue(arc(SLOW, RANGE).rolls());
        }

        @Test
        void aSlowArcNamingNoRangeDoesNotRoll() {
            assertFalse(arc(SLOW, 0).rolls());
        }

        // orb-aims-a-straight-line: a rolling goo previews a straight line with no peak
        @Test
        void aRollingDeliveryAimsStraightWithNoPeak() {
            Delivery orb = arc(SLOW, RANGE);
            assertTrue(orb.aimsStraight());
            assertEquals(0.0, orb.peak(START, TEN_AWAY, false), TOLERANCE);
            assertEquals(0.0, orb.peak(START, TEN_AWAY, true), TOLERANCE);
        }

        @Test
        void aPlainArcStillAimsAnArc() {
            assertFalse(Delivery.ARC.aimsStraight());
        }

        @Test
        void anArcAtTheDefaultSpeedDoesNotRoll() {
            assertFalse(arc(Delivery.DEFAULT_BLOCKS_PER_TICK, RANGE).rolls());
            assertFalse(Delivery.ARC.rolls());
        }
    }

    @Nested
    class TravelAndPeak {

        @Test
        void beamTravelsAtItsSpeedWithNoPeak() {
            Delivery beam = Delivery.of(DeliveryKind.BEAM);
            assertEquals((int) Math.ceil(TEN_BLOCKS / beam.blocksPerTick()),
                    beam.travelTicks(TEN_BLOCKS, LEVITY, BASE_FLIGHT_TIME));
            assertEquals(0.0, beam.peak(START, TEN_AWAY, false), TOLERANCE);
        }

        @Test
        void beamTravelsAtLeastOneTick() {
            assertEquals(1, Delivery.BEAM.travelTicks(0, LEVITY, BASE_FLIGHT_TIME));
        }

        @Test
        void arcTakesTheTypeFlightTimeAndBasePeak() {
            assertEquals((int) ThrowArc.travelTicks(TEN_BLOCKS, LEVITY, BASE_FLIGHT_TIME),
                    Delivery.ARC.travelTicks(TEN_BLOCKS, LEVITY, BASE_FLIGHT_TIME));
            assertEquals(ThrowArc.basePeak(TEN_BLOCKS), Delivery.ARC.peak(START, TEN_AWAY, false), TOLERANCE);
        }

        /**
         * A lob flight flies the peak the aim indicator draws for the same start
         * and top face, whether the hand sits below the face or above it
         * (decision lob-apex-at-top-face-height).
         */
        @Test
        void grannyArcFliesTheIndicatorPeak() {
            Vec3 faceAboveHand = START.add(0, 2, 8);
            Vec3 faceBelowHand = START.add(0, -2, 8);
            assertEquals(ArcRenderer.computeArcPeak(START, faceAboveHand, 1),
                    Delivery.ARC.peak(START, faceAboveHand, true), TOLERANCE);
            assertEquals(ArcRenderer.computeArcPeak(START, faceBelowHand, 1),
                    Delivery.ARC.peak(START, faceBelowHand, true), TOLERANCE);
        }

        @Test
        void arcBarringGrannyFliesTheBasePeak() {
            Vec3 faceAboveHand = START.add(0, 2, 8);
            Delivery noGranny = new Delivery(DeliveryKind.ARC, Delivery.DEFAULT_BLOCKS_PER_TICK, 0,
                    Delivery.DEFAULT_CONE_DEGREES, Delivery.DEFAULT_TICKS_PER_CHARGE, false, java.util.Optional.empty(), Delivery.DEFAULT_TRANSFORM_AT);
            assertEquals(ThrowArc.basePeak(START.distanceTo(faceAboveHand)),
                    noGranny.peak(START, faceAboveHand, true), TOLERANCE);
        }
    }

    @Nested
    class Shipped {

        /** Every bundled ability names its delivery kind (decision standing-abilities-name-arc-or-beam). */
        @Test
        void everyBundledAbilityNamesAKind() {
            assertAll(AbilityJson.files().stream().map(file -> (Executable) () ->
                    assertNotNull(AbilityJson.decode(file).delivery().kind(), file.getFileName().toString())));
        }

        /** A kind shipped without its proving ability fails the build (decision one-proving-ability-per-kind). */
        @Test
        void everyDeliveryKindIsNamedByABundledAbility() {
            Set<DeliveryKind> named = AbilityJson.files().stream()
                    .map(file -> AbilityJson.decode(file).delivery().kind())
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(DeliveryKind.class)));
            Set<DeliveryKind> unnamed = EnumSet.complementOf(EnumSet.copyOf(named));
            assertEquals(Set.of(), unnamed, "No bundled ability names delivery kind " + unnamed);
        }

        /**
         * A bulb cast at the throw's full range lands within a handful of
         * ticks, so placing a bulb feels like pointing, not waiting.
         */
        // bulb-beam-lands-in-a-few-ticks
        @Test
        void bulbBeamLandsAFullRangeCastWithinTheTickBudget() {
            Delivery bulb = AbilityJson.decode("glow_crystal").delivery();
            assertTrue(bulb.travelTicks(GooThrowHandler.MAX_RANGE, LEVITY, BASE_FLIGHT_TIME)
                    <= BULB_FULL_RANGE_TICK_BUDGET);
        }

        /**
         * A dart thrown at a mob ten blocks off, Metal's levity and base
         * flight time, stays in the air at least the ticks the top speed
         * metal_javelin.json names takes to cover the distance, so the eye
         * follows it (decision dart-flies-at-a-speed-you-can-follow).
         */
        @Test
        void dartFliesNoFasterThanTheSpeedItsJsonNames() {
            JsonObject delivery = JsonParser.parseString(AbilityJson.read("metal_javelin")).getAsJsonObject()
                    .getAsJsonObject("delivery");
            assertTrue(delivery.has(TOP_SPEED), "metal_javelin.json names no " + TOP_SPEED);
            double topSpeed = delivery.get(TOP_SPEED).getAsDouble();
            int fewestTicks = (int) Math.ceil(TEN_BLOCKS / topSpeed);
            int flight = AbilityJson.decode("metal_javelin").delivery().travelTicks(TEN_BLOCKS, LEVITY,
                    BASE_FLIGHT_TIME);
            assertTrue(flight >= fewestTicks, "a ten-block dart flew " + flight + " ticks where "
                    + topSpeed + " blocks a tick takes " + fewestTicks);
        }

        @Test
        void bulbBeamsAndSunbeamChannelsOnSelf() {
            assertEquals(DeliveryKind.BEAM, AbilityJson.decode("glow_crystal").delivery().kind());
            assertEquals(DeliveryKind.SELF, AbilityJson.decode("glow_sunbeam").delivery().kind());
        }
    }

    /**
     * Goop and Slick are the shipped streams throwing drips, so their spray
     * lands where their coat lands; every other stream's motes keep the
     * crosshair-distance launch.
     * goop-and-slick-spray-follows-the-coat
     */
    @Nested
    class SprayFollowsTheCoat {

        /**
         * goop-and-slick-coat-lands-where-each-drip-arc-strikes
         */
        @Test
        void goopAndSlickAloneThrowDripsOnTheirArcs() {
            Set<Identifier> throwing = AbilityJson.files().stream()
                    .filter(file -> AbilityJson.decode(file).delivery().throwsDrips())
                    .map(file -> AbilityJson.idOf(file.getFileName().toString()))
                    .collect(Collectors.toSet());

            assertEquals(Set.of(Identifier.fromNamespaceAndPath("goo", "weird_goop"),
                    Identifier.fromNamespaceAndPath("goo", "kinetic_slick")), throwing);
        }
    }

}
