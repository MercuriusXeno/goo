package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.TargetingHint;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The aim resolves once per frame, and every reader until the next frame,
 * the draw and the throw, reads that one answer (decisions render-context-is-the-one-emitter,
 * aim-target-follows-client-aim).
 */
class AimStateTest {

    private static final int READS = 3;
    private static final BlockPos BLOCK = new BlockPos(4, 70, -2);
    private static final BlockPos OTHER = new BlockPos(5, 70, -2);

    /** A resolver that records each seed it is handed and answers a block target and its own hit. */
    private static final class CountingResolver implements AimState.AimResolver {
        private final List<AimAssistResolver.AimHit> seeds = new ArrayList<>();
        private final BlockPos block;
        /** Stands for whatever hit the frame found; the aim state only hands it on. */
        private final AimAssistResolver.AimHit hit = new AimAssistResolver.AimHit.EntityHit(null);

        CountingResolver(BlockPos block) {
            this.block = block;
        }

        @Override
        public AimState.Resolution resolve(AimAssistResolver.AimHit seed) {
            seeds.add(seed);
            return new AimState.Resolution(TargetResult.block(block, Direction.UP), hit);
        }
    }

    @Test
    void oneFrameResolvesOnceAndEveryReaderAndTheThrowReadItsAnswer() {
        AimState aim = new AimState();
        CountingResolver resolver = new CountingResolver(BLOCK);

        aim.update(resolver, 0);
        List<TargetResult> reads = new ArrayList<>();
        for (int read = 0; read < READS; read++) {
            reads.add(aim.target());
        }
        TargetResult throwRead = aim.target();

        assertEquals(1, resolver.seeds.size());
        TargetResult resolved = TargetResult.block(BLOCK, Direction.UP);
        for (TargetResult read : reads) {
            assertEquals(resolved, read);
        }
        assertEquals(resolved, throwRead);
    }

    @Test
    void theNextFrameSeedsFromTheHitThisFrameFound() {
        AimState aim = new AimState();
        CountingResolver first = new CountingResolver(BLOCK);
        CountingResolver second = new CountingResolver(OTHER);

        aim.update(first, 0);
        aim.update(second, 0);

        assertNull(first.seeds.get(0));
        assertSame(first.hit, second.seeds.get(0));
    }

    @Test
    void twoFramesInsideOneTickEachSetTheTarget() {
        AimState aim = new AimState();

        aim.update(new CountingResolver(BLOCK), 0);
        aim.update(new CountingResolver(OTHER), 0);

        assertEquals(TargetResult.block(OTHER, Direction.UP), aim.target());
    }

    @Test
    void aFrozenResolutionHoldsAcrossFrames() {
        AimState aim = new AimState();
        TargetResult frozen = TargetResult.block(BLOCK, Direction.UP);

        for (int frame = 0; frame < READS; frame++) {
            aim.update(seed -> new AimState.Resolution(frozen, seed), 0);
        }

        assertEquals(frozen, aim.target());
    }

    @Test
    void clearingDropsTheTargetAndTheSeed() {
        AimState aim = new AimState();
        aim.update(new CountingResolver(BLOCK), 0);

        aim.clear();

        assertSame(TargetResult.NONE, aim.target());
    }

    /**
     * The aimed mob carries a mark beside its outline that ripples in time,
     * whatever badge locked it or whether Grab holds it, and no other mob
     * carries one (decision active-target-indicators-carry-depth-and-animation).
     */
    @Nested
    class TheAimedMobsMark {

        private static final float NOW = 1000f;
        private static final int OUTLINE = 0xFF33CC66;
        private final Mob slime = mob(new AABB(0, 0, 0, 2, 2, 2));
        private final Mob bystander = mob(new AABB(10, 0, 0, 11, 1, 1));

        private AimState aimingAt(Entity mob) {
            AimState aim = new AimState();
            aim.update(seed -> picking(mob), OUTLINE);
            return aim;
        }

        @Test
        void theAimedMobsMarkShowsAndRipplesWithinOnePeriod() {
            AimState aim = aimingAt(slime);
            AimedMobMark early = aim.markFor(slime, NOW);
            AimedMobMark later = aim.markFor(slime, NOW + AimedMobMark.PERIOD_TICKS / 3f);

            assertTrue(early.strength() > 0f, "the aimed mob's mark should show, strength " + early.strength());
            assertNotEquals(early.phase(), later.phase(), "the mark's phase should advance within one period");
        }

        @Test
        void aMobTheAimDoesNotHoldCarriesNoMark() {
            assertEquals(0f, aimingAt(slime).markFor(bystander, NOW).strength());
        }

        @Test
        void aMobTheChanneledAimLocksCarriesTheMark() {
            AimState aim = new AimState();
            aim.update(seed -> AimTargets.resolveFor(TargetingHint.of(AbilityBadge.CHANNELED),
                    new LockedSources(slime)), OUTLINE);

            assertTrue(aim.markFor(slime, NOW).strength() > 0f);
        }

        @Test
        void theMobGrabHoldsCarriesTheMark() {
            HeldPick held = new HeldPick();
            AimState aim = new AimState();
            aim.update(seed -> held.resolve(true, Set.of()::contains, () -> picking(slime)), OUTLINE);
            aim.update(seed -> held.resolve(true, Set.<Entity>of(slime)::contains,
                    () -> AimState.Resolution.NOTHING), OUTLINE);

            assertTrue(held.holds(), "the Grab press should hold the slime");
            assertTrue(aim.markFor(slime, NOW).strength() > 0f);
        }
    }

    /** Sources whose aim assist finds the given mob near the ray. */
    private record LockedSources(Entity mob) implements AimTargets.AimSources {
        @Override
        public AimAssistResolver.AimHit entityHit() {
            return new AimAssistResolver.AimHit.EntityHit(mob);
        }

        @Override
        public TargetResult blockTarget() {
            return TargetResult.NONE;
        }

        @Override
        public TargetResult pointTarget() {
            return TargetResult.NONE;
        }

        @Override
        public Vec3 entityPoint(Entity entity) {
            return entity.getBoundingBox().getCenter();
        }
    }

    private static AimState.Resolution picking(Entity mob) {
        return new AimState.Resolution(TargetResult.entity(mob, mob.getBoundingBox().getCenter()),
                new AimAssistResolver.AimHit.EntityHit(mob));
    }

    private static Mob mob(AABB box) {
        Mob mob = mock(Mob.class);
        when(mob.isAlive()).thenReturn(true);
        when(mob.getBoundingBox()).thenReturn(box);
        return mob;
    }
}
