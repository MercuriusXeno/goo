package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooTouchHandler.AttackPress;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gametests for the attack key: a press with a glove, a mob ability and an
 * entity within reach touches, and the touch and the vanilla melee hit both
 * land in full in one press, in either order, because a goo hit clears
 * damage immunity; every other press stays vanilla
 * (decision attack-key-touches-plus-punches). The target is a villager,
 * which wears no armor to shave the melee hit.
 */
public final class AttackTouchTests {

    private static final BlockPos MOB_POS = new BlockPos(3, 1, 3);
    /** Two blocks west of the mob, inside a player's reach of three. */
    private static final BlockPos PLAYER_POS = MOB_POS.west(2);
    /** Eight blocks west of the mob, beyond a player's reach of three. */
    private static final double BEYOND_REACH = 8;
    private static final int SETTLE_TICKS = 1;
    private static final int NO_ENTITY = -1;
    private static final int HELD_GOO = 4;
    /** An attack speed whose cooldown passes within the half tick vanilla's attack adds, for a full-strength first swing. */
    private static final double INSTANT_ATTACK_SPEED = 1024;
    private static final Identifier METAL_JAVELIN = Identifier.parse("goo:metal_javelin");
    private static final Identifier FROST_TUNNEL = Identifier.parse("goo:frost_tunnel");
    private static final Identifier ENDER_BLINK = Identifier.parse("goo:ender_blink");
    private static final Identifier BLAZE_SPITFIRE = Identifier.parse("goo:blaze_spitfire");
    /** The damage metal_javelin.json's damage step names. */
    private static final float JAVELIN_DAMAGE = 8.0f;
    private static final float DAMAGE_TOLERANCE = 0.01f;
    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    private static final String SHOULD_TOUCH = "A glove press with metal javelin on a mob within reach should touch";
    private static final String SHOULD_SUM = "Villager should lose the javelin's %.1f plus the melee %.1f, lost %.1f";
    private static final String SHOULD_STAY_VANILLA = "An attack-key press %s should stay vanilla";

    private AttackTouchTests() {
    }

    /**
     * The press touches, then the vanilla melee hit lands in the same tick,
     * and the villager loses both in full.
     *
     * @param helper the gametest helper
     */
    public static void touchThenMeleeLandInFull(GameTestHelper helper) {
        pressOnVillager(helper, true);
    }

    /**
     * The vanilla melee hit lands first and the touch after it in the same
     * tick, and the villager still loses both in full.
     *
     * @param helper the gametest helper
     */
    public static void meleeThenTouchLandInFull(GameTestHelper helper) {
        pressOnVillager(helper, false);
    }

    private static void pressOnVillager(GameTestHelper helper, boolean touchFirst) {
        AbilityDefinition javelin = ability(helper, METAL_JAVELIN);
        Mob villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, MOB_POS);
        ServerPlayer player = attacker(helper, new ItemStack(GooItems.GOO_GLOVE.get()));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(GooTouchHandler.attackTouches(press(player, javelin, villager, player.position())),
                    SHOULD_TOUCH);
            float healthBefore = villager.getHealth();
            float melee = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (touchFirst) {
                touch(player, villager);
                player.attack(villager);
            } else {
                player.attack(villager);
                touch(player, villager);
            }
            float lost = healthBefore - villager.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(Math.abs(lost - JAVELIN_DAMAGE - melee) < DAMAGE_TOLERANCE,
                    String.format(SHOULD_SUM, JAVELIN_DAMAGE, melee, lost));
            helper.succeed();
        });
    }

    /**
     * An attack-key press stays vanilla on a mob beyond reach, on a block,
     * with a world, self or channeled ability selected, and with no glove in
     * the pressing hand.
     *
     * @param helper the gametest helper
     */
    public static void attackStaysVanilla(GameTestHelper helper) {
        AbilityDefinition javelin = ability(helper, METAL_JAVELIN);
        Mob villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, MOB_POS);
        ServerPlayer gloved = attacker(helper, new ItemStack(GooItems.GOO_GLOVE.get()));
        ServerPlayer bare = attacker(helper, new ItemStack(Items.STICK));
        Vec3 within = gloved.position();
        Vec3 beyond = villager.position().add(-BEYOND_REACH, 0, 0);
        assertVanilla(helper, press(gloved, javelin, villager, beyond), "on a mob beyond reach");
        assertVanilla(helper, press(gloved, javelin, null, within), "on a block");
        assertVanilla(helper, press(gloved, ability(helper, FROST_TUNNEL), villager, within), "with a world ability");
        assertVanilla(helper, press(gloved, ability(helper, ENDER_BLINK), villager, within), "with a self ability");
        assertVanilla(helper, press(gloved, ability(helper, BLAZE_SPITFIRE), villager, within),
                "with a channeled ability");
        assertVanilla(helper, press(bare, javelin, villager, within), "with no glove in the pressing hand");
        helper.getLevel().getServer().getPlayerList().remove(gloved);
        helper.getLevel().getServer().getPlayerList().remove(bare);
        helper.succeed();
    }

    private static void assertVanilla(GameTestHelper helper, AttackPress press, String condition) {
        helper.assertFalse(GooTouchHandler.attackTouches(press), String.format(SHOULD_STAY_VANILLA, condition));
    }

    private static AttackPress press(ServerPlayer player, AbilityDefinition selected, @Nullable Entity target,
                                     Vec3 playerPosition) {
        return new AttackPress(player.getMainHandItem().getItem() instanceof GooGloveItem, selected.delivery(),
                selected.badge(), target, playerPosition, player.entityInteractionRange());
    }

    private static void touch(ServerPlayer player, Mob target) {
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.METAL), target.getId(),
                BlockPos.ZERO, NO_ENTITY, false, METAL_JAVELIN.toString(), player.getEyePosition()));
    }

    private static AbilityDefinition ability(GameTestHelper helper, Identifier id) {
        AbilityDefinition definition = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(definition != null, String.format(ABILITY_REQUIRED, id));
        return definition;
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer attacker(GameTestHelper helper, ItemStack held) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(PLAYER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(INSTANT_ATTACK_SPEED);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.METAL, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
