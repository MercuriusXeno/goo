package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.util.Collections;
import java.util.List;

/**
 * Gametests for the heart overlay through Blaze Kindle invoked on a survival
 * mock player by the real self delivery: embers shield real health, water
 * quenches them, bare ash costs double and a striking mob burns (decisions
 * overlay-hearts-are-an-elemental-overshield and
 * kindle-ember-hearts-ash-and-retaliate).
 */
public final class HeartOverlayTests {

    private static final Identifier BLAZE_KINDLE = Identifier.parse("goo:blaze_kindle");
    private static final Identifier LEAF_BARKSKIN = Identifier.parse("goo:leaf_barkskin");
    /** Two thousand mB of the second goo, enough for one cast. */
    private static final int SECOND_GOO = 2 * GooStacks.THOUSAND;
    private static final String SHOULD_REPLACE = "%s should stand alone, primed with one heart: stood %s with %d halves";
    private static final String SHOULD_HOLD_BARKSKIN_ALONE = "Barkskin alone should be held, held reads %s";
    private static final String SHOULD_STOP_KINDLE_UPKEEP =
            "Kindle's upkeep should stop, drained %d blaze; Barkskin's should drain %d leaf, drained %d";
    /** The ticks the replaced upkeep is watched over. */
    private static final int WATCHED_TICKS = 5;
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final BlockPos ATTACKER_POS = new BlockPos(2, 1, 3);
    private static final float FULL_HEALTH = 20f;
    private static final int FULL_HALVES = 20;
    private static final float ONE_POINT = 1f;
    /** One point on bare ash, worth half a heart, costs two. */
    private static final float ASH_COST = 2f;
    private static final float TOLERANCE = 1e-4f;
    private static final String SHOULD_KINDLE = "Kindle should lay %d ember halves over a full bar, laid %d";
    private static final String SHOULD_SHIELD = "An ember should take the hit: health %.1f with %d ember halves";
    private static final String SHOULD_QUENCH = "Water should leave no ember standing, %d stand";
    private static final String SHOULD_COST_DOUBLE = "A point on bare ash should cost %.1f, cost %.1f";
    private static final int BROKEN_EMBERS = 3;
    private static final int BURN_SECONDS = 5;
    private static final float HEART = 2f;
    private static final String SHOULD_SPARE_EMBER_BAR =
            "Fire should not touch an all-ember bar and should go out: health %.1f, %d ember halves, fire ticks %d";
    private static final String SHOULD_RELIGHT =
            "Fire should cost one heart, relight the rest and go out: health %.1f, %d ember halves, fire ticks %d";
    private static final String SHOULD_COOL_DOWN =
            "Fire inside its cooldown should break an ember like any hit: health %.1f, %d ember halves";
    private static final String SHOULD_HEAL_LIT =
            "A heart healed while burning should return ember: health %.1f, %d ember halves";
    private static final String SHOULD_BURN = "The zombie should take %.1f fire and burn: health %.1f of %.1f, fire ticks %d";

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
        int laid = halves(player);
        helper.assertTrue(laid == FULL_HALVES, String.format(SHOULD_KINDLE, FULL_HALVES, laid));

        hurt(helper, player, player.damageSources().generic(), ONE_POINT);
        float shielded = player.getHealth();
        int afterHit = halves(player);
        helper.assertTrue(shielded == FULL_HEALTH && afterHit == FULL_HALVES - 1,
                String.format(SHOULD_SHIELD, shielded, afterHit));

