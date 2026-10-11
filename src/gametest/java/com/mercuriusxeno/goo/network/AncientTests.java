package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.registry.GooPotions;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;

/**
 * Gametests for yore Ancient: a survival player under Ancient, from the
 * glove or from a drunk yore brew, takes a lethal hit and stands at half a
 * heart; the brew wears the yore brew effect alone, no vanilla effect
 * beside it.
 * ancient-makes-the-player-immortal
 */
public final class AncientTests {

    private static final Identifier YORE_ANCIENT = Identifier.parse("goo:yore_ancient");
    /** Far past a full bar of health. */
    private static final float LETHAL_HIT = 1000f;
    /** Half a heart, where Ancient holds the player. */
    private static final float HALF_HEART = 1f;
    private static final String SHOULD_SURVIVE = "A player under Ancient should stand at half a heart, alive %s at %.1f";
    private static final String SHOULD_WEAR_ONLY_THE_BREW = "The yore brew should be the one effect standing, stands %s";

    private AncientTests() {
    }

    /**
     * A survival player holding Ancient from the glove takes a lethal hit
     * and stands at half a heart.
     *
     * @param helper the gametest helper
     */
    public static void ancientSurvivesLethalDamage(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.YORE, YORE_ANCIENT);
        player.hurtServer(helper.getLevel(), player.damageSources().generic(), LETHAL_HIT);
        boolean alive = player.isAlive();
        float health = player.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(alive && health == HALF_HEART, String.format(SHOULD_SURVIVE, alive, health));
        helper.succeed();
    }

    /**
     * A survival player who drinks the yore brew wears the yore brew effect
     * alone and survives a lethal hit at half a heart.
     *
     * @param helper the gametest helper
     */
    public static void yoreBrewAncientForAnHour(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.YORE);
        // the mock player helper makes a creative player, whom no hit lands on
        player.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded is invulnerable, and no mock client reports
        player.connection.markClientLoaded();
        for (MobEffectInstance effect : GooPotions.GOO_POTIONS.get(GooTypes.YORE).value().getEffects()) {
            player.addEffect(new MobEffectInstance(effect));
        }
        Holder<MobEffect> brew = GooMobEffects.BREW_EFFECTS.get(GooTypes.YORE);
        boolean alone = player.getActiveEffects().size() == 1 && player.hasEffect(brew);
        String standing = player.getActiveEffects().toString();
        player.hurtServer(helper.getLevel(), player.damageSources().generic(), LETHAL_HIT);
        boolean alive = player.isAlive();
        float health = player.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(alone, String.format(SHOULD_WEAR_ONLY_THE_BREW, standing));
        helper.assertTrue(alive && health == HALF_HEART, String.format(SHOULD_SURVIVE, alive, health));
        helper.succeed();
    }
}
