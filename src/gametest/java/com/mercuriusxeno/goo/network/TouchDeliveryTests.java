package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for the touch: a glove use with metal javelin, a mob ability,
 * lands at once on a mob within the player's entity interaction range and
 * throws a flight at one beyond it, through the real throw path
 * (decision mob-ability-touches-at-reach). They sit in the scheduler's
 * package to read its pending effects. The mob is a husk, which takes no
 * daylight burn that would move its health between throw and arrival.
 */
public final class TouchDeliveryTests {

    private static final BlockPos NEAR_MOB_POS = new BlockPos(3, 1, 3);
    /** Two blocks west of the near mob, inside a player's reach of three. */
    private static final BlockPos NEAR_PLAYER_POS = NEAR_MOB_POS.west(2);
    private static final BlockPos FAR_MOB_POS = new BlockPos(11, 1, 8);
    /** Eight blocks west of the far mob, beyond a player's reach of three. */
    private static final BlockPos FAR_PLAYER_POS = FAR_MOB_POS.west(8);
    private static final int SETTLE_TICKS = 1;
    private static final int NO_ENTITY = -1;
    private static final int HELD_GOO = 4;
    private static final Identifier METAL_JAVELIN = Identifier.parse("goo:metal_javelin");
    /** The damage metal_javelin.json's damage step names. */
    private static final float JAVELIN_DAMAGE = 8.0f;
    private static final float DAMAGE_TOLERANCE = 0.01f;
    private static final String ABILITY_REQUIRED = "Ability registry must hold metal_javelin";
    private static final String SHOULD_TAKE_DAMAGE = "Husk should lose %.1f health the tick it is touched, lost %.1f";
    private static final String SHOULD_DRAIN_COST = "A touch should drain its cost of %d mB, drained %d";
    private static final String SHOULD_SCHEDULE_NOTHING = "A touch should schedule no flight";
    private static final String SHOULD_SCHEDULE_FLIGHT = "A throw beyond reach should schedule one flight, scheduled %d";
    private static final String SHOULD_WAIT_FOR_FLIGHT = "Husk beyond reach should keep its health until the flight arrives";
    private static final String SHOULD_TAKE_DAMAGE_ON_ARRIVAL = "Husk beyond reach should lose %.1f health on arrival";

    private TouchDeliveryTests() {
    }

    /**
     * A mock player uses metal javelin on a husk two blocks ahead: the husk
     * loses the javelin's damage and the player the javelin's cost in that
     * tick, and no flight is scheduled.
     *
     * @param helper the gametest helper
     */
    public static void javelinTouchesWithinReach(GameTestHelper helper) {
        AbilityDefinition javelin = javelin(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, NEAR_MOB_POS);
        ServerPlayer player = thrower(helper, NEAR_PLAYER_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float healthBefore = husk.getHealth();
            int heldBefore = metalHeld(player);
            GooEffectScheduler effects = GooServerState.of(helper.getLevel().getServer()).gooEffects();
            int pendingBefore = effects.pendingCount();
            GooThrowHandler.execute(player, javelinPayload(player, husk));
            float lost = healthBefore - husk.getHealth();
            int drained = heldBefore - metalHeld(player);
            // Gametests share the server's scheduler, so the touch is read by what it added.
            int scheduled = effects.pendingCount() - pendingBefore;
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(Math.abs(lost - JAVELIN_DAMAGE) < DAMAGE_TOLERANCE,
                    String.format(SHOULD_TAKE_DAMAGE, JAVELIN_DAMAGE, lost));
            helper.assertTrue(drained == javelin.throwCost(0),
                    String.format(SHOULD_DRAIN_COST, javelin.throwCost(0), drained));
            helper.assertTrue(scheduled == 0, SHOULD_SCHEDULE_NOTHING);
            helper.succeed();
        });
    }

    /**
     * A mock player uses metal javelin on a husk eight blocks ahead: one
     * flight is scheduled and the husk keeps its health, then loses the
     * javelin's damage once the flight arrives.
     *
     * @param helper the gametest helper
     */
    public static void javelinThrowsBeyondReach(GameTestHelper helper) {
        javelin(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR_MOB_POS);
        ServerPlayer player = thrower(helper, FAR_PLAYER_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float healthBefore = husk.getHealth();
            GooEffectScheduler effects = GooServerState.of(helper.getLevel().getServer()).gooEffects();
            int pendingBefore = effects.pendingCount();
            GooThrowHandler.execute(player, javelinPayload(player, husk));
            int scheduled = effects.pendingCount() - pendingBefore;
            helper.assertTrue(scheduled == 1, String.format(SHOULD_SCHEDULE_FLIGHT, scheduled));
            helper.assertTrue(husk.getHealth() == healthBefore, SHOULD_WAIT_FOR_FLIGHT);
            helper.succeedWhen(() -> {
                helper.assertTrue(Math.abs(healthBefore - husk.getHealth() - JAVELIN_DAMAGE) < DAMAGE_TOLERANCE,
                        String.format(SHOULD_TAKE_DAMAGE_ON_ARRIVAL, JAVELIN_DAMAGE));
                // The thrower stays listed until arrival, since the arriving effect names it.
                helper.getLevel().getServer().getPlayerList().remove(player);
            });
        });
    }

    private static AbilityDefinition javelin(GameTestHelper helper) {
        AbilityDefinition javelin = AbilityRegistry.of(helper.getLevel()).getAbility(METAL_JAVELIN);
        helper.assertTrue(javelin != null, ABILITY_REQUIRED);
        return javelin;
    }

    private static GooThrowPayload javelinPayload(ServerPlayer player, Mob target) {
        return new GooThrowPayload(GooTypes.id(GooTypes.METAL), target.getId(), BlockPos.ZERO, NO_ENTITY, false,
                METAL_JAVELIN.toString(), player.getEyePosition());
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
