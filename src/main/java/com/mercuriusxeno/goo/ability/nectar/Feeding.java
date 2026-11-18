package com.mercuriusxeno.goo.ability.nectar;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * What Jelly's Nectar does to an animal it reaches: it counts as the animal's
 * food, setting an adult ready to breed in love the normal way and growing a
 * baby one stage, to an adult; crumbs scatter and a heart rises either way.
 * A slime it reaches grows one size step instead.
 * feed-blob-feeds-and-draws-mobs
 * slimes-grow-on-jelly-feed
 */
public final class Feeding {

    /** The age an adult ready to breed stands at, and a grown baby reaches. */
    static final int ADULT_AGE = 0;
    /** Each vanilla slime size step doubles the size: 1, 2, 4. */
    private static final int SLIME_GROWTH_FACTOR = 2;
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
     * @param feeder the player whose nectar it is, credited with the breeding, or null
     * @return true when the nectar did either
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
     * What an animal reaching Nectar goo eats.
     *
     * @param layersLeft  the cell's layers after the meal
     * @param ageAfter    the animal's age after it
     * @param fallsInLove whether the animal falls in love
     */
    public record Meal(int layersLeft, int ageAfter, boolean fallsInLove) {
    }

    /**
     * What an animal reaching a Nectar cell eats: a baby grows to an adult
     * and an adult ready to breed falls in love, each eating one layer of
     * the cell; an animal neither feeds eats nothing.
     * each-feeding-eats-a-layer-of-nectar
     * nectar-goo-keeps-feeds-effects-as-a-universal-food
     *
     * @param layers        the cell's layers
     * @param age           the animal's age, below zero for a baby
     * @param canFallInLove whether the animal may fall in love
     * @return the meal, empty where the goo feeds it nothing
     */
    public static Optional<Meal> mealOf(int layers, int age, boolean canFallInLove) {
        if (layers <= 0) {
            return Optional.empty();
        }
        if (age < ADULT_AGE) {
            return Optional.of(new Meal(layers - 1, ADULT_AGE, false));
        }
        if (age == ADULT_AGE && canFallInLove) {
            return Optional.of(new Meal(layers - 1, ADULT_AGE, true));
        }
        return Optional.empty();
    }

    /**
     * The size a fed slime grows to:the next vanilla size step, capped at
     * the largest natural size.
     * slimes-grow-on-jelly-feed
     *
     * @param size the slime's size before the nectar
     * @return its size after
     */
    static int grownSlimeSize(int size) {
        return Math.min(size * SLIME_GROWTH_FACTOR, Slime.MAX_NATURAL_SIZE);
    }

    /**
     * Feeds a slime: it grows one size step, healed to its new size's full
     * health, and a slime at the largest natural size stays there.
     * slimes-grow-on-jelly-feed
     *
     * @param slime the slime fed
     */
    public static void feed(Slime slime) {
        slime.setSize(grownSlimeSize(slime.getSize()), true);
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
