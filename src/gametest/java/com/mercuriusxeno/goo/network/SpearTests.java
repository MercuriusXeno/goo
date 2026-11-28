package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Osseous's Spear: a javelin the hold formed, thrown at a cow, flies as the barbed SPEAR form and
 * takes the cow's health.
 * decision spear-is-a-barbed-javelin-in-the-dart-family
 */
@RegistersGameTests
public final class SpearTests {
    private static final String SPEAR_STRIKES_A_COW = "spear_javelin_flies_barbed_and_strikes_a_cow";

    private static final BlockPos MOB_POS = new BlockPos(3, 1, 3);
    /** Two blocks west of the cow. */
    private static final BlockPos PLAYER_POS = MOB_POS.west(2);
    /** The ticks Spear's hold takes to form its first javelin, which a throw takes. */
    private static final int ONE_JAVELIN_TICKS = 10;
    /** Ticks past a throw by which the javelin has struck the cow. */
    private static final int FLIGHT_TICKS = 10;
    /** A javelin's speed as it leaves, in blocks a tick (osseous_spear.json). */
    private static final double JAVELIN_SPEED = 3.2;
    private static final int HELD_GOO = 4;
    private static final String OSSEOUS_SPEAR = "goo:osseous_spear";
    private static final String SHOULD_FLY_BARBED = "A thrown Spear javelin should fly as SPEAR, flies as %s";
    private static final String SHOULD_STRIKE = "A cow struck by a Spear javelin should lose health from %.1f,"
            + " stands at %.1f";

    private SpearTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPEAR_STRIKES_A_COW, SpearTests::spearFliesBarbedAndStrikesACow);
    }

    /**
     * A Spear javelin thrown at a cow flies as the SPEAR form and takes the cow's health.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void spearFliesBarbedAndStrikesACow(GameTestHelper helper) {
        Mob cow = helper.spawnWithNoFreeWill(EntityTypes.COW, MOB_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(PLAYER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.OSSEOUS, HELD_GOO * GooStacks.THOUSAND));
        KnivesHoldHandler.hold(player, new KnivesHoldPayload(true, GooTypes.id(GooTypes.OSSEOUS), OSSEOUS_SPEAR));
        helper.runAfterDelay(ONE_JAVELIN_TICKS, () -> {
            float before = cow.getHealth();
            ThrowAim.aimAt(player, cow.getBoundingBox().getCenter(), 1, JAVELIN_SPEED);
            KnivesThrowHandler.leftClick(player, true);
            KnivesThrowHandler.leftClick(player, false);
            List<TravelForm> flying = GooServerState.of(helper.getLevel().getServer()).thrownKnives()
                    .flyingFrom(player).stream().map(ThrownKnives.Flight::form).toList();
            helper.assertTrue(flying.equals(List.of(TravelForm.SPEAR)), String.format(SHOULD_FLY_BARBED, flying));
            helper.runAfterDelay(FLIGHT_TICKS, () -> {
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.assertTrue(cow.getHealth() < before, String.format(SHOULD_STRIKE, before, cow.getHealth()));
                helper.succeed();
            });
        });
    }
}
