package com.mercuriusxeno.goo.ability.program;

import net.minecraft.resources.Identifier;
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
 * Covers what Jelly Feed's program takes: the sound beside the feed acts on
 * no entity, so the ability takes the animals and slimes the feed takes and
 * no zombie or player (decision steps-declare-the-entities-they-take).
 */
class FeedStepTest {

    private final List<Step> jellyFeed = List.of(
            new FeedStep(Expr.literal(12), Expr.literal(100)),
            new SoundStep(Identifier.fromNamespaceAndPath("minecraft", "entity.generic.eat"), FxAnchor.HOST,
                    SoundKind.PLAYERS, Expr.literal(1), Expr.literal(1), Expr.literal(0)));

    @Test
    void feedTakesAnAnimalAndASlime() {
        assertTrue(Step.takenByAny(jellyFeed, mock(Animal.class)));
        assertTrue(Step.takenByAny(jellyFeed, mock(Slime.class)));
    }

    @Test
    void theSoundBesideTheFeedLeavesAZombieAndAPlayerUntaken() {
        assertFalse(Step.takenByAny(jellyFeed, mock(Zombie.class)));
        assertFalse(Step.takenByAny(jellyFeed, mock(Player.class)));
    }
}
