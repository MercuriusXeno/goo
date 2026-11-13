package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * What the entity filters keep, read through {@link EntityScan#passes} on mocked entities of each class a filter checks.
 */
class EntityScanTest {

    private static final int IMMUNE_TICKS = 10;

    private static List<String> kept(EntityFilter filter, Map<String, ? extends Entity> candidates) {
        return candidates.entrySet().stream()
                .filter(candidate -> EntityScan.passes(candidate.getValue(), Set.of(filter), candidate.getValue()))
                .map(Map.Entry::getKey).toList();
    }

    /**
     * has_baby_form keeps the ageable mobs but the four the 26.1 tree pins adult, and the zombies, piglins and
     * zoglins that keep a baby flag of their own.
     */
    @Test
    void hasBabyFormKeepsTheMobsTheTreeLetsBeABaby() {
        Map<String, Entity> mobs = new LinkedHashMap<>();
        mobs.put("cow", mock(Cow.class));
        mobs.put("zombie", mock(Zombie.class));
        mobs.put("piglin", mock(Piglin.class));
        mobs.put("zoglin", mock(Zoglin.class));
        mobs.put("frog", mock(Frog.class));
        mobs.put("parrot", mock(Parrot.class));
        mobs.put("camel husk", mock(CamelHusk.class));
        mobs.put("zombie nautilus", mock(ZombieNautilus.class));
        mobs.put("creeper", mock(Creeper.class));
        mobs.put("skeleton", mock(Skeleton.class));
        assertEquals(List.of("cow", "zombie", "piglin", "zoglin"), kept(EntityFilter.HAS_BABY_FORM, mobs));
    }

    /**
     * vulnerable keeps the mob whose hit immunity has run out, so a held strike spaces its hits by the immunity
     * each leaves.
     */
    @Test
    void vulnerableKeepsTheMobWhoseImmunityHasRunOut() {
        Mob immune = mock(Mob.class);
        immune.invulnerableTime = IMMUNE_TICKS;
        Mob spent = mock(Mob.class);
        Map<String, Mob> mobs = new LinkedHashMap<>();
        mobs.put("immune", immune);
        mobs.put("spent", spent);
        assertEquals(List.of("spent"), kept(EntityFilter.VULNERABLE, mobs));
    }
}
