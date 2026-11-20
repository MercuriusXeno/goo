package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for metal Dart's javelin, a mob ability thrown as a projectile:
 * it strikes a husk in its path at any distance, inside the old touch reach
 * or beyond it, through the real throw path (decisions
 * knives-and-dart-function-as-projectiles, dart-does-not-seek-or-highlight).
 * The mob is a husk, which takes no daylight burn that would move its health
 * between throw and arrival.
 */
@RegistersGameTests
public final class TouchDeliveryTests {
    private static final String MOB_METAL_TOUCH = "mob_metal_dart_touch";
    private static final String MOB_METAL_THROW_BEYOND_REACH = "mob_metal_dart_beyond_reach";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MOB_METAL_TOUCH, TouchDeliveryTests::dartTouchesWithinReach);
        GooTestFunctions.registerTest(registrar, MOB_METAL_THROW_BEYOND_REACH, TouchDeliveryTests::dartThrowsBeyondReach);
    }


    private static final BlockPos NEAR_MOB_POS = new BlockPos(3, 1, 3);
    /** Two blocks west of the near mob, inside a player's reach of three. */
    private static final BlockPos NEAR_PLAYER_POS = NEAR_MOB_POS.west(2);
    private static final BlockPos FAR_MOB_POS = new BlockPos(11, 1, 8);
    /** Eight blocks west of the far mob, beyond a player's reach of three. */
    private static final BlockPos FAR_PLAYER_POS = FAR_MOB_POS.west(8);
    /** The ticks Dart's hold takes to form its first javelin, which a throw takes. */
    private static final int ONE_JAVELIN_TICKS = 10;
    /** A javelin's speed as it leaves, in blocks a tick (metal_dart.json). */
    private static final double JAVELIN_SPEED = 2.5;
    private static final int HELD_GOO = 4;
    private static final Identifier METAL_DART = Identifier.parse("goo:metal_dart");
    private static final String ABILITY_REQUIRED = "Ability registry must hold metal_dart";
    /** Dart's damage, the husk's armor taking its share (metal_dart.json). */
    private static final float DART_DAMAGE = 8f;
    /** How far apart two float healths may read and still be one, the subtraction's rounding. */
    private static final float HEALTH_TOLERANCE = 1e-4f;
    /** Dart's cost in mB (metal_dart.json). */
    private static final int DART_COST = 1000;
    private static final String SHOULD_TAKE_DAMAGE = "Husk should lose 8 health past its armor the tick it is touched, lost %.3f";
    private static final String SHOULD_BE_THE_THROWERS_ATTACK = "Dart's hit should be the thrower's attack";
    private static final String SHOULD_DRAIN_COST_ONCE = "A throw should drain 1000 mB of metal goo once, drained %d mB";

    private TouchDeliveryTests() {
    }

    /**
     * A mock player throws metal Dart's javelin at a husk two blocks ahead,
     * inside the old touch reach: the javelin flies to it, and the husk loses
     * Dart's damage past its armor to the thrower's attack, the throw draining
     * Dart's cost once.
     * decision knives-and-dart-function-as-projectiles
     *
     * @param helper the gametest helper
     */
    public static void dartTouchesWithinReach(GameTestHelper helper) {
        dartStrikesAlongItsFlight(helper, NEAR_MOB_POS, NEAR_PLAYER_POS);
    }

    /**
     * A mock player throws metal Dart's javelin at a husk eight blocks ahead,
     * beyond the old touch reach: the javelin flies to it, and the husk loses
     * Dart's damage past its armor to the thrower's attack, the throw draining
     * Dart's cost once.
     * decision knives-and-dart-function-as-projectiles
     *
     * @param helper the gametest helper
     */
    public static void dartThrowsBeyondReach(GameTestHelper helper) {
        dartStrikesAlongItsFlight(helper, FAR_MOB_POS, FAR_PLAYER_POS);
    }

    // A javelin thrown at a husk in its path strikes it with Dart's damage, at whatever distance.
    private static void dartStrikesAlongItsFlight(GameTestHelper helper, BlockPos mobPos, BlockPos playerPos) {
        dart(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, mobPos);
        ServerPlayer player = thrower(helper, playerPos);
        formAJavelin(player);
        helper.runAfterDelay(ONE_JAVELIN_TICKS, () -> {
            float healthBefore = husk.getHealth();
            int heldBefore = metalHeld(player);
            ThrowAim.aimAt(player, husk.getBoundingBox().getCenter(), 1, JAVELIN_SPEED);
            KnivesThrowHandler.leftClick(player, true);
            KnivesThrowHandler.leftClick(player, false);
            int drained = heldBefore - metalHeld(player);
            helper.assertTrue(drained == DART_COST, String.format(SHOULD_DRAIN_COST_ONCE, drained));
            helper.succeedWhen(() -> {
                helper.assertTrue(struckByTheThrower(husk, player), SHOULD_BE_THE_THROWERS_ATTACK);
                float lost = healthBefore - husk.getHealth();
                helper.assertTrue(Math.abs(lost - dartDamagePastArmor(husk)) < HEALTH_TOLERANCE,
                        String.format(SHOULD_TAKE_DAMAGE, lost));
                helper.getLevel().getServer().getPlayerList().remove(player);
            });
        });
    }
    private static AbilityDefinition dart(GameTestHelper helper) {
        AbilityDefinition dart = AbilityRegistry.of(helper.getLevel()).getAbility(METAL_DART);
        helper.assertTrue(dart != null, ABILITY_REQUIRED);
        return dart;
    }

    // Begins a Dart hold, which forms the javelin a throw takes (dart-stacks-three-javelins-one-per-ten-ticks).
    private static void formAJavelin(ServerPlayer player) {
        KnivesHoldHandler.hold(player, new KnivesHoldPayload(true, GooTypes.id(GooTypes.METAL), METAL_DART.toString()));
    }

    /**
     * Whether the husk's last hit was the thrower's own attack, which Dart
     * deals (operator ruling 2026-11-16: Dart deals attack damage, not magic).
     *
     * @param husk   the struck husk
     * @param player the thrower
     * @return true where the thrower's attack struck last
     */
    private static boolean struckByTheThrower(Mob husk, ServerPlayer player) {
        DamageSource last = husk.getLastDamageSource();
        return last != null && last.getEntity() == player && last.is(DamageTypes.PLAYER_ATTACK);
    }

    /**
     * What Dart's 8 leaves past the husk's armor, by vanilla's armor rule.
     *
     * @param husk the struck husk, its last hit Dart's
     * @return the health the hit takes
     */
    private static float dartDamagePastArmor(Mob husk) {
        return CombatRules.getDamageAfterAbsorb(husk, DART_DAMAGE, husk.getLastDamageSource(), husk.getArmorValue(),
                (float) husk.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    }

    private static int metalHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.METAL, 0);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer thrower(GameTestHelper helper, BlockPos standPos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(standPos));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.METAL, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
