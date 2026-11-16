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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests where Grab hangs a held body, its reach with and without
 * Remote, and which left clicks throw through it
 * (decisions grab-holds-and-throws-a-physics-body, grab-reaches-telekinesis-range,
 * grab-holds-the-mob-against-the-surface-you-look-at, grab-holds-slightly-closer-in-open-space).
 */
class GrabStepTest {

    private static final double HOLD_DISTANCE = 4;
    private static final double EPSILON = 1e-6;
    /** An acceleration unequal to the shipped one, so the test reads the step's field. */
    private static final double PULL_ACCELERATION = 0.25;
    private static final double PULL_SPEED = 1;
    /** A hold acceleration and reference volume unequal to the shipped ones, so the tests read the step's fields. */
    private static final double HOLD_ACCELERATION = 0.3;
    private static final double REFERENCE_VOLUME = 1.4;
    private static final GrabStep GRAB = new GrabStep(8, HOLD_DISTANCE, 1.5, PULL_SPEED, 10, PULL_ACCELERATION, 4, 3,
            HOLD_ACCELERATION, REFERENCE_VOLUME);

    /**
     * A locked item gains speed tick over tick toward where the eye stands
     * that tick, up to the pull speed
     * (decision grabbed-item-locks-in-and-homes-on-your-current-position).
     */
    /**
     * Which neighbors join a targeted item's group: the same item within the
     * step's radius of it, whatever components the stacks carry, and none
     * while the player sneaks
     * (decisions grab-on-one-item-grabs-every-same-item-within-four-blocks,
     * sneaking-grab-pulls-the-single-targeted-item).
     */
    @Nested
    class GroupMembership {

        /** A radius unequal to the shipped 4, so the test reads the step's field. */
        private static final double RADIUS = 2.5;
        private static final double JUST_INSIDE = RADIUS - 0.01;
        private static final double JUST_OUTSIDE = RADIUS + 0.01;
        private final GrabStep step = new GrabStep(8, HOLD_DISTANCE, 1.5, PULL_SPEED, 10, PULL_ACCELERATION, RADIUS, 3,
                HOLD_ACCELERATION, REFERENCE_VOLUME);
        private final Item cobblestone = mock(Item.class);
        private final Item dirt = mock(Item.class);
        private final ItemStack targeted = stackOf(cobblestone);

        @Test
        void theSameItemJustInsideTheRadiusJoins() {
            assertTrue(GrabStep.joinsGroup(targeted, stackOf(cobblestone), JUST_INSIDE, step.groupRadius(), false));
        }

        @Test
        void theSameItemJustOutsideTheRadiusStaysOut() {
            assertFalse(GrabStep.joinsGroup(targeted, stackOf(cobblestone), JUST_OUTSIDE, step.groupRadius(), false));
        }

        @Test
        void anotherItemInsideTheRadiusStaysOut() {
            assertFalse(GrabStep.joinsGroup(targeted, stackOf(dirt), JUST_INSIDE, step.groupRadius(), false));
        }

        @Test
        void theSameItemCarryingOtherComponentsJoins() {
            ItemStack renamed = stackOf(cobblestone);
            when(renamed.getComponentsPatch()).thenReturn(mock(net.minecraft.core.component.DataComponentPatch.class));
            assertTrue(GrabStep.joinsGroup(targeted, renamed, JUST_INSIDE, step.groupRadius(), false));
        }

        @Test
        void sneakingAnswersFalseForEveryNeighbor() {
            for (ItemStack neighbor : List.of(stackOf(cobblestone), stackOf(dirt))) {
                for (double distance : new double[] {0, JUST_INSIDE, JUST_OUTSIDE}) {
                    assertFalse(GrabStep.joinsGroup(targeted, neighbor, distance, step.groupRadius(), true));
                }
            }
        }

        private static ItemStack stackOf(Item item) {
            ItemStack stack = mock(ItemStack.class);
            when(stack.getItem()).thenReturn(item);
            return stack;
        }
    }

