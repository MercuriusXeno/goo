package com.mercuriusxeno.goo.ability.zoo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Set;

/**
 * A downed mob's damage rule and timers: at any time down an attack or a
 * fall is negated and the world's other harm lands, and a mob still down
 * dies at 15 seconds.
 */
class DownedTest {

    private static final long DOWNED_AT = 1000L;

    private static DamageSource hit(ResourceKey<DamageType> type, Set<TagKey<DamageType>> tags,
                                    @Nullable Entity causing) {
        DamageSource source = mock(DamageSource.class);
        when(source.is(any(ResourceKey.class))).thenAnswer(call -> call.getArgument(0).equals(type));
        when(source.is(any(TagKey.class))).thenAnswer(call -> tags.contains(call.getArgument(0)));
        when(source.getEntity()).thenReturn(causing);
        when(source.getDirectEntity()).thenReturn(causing);
        return source;
    }

    private static DamageSource zombieAttack() {
        return hit(DamageTypes.MOB_ATTACK, Set.of(), mock(Zombie.class));
    }

    private static DamageSource explosion() {
        return hit(DamageTypes.EXPLOSION, Set.of(DamageTypeTags.IS_EXPLOSION), null);
    }

    private static DamageSource fall() {
        return hit(DamageTypes.FALL, Set.of(DamageTypeTags.IS_FALL), null);
    }

    private static DamageSource lava() {
        return hit(DamageTypes.LAVA, Set.of(DamageTypeTags.IS_FIRE), null);
    }

    private static DamageSource theVoid() {
        return hit(DamageTypes.FELL_OUT_OF_WORLD, Set.of(DamageTypeTags.BYPASSES_INVULNERABILITY), null);
    }

    @Nested
    class AtAnyTimeDown {

        @Test
        void anAttackIsNegated() {
            assertTrue(Downed.negates(zombieAttack()));
        }

        @Test
        void anExplosionIsNegated() {
            assertTrue(Downed.negates(explosion()));
        }

        @Test
        void aFallIsNegated() {
            assertTrue(Downed.negates(fall()));
        }

        @Test
        void lavaLands() {
            assertFalse(Downed.negates(lava()));
        }

        @Test
        void theVoidLands() {
            assertFalse(Downed.negates(theVoid()));
        }
    }

    @Test
    void anAttackDownsAndLavaKills() {
        assertTrue(Downed.downs(zombieAttack()));
        assertFalse(Downed.downs(lava()));
    }

    @Test
    void aMobStillDownDiesAtFifteenSeconds() {
        Downed downed = new Downed(DOWNED_AT, false, 0f);
        assertFalse(downed.diesAt(DOWNED_AT + Downed.DEATH_TICKS - 1));
        assertTrue(downed.diesAt(DOWNED_AT + Downed.DEATH_TICKS));
    }
}
