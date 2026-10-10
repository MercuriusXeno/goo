package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.held.LuxEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Gametests for glow's Lux as a held effect through the glove: eaten, it
 * stands and keeps night vision up without particles; invoked again, it
 * ends and takes the night vision with it (decisions
 * lux-night-vision-without-particles, self-effects-trickle-until-ended).
 */
public final class LuxTests {

    private static final Identifier GLOW_LUX = Identifier.parse("goo:glow_lux");
    private static final String SHOULD_HOLD = "Once the eat finishes the player should hold Lux";
    private static final String SHOULD_SEE_IN_THE_DARK = "Lux should grant its endless night vision with no particles or card, granted %s";
    private static final String SHOULD_END = "Invoking held Lux again should end it and its night vision";

    private LuxTests() {
    }

    /**
     * A survival player eats Lux from the glove and the player ticks once:
     * Lux is held and stands, and night vision stands without particles.
     * Invoking Lux again ends it and the night vision both.
     *
     * @param helper the gametest helper
     */
    public static void luxSeesInTheDarkUntilEnded(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.GLOW, GLOW_LUX);
        player.doTick();
        boolean held = player.getData(GooAttachments.HELD_EFFECTS).holds(GLOW_LUX);
        boolean stands = player.getData(GooAttachments.LUX).standsAt(player.level().getGameTime());
        MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(held && stands, SHOULD_HOLD);
        helper.assertTrue(LuxEvents.isLuxVision(vision), String.format(SHOULD_SEE_IN_THE_DARK, vision));

        SelfDeliveryTests.invoke(player, GooTypes.GLOW, GLOW_LUX);
        player.doTick();
        boolean stillHeld = player.getData(GooAttachments.HELD_EFFECTS).holds(GLOW_LUX);
        boolean stillStands = player.getData(GooAttachments.LUX).standsAt(player.level().getGameTime());
        boolean stillSees = player.hasEffect(MobEffects.NIGHT_VISION);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(stillHeld || stillStands || stillSees, SHOULD_END);
        helper.succeed();
    }
}
