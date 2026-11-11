package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.zoo.Leader;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers Leader's sensed set on the client: every mob inside the radius,
 * hostile and passive alike, appears glowing, and the outline's strength
 * stands whole while Leader stands and fades over the second after it ends.
 * leader-outlines-every-mob-through-walls
 */
class LeaderSenseTest {

    private static final Vec3 CENTER = new Vec3(0, 64, 0);
    private static final Leader STANDING = new Leader(12, 4);
    private static final float NOW = 1000f;
    private static final float EPSILON = 1e-6f;

    private static <E extends Entity> E at(Class<E> kind, int id, double x) {
        E entity = mock(kind);
        when(entity.getId()).thenReturn(id);
        when(entity.distanceToSqr(CENTER)).thenReturn(x * x);
        return entity;
    }

    @Nested
    class WhichEntities {

        @Test
        void sensesHostileAndPassiveMobsInsideTheRadius() {
            assertTrue(LeaderSense.senses(at(Monster.class, 1, 5), CENTER, STANDING.radius()));
            assertTrue(LeaderSense.senses(at(Mob.class, 2, 11.5), CENTER, STANDING.radius()));
        }

        @Test
        void mobBeyondTheRadiusIsNotSensed() {
            assertFalse(LeaderSense.senses(at(Mob.class, 3, 12.5), CENTER, STANDING.radius()));
        }

        @Test
        void nonMobEntityIsNotSensed() {
            assertFalse(LeaderSense.senses(at(ItemEntity.class, 4, 1), CENTER, STANDING.radius()));
        }

        @Test
        void sensedMobAppearsGlowing() {
            LeaderSense sense = new LeaderSense();
            Mob near = at(Mob.class, 5, 3);
            Mob far = at(Mob.class, 6, 30);

            sense.sense(STANDING, List.of(near, far), CENTER, NOW);

            assertTrue(sense.appearsGlowing(5));
            assertFalse(sense.appearsGlowing(6));
        }
    }

    /** decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends */
    @Nested
    class Strength {

        @Test
        void outlineIsWholeWhileLeaderStands() {
            LeaderSense sense = new LeaderSense();
            sense.sense(STANDING, List.of(at(Mob.class, 7, 3)), CENTER, NOW);

            assertEquals(1f, sense.strength(NOW + Afterglow.FADE_TICKS * 3), EPSILON);
        }

        @Test
        void outlineFadesOverASecondAfterLeaderEnds() {
            LeaderSense sense = new LeaderSense();
            Mob near = at(Mob.class, 8, 3);
            sense.sense(STANDING, List.of(near), CENTER, NOW - 1);
            sense.sense(Leader.NONE, List.of(near), CENTER, NOW);

            assertTrue(sense.appearsGlowing(8), "the outline lingers where Leader left it");
            assertEquals(0.5f, sense.strength(NOW + Afterglow.FADE_TICKS / 2f), EPSILON);
            assertEquals(0f, sense.strength(NOW + Afterglow.FADE_TICKS), EPSILON);
            sense.sense(Leader.NONE, List.of(near), CENTER, NOW + Afterglow.FADE_TICKS);
            assertFalse(sense.appearsGlowing(8), "the outline is gone once the fade is spent");
        }
    }
}
