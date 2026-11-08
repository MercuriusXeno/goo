package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
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
 * Tests Grab's hold distance by pitch, its reach with and without
 * Telekinesis, and which left clicks throw through it
 * (decisions grab-holds-and-throws-a-physics-body, grab-reaches-telekinesis-range).
 */
class GrabStepTest {

    private static final double NEAR = 2;
    private static final double FAR = 4;
    private static final double EPSILON = 1e-6;
    private static final GrabStep GRAB = new GrabStep(8, NEAR, FAR, 1.5, 1);

    @Nested
    class HoldDistance {

        @Test
        void levelOrUpwardLookHoldsAtFar() {
            assertEquals(FAR, GrabStep.holdDistance(0f, NEAR, FAR), EPSILON);
            assertEquals(FAR, GrabStep.holdDistance(-45f, NEAR, FAR), EPSILON);
        }

        @Test
        void downwardLookDrawsInLinearlyToNear() {
            assertEquals(3, GrabStep.holdDistance(GrabStep.FULL_DRAW_PITCH / 2, NEAR, FAR), EPSILON);
            assertEquals(NEAR, GrabStep.holdDistance(GrabStep.FULL_DRAW_PITCH, NEAR, FAR), EPSILON);
            assertEquals(NEAR, GrabStep.holdDistance(90f, NEAR, FAR), EPSILON);
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