    /**
     * Where and when a locked group meets: the member closest to the player
     * flies at the pull speed to its nearest point on the aim line, and every
     * member flies its own distance to that point over one meet time, no
     * faster than the cap
     * (decisions grabbed-item-group-converges-on-the-aim-line-then-pulls-as-one,
     * group-meet-caps-the-fastest-item-at-a-multiple-of-pull-speed).
     */
    @Nested
    class GroupMeeting {

        private static final Vec3 EYE = Vec3.ZERO;
        private static final Vec3 LOOK = new Vec3(1, 0, 0);
        /** A cap loose enough that the closest member's flight sets the meet time. */
        private static final double LOOSE_CAP = 5;
        /** A cap unequal to the shipped 3, tight enough that the farthest member's flight sets the meet time. */
        private static final double TIGHT_CAP = 2;
        private static final Vec3 FARTHER = new Vec3(5, 2, 0);
        private static final Vec3 FARTHEST = new Vec3(6, -2, 1);

        @Test
        void theClosestMemberFliesAtThePullSpeedAndAllLandOnOneTick() {
            Vec3 closest = new Vec3(3, 1, 0);
            List<Vec3> members = List.of(FARTHER, closest, FARTHEST);
            GrabStep.GroupMeet meet = GrabStep.meetOf(EYE, LOOK, members, PULL_SPEED, LOOSE_CAP);
            assertEquals(0, meet.point().distanceTo(new Vec3(3, 0, 0)), EPSILON, "The meet point should be the "
                    + "closest member's nearest point on the aim line, is " + meet.point());
            assertEquals(PULL_SPEED, meet.speeds().get(1), EPSILON);
            assertTrue(meet.speeds().get(0) > PULL_SPEED && meet.speeds().get(2) > PULL_SPEED,
                    "Each farther member should fly faster, flies " + meet.speeds());
            for (int member = 0; member < members.size(); member++) {
                assertEquals(meet.time(), members.get(member).distanceTo(meet.point()) / meet.speeds().get(member),
                        EPSILON, "Member " + member + " should land at the one meet time");
            }
        }

        @Test
        void theCapSetsTheMeetTimeWhereTheClosestMemberWaitsOnTheLine() {
            GrabStep capped = new GrabStep(8, HOLD_DISTANCE, 1.5, PULL_SPEED, 10, PULL_ACCELERATION, 4, TIGHT_CAP,
                    HOLD_ACCELERATION, REFERENCE_VOLUME);
            Vec3 onTheLine = new Vec3(3, 0, 0);
            List<Vec3> members = List.of(onTheLine, FARTHER, FARTHEST);
            GrabStep.GroupMeet meet = GrabStep.meetOf(EYE, LOOK, members, PULL_SPEED, capped.meetCapMultiple());
            double cap = PULL_SPEED * TIGHT_CAP;
            assertEquals(cap, meet.speeds().get(2), EPSILON, "The farthest member should fly at the cap");
            assertEquals(FARTHEST.distanceTo(meet.point()) / cap, meet.time(), EPSILON);
            assertTrue(meet.speeds().stream().allMatch(speed -> speed <= cap + EPSILON), "No member should fly past "
                    + "the cap, flies " + meet.speeds());
            assertEquals(0, meet.speeds().get(0), EPSILON, "The closest member should wait on the line");
        }
    }

    /**
     * A held mob gains speed toward the hold point by an acceleration its
     * hitbox volume divides, so a bigger mob comes slower
     * (decision grabbed-mob-accelerates-slower-the-bigger-its-hitbox).
     */
    @Nested
    class HeldMobMass {

        private static final Vec3 FROM = Vec3.ZERO;
        private static final Vec3 HOLD_POINT = new Vec3(10, 0, 0);
        /** A chicken's box, 0.4 by 0.4 by 0.7. */
        private static final double CHICKEN = 0.4 * 0.4 * 0.7;
        /** A ravager's box, 1.95 by 1.95 by 2.2. */
        private static final double RAVAGER = 1.95 * 1.95 * 2.2;
        private static final int TICKS = 5;

