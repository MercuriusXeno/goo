package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.ability.nourish.Nourish;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.registry.GooPotions;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import java.util.List;

/**
 * Gametests for drinking a goo brew: the potion's effect lands on a survival
 * mock player and runs the type's self + brew ability for the hour the
 * effect carries, with no goo drained; a type with no brew ability yet runs
 * nothing, and every bundled type's potion carries its own brew effect
 * (decision brew-grants-the-self-ability-for-an-hour).
 */
public final class BrewEffectTests {

    /** One heart of shield, which a brew primes before its crawl reaches the rest. */
    private static final int PRIMED_HALVES = HeartOverlay.FULL_SHIELD;
    /** Half of a twenty-point bar: five hearts held, five missing. */
    private static final float HALF_HEALTH = 10f;
    /** The first missing heart at half health, which the stone primes. */
    private static final int FIRST_MISSING = 5;
    private static final String SHOULD_CARRY = "The %s potion should carry its brew effect alone for %d ticks, carries %s";
    private static final String SHOULD_LAY = "The %s brew should lay %d %s halves expiring at %d, laid %s %d expiring at %d";
    private static final String SHOULD_HOLD_EFFECT = "The %s brew effect should stand for %d ticks, stands %s";
    private static final String SHOULD_DRAIN_NOTHING = "A brew should drain no goo, drained %d";
    /** shroom_sight.json's factor. */
    private static final float SIGHT_FACTOR = 3f;
    private static final String SHOULD_SEE = "The shroom brew should grant sight at %.1f until %d, granted %.1f until %d";
    private static final String SHOULD_SHOW_NO_PARTICLES = "A brew should show its icon and no particles, stands %s";
    private static final String SHOULD_NOURISH = "The vital brew should nourish until %d, nourishes until %d";
    private static final String SHOULD_RUN_NOTHING = "A brew of a type with no brew ability should lay nothing, laid %s";
    private static final String SHOULD_HOLD_PREPAID = "The blaze brew should hold Kindle prepaid until %d, held %s";
    private static final String SHOULD_DRAIN_NOTHING_HELD = "A prepaid brew should drain no goo over %d ticks, drained %d";
    private static final String SHOULD_END_BREW_EFFECT = "Barkskin replacing a drunk Kindle should end the blaze brew effect, stands %s";
    private static final Identifier BLAZE_KINDLE = Identifier.parse("goo:blaze_kindle");
    private static final Identifier LEAF_BARKSKIN = Identifier.parse("goo:leaf_barkskin");
    /** The ticks a prepaid brew is watched paying nothing. */
    private static final int WATCHED_TICKS = 20;

    private BrewEffectTests() {
    }

    /**
     * Every bundled type's potion carries that type's brew effect alone, for an hour.
     *
     * @param helper the gametest helper
     */
    public static void everyPotionCarriesItsBrewEffect(GameTestHelper helper) {
        for (ResourceKey<GooTypeDefinition> key : GooPotions.POTION_TYPES) {
            List<MobEffectInstance> effects = GooPotions.GOO_POTIONS.get(key).value().getEffects();
            Holder<MobEffect> brew = GooMobEffects.BREW_EFFECTS.get(key);
            boolean carries = effects.size() == 1 && effects.getFirst().is(brew)
                    && effects.getFirst().getDuration() == GooPotions.BREW_DURATION;
            helper.assertTrue(carries, String.format(SHOULD_CARRY, key.identifier(), GooPotions.BREW_DURATION, effects));
        }
        helper.succeed();
    }

    /**
     * Drinking the blaze brew kindles the player for an hour.
     *
     * @param helper the gametest helper
     */
    public static void blazeBrewKindlesForAnHour(GameTestHelper helper) {
        brewLaysForAnHour(helper, GooTypes.BLAZE, HeartKind.KINDLE);
    }

    /**
     * Drinking the leaf brew barks the player for an hour.
     *
     * @param helper the gametest helper
     */
    public static void leafBrewBarksForAnHour(GameTestHelper helper) {
        brewLaysForAnHour(helper, GooTypes.LEAF, HeartKind.BARKSKIN);
    }

    /**
     * Drinking the rock brew while missing five hearts primes stone over the
     * first of those five for an hour (decisions
     * stoneskin-stone-hearts-block-regeneration and heart-effects-crawl-while-held).
     *
     * @param helper the gametest helper
     */
    public static void rockBrewStoneskinsForAnHour(GameTestHelper helper) {
        ServerPlayer player = drinker(helper, GooTypes.ROCK);
        player.setHealth(HALF_HEALTH);
        long now = player.level().getGameTime();

        drink(player, GooTypes.ROCK);

        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        helper.getLevel().getServer().getPlayerList().remove(player);
        long expected = now + GooPotions.BREW_DURATION;
        helper.assertTrue(overlay.kind() == HeartKind.STONESKIN && overlay.shieldHalves() == PRIMED_HALVES
                        && overlay.shieldAt(FIRST_MISSING) == PRIMED_HALVES && overlay.expiresAt() == expected,
                String.format(SHOULD_LAY, GooTypes.ROCK.identifier(), PRIMED_HALVES, HeartKind.STONESKIN,
                        expected, overlay.kind(), overlay.shieldHalves(), overlay.expiresAt()));
        helper.succeed();
    }

