package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for the heart overlay through Blaze Kindle invoked on a survival
 * mock player by the real self delivery: embers shield real health, water
 * quenches them, bare ash costs double and a striking mob burns (decisions
 * overlay-hearts-are-an-elemental-overshield and
 * kindle-ember-hearts-ash-and-retaliate).
 */
public final class HeartOverlayTests {

    private static final int NO_ENTITY = -1;
    private static final Identifier BLAZE_KINDLE = Identifier.parse("goo:blaze_kindle");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final BlockPos ATTACKER_POS = new BlockPos(2, 1, 3);
    private static final float FULL_HEALTH = 20f;
    private static final int FULL_EMBERS = 10;
    private static final float ONE_POINT = 1f;
    /** One point on bare ash, worth half a heart, costs two. */
    private static final float ASH_COST = 2f;
    private static final float TOLERANCE = 1e-4f;
    private static final String SHOULD_KINDLE = "Kindle should lay %d embers over a full bar, laid %d";
    private static final String SHOULD_SHIELD = "An ember should take the hit: health %.1f with %d embers";
    private static final String SHOULD_QUENCH = "Water should leave no ember standing, %d stand";
    private static final String SHOULD_COST_DOUBLE = "A point on bare ash should cost %.1f, cost %.1f";
    private static final int BROKEN_EMBERS = 3;
    private static final int BURN_SECONDS = 5;
    private static final float HEART = 2f;
    private static final String SHOULD_SPARE_EMBER_BAR =
            "Fire should not touch an all-ember bar and should go out: health %.1f, %d embers, fire ticks %d";
    private static final String SHOULD_RELIGHT =
            "Fire should cost one heart, relight the rest and go out: health %.1f, %d embers, fire ticks %d";
    private static final String SHOULD_COOL_DOWN =
            "Fire inside its cooldown should break an ember like any hit: health %.1f, %d embers";
    private static final String SHOULD_HEAL_LIT =
            "A heart healed while burning should return ember: health %.1f, %d embers";
    private static final String SHOULD_BURN = "The zombie should take %d fire and burn: health %.1f of %.1f, fire ticks %d";

    private HeartOverlayTests() {
    }

    /**
     * A kindled player takes a hit on an ember with health untouched, loses
     * every ember in water, and then pays double for a hit on bare ash.
     *
     * @param helper the gametest helper
     */
    public static void kindleShieldsThenQuenches(GameTestHelper helper) {
        ServerPlayer player = kindled(helper);
        int laid = embers(player);
        helper.assertTrue(laid == FULL_EMBERS, String.format(SHOULD_KINDLE, FULL_EMBERS, laid));

        hurt(helper, player, player.damageSources().generic(), ONE_POINT);
        float shielded = player.getHealth();
        int afterHit = embers(player);
        helper.assertTrue(shielded == FULL_HEALTH && afterHit == FULL_EMBERS - 1,
                String.format(SHOULD_SHIELD, shielded, afterHit));

        helper.setBlock(STAND_POS, Blocks.WATER);
        helper.succeedWhen(() -> {
            player.doTick();
            int standing = embers(player);
            helper.assertTrue(standing == 0, String.format(SHOULD_QUENCH, standing));
            hurt(helper, player, player.damageSources().generic(), ONE_POINT);
            float cost = FULL_HEALTH - player.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(Math.abs(cost - ASH_COST) < TOLERANCE, String.format(SHOULD_COST_DOUBLE, ASH_COST, cost));
        });
    }

    /**
     * A zombie striking a kindled player takes fire damage by the embers
     * standing when it struck, and burns.
     *
     * @param helper the gametest helper
     */
    public static void kindleBurnsTheAttacker(GameTestHelper helper) {
        ServerPlayer player = kindled(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ATTACKER_POS);
        float zombieMax = zombie.getMaxHealth();
        int embers = embers(player);
        // the zombie's natural armor takes its cut of the fire, as it would of any armored hit
        float expectedFire = CombatRules.getDamageAfterAbsorb(zombie, embers, player.damageSources().inFire(),
                zombie.getArmorValue(), (float) zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS));

