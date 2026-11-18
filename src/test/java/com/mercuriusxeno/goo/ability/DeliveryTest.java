package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final double SIXTEEN_BLOCKS = 16;
    private static final Vec3 SIXTEEN_AWAY = START.add(0, 0, SIXTEEN_BLOCKS);
    private static final String PEAK_SHARE = "peak_share";

    private static DataResult<AbilityDefinition> decode(String deliveryField) {
        return AbilityDefinition.codecFor(ID)
                .parse(JsonOps.INSTANCE, JsonParser.parseString(ABILITY_HEAD + deliveryField + " \"order\": 0 }"));
    }

    private static Delivery deliveryOf(String deliveryField) {
        return decode(deliveryField).getOrThrow().delivery();
    }

    /**
     * Bore drills only up to 3 blocks away, its own range, whatever the
     * caster's block reach (operator ruling 2026-11-14, replacing decision
     * bore-reaches-as-far-as-your-block-reach).
     */
    @Nested
    class BoresReach {

        private final Delivery bore = AbilityJson.decode("rock_bore").delivery();

        @Test
        void aBoreReachesThreeBlocksWhateverTheBlockReach() {
            assertEquals(3, bore.reachingTo(4.5).range(), TOLERANCE);
            assertEquals(3, bore.reachingTo(12.5).range(), TOLERANCE);
        }

        @Test
        void aStreamNamingItsOwnRangeKeepsItWhateverTheBlockReach() {
            Delivery stream = deliveryOf("\"delivery\": { \"kind\": \"stream\", \"range\": 6 },");
            assertEquals(6, stream.reachingTo(12.5).range(), TOLERANCE);
        }
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

        // knives-form-in-fibonacci-stages-to-twelve
        @Test
        void crystalKnivesFormsItsKnivesInStages() {
            Delivery knives = AbilityJson.decode("crystal_knives").delivery();
            assertTrue(knives.charge().formsInStages());
        }

        // knives-aims-a-spread-fan-not-a-blob-line
        @Test
        void knivesSlingsAFanAndAimsNoLineWhileABlobArcAndABeamAimALine() {
            Delivery knives = AbilityJson.decode("crystal_knives").delivery();
            assertTrue(knives.slingsAFan());
            assertFalse(knives.aimsALine());
            assertTrue(AbilityJson.decode("frost_orb").delivery().aimsALine());
            assertTrue(AbilityJson.decode("pulse_zap").delivery().aimsALine());
        }

        // traveling-form-transforms-in-flight
        @Test
        void dartMorphsToADartAndAPlainArcStaysABlob() {
            assertEquals(TravelForm.DART, AbilityJson.decode("metal_dart").delivery().form());
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
         * A lob flight flies the lob peak for its start and top face, whether
         * the hand sits below the face or above it
         * (decision lob-apex-at-top-face-height).
         */
        @Test
        void grannyArcFliesTheLobPeak() {
            Vec3 faceAboveHand = START.add(0, 2, 8);
            Vec3 faceBelowHand = START.add(0, -2, 8);
            assertEquals(ThrowArc.lobPeak(START, faceAboveHand),
                    Delivery.ARC.peak(START, faceAboveHand, true), TOLERANCE);
            assertEquals(ThrowArc.lobPeak(START, faceBelowHand),
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
         * The shipped dart shinks as its javelin forms and shinks lower as it
         * reverts: one sample, the revert pitched below the form
         * (decision dart-shinks-as-it-forms-and-pitched-down-as-it-reverts).
         */
        @Test
        void dartNamesAFormShinkAndALowerRevertShinkOfOneSample() {
            FormSounds sounds = AbilityJson.decode("metal_dart").delivery().formSounds();

            assertTrue(sounds.form().isPresent(), "metal_dart.json names no form sound");
            assertTrue(sounds.revert().isPresent(), "metal_dart.json names no revert sound");
            assertEquals(sounds.form().get().sound(), sounds.revert().get().sound());
            assertTrue(sounds.revert().get().pitch() < sounds.form().get().pitch(),
                    "the revert's pitch stands at or above the form's");
        }

        /**
         * The shipped dart flies 2.5 blocks a tick at every distance, Metal's
         * levity and base flight time passed in
         * (decision dart-flies-low-and-straight-at-double-speed).
         */
        @Test
        void dartFliesTwoAndAHalfBlocksATickAtEveryDistance() {
            Delivery dart = AbilityJson.decode("metal_dart").delivery();
            assertEquals(2, dart.travelTicks(4, LEVITY, BASE_FLIGHT_TIME));
            assertEquals(7, dart.travelTicks(SIXTEEN_BLOCKS, LEVITY, BASE_FLIGHT_TIME));
            assertEquals(13, dart.travelTicks(32, LEVITY, BASE_FLIGHT_TIME));
        }

        /**
         * The shipped dart flies a peak above zero and no higher than a
         * quarter of the base peak, the share metal_dart.json names, while a
         * bare arc keeps the base peak and its type's flight time
         * (decision dart-flies-low-and-straight-at-double-speed).
         */
        @Test
        void dartFliesALowPeakTheShareItsJsonNamesAndABareArcKeepsTheBasePeak() {
            JsonObject json = JsonParser.parseString(AbilityJson.read("metal_dart")).getAsJsonObject()
                    .getAsJsonObject("delivery");
            assertTrue(json.has(PEAK_SHARE), "metal_dart.json names no " + PEAK_SHARE);
            double basePeak = ThrowArc.basePeak(SIXTEEN_BLOCKS);
            double dartPeak = AbilityJson.decode("metal_dart").delivery().peak(START, SIXTEEN_AWAY, false);
            assertEquals(basePeak * json.get(PEAK_SHARE).getAsDouble(), dartPeak, TOLERANCE);
            assertTrue(dartPeak > 0, "the dart flies no peak");
            assertTrue(dartPeak <= basePeak / 4, "the dart's peak " + dartPeak + " tops a quarter of " + basePeak);

            Delivery bare = deliveryOf("\"delivery\": { \"kind\": \"arc\" },");
            assertEquals(basePeak, bare.peak(START, SIXTEEN_AWAY, false), TOLERANCE);
            assertEquals((int) ThrowArc.travelTicks(SIXTEEN_BLOCKS, LEVITY, BASE_FLIGHT_TIME),
                    bare.travelTicks(SIXTEEN_BLOCKS, LEVITY, BASE_FLIGHT_TIME));
        }

        /**
         * Bulb casts at the aimed place the release tick, so placing a bulb
         * feels like pointing, not waiting; Zap still beams
         * (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob).
         */
        @Test
        void bulbCastsSpontaneouslyZapBeamsAndLaserChannelsOnSelf() {
            assertEquals(DeliveryKind.SPONTANEOUS, AbilityJson.decode("glow_bulb").delivery().kind());
            assertEquals(DeliveryKind.BEAM, AbilityJson.decode("pulse_zap").delivery().kind());
            assertEquals(DeliveryKind.SELF, AbilityJson.decode("glow_laser").delivery().kind());
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
        void theWorldGooSpraysAloneThrowDripsOnTheirArcs() {
            Set<Identifier> throwing = AbilityJson.files().stream()
                    .filter(file -> AbilityJson.decode(file).delivery().throwsDrips())
                    .map(file -> AbilityJson.idOf(file.getFileName().toString()))
                    .collect(Collectors.toSet());

            assertEquals(Set.of(Identifier.fromNamespaceAndPath("goo", "weird_goop"),
                    Identifier.fromNamespaceAndPath("goo", "kinetic_slick"),
                    Identifier.fromNamespaceAndPath("goo", "blaze_molten"),
                    Identifier.fromNamespaceAndPath("goo", "crystal_shatter"),
                    Identifier.fromNamespaceAndPath("goo", "deep_host"),
                    Identifier.fromNamespaceAndPath("goo", "frost_rime"),
                    Identifier.fromNamespaceAndPath("goo", "glow_wisps"),
                    Identifier.fromNamespaceAndPath("goo", "hex_leech"),
                    Identifier.fromNamespaceAndPath("goo", "typhoon_updraft"),
                    Identifier.fromNamespaceAndPath("goo", "jelly_nectar"),
                    Identifier.fromNamespaceAndPath("goo", "pulse_signal"),
                    Identifier.fromNamespaceAndPath("goo", "metal_spikes"),
                    Identifier.fromNamespaceAndPath("goo", "leaf_vines")), throwing);
        }
    }

    /**
     * A spray's drips leave faster by the caster's horizontal speed, so a
     * running caster never outruns the goo they lay; a stream throwing no
     * drips keeps a throw speed of none
     * (decision goop-and-slick-drips-leave-at-one-exit-speed).
     */
    @Nested
    class ExitSpeed {

        private static final double RAMPED = 1.0;
        /** The drips a spray throws, and draws, each tick. */
        private static final int SIX_DRIPS = 6;
        /** The drips Slick throws, and draws, each tick. */
        private static final int SLICK_DRIPS = 2;

        /**
         * operator UAT 2026-11-17: every drawn drip lands, so each spray throws the six drips a tick it draws;
         * Slick keeps the two a tick its ramp and spread are tuned to, drawing two
         */
        @Test
        void eachSprayThrowsTheSixDripsATickItDraws() {
            assertEquals(SLICK_DRIPS, AbilityJson.decode("kinetic_slick").delivery().dripsPerTick());
            for (String spray : Set.of("weird_goop", "blaze_molten", "crystal_shatter",
                    "deep_host", "frost_rime", "glow_wisps", "hex_leech", "typhoon_updraft", "jelly_nectar",
                    "pulse_signal")) {
                assertEquals(SIX_DRIPS, AbilityJson.decode(spray).delivery().dripsPerTick(), spray);
            }
        }

        @Test
        void eachSprayLeavesFasterByTheCastersSpeed() {
            for (String spray : Set.of("weird_goop", "kinetic_slick", "blaze_molten", "crystal_shatter",
                    "deep_host", "frost_rime", "glow_wisps", "hex_leech", "typhoon_updraft", "jelly_nectar",
                    "pulse_signal", "metal_spikes", "leaf_vines")) {
                Delivery delivery = AbilityJson.decode(spray).delivery();

                assertEquals(delivery.throwSpeed() + RAMPED, delivery.exitSpeedAt(RAMPED), 1e-9, spray);
            }
        }

        @Test
        void aCasterAtRestThrowsAtTheThrowSpeed() {
            Delivery goop = AbilityJson.decode("weird_goop").delivery();

            assertEquals(goop.throwSpeed(), goop.exitSpeedAt(0), 1e-9);
        }

        @Test
        void aStreamThrowingNoDripsKeepsNoThrowSpeed() {
            assertEquals(Delivery.NO_THROW_SPEED, Delivery.of(DeliveryKind.STREAM).exitSpeedAt(RAMPED), 1e-9);
        }
    }
}
