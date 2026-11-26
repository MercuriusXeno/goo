package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Moonrock: a player holding it in either hand falls under one sixth of the overworld's gravity, and
 * under the whole of it once the stone leaves the hand or rides unheld in the inventory; in lunar, holding it leaves
 * lunar's own sixth as it was.
 * decision lunar-stone-carries-one-sixth-gravity-while-held
 */
@RegistersGameTests
public final class MoonrockTests {
    private static final String HELD_MOONROCK_SIXTHS_ITS_HOLDERS_GRAVITY = "held_moonrock_sixths_its_holders_gravity";

    private static final double ONE_SIXTH = 1.0 / 6.0;
    private static final double SAME = 1e-9;
    /** A hotbar slot past the selected one, where an unheld stone rides. */
    private static final int UNHELD_SLOT = 5;
    private static final Vec3 HIGH_OVER_LUNAR = new Vec3(0.5, 150, 0.5);

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String WRONG_GRAVITY = "A player with Moonrock %s should fall under %s of its gravity,"
            + " read %s against %s";

    private MoonrockTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HELD_MOONROCK_SIXTHS_ITS_HOLDERS_GRAVITY,
                MoonrockTests::heldMoonrockSixthsItsHoldersGravity);
    }

    /**
     * A player in the overworld reads its gravity bare, then with Moonrock in the main hand, in the off hand, out of
     * both hands, and in an inventory slot it does not hold: a sixth for the two held reads and the whole for the
     * rest; then in lunar it reads lunar's sixth holding the stone as without it.
     * decision lunar-stone-carries-one-sixth-gravity-while-held
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void heldMoonrockSixthsItsHoldersGravity(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel lunar = overworld.getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        double full = player.getGravity();

        player.setItemInHand(InteractionHand.MAIN_HAND, moonrock());
        double mainHand = player.getGravity();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, moonrock());
        double offHand = player.getGravity();
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        double letGo = player.getGravity();
        player.getInventory().setItem(UNHELD_SLOT, moonrock());
        double unheld = player.getGravity();
        player.getInventory().setItem(UNHELD_SLOT, ItemStack.EMPTY);

        player.teleport(new TeleportTransition(lunar, HIGH_OVER_LUNAR, Vec3.ZERO, 0F, 0F,
                TeleportTransition.DO_NOTHING));
        double lunarBare = player.getGravity();
        player.setItemInHand(InteractionHand.MAIN_HAND, moonrock());
        double lunarHeld = player.getGravity();
        overworld.getServer().getPlayerList().remove(player);

        assertGravity(helper, "in the main hand", ONE_SIXTH, mainHand, full);
        assertGravity(helper, "in the off hand", ONE_SIXTH, offHand, full);
        assertGravity(helper, "just let go of", 1, letGo, full);
        assertGravity(helper, "unheld in the inventory", 1, unheld, full);
        assertGravity(helper, "held in lunar", ONE_SIXTH, lunarHeld, full);
        helper.assertTrue(Math.abs(lunarHeld - lunarBare) < SAME,
                String.format(WRONG_GRAVITY, "held in lunar", "lunar's own share", lunarHeld, lunarBare));
        helper.succeed();
    }

    private static ItemStack moonrock() {
        return new ItemStack(GooItems.MOONROCK.get());
    }

    private static void assertGravity(GameTestHelper helper, String how, double share, double read, double full) {
        helper.assertTrue(full > 0 && Math.abs(read - full * share) < SAME,
                String.format(WRONG_GRAVITY, how, share, read, full));
    }
}