        zombie.doHurtTarget(helper.getLevel(), player);

        float zombieHealth = zombie.getHealth();
        int fireTicks = zombie.getRemainingFireTicks();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(Math.abs(zombieMax - expectedFire - zombieHealth) < TOLERANCE && fireTicks > 0,
                String.format(SHOULD_BURN, embers, zombieHealth, zombieMax, fireTicks));
        helper.succeed();
    }

    /**
     * Fire leaves an all-ember bar alone and goes out; with ash standing it
     * relights every heart left at the price of one heart and goes out; inside
     * its cooldown it breaks an ember like any hit; and a heart healed while
     * burning returns as ember.
     *
     * @param helper the gametest helper
     */
    public static void kindleFireRelightsForAHeart(GameTestHelper helper) {
        ServerPlayer player = kindled(helper);
        player.igniteForSeconds(BURN_SECONDS);
        hurt(helper, player, player.damageSources().inFire(), ONE_POINT);
        Reading spared = Reading.of(player);

        for (int hit = 0; hit < BROKEN_EMBERS; hit++) {
            hurt(helper, player, player.damageSources().generic(), ONE_POINT);
        }
        player.igniteForSeconds(BURN_SECONDS);
        hurt(helper, player, player.damageSources().inFire(), ONE_POINT);
        Reading relit = Reading.of(player);

        hurt(helper, player, player.damageSources().generic(), ONE_POINT);
        hurt(helper, player, player.damageSources().inFire(), ONE_POINT);
        Reading cooling = Reading.of(player);

        player.igniteForSeconds(BURN_SECONDS);
        player.heal(HEART);
        Reading healed = Reading.of(player);
        helper.getLevel().getServer().getPlayerList().remove(player);

        helper.assertTrue(spared.health() == FULL_HEALTH && spared.embers() == FULL_EMBERS && spared.fireTicks() <= 0,
                String.format(SHOULD_SPARE_EMBER_BAR, spared.health(), spared.embers(), spared.fireTicks()));
        helper.assertTrue(relit.health() == FULL_HEALTH - HEART && relit.embers() == FULL_EMBERS - 1
                        && relit.fireTicks() <= 0,
                String.format(SHOULD_RELIGHT, relit.health(), relit.embers(), relit.fireTicks()));
        helper.assertTrue(cooling.health() == FULL_HEALTH - HEART && cooling.embers() == FULL_EMBERS - 3,
                String.format(SHOULD_COOL_DOWN, cooling.health(), cooling.embers()));
        helper.assertTrue(healed.health() == FULL_HEALTH && healed.embers() == FULL_EMBERS - 2,
                String.format(SHOULD_HEAL_LIT, healed.health(), healed.embers()));
        helper.succeed();
    }

    /**
     * What a step of a gametest reads off the player.
     *
     * @param health    the player's health
     * @param embers    the embers standing
     * @param fireTicks the player's remaining fire ticks
     */
    private record Reading(float health, int embers, int fireTicks) {
        static Reading of(ServerPlayer player) {
            return new Reading(player.getHealth(), HeartOverlayTests.embers(player), player.getRemainingFireTicks());
        }
    }

    private static ServerPlayer kindled(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.BLAZE);
        // the mock player helper makes a creative player, whom no hit lands on
        player.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded is invulnerable, and no mock client reports
        player.connection.markClientLoaded();
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.BLAZE), NO_ENTITY,
                player.blockPosition(), NO_ENTITY, false, BLAZE_KINDLE.toString(), player.getEyePosition()));
        return player;
    }

    private static void hurt(GameTestHelper helper, ServerPlayer player, DamageSource source, float amount) {
        player.invulnerableTime = 0;
        player.hurtServer(helper.getLevel(), source, amount);
    }

    private static int embers(ServerPlayer player) {
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        return overlay.emberCount();
    }
}