        helper.setBlock(STAND_POS, Blocks.WATER);
        helper.succeedWhen(() -> {
            player.doTick();
            int standing = halves(player);
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
        float embers = halves(player) / (float) HeartOverlay.FULL_SHIELD;
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

        helper.assertTrue(spared.health() == FULL_HEALTH && spared.embers() == FULL_HALVES && spared.fireTicks() <= 0,
                String.format(SHOULD_SPARE_EMBER_BAR, spared.health(), spared.embers(), spared.fireTicks()));
        helper.assertTrue(relit.health() == FULL_HEALTH - HEART && relit.embers() == FULL_HALVES - 2
                        && relit.fireTicks() <= 0,
                String.format(SHOULD_RELIGHT, relit.health(), relit.embers(), relit.fireTicks()));
        helper.assertTrue(cooling.health() == FULL_HEALTH - HEART && cooling.embers() == FULL_HALVES - 4,
                String.format(SHOULD_COOL_DOWN, cooling.health(), cooling.embers()));
        helper.assertTrue(healed.health() == FULL_HEALTH && healed.embers() == FULL_HALVES - 2,
                String.format(SHOULD_HEAL_LIT, healed.health(), healed.embers()));
        helper.succeed();
    }

    /**
     * What a step of a gametest reads off the player.
     *
     * @param health    the player's health
     * @param embers    the ember halves standing
     * @param fireTicks the player's remaining fire ticks
     */
    private record Reading(float health, int embers, int fireTicks) {
        static Reading of(ServerPlayer player) {
            return new Reading(player.getHealth(), HeartOverlayTests.halves(player), player.getRemainingFireTicks());
        }
    }

    private static ServerPlayer kindled(GameTestHelper helper) {
        return crawledWhole(selfInvoked(helper, GooTypes.BLAZE, BLAZE_KINDLE));
    }

    /**
     * Carries a primed heart overlay to the whole bar its crawl reaches, a
     * full shield over every present heart, so a test of hits and fire reads
     * a full bar without ticking through the crawl
     * (decision heart-effects-crawl-while-held).
     *
     * @param player the player whose overlay primed
     * @return the player
     */
    static ServerPlayer crawledWhole(ServerPlayer player) {
        HeartOverlay primed = player.getData(GooAttachments.HEART_OVERLAY);
        List<Integer> whole = Collections.nCopies(HeartOverlay.filledSlots(player.getHealth()), HeartOverlay.FULL_SHIELD);
        player.setData(GooAttachments.HEART_OVERLAY, new HeartOverlay(primed.kind(), whole, primed.expiresAt(),
                primed.regrowAt(), primed.fireReadyAt(), primed.damageTaken(), primed.drainCarry()));
        return player;
    }

    /**
     * A survival mock player that has invoked a self ability through the real
     * self delivery.
     *
     * @param helper  the gametest helper
     * @param gooType the goo type the player holds and spends
     * @param ability the self ability's id
     * @return the player
     */
    public static ServerPlayer selfInvoked(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType,
                                           Identifier ability) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, gooType);
        // the mock player helper makes a creative player, whom no hit lands on
        player.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded is invulnerable, and no mock client reports
        player.connection.markClientLoaded();
        invoke(player, gooType, ability);
        return player;
    }

    /**
     * Invokes a heart brew the way the glove does and eats it through, so the
     * hearts stand when the call returns (decision
     * self-brew-goos-eat-before-the-effect).
     *
     * @param player  the invoking player
     * @param gooType the brew's goo type
     * @param ability the brew's id
     */
    private static void invoke(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, Identifier ability) {
        KnownRecipes.teachRequires(player, AbilityRegistry.of(player.level()).getAbility(ability));
        SelfDeliveryTests.invoke(player, gooType, ability);
        SelfDeliveryTests.eatThrough(player);
    }

    /**
     * Barkskin over a whole Kindle bar leaves only Barkskin standing, primed
     * with one bark heart, and only Barkskin held: Kindle ends the moment
     * Barkskin takes effect, and its upkeep stops with it while Barkskin's is
     * paid.
     * one-heart-overlay-at-a-time
     * self-effects-trickle-until-ended
     *
     * @param helper the gametest helper
     */
    public static void heartBrewsReplaceEachOther(GameTestHelper helper) {
        ServerPlayer player = kindled(helper);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.LEAF, SECOND_GOO));
        hurt(helper, player, player.damageSources().generic(), ONE_POINT);

        invoke(player, GooTypes.LEAF, LEAF_BARKSKIN);
        HeartOverlay barked = player.getData(GooAttachments.HEART_OVERLAY);
        HeldEffects held = player.getData(GooAttachments.HELD_EFFECTS);
        int blazeBefore = goo(player, GooTypes.BLAZE);
        int leafBefore = goo(player, GooTypes.LEAF);
        helper.assertTrue(barked.kind() == HeartKind.BARKSKIN && barked.shieldHalves() == HeartOverlay.FULL_SHIELD
                        && barked.shieldAt(0) == HeartOverlay.FULL_SHIELD,
                String.format(SHOULD_REPLACE, HeartKind.BARKSKIN, barked.kind(), barked.shieldHalves()));
        helper.assertTrue(held.held().size() == 1 && held.holds(LEAF_BARKSKIN),
                String.format(SHOULD_HOLD_BARKSKIN_ALONE, held));
        SelfDeliveryTests.tickFor(helper, player, WATCHED_TICKS);
        helper.runAfterDelay(WATCHED_TICKS + 1, () -> {
            int blazeDrained = blazeBefore - goo(player, GooTypes.BLAZE);
            int leafDrained = leafBefore - goo(player, GooTypes.LEAF);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(blazeDrained == 0 && leafDrained == WATCHED_TICKS,
                    String.format(SHOULD_STOP_KINDLE_UPKEEP, blazeDrained, WATCHED_TICKS, leafDrained));
            helper.succeed();
        });
    }

    private static int goo(ServerPlayer player, ResourceKey<GooTypeDefinition> type) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(type, 0);
    }

    /**
     * Hurts a player past its hurt cooldown, so back-to-back hits each land.
     *
     * @param helper the gametest helper
     * @param player the player
     * @param source the damage source
     * @param amount the damage
     */
    static void hurt(GameTestHelper helper, ServerPlayer player, DamageSource source, float amount) {
        player.invulnerableTime = 0;
        player.hurtServer(helper.getLevel(), source, amount);
    }

    /**
     * Counts the shield halves standing on a player's heart overlay.
     *
     * @param player the player
     * @return the shield halves
     */
    static int halves(ServerPlayer player) {
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        return overlay.shieldHalves();
    }
}
