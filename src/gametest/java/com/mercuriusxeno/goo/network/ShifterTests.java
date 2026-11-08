package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for ender's Shifter held on the glove by a survival mock player:
 * a hit lands no damage and blinks the player along their look, and a fall
 * out of the world returns the player to the ground they last stood on.
 * shifter-blinks-along-the-cursor-on-hit
 */
@RegistersGameTests
public final class ShifterTests {
    private static final String SHIFTER_BLINKS = "shifter_blinks_instead_of_damage";
    private static final String SHIFTER_VOID = "shifter_void_returns_to_safe_ground";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SHIFTER_BLINKS, ShifterTests::shifterBlinksInsteadOfDamage);
        GooTestFunctions.registerTest(registrar, SHIFTER_VOID, ShifterTests::shifterVoidReturnsToSafeGround);
    }

    private static final Identifier ENDER_SHIFTER = Identifier.parse("goo:ender_shifter");
    /** A hit of two hearts. */
    private static final float HIT = 4f;
    /** Where the void test lifts the player before the fall, well off the floor it stood on. */
    private static final double FALLING_ABOVE = 30.0;
    private static final String SHOULD_TAKE_NO_DAMAGE = "A player under shifter should keep %.1f health, has %.1f";
    private static final String SHOULD_BLINK = "A hit should blink the player along their look from %s, stands at %s";
    private static final String SHOULD_RETURN_TO_GROUND =
            "A fall out of the world should return the player to %s, stands at %s";

    private ShifterTests() {
    }

    /**
     * A player holding Shifter takes a hit: the hit lands no damage and the
     * player stands somewhere else along their look.
     *
     * @param helper the gametest helper
     */
    public static void shifterBlinksInsteadOfDamage(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.ENDER, ENDER_SHIFTER);
        Vec3 stood = player.position();
        float health = player.getHealth();

        HeartOverlayTests.hurt(helper, player, player.damageSources().generic(), HIT);

        Vec3 after = player.position();
        float healthAfter = player.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(healthAfter == health, String.format(SHOULD_TAKE_NO_DAMAGE, health, healthAfter));
        helper.assertTrue(after.distanceTo(stood) > 0, String.format(SHOULD_BLINK, stood, after));
        helper.succeed();
    }

    /**
     * A player holding Shifter who stood on a stone block falls out of the
     * world: the hit lands no damage and the player stands on that stone again.
     *
     * @param helper the gametest helper
     */
    public static void shifterVoidReturnsToSafeGround(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.ENDER, ENDER_SHIFTER);
        Vec3 ground = player.position();
        helper.getLevel().setBlockAndUpdate(BlockPos.containing(ground).below(), Blocks.STONE.defaultBlockState());
        player.doTick();
        player.setPos(ground.add(0, FALLING_ABOVE, 0));
        float health = player.getHealth();

        HeartOverlayTests.hurt(helper, player, player.damageSources().fellOutOfWorld(), HIT);

        Vec3 after = player.position();
        float healthAfter = player.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(healthAfter == health, String.format(SHOULD_TAKE_NO_DAMAGE, health, healthAfter));
        helper.assertTrue(after.equals(ground), String.format(SHOULD_RETURN_TO_GROUND, ground, after));
        helper.succeed();
    }
}