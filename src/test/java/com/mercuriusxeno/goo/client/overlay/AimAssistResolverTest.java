package com.mercuriusxeno.goo.client.overlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.OwnMinions;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.GrabStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.Step;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the aim assist answering entities alone: a standing ability block
 * is never an aim target, so a throw at one lands beside it (decision
 * splat-runs-the-program-no-fuse); its cone taking no mob beyond the ray's
 * reach (decision grab-reaches-telekinesis-range); and its candidates being
 * what the selected ability's steps take (decision steps-declare-the-entities-they-take).
 */
class AimAssistResolverTest {

    @Test
    void anAimHitIsAnEntityAlone() {
        assertEquals(List.of(AimAssistResolver.AimHit.EntityHit.class),
                List.of(AimAssistResolver.AimHit.class.getPermittedSubclasses()));
    }

    @Test
    void theConeTakesAMobWithinTheRaysReachAndNoneBeyondIt() {
        assertTrue(AimAssistResolver.inReach(12, 16));
        assertFalse(AimAssistResolver.inReach(20, 16));
    }

    /**
     * Grab picks with a cone of its own, wider than the shared one, the mob
     * nearest the look winning inside it
     * (decision grab-cone-picks-more-aggressively-than-other-goo-types).
     */
    @Nested
    class PickCone {

        private static final double RANGE = 16;
        private static final double AWAY = 10;
        /** Off the look by more than the shared cone, inside Grab's. */
        private static final double PAST_THE_SHARED_CONE = AimAssistResolver.AIM_ASSIST_DEGREES + 3;
        private final List<Step> heal = List.of(LeafSteps.HEAL.step(Expr.literal(1)));

        @Test
        void grabPicksAMobTheSharedConeMisses() {
            AimAssistResolver.AimHit offTheLook = mobOffTheLookBy(PAST_THE_SHARED_CONE);
            assertEquals(offTheLook, bestUnder(shippedGrab(), List.of(offTheLook)),
                    "Grab should pick a mob " + PAST_THE_SHARED_CONE + " degrees off the look");
            assertNull(bestUnder(heal, List.of(offTheLook)),
                    "An ability on the shared cone should pick no mob " + PAST_THE_SHARED_CONE + " degrees off");
        }

        @Test
        void theMobNearestTheLookWinsInsideGrabsCone() {
            AimAssistResolver.AimHit nearer = mobOffTheLookBy(PAST_THE_SHARED_CONE - 1);
            AimAssistResolver.AimHit farther = mobOffTheLookBy(PAST_THE_SHARED_CONE);
            assertEquals(nearer, bestUnder(shippedGrab(), List.of(farther, nearer)));
        }

        private static AimAssistResolver.@Nullable AimHit bestUnder(List<Step> behaviors,
                List<AimAssistResolver.AimHit> candidates) {
            double coneCos = Math.cos(Math.toRadians(AimAssistResolver.pickConeDegrees(behaviors)));
            return AimAssistResolver.findBestConeHit(candidates, Vec3.ZERO, new Vec3(1, 0, 0), RANGE, coneCos,
                    candidate -> true);
        }

        private static AimAssistResolver.AimHit mobOffTheLookBy(double degrees) {
            double radians = Math.toRadians(degrees);
            Vec3 middle = new Vec3(AWAY * Math.cos(radians), 0, AWAY * Math.sin(radians));
            Mob mob = mock(Mob.class);
            when(mob.getBoundingBox()).thenReturn(new AABB(middle, middle).inflate(0.3));
            return new AimAssistResolver.AimHit.EntityHit(mob);
        }

        private static List<Step> shippedGrab() {
            String path = "/data/goo/goo_abilities/kinetic_grab.json";
            try (InputStream in = AimAssistResolverTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "classpath should hold " + path);
                JsonObject behavior = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("behaviors").get(0).getAsJsonObject();
                return List.of(GrabStep.CODEC.codec().parse(JsonOps.INSTANCE, behavior).getOrThrow());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Nested
    class Candidates {

        private final List<Step> grab = List.of(new GrabStep(16, 3, 1.5, 1, 10));
        private final List<Step> heal = List.of(LeafSteps.HEAL.step(Expr.literal(1)));

        @Test
        void withGrabSelectedAnItemEntityIsACandidate() {
            assertTrue(AimAssistResolver.isCandidate(pickable(ItemEntity.class), grab));
        }

        @Test
        void withGrabSelectedAPlayerABossAndAPassengerAreNoCandidates() {
            Mob boss = pickable(Mob.class);
            when(boss.getType()).thenAnswer(unused -> EntityType.WITHER);
            Mob rider = pickable(Mob.class);
            when(rider.isPassenger()).thenReturn(true);
            assertFalse(AimAssistResolver.isCandidate(pickable(Player.class), grab));
            assertFalse(AimAssistResolver.isCandidate(boss, grab));
            assertFalse(AimAssistResolver.isCandidate(rider, grab));
        }

        @Test
        void withGenericStepsSelectedALivingMobIsACandidate() {
            assertTrue(AimAssistResolver.isCandidate(pickable(Mob.class), heal));
        }

        @Test
        void aSelfAbilityRunningOnTheCasterOutlinesNoMob() {
            assertFalse(AimAssistResolver.isCandidate(pickable(Mob.class), Step.reachingTheAim(heal, true)));
        }

        @Test
        void aSelfGrabStillOutlinesTheMobItLifts() {
            assertTrue(AimAssistResolver.isCandidate(pickable(Mob.class), Step.reachingTheAim(grab, true)));
        }

        /** decision mob-targeted-attacks-refuse-your-minions */
        @Test
        void aHarmfulMobAbilityOutlinesNoOwnMinion() {
            Mob minion = pickable(Mob.class);
            assertFalse(AimAssistResolver.isCandidate(minion, heal,
                    OwnMinions.sparesOwnMinions(AbilityBadge.MOB, true), ownMinion -> ownMinion == minion));
        }

        /** decision mob-targeted-attacks-refuse-your-minions */
        @Test
        void anUntaggedMobAbilityOutlinesAnOwnMinion() {
            Mob minion = pickable(Mob.class);
            assertTrue(AimAssistResolver.isCandidate(minion, heal,
                    OwnMinions.sparesOwnMinions(AbilityBadge.MOB, false), ownMinion -> ownMinion == minion));
        }

        private static <E extends Entity> E pickable(Class<E> kind) {
            E entity = mock(kind);
            when(entity.isAlive()).thenReturn(true);
            when(entity.isPickable()).thenReturn(true);
            return entity;
        }
    }
}
