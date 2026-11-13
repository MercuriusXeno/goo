package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for aggravated damage through Leaf Barkskin invoked on a survival
 * mock player by the real self delivery: fire and axes pass the bark to take
 * real health and burn twice their hearts in bark, an arrow breaks bark alone,
 * and a striking mob takes thorns by the bark count (decisions
 * aggravated-damage-is-a-per-heart-rule and barkskin-bark-hearts-thorn-and-burn).
 */
@RegistersGameTests
public final class BarkskinTests {
    private static final String SELF_BARKSKIN_BARE_STAYS_HELD = "self_barkskin_bare_stays_held_and_regrows";
    private static final String SELF_BARKSKIN_THORNS = "self_barkskin_thorns_and_the_axe";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SELF_BARKSKIN_BARE_STAYS_HELD, BarkskinTests::bareBarkskinStaysHeldAndRegrows);
        GooTestFunctions.registerTest(registrar, SELF_BARKSKIN_THORNS, BarkskinTests::thornsAndTheAxe);
    }


    private static final Identifier LEAF_BARKSKIN = Identifier.parse("goo:leaf_barkskin");
    private static final BlockPos ATTACKER_POS = new BlockPos(2, 1, 3);
    private static final int FULL_BARK = 10;
    private static final float TOLERANCE = 1e-4f;
    private static final String SHOULD_THORN = "The zombie should take %d thorns: health %.1f of %.1f";
    /** Ten fire burns twenty halves, every bark on a full bar. */
    private static final float FULL_FIRE = 10f;
    /** Past one bark regrow interval, two and a half seconds. */
    private static final int REGROW_WATCH = 55;
    private static final String SHOULD_BURN_BARE = "Ten fire should burn every bark, %d halves stand";
    private static final String SHOULD_STAY_AND_REGROW = "Bare Barkskin should stay held and regrow: held %s, %d halves";

    private BarkskinTests() {
    }

    /**
     * A bare-handed zombie striking a barkskinned player takes thorns by the
     * bark standing when it struck; BarkskinStrikeTest holds an axe's burn.
     *
     * @param helper the gametest helper
     */
    public static void thornsAndTheAxe(GameTestHelper helper) {
        ServerPlayer player = barked(helper);
        Zombie zombie = unarmored(helper, ATTACKER_POS);
        float zombieMax = zombie.getMaxHealth();
        zombie.doHurtTarget(helper.getLevel(), player);
        float zombieHealth = zombie.getHealth();

        helper.getLevel().getServer().getPlayerList().remove(player);

        helper.assertTrue(Math.abs(zombieMax - FULL_BARK - zombieHealth) < TOLERANCE,
                String.format(SHOULD_THORN, FULL_BARK, zombieHealth, zombieMax));
        helper.succeed();
    }

    /**
     * A held Barkskin whose every bark burns away stays held, and its crawl
     * barks a heart again once the regrow interval passes.
     * heart-effects-crawl-while-held
     *
     * @param helper the gametest helper
     */
    public static void bareBarkskinStaysHeldAndRegrows(GameTestHelper helper) {
        ServerPlayer player = barked(helper);
        HeartOverlayTests.hurt(helper, player, player.damageSources().inFire(), FULL_FIRE);
        int bare = HeartOverlayTests.halves(player);
        helper.assertTrue(bare == 0, String.format(SHOULD_BURN_BARE, bare));
        SelfDeliveryTests.tickFor(helper, player, REGROW_WATCH);
        helper.runAfterDelay(REGROW_WATCH + 1, () -> {
            boolean held = player.getData(GooAttachments.HELD_EFFECTS).holds(LEAF_BARKSKIN);
            int regrown = HeartOverlayTests.halves(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(held && regrown > 0, String.format(SHOULD_STAY_AND_REGROW, held, regrown));
            helper.succeed();
        });
    }

    private static Zombie unarmored(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
        // the zombie's natural armor would cut the thorns; the test reads the thorns whole
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0);
        return zombie;
    }

    private static ServerPlayer barked(GameTestHelper helper) {
        return HeartOverlayTests.crawledWhole(HeartOverlayTests.selfInvoked(helper, GooTypes.LEAF, LEAF_BARKSKIN));
    }
}
