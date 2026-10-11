package com.mercuriusxeno.goo.ability.feed;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What Jelly's Feed does to an animal it reaches: it counts as the animal's
 * food, setting an adult ready to breed in love the normal way and growing a
 * baby one stage, to an adult; crumbs scatter and a heart rises either way.
 * feed-blob-feeds-and-draws-mobs
 */
public final class Feeding {

    /** The age an adult ready to breed stands at, and a grown baby reaches. */
    static final int ADULT_AGE = 0;
    private static final int CRUMBS = 8;
    private static final int HEARTS = 3;
    private static final double SPREAD = 0.3;
    private static final double CRUMB_SPEED = 0.05;

    private Feeding() {
    }

    /**
     * Feeds an animal: a baby grows to an adult, and an adult that may breed
     * falls in love.
     *
     * @param animal the animal fed
     * @param feeder the player whose feed it is, credited with the breeding, or null
     * @return true when the feed did either
     */
    public static boolean feed(Animal animal, @Nullable Player feeder) {
        if (animal.isBaby()) {
            animal.setAge(ADULT_AGE);
            return true;
        }
        if (animal.getAge() == ADULT_AGE && animal.canFallInLove()) {
            animal.setInLove(feeder);
            return true;
        }
        return false;
    }

    /**
     * Scatters jelly crumbs about an eater and raises hearts over it.
     *
     * @param level the server level
     * @param eater the mob that ate
     */
    public static void crumbsAndHearts(ServerLevel level, LivingEntity eater) {
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.HONEY_BOTTLE), eater.getX(),
                eater.getY() + eater.getBbHeight() * SPREAD, eater.getZ(), CRUMBS, SPREAD, SPREAD, SPREAD, CRUMB_SPEED);
        level.sendParticles(ParticleTypes.HEART, eater.getX(), eater.getY() + eater.getBbHeight(), eater.getZ(),
                HEARTS, SPREAD, SPREAD, SPREAD, 0);
    }
}
