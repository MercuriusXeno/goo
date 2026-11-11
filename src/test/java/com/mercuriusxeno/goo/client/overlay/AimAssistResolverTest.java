package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.GrabStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.Step;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void theConeTakesAMobWithinTheRaysReachAndNoneBeyondIt() {
        assertTrue(AimAssistResolver.inReach(12, 16));
        assertFalse(AimAssistResolver.inReach(20, 16));
    }

    @Nested
    class Candidates {

        private final List<Step> grab = List.of(new GrabStep(16, 2, 4, 1.5, 1));
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

        private static <E extends Entity> E pickable(Class<E> kind) {
            E entity = mock(kind);
            when(entity.isAlive()).thenReturn(true);
            when(entity.isPickable()).thenReturn(true);
            return entity;
        }
    }
}
