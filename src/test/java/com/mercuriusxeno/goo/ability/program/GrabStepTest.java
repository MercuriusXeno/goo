package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests where Grab hangs a held body, its reach with and without
 * Telekinesis, and which left clicks throw through it
 * (decisions grab-holds-and-throws-a-physics-body, grab-reaches-telekinesis-range,
 * grab-holds-the-mob-against-the-surface-you-look-at, grab-holds-slightly-closer-in-open-space).
 */
class GrabStepTest {

    private static final double HOLD_DISTANCE = 4;
    private static final double EPSILON = 1e-6;
    private static final GrabStep GRAB = new GrabStep(8, HOLD_DISTANCE, 1.5, 1, 10);

    /**
     * Where Grab hangs a held body's middle: against the first block the look
     * meets short of the open-air distance, else at that distance
     * (decision grab-holds-the-mob-against-the-surface-you-look-at).
     */
    @Nested
    class HoldPoint {

        private static final ResourceKey<Level> OVERWORLD =
                ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
        private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
        /** A zombie's box. */
        private static final double WIDTH = 0.6;
        private static final double HEIGHT = 1.95;
        private static final GrabStep.LookClip OPEN_AIR = (dimension, from, to) ->
                BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));

        @Test
        void aWallAheadHoldsTheBodyFlushAgainstIt() {
            double wallX = 2.5;
            Vec3 middle = GRAB.restingMiddle(List.of(), OVERWORLD, EYE, new Vec3(1, 0, 0),
                    plane(Direction.WEST, wallX), WIDTH, HEIGHT);
            AABB box = boxAround(middle);
            assertEquals(wallX, box.maxX, EPSILON, "The body's box should touch the wall, its middle at " + middle);
            assertEquals(EYE.y, middle.y, EPSILON, "The body should hang on the level look");
        }

        @Test
        void aLookAtTheFloorSetsTheBodyOnTheFloor() {
            Vec3 down = Vec3.directionFromRotation(45f, -90f);
            Vec3 middle = GRAB.restingMiddle(List.of(), OVERWORLD, EYE, down, plane(Direction.UP, 0), WIDTH, HEIGHT);
            AABB box = boxAround(middle);
            assertEquals(0, box.minY, EPSILON, "The body's box should rest on the floor, its middle at " + middle);
            assertTrue(middle.x > EYE.x, "The body should rest ahead of the holder along the look, at " + middle);
        }

        @Test
        void aClearLookHoldsAtTheOpenAirDistanceAtAnyPitch() {
            for (float pitch : new float[] {0f, -45f, 45f, 80f}) {
                Vec3 look = Vec3.directionFromRotation(pitch, -90f);
                Vec3 middle = GRAB.restingMiddle(List.of(), OVERWORLD, EYE, look, OPEN_AIR, WIDTH, HEIGHT);
                assertEquals(HOLD_DISTANCE, middle.distanceTo(EYE), EPSILON, "A clear look at pitch " + pitch
                        + " should hold at the open-air distance");
            }
        }

        /** The distance decision grab-holds-slightly-closer-in-open-space holds a body closer than. */
        private static final double FOUR_BLOCKS = 4;
        /** A large slime's box, the widest body Grab commonly holds. */
        private static final double LARGE_SLIME = 2.08;
        /** A player's box, its eye this far above its feet. */
        private static final double HOLDER_WIDTH = 0.6;
        private static final double HOLDER_HEIGHT = 1.8;

        @Test
        void theShippedGrabHoldsSlightlyCloserThanFourBlocks() {
            JsonObject behavior = Reach.firstBehavior("kinetic_grab.json");
            assertTrue(behavior.has("hold_distance"), "kinetic_grab.json should carry one hold_distance, has "
                    + behavior.keySet());
            double holdDistance = behavior.get("hold_distance").getAsDouble();
            assertTrue(holdDistance < FOUR_BLOCKS, "The open-air hold should sit under 4 blocks, sits " + holdDistance);
            GrabStep shipped = GrabStep.CODEC.codec().parse(JsonOps.INSTANCE, behavior).getOrThrow();
            Vec3 middle = shipped.restingMiddle(List.of(), OVERWORLD, EYE, new Vec3(1, 0, 0), OPEN_AIR, WIDTH, HEIGHT);
            assertEquals(holdDistance, middle.distanceTo(EYE), EPSILON);
        }

        @Test
        void aLargeSlimeHeldAtTheShippedDistanceClearsItsHolder() {
            GrabStep shipped = GrabStep.CODEC.codec().parse(JsonOps.INSTANCE,
                    Reach.firstBehavior("kinetic_grab.json")).getOrThrow();
            AABB holder = new AABB(EYE.x - HOLDER_WIDTH / 2, EYE.y - 1.62, EYE.z - HOLDER_WIDTH / 2,
                    EYE.x + HOLDER_WIDTH / 2, EYE.y - 1.62 + HOLDER_HEIGHT, EYE.z + HOLDER_WIDTH / 2);
            for (float pitch : new float[] {-90f, -45f, 0f, 45f, 90f}) {
                Vec3 middle = shipped.restingMiddle(List.of(), OVERWORLD, EYE, Vec3.directionFromRotation(pitch, -90f),
                        OPEN_AIR, LARGE_SLIME, LARGE_SLIME);
                AABB slime = new AABB(middle, middle).inflate(LARGE_SLIME / 2);
                assertFalse(slime.intersects(holder), "A large slime held at pitch " + pitch + " should clear its "
                        + "holder, held at " + middle);
            }
        }

        private static AABB boxAround(Vec3 middle) {
            return new AABB(middle.x - WIDTH / 2, middle.y - HEIGHT / 2, middle.z - WIDTH / 2,
                    middle.x + WIDTH / 2, middle.y + HEIGHT / 2, middle.z + WIDTH / 2);
        }

        /**
         * A clip meeting one solid plane, the face of the blocks behind it
         * looking the way {@code face} points.
         */
        private static GrabStep.LookClip plane(Direction face, double at) {
            Direction.Axis axis = face.getAxis();
            return (dimension, from, to) -> {
                double start = from.get(axis);
                double end = to.get(axis);
                boolean crosses = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? start >= at && end < at : start <= at && end > at;
                if (!crosses) {
                    return BlockHitResult.miss(to, face, BlockPos.containing(to));
                }
                Vec3 hit = from.lerp(to, (at - start) / (end - start));
                return new BlockHitResult(hit, face, BlockPos.containing(hit.subtract(face.getUnitVec3().scale(0.5))),
                        false);
            };
        }
    }

    @Nested
    class Reach {

        private static final double TELEKINESIS_REACH = 8;

        @Test
        void withoutTelekinesisReachesItsOwnRange() {
            assertEquals(8, GRAB.reachWith(null), EPSILON);
        }

        @Test
        void telekinesisAddsItsReachToTheRange() {
            assertEquals(16, GRAB.reachWith(laid(TELEKINESIS_REACH)), EPSILON);
        }

        @Test
        void shippedGrabReachesSixteenAloneAndTwentyFourWithShippedTelekinesis() {
            GrabStep shipped = GrabStep.CODEC.codec().parse(JsonOps.INSTANCE,
                    firstBehavior("kinetic_grab.json")).getOrThrow();
            TelekinesisStep telekinesis = TelekinesisStep.CODEC.codec().parse(JsonOps.INSTANCE,
                    firstBehavior("kinetic_telekinesis.json")).getOrThrow();
            assertEquals(16, shipped.reachWith(null), EPSILON);
            assertEquals(24, shipped.reachWith(laid(telekinesis.reach())), EPSILON);
        }

        @Test
        void aGrabAbilityAimsAsFarAsGrabReaches() {
            assertEquals(16, GrabStep.reachOf(List.of(new AncientStep(), GRAB), laid(TELEKINESIS_REACH))
                    .orElseThrow(), EPSILON);
        }

        @Test
        void anAbilityWithoutGrabNamesNoReach() {
            assertTrue(GrabStep.reachOf(List.of(new AncientStep()), null).isEmpty());
        }

        @Test
        void aSentMobWithinReachIsTakenAndOnePastItIsRefused() {
            Mob sent = mock(Mob.class);
            when(sent.isAlive()).thenReturn(true);
            assertTrue(GRAB.takesSent(sent, 7 * 7, null));
            assertFalse(GRAB.takesSent(sent, 9 * 9, null));
            assertTrue(GRAB.takesSent(sent, 15 * 15, laid(TELEKINESIS_REACH)));
        }

        @Test
        void aSentPlayerWithinReachIsRefused() {
            Player sent = mock(Player.class);
            when(sent.isAlive()).thenReturn(true);
            assertFalse(GRAB.takesSent(sent, 1, null));
        }

        private static AttributeModifier laid(double reach) {
            return new AttributeModifier(Identifier.fromNamespaceAndPath("goo", "telekinesis_entity_reach"),
                    reach, AttributeModifier.Operation.ADD_VALUE);
        }

        private static JsonObject firstBehavior(String file) {
            String path = "/data/goo/goo_abilities/" + file;
            try (InputStream in = GrabStepTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "classpath should hold " + path);
                JsonObject ability = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                return ability.getAsJsonArray("behaviors").get(0).getAsJsonObject();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Nested
    class EntityAnswer {

        private final List<Step> grabProgram = List.of(GRAB);

        @Test
        void grabTakesAnItemEntityAndAFreeLivingMob() {
            assertTrue(Step.takenByAny(grabProgram, alive(ItemEntity.class)));
            assertTrue(Step.takenByAny(grabProgram, alive(Mob.class)));
        }

        @Test
        void grabRefusesAPlayerABossAnArmorStandAndAPassenger() {
            Mob boss = alive(Mob.class);
            when(boss.getType()).thenAnswer(unused -> EntityType.WITHER);
            Mob rider = alive(Mob.class);
            when(rider.isPassenger()).thenReturn(true);
            assertFalse(Step.takenByAny(grabProgram, alive(Player.class)));
            assertFalse(Step.takenByAny(grabProgram, boss));
            assertFalse(Step.takenByAny(grabProgram, alive(ArmorStand.class)));
            assertFalse(Step.takenByAny(grabProgram, rider));
        }

        @Test
        void aStepWithNoRuleOfItsOwnTakesALivingEntity() {
            Step heal = LeafSteps.HEAL.step(Expr.literal(1));
            assertTrue(heal.takesEntity(alive(Player.class)));
            assertFalse(heal.takesEntity(alive(ItemEntity.class)));
        }

        @Test
        void anAbilityTakesWhatAnyOfItsStepsTakes() {
            Step heal = LeafSteps.HEAL.step(Expr.literal(1));
            assertTrue(Step.takenByAny(List.of(GRAB, heal), alive(Player.class)));
        }

        private static <E extends Entity> E alive(Class<E> kind) {
            E entity = mock(kind);
            when(entity.isAlive()).thenReturn(true);
            return entity;
        }
    }

    @Nested
    class ThrowsOnAttack {

        @Test
        void armedPressOnAGrabThrows() {
            assertTrue(GrabStep.throwsOnAttack(true, List.of(GRAB)));
        }

        @Test
        void unarmedPressOrAnotherAbilityPunches() {
            assertFalse(GrabStep.throwsOnAttack(false, List.of(GRAB)));
            assertFalse(GrabStep.throwsOnAttack(true, List.of(new AncientStep())));
        }
    }
}
