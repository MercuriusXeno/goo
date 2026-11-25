package com.mercuriusxeno.goo.client.overlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.OwnMinions;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.GrabStep;
import com.mercuriusxeno.goo.ability.program.HealPulse;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.yarn.Led;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
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
import java.util.UUID;
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
 * what the selected ability's steps take (decision steps-declare-the-entities-they-take), less
 * a mob the selected Lead or Charm refuses (decision refused-rally-or-charm-spends-no-goo).
 */
class AimAssistResolverTest {

    @Test
    void theConeTakesAMobWithinTheRaysReachAndNoneBeyondIt() {
        assertTrue(AimAssistResolver.inReach(12, 16));
        assertFalse(AimAssistResolver.inReach(20, 16));
    }

    /**
     * The cone's search reaches as wide as the aim ray it runs along, so
     * Zap's 32 block ray searches a 32 block cone.
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     */
    @Test
    void theSearchConeWidensToTheAimRaysLength() {
        double degrees = AimAssistResolver.AIM_ASSIST_DEGREES;

        assertEquals(32 * Math.tan(Math.toRadians(degrees)),
                AimAssistResolver.searchConeRadius(Vec3.ZERO, new Vec3(0, 0, 32), degrees), 1e-9);
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
        private final List<Step> heal = List.of(LeafSteps.HEAL.step(new HealPulse(Expr.literal(1), 4)));

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

        /**
         * With the camera four blocks behind the eye, the cone takes a mob by
         * its distance from the eye, the measure the server's range gate reads.
         * decision diagnose-and-correct-the-indicator-disagreeing-with-the-cast
         */
        @Test
        void theConeTakesAMobWithinRangeOfTheEyeAndNonePastIt() {
            Vec3 eye = new Vec3(4, 0, 0);
            AimTargets.AimRay ray = AimTargets.AimRay.fromCamera(Vec3.ZERO, new Vec3(1, 0, 0), eye, RANGE);
            double coneCos = Math.cos(Math.toRadians(AimAssistResolver.pickConeDegrees(heal)));
            AimAssistResolver.AimHit within = mobCenteredAt(eye.add(RANGE - 0.5, 0, 0));
            AimAssistResolver.AimHit past = mobCenteredAt(eye.add(RANGE + 0.5, 0, 0));

            assertEquals(within, AimAssistResolver.findBestConeHit(List.of(within), ray, coneCos, candidate -> true));
            assertNull(AimAssistResolver.findBestConeHit(List.of(past), ray, coneCos, candidate -> true));
        }

        private static AimAssistResolver.AimHit mobCenteredAt(Vec3 middle) {
            Mob mob = mock(Mob.class);
            when(mob.getBoundingBox()).thenReturn(new AABB(middle, middle).inflate(0.3));
            return new AimAssistResolver.AimHit.EntityHit(mob);
        }

        private static AimAssistResolver.@Nullable AimHit bestUnder(List<Step> behaviors,
                List<AimAssistResolver.AimHit> candidates) {
            double coneCos = Math.cos(Math.toRadians(AimAssistResolver.pickConeDegrees(behaviors)));
            return AimAssistResolver.findBestConeHit(candidates,
                    AimTargets.AimRay.along(Vec3.ZERO, new Vec3(1, 0, 0), RANGE), coneCos, candidate -> true);
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

        private final List<Step> grab = List.of(new GrabStep(16, 3, 1.5, 1, 10, 0.15, 4, 3, 0.4, 0.7));
        private final List<Step> heal = List.of(LeafSteps.HEAL.step(new HealPulse(Expr.literal(1), 4)));

        @Test
        void withGrabSelectedAnItemEntityIsACandidate() {
            assertTrue(AimAssistResolver.isCandidate(pickable(ItemEntity.class), grab));
        }

        @Test
        void withGrabSelectedAPlayerABossAndAPassengerAreNoCandidates() {
            Mob boss = pickable(Mob.class);
            when(boss.getType()).thenAnswer(unused -> EntityTypes.WITHER);
            Mob rider = pickable(Mob.class);
            when(rider.isPassenger()).thenReturn(true);
            assertFalse(AimAssistResolver.isCandidate(pickable(Player.class), grab));
            assertFalse(AimAssistResolver.isCandidate(boss, grab));
            assertFalse(AimAssistResolver.isCandidate(rider, grab));
        }

        /**
         * Shulk's release spews a shulker bullet and runs no program on the
         * mob, yet its press takes the mob the aim highlights
         * (decision charm-like-abilities-lock-the-highlighted-mob-on-right-click).
         */
        @Test
        void withShulkSelectedALivingMobIsACandidate() {
            assertTrue(AimAssistResolver.isCandidate(pickable(Mob.class),
                    AbilityJson.decode("typhoon_shulk").behaviors()));
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
            Mob minion = led(pickableCow(), PLAYER);
            assertFalse(AimAssistResolver.isCandidate(minion, heal, player(),
                    OwnMinions.sparesOwnMinions(AbilityBadge.MOB, DeliveryKind.ARC, true)));
        }

        /** decision mob-targeted-attacks-refuse-your-minions */
        @Test
        void anUntaggedMobAbilityOutlinesAnOwnMinion() {
            Mob minion = led(pickableCow(), PLAYER);
            assertTrue(AimAssistResolver.isCandidate(minion, heal, player(),
                    OwnMinions.sparesOwnMinions(AbilityBadge.MOB, DeliveryKind.ARC, false)));
        }

        private static <E extends Entity> E pickable(Class<E> kind) {
            E entity = mock(kind);
            when(entity.isAlive()).thenReturn(true);
            when(entity.isPickable()).thenReturn(true);
            return entity;
        }
    }

    /**
     * The aim drops a mob the selected Lead or Charm refuses for the player,
     * so it is neither outlined nor locked, and keeps the casts that still
     * land (decisions refused-rally-or-charm-spends-no-goo,
     * rally-cast-on-your-rallied-mob-releases-it).
     */
    @Nested
    class RefusedLeadAndCharm {

        private final List<Step> charm = AbilityJson.decode("hex_charm").behaviors();
        private final List<Step> lead = AbilityJson.decode("yarn_lead").behaviors();

        @Test
        void charmOverARalliedMobIsNoCandidate() {
            assertFalse(candidate(led(pickableCow(), PLAYER), charm));
        }

        @Test
        void leadOverACharmedMobIsNoCandidate() {
            assertFalse(candidate(charmed(pickableCow(), PLAYER), lead));
        }

        @Test
        void leadOverAnotherPlayersLedMobIsNoCandidate() {
            assertFalse(candidate(led(pickableCow(), OTHER_PLAYER), lead));
        }

        @Test
        void leadOverAnotherPlayersCharmedMobIsNoCandidate() {
            assertFalse(candidate(charmed(pickableCow(), OTHER_PLAYER), lead));
        }

        @Test
        void charmOverAnotherPlayersLedMobIsNoCandidate() {
            assertFalse(candidate(led(pickableCow(), OTHER_PLAYER), charm));
        }

        @Test
        void charmOverAnotherPlayersCharmedMobIsNoCandidate() {
            assertFalse(candidate(charmed(pickableCow(), OTHER_PLAYER), charm));
        }

        @Test
        void leadOverAMobHoldingNeitherEffectIsACandidate() {
            assertTrue(candidate(pickableCow(), lead));
        }

        @Test
        void charmOverAMobHoldingNeitherEffectIsACandidate() {
            assertTrue(candidate(pickableCow(), charm));
        }

        @Test
        void leadOverThePlayersOwnLedMobIsACandidate() {
            assertTrue(candidate(led(pickableCow(), PLAYER), lead));
        }

        private static boolean candidate(Mob mob, List<Step> behaviors) {
            return AimAssistResolver.isCandidate(mob, behaviors, player(), false);
        }
    }

    private static final UUID PLAYER = new UUID(1L, 1L);
    private static final UUID OTHER_PLAYER = new UUID(2L, 2L);
    private static final float STRIKE = 6f;

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUUID()).thenReturn(PLAYER);
        return player;
    }

    private static Cow pickableCow() {
        Cow cow = mock(Cow.class);
        when(cow.isAlive()).thenReturn(true);
        when(cow.isPickable()).thenReturn(true);
        when(cow.getType()).thenAnswer(unused -> EntityTypes.COW);
        return cow;
    }

    private static Cow led(Cow cow, UUID leader) {
        when(cow.hasData(GooAttachments.LED)).thenReturn(true);
        when(cow.getData(GooAttachments.LED)).thenReturn(new Led(leader, STRIKE));
        return cow;
    }

    private static Cow charmed(Cow cow, UUID charmer) {
        when(cow.hasData(GooAttachments.CHARMED)).thenReturn(true);
        when(cow.getData(GooAttachments.CHARMED)).thenReturn(new Charmed(charmer));
        return cow;
    }
}
