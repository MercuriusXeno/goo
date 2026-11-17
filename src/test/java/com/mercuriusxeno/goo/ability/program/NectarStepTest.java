package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers what Jelly's Nectar's program takes: the goo step beside the nectar
 * acts on no entity, so the ability takes the animals and slimes the nectar
 * takes and no zombie or player (decision steps-declare-the-entities-they-take).
 */
class NectarStepTest {

    private final List<Step> jellyFeed = List.of(new NectarGooStep(2, 16, 12, 100), new NectarStep());

    @Test
    void feedTakesAnAnimalAndASlime() {
        assertTrue(Step.takenByAny(jellyFeed, mock(Animal.class)));
        assertTrue(Step.takenByAny(jellyFeed, mock(Slime.class)));
    }

    @Test
    void theGooBesideTheFeedLeavesAZombieAndAPlayerUntaken() {
        assertFalse(Step.takenByAny(jellyFeed, mock(Zombie.class)));
        assertFalse(Step.takenByAny(jellyFeed, mock(Player.class)));
    }
}