        @Test
        void aChickenSizedBoxTravelsFartherThanARavagerSizedOne() {
            double chicken = travelled(CHICKEN);
            double ravager = travelled(RAVAGER);
            assertTrue(chicken > ravager, "The chicken-sized box should travel farther, " + chicken + " against "
                    + ravager);
        }

        @Test
        void doublingTheVolumeHalvesTheFirstTicksGain() {
            double once = GRAB.heldVelocity(FROM, Vec3.ZERO, HOLD_POINT, REFERENCE_VOLUME).length();
            double twice = GRAB.heldVelocity(FROM, Vec3.ZERO, HOLD_POINT, 2 * REFERENCE_VOLUME).length();
            assertEquals(once / 2, twice, EPSILON);
        }

        private static double travelled(double volume) {
            Vec3 at = FROM;
            Vec3 velocity = Vec3.ZERO;
            for (int tick = 0; tick < TICKS; tick++) {
                velocity = GRAB.heldVelocity(at, velocity, HOLD_POINT, volume);
                at = at.add(velocity);
            }
            return at.distanceTo(FROM);
        }
    }

    @Nested
    class Homing {

        private static final Vec3 EYE = new Vec3(0, 1.62, 0);
        private static final Vec3 ITEM_AT = new Vec3(12, 0.2, 0);
        /** Where the eye stands once the player moves, off the item's first line to it. */
        private static final Vec3 MOVED_EYE = new Vec3(4, 1.62, 6);
        private static final int TICKS = 30;

        @Test
        void theFirstTickFromRestGainsTheAcceleration() {
            Vec3 velocity = GRAB.homingVelocity(Vec3.ZERO, ITEM_AT, EYE);
            assertEquals(PULL_ACCELERATION, velocity.length(), EPSILON);
        }

        @Test
        void aLaterTickFliesFasterThanTheFirst() {
            Vec3 first = GRAB.homingVelocity(Vec3.ZERO, ITEM_AT, EYE);
            Vec3 second = GRAB.homingVelocity(first, ITEM_AT.add(first), EYE);
            assertTrue(second.length() > first.length(), "The second tick should fly faster, " + first.length()
                    + " then " + second.length());
        }

        @Test
        void theMotionAfterTheEyeMovesPointsAtTheEyesNewPosition() {
            Vec3 at = ITEM_AT;
            Vec3 velocity = Vec3.ZERO;
            for (int tick = 0; tick < 3; tick++) {
                velocity = GRAB.homingVelocity(velocity, at, EYE);
                at = at.add(velocity);
            }
            Vec3 turned = GRAB.homingVelocity(velocity, at, MOVED_EYE);
            Vec3 toMovedEye = MOVED_EYE.subtract(at).normalize();
            assertEquals(1, turned.normalize().dot(toMovedEye), EPSILON, "The motion should point at the eye's new "
                    + "position, moves " + turned + " from " + at);
        }

        @Test
        void noTickFliesFasterThanThePullSpeed() {
            Vec3 at = ITEM_AT;
            Vec3 velocity = Vec3.ZERO;
            for (int tick = 0; tick < TICKS; tick++) {
                velocity = GRAB.homingVelocity(velocity, at, EYE);
                assertTrue(velocity.length() <= PULL_SPEED + EPSILON, "Tick " + tick + " flies " + velocity.length());
                at = at.add(velocity);
            }
        }
    }

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

        /** A large slime's box, the widest body Grab commonly holds. */
        private static final double LARGE_SLIME = 2.08;
        /** A player's box, its eye this far above its feet. */
        private static final double HOLDER_WIDTH = 0.6;
        private static final double HOLDER_HEIGHT = 1.8;