    /**
     * Drinking the vital brew nourishes the player for an hour, draining no
     * goo (decision nourish-restores-hunger-over-time).
     *
     * @param helper the gametest helper
     */
    public static void vitalBrewNourishesForAnHour(GameTestHelper helper) {
        ServerPlayer player = drinker(helper, GooTypes.VITAL);
        int heldBefore = held(player, GooTypes.VITAL);
        long now = player.level().getGameTime();

        drink(player, GooTypes.VITAL);

        Nourish nourish = player.getData(GooAttachments.NOURISH);
        MobEffectInstance standing = player.getEffect(GooMobEffects.BREW_EFFECTS.get(GooTypes.VITAL));
        int drained = heldBefore - held(player, GooTypes.VITAL);
        helper.getLevel().getServer().getPlayerList().remove(player);
        long expected = now + GooPotions.BREW_DURATION;
        helper.assertTrue(nourish.expiresAt() == expected,
                String.format(SHOULD_NOURISH, expected, nourish.expiresAt()));
        helper.assertTrue(standing != null && standing.getDuration() == GooPotions.BREW_DURATION,
                String.format(SHOULD_HOLD_EFFECT, GooTypes.VITAL.identifier(), GooPotions.BREW_DURATION, standing));
        helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING, drained));
        helper.succeed();
    }

    /**
     * Drinking the shroom brew grants fungal sight at Sight's factor for an
     * hour, draining no goo (decision sight-lengthens-shift-and-outlines-fungus).
     *
     * @param helper the gametest helper
     */
    public static void shroomBrewSightForAnHour(GameTestHelper helper) {
        ServerPlayer player = drinker(helper, GooTypes.SHROOM);
        int heldBefore = held(player, GooTypes.SHROOM);
        long now = player.level().getGameTime();

        drink(player, GooTypes.SHROOM);

        Sight sight = player.getData(GooAttachments.SIGHT);
        int drained = heldBefore - held(player, GooTypes.SHROOM);
        helper.getLevel().getServer().getPlayerList().remove(player);
        long expected = now + GooPotions.BREW_DURATION;
        helper.assertTrue(sight.factor() == SIGHT_FACTOR && sight.expiresAt() == expected,
                String.format(SHOULD_SEE, SIGHT_FACTOR, expected, sight.factor(), sight.expiresAt()));
        helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING, drained));
        helper.succeed();
    }

    /**
     * Drinks the type's potion, as a test player outside this class does.
     *
     * @param player  the drinking player
     * @param gooType the potion's type
     */
    static void drinkBrew(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType) {
        drink(player, gooType);
    }

    /**
     * Drinking the typhoon brew, a type with no brew ability yet, holds the
     * effect and lays no hearts.
     *
     * @param helper the gametest helper
     */
    public static void brewWithoutAnAbilityRunsNothing(GameTestHelper helper) {
        ServerPlayer player = drinker(helper, GooTypes.TYPHOON);
        drink(player, GooTypes.TYPHOON);
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        MobEffectInstance standing = player.getEffect(GooMobEffects.BREW_EFFECTS.get(GooTypes.TYPHOON));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing != null, String.format(SHOULD_HOLD_EFFECT, GooTypes.TYPHOON.identifier(),
                GooPotions.BREW_DURATION, standing));
        helper.assertFalse(overlay.stands(), String.format(SHOULD_RUN_NOTHING, overlay));
        helper.succeed();
    }

    /**
     * A frost brew lays Iceborn's frozen hearts for the brew's hour
     * (decision iceborn-frozen-hearts-thaw-on-fire).
     *
     * @param helper the gametest helper
     */
    public static void frostBrewIcebornForAnHour(GameTestHelper helper) {
        brewLaysForAnHour(helper, GooTypes.FROST, HeartKind.ICEBORN);
    }

    private static void brewLaysForAnHour(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType,
                                          HeartKind kind) {
        ServerPlayer player = drinker(helper, gooType);
        int heldBefore = held(player, gooType);
        long now = player.level().getGameTime();

        drink(player, gooType);

        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        MobEffectInstance standing = player.getEffect(GooMobEffects.BREW_EFFECTS.get(gooType));
        int drained = heldBefore - held(player, gooType);
        helper.getLevel().getServer().getPlayerList().remove(player);
        long expected = now + GooPotions.BREW_DURATION;
        helper.assertTrue(overlay.kind() == kind && overlay.shieldHalves() == PRIMED_HALVES
                        && overlay.shieldAt(0) == PRIMED_HALVES && overlay.expiresAt() == expected,
                String.format(SHOULD_LAY, gooType.identifier(), PRIMED_HALVES, kind, expected,
                        overlay.kind(), overlay.shieldHalves(), overlay.expiresAt()));
        helper.assertTrue(standing != null && standing.getDuration() == GooPotions.BREW_DURATION,
                String.format(SHOULD_HOLD_EFFECT, gooType.identifier(), GooPotions.BREW_DURATION, standing));
        helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING, drained));
        // brew-runs-the-crawl-prepaid-on-a-shown-clock: the icon and its time show, particles never
        helper.assertTrue(standing.showIcon() && !standing.isVisible(), String.format(SHOULD_SHOW_NO_PARTICLES, standing));
        helper.succeed();
    }

    /**
     * Drinking the blaze brew holds Kindle prepaid until the brew's end, its
     * first ember laid, and twenty ticks on no blaze goo has drained.
     * brew-runs-the-crawl-prepaid-on-a-shown-clock
     *
     * @param helper the gametest helper
     */
    public static void blazeBrewHoldsKindlePrepaid(GameTestHelper helper) {
        ServerPlayer player = drinker(helper, GooTypes.BLAZE);
        int heldBefore = held(player, GooTypes.BLAZE);
        long expected = player.level().getGameTime() + GooPotions.BREW_DURATION;

        drink(player, GooTypes.BLAZE);

        HeldEffects heldEffects = player.getData(GooAttachments.HELD_EFFECTS);
        boolean prepaid = heldEffects.held().stream().anyMatch(effect -> effect.ability().equals(BLAZE_KINDLE)
                && effect.prepaid() && effect.expiresAt() == expected);
        helper.assertTrue(prepaid, String.format(SHOULD_HOLD_PREPAID, expected, heldEffects));
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        helper.assertTrue(overlay.shieldHalves() == PRIMED_HALVES && overlay.shieldAt(0) == PRIMED_HALVES,
                String.format(SHOULD_LAY, GooTypes.BLAZE.identifier(), PRIMED_HALVES, HeartKind.KINDLE, expected,
                        overlay.kind(), overlay.shieldHalves(), overlay.expiresAt()));
        SelfDeliveryTests.tickFor(helper, player, WATCHED_TICKS);
        helper.runAfterDelay(WATCHED_TICKS + 1, () -> {
            int drained = heldBefore - held(player, GooTypes.BLAZE);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING_HELD, WATCHED_TICKS, drained));
            helper.succeed();
        });
    }

    /**
     * Eating Barkskin over a drunk Kindle ends Kindle and the blaze brew's
     * effect with it.
     * brew-runs-the-crawl-prepaid-on-a-shown-clock
     *
     * @param helper the gametest helper
     */
    public static void replacedBrewEndsItsEffect(GameTestHelper helper) {
        ServerPlayer player = drinker(helper, GooTypes.LEAF);
        drink(player, GooTypes.BLAZE);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(player.level()).getAbility(LEAF_BARKSKIN));

        SelfDeliveryTests.invoke(player, GooTypes.LEAF, LEAF_BARKSKIN);
        SelfDeliveryTests.eatThrough(player);

        MobEffectInstance standing = player.getEffect(GooMobEffects.BREW_EFFECTS.get(GooTypes.BLAZE));
        HeldEffects heldEffects = player.getData(GooAttachments.HELD_EFFECTS);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing == null, String.format(SHOULD_END_BREW_EFFECT, standing));
        helper.assertTrue(heldEffects.holds(LEAF_BARKSKIN) && !heldEffects.holds(BLAZE_KINDLE),
                String.format(SHOULD_END_BREW_EFFECT, heldEffects));
        helper.succeed();
    }

    /**
     * A survival mock player holding goo of the type, whom a hit lands on.
     *
     * @param helper  the gametest helper
     * @param gooType the type the player holds
     * @return the player
     */
    private static ServerPlayer drinker(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, gooType);
        // the mock player helper makes a creative player
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /**
     * Applies the type's potion's effects to the player, as drinking it does.
     *
     * @param player  the drinking player
     * @param gooType the potion's type
     */
    private static void drink(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType) {
        for (MobEffectInstance effect : GooPotions.GOO_POTIONS.get(gooType).value().getEffects()) {
            player.addEffect(new MobEffectInstance(effect));
        }
    }

    private static int held(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(gooType, 0);
    }
}
