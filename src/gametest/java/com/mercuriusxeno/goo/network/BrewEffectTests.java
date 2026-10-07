package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.nourish.Nourish;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.registry.GooPotions;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
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

    private static final int FULL_HALVES = 20;
    /** Half of a twenty-point bar: five hearts held, five missing. */
    private static final float HALF_HEALTH = 10f;
    /** The halves of stone over the five missing hearts. */
    private static final int MISSING_HALVES = 10;
    private static final String SHOULD_CARRY = "The %s potion should carry its brew effect alone for %d ticks, carries %s";
    private static final String SHOULD_LAY = "The %s brew should lay %d %s halves expiring at %d, laid %s %d expiring at %d";
    private static final String SHOULD_HOLD_EFFECT = "The %s brew effect should stand for %d ticks, stands %s";
    private static final String SHOULD_DRAIN_NOTHING = "A brew should drain no goo, drained %d";
    private static final String SHOULD_CHARGE = "The unstable brew should charge until %d, charges until %d";
    private static final String SHOULD_NOURISH = "The vital brew should nourish until %d, nourishes until %d";
    private static final String SHOULD_RUN_NOTHING = "A brew of a type with no brew ability should lay nothing, laid %s";

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
     * Drinking the unstable brew charges the player for an hour
     * (decision charged-scales-channel-params-by-json).
     *
     * @param helper the gametest helper
     */
    public static void unstableBrewChargesForAnHour(GameTestHelper helper) {
        ServerPlayer player = drinker(helper, GooTypes.UNSTABLE);
        long now = player.level().getGameTime();

        drink(player, GooTypes.UNSTABLE);

        long chargedUntil = player.getData(GooAttachments.CHARGED);
        helper.getLevel().getServer().getPlayerList().remove(player);
        long expected = now + GooPotions.BREW_DURATION;
        helper.assertTrue(chargedUntil == expected, String.format(SHOULD_CHARGE, expected, chargedUntil));
        helper.succeed();
    }

    /**
     * Drinking the rock brew while missing five hearts lays stone over those
     * five for an hour (decision stoneskin-stone-hearts-block-regeneration).
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
        helper.assertTrue(overlay.kind() == HeartKind.STONESKIN && overlay.shieldHalves() == MISSING_HALVES
                        && overlay.shieldAt(0) == 0 && overlay.expiresAt() == expected,
                String.format(SHOULD_LAY, GooTypes.ROCK.identifier(), MISSING_HALVES, HeartKind.STONESKIN,
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
     * Drinking the frost brew, a type with no brew ability yet, holds the
     * effect and lays no hearts.
     *
     * @param helper the gametest helper
     */
    public static void brewWithoutAnAbilityRunsNothing(GameTestHelper helper) {
        ServerPlayer player = drinker(helper, GooTypes.FROST);
        drink(player, GooTypes.FROST);
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        MobEffectInstance standing = player.getEffect(GooMobEffects.BREW_EFFECTS.get(GooTypes.FROST));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing != null, String.format(SHOULD_HOLD_EFFECT, GooTypes.FROST.identifier(),
                GooPotions.BREW_DURATION, standing));
        helper.assertFalse(overlay.stands(), String.format(SHOULD_RUN_NOTHING, overlay));
        helper.succeed();
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
        helper.assertTrue(overlay.kind() == kind && overlay.shieldHalves() == FULL_HALVES
                        && overlay.expiresAt() == expected,
                String.format(SHOULD_LAY, gooType.identifier(), FULL_HALVES, kind, expected,
                        overlay.kind(), overlay.shieldHalves(), overlay.expiresAt()));
        helper.assertTrue(standing != null && standing.getDuration() == GooPotions.BREW_DURATION,
                String.format(SHOULD_HOLD_EFFECT, gooType.identifier(), GooPotions.BREW_DURATION, standing));
        helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING, drained));
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