        @Test
        void theShippedGrabHoldsAtTheHoldDistanceItNames() {
            JsonObject behavior = Reach.firstBehavior("kinetic_grab.json");
            assertTrue(behavior.has("hold_distance"), "kinetic_grab.json should carry one hold_distance, has "
                    + behavior.keySet());
            double holdDistance = behavior.get("hold_distance").getAsDouble();
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

        /** Remote reaches unequal to GRAB's range of 8, so doubling and adding answer apart. */
        private static final double SHORT_REMOTE_REACH = 3;
        private static final double LONG_REMOTE_REACH = 5;

        @Test
        void withoutRemoteReachesItsOwnRange() {
            assertEquals(8, GRAB.reachWith(null), EPSILON);
        }

        @Test
        void remoteDoublesTheRangeWhateverReachItLays() {
            assertEquals(16, GRAB.reachWith(laid(SHORT_REMOTE_REACH)), EPSILON);
            assertEquals(16, GRAB.reachWith(laid(LONG_REMOTE_REACH)), EPSILON);
        }

        @Test
        void shippedRemoteDoublesTheShippedGrabsReach() {
            GrabStep shipped = GrabStep.CODEC.codec().parse(JsonOps.INSTANCE,
                    firstBehavior("kinetic_grab.json")).getOrThrow();
            RemoteStep remote = RemoteStep.CODEC.codec().parse(JsonOps.INSTANCE,
                    firstBehavior("kinetic_remote.json")).getOrThrow();
            double alone = shipped.reachWith(null);
            assertEquals(2 * alone, shipped.reachWith(laid(remote.reach())), EPSILON);
        }

        @Test
        void aGrabAbilityAimsAsFarAsGrabReaches() {
            assertEquals(16, GrabStep.reachOf(List.of(new AncientStep(), GRAB), laid(SHORT_REMOTE_REACH))
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
        }

        @Test
        void remoteTakesASentMobOutToTwiceTheRange() {
            Mob sent = mock(Mob.class);
            when(sent.isAlive()).thenReturn(true);
            assertTrue(GRAB.takesSent(sent, 15 * 15, laid(SHORT_REMOTE_REACH)));
            assertFalse(GRAB.takesSent(sent, 17 * 17, laid(SHORT_REMOTE_REACH)));
            assertFalse(GRAB.takesSent(sent, 15 * 15, null));
        }

        @Test
        void aSentPlayerWithinReachIsRefused() {
            Player sent = mock(Player.class);
            when(sent.isAlive()).thenReturn(true);
            assertFalse(GRAB.takesSent(sent, 1, null));
        }

        private static AttributeModifier laid(double reach) {
            return new AttributeModifier(Identifier.fromNamespaceAndPath("goo", "remote_entity_reach"),
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
            Step heal = LeafSteps.HEAL.step(new HealPulse(Expr.literal(1), 4));
            assertTrue(heal.takesEntity(alive(Player.class)));
            assertFalse(heal.takesEntity(alive(ItemEntity.class)));
        }

        @Test
        void anAbilityTakesWhatAnyOfItsStepsTakes() {
            Step heal = LeafSteps.HEAL.step(new HealPulse(Expr.literal(1), 4));
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

    /**
     * A body the eye does not see is reached the shorter way: straight, or
     * round through a linked pair where that runs shorter, so a pair standing
     * far off never stretches the reach past the straight line
     * (decision aim-and-shots-carry-through-linked-gates).
     */
    @Nested
    class ReachedLength {

        /** The pig on the gametest's ledge: 4.4 blocks from the eye, its middle behind the ledge's edge. */
        private static final double BEHIND_THE_LEDGE = 4.4;
        /** The way round through another test's pair forty blocks off, as the full suite measured it. */
        private static final double ROUND_A_FAR_PAIR = 85.6;
        /** A way round through a pair beside the player, shorter than the walled straight line. */
        private static final double THROUGH_A_NEAR_PAIR = 3.0;
        private static final double EXACT = 1.0E-9;

        @Test
        void aFarPairNeverStretchesTheReachOfAnUnseenBody() {
            assertEquals(BEHIND_THE_LEDGE,
                    GrabStep.reachedLength(BEHIND_THE_LEDGE, false, Optional.of(ROUND_A_FAR_PAIR)), EXACT);
        }

        @Test
        void aNearPairShortensTheReachOfAnUnseenBody() {
            assertEquals(THROUGH_A_NEAR_PAIR,
                    GrabStep.reachedLength(BEHIND_THE_LEDGE, false, Optional.of(THROUGH_A_NEAR_PAIR)), EXACT);
        }
    }
}
