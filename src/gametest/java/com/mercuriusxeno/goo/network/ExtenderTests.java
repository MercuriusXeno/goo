package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooPotions;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * Gametests for the pulse brew: drinking it lengthens every timed effect
 * standing, a goo brew and a vanilla potion alike, by its duration, and each
 * timed effect applied while it stands lands lengthened the same.
 * extender-multiplies-the-next-self-duration
 */
public final class ExtenderTests {

    private static final Identifier BLAZE_KINDLE = Identifier.parse("goo:blaze_kindle");
    private static final int REGENERATION_TICKS = 600;
    private static final long NOT_HELD = -1;
    private static final String KINDLE_EXTENDED = "The blaze brew should end %d ticks later, expiry %d became %d";
    private static final String REGENERATION_EXTENDED = "Regeneration should stand %d ticks, stands %d";

    private ExtenderTests() {
    }

    /**
     * A player under a blaze brew and a regeneration potion drinks the pulse
     * brew: the blaze brew's expiry and the potion's duration each grow by
     * the pulse brew's duration.
     *
     * @param helper the gametest helper
     */
    public static void pulseBrewExtendsStandingEffects(GameTestHelper helper) {
        ServerPlayer player = drinker(helper);
        BrewEffectTests.drinkBrew(player, GooTypes.BLAZE);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGENERATION_TICKS));
        long kindleBefore = kindleExpiry(player);

        BrewEffectTests.drinkBrew(player, GooTypes.PULSE);

        long kindleAfter = kindleExpiry(player);
        int regeneration = player.getEffect(MobEffects.REGENERATION).getDuration();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(kindleBefore != NOT_HELD && kindleAfter == kindleBefore + GooPotions.BREW_DURATION,
                String.format(KINDLE_EXTENDED, GooPotions.BREW_DURATION, kindleBefore, kindleAfter));
        int expected = REGENERATION_TICKS + GooPotions.BREW_DURATION;
        helper.assertTrue(regeneration == expected, String.format(REGENERATION_EXTENDED, expected, regeneration));
        helper.succeed();
    }

    /**
     * A player under the pulse brew drinks a regeneration potion: it lands
     * lengthened by the pulse brew's duration.
     *
     * @param helper the gametest helper
     */
    public static void pulseBrewExtendsLaterEffects(GameTestHelper helper) {
        ServerPlayer player = drinker(helper);
        BrewEffectTests.drinkBrew(player, GooTypes.PULSE);

        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGENERATION_TICKS));

        int regeneration = player.getEffect(MobEffects.REGENERATION).getDuration();
        helper.getLevel().getServer().getPlayerList().remove(player);
        int expected = REGENERATION_TICKS + GooPotions.BREW_DURATION;
        helper.assertTrue(regeneration == expected, String.format(REGENERATION_EXTENDED, expected, regeneration));
        helper.succeed();
    }

    private static ServerPlayer drinker(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.PULSE);
        // the mock player helper makes a creative player
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static long kindleExpiry(ServerPlayer player) {
        HeldEffects held = player.getData(GooAttachments.HELD_EFFECTS);
        return held.held().stream().filter(effect -> effect.ability().equals(BLAZE_KINDLE))
                .mapToLong(HeldEffects.Held::expiresAt).findFirst().orElse(NOT_HELD);
    }
}
