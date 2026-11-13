package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for yore Ancient: a survival player holding Ancient on the glove
 * wears the yore held effect, takes a lethal hit and stands at half a heart.
 * ancient-makes-the-player-immortal
 */
@RegistersGameTests
public final class AncientTests {
    private static final String ANCIENT_SURVIVES_LETHAL_DAMAGE = "ancient_survives_lethal_damage";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ANCIENT_SURVIVES_LETHAL_DAMAGE, AncientTests::ancientSurvivesLethalDamage);
    }


    private static final Identifier YORE_ANCIENT = Identifier.parse("goo:yore_ancient");
    /** Far past a full bar of health. */
    private static final float LETHAL_HIT = 1000f;
    /** Half a heart, where Ancient holds the player. */
    private static final float HALF_HEART = 1f;
    private static final String SHOULD_SURVIVE = "A player under Ancient should stand at half a heart, alive %s at %.1f";
    private static final String SHOULD_WEAR_THE_HELD_EFFECT =
            "The player should wear the yore held effect, stands %s";

    private AncientTests() {
    }

    /**
     * A survival player holding Ancient from the glove wears the yore held
     * effect, takes a lethal hit and stands at half a heart.
     *
     * @param helper the gametest helper
     */
    public static void ancientSurvivesLethalDamage(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.YORE, YORE_ANCIENT);
        boolean wears = player.hasEffect(GooMobEffects.HELD_EFFECTS.get(GooTypes.YORE));
        String standing = player.getActiveEffects().toString();
        player.hurtServer(helper.getLevel(), player.damageSources().generic(), LETHAL_HIT);
        boolean alive = player.isAlive();
        float health = player.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(wears, String.format(SHOULD_WEAR_THE_HELD_EFFECT, standing));
        helper.assertTrue(alive && health == HALF_HEART, String.format(SHOULD_SURVIVE, alive, health));
        helper.succeed();
    }
}