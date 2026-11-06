package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.entity.SwapBody;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for Quantum's swap: the first cast blinks the player to the spot
 * they aim at and leaves a body where they stood; cast again from elsewhere,
 * the player is back in that body, at its spot and with the health it held,
 * and a fresh body stands where they were
 * (decisions swap-body-stands-where-you-aim, swap-body-stands-until-reverted).
 */
@RegistersGameTests
public final class SwapBodyTests {
    private static final String SWAP_BODY_ROUND_TRIP = "swap_body_round_trip";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SWAP_BODY_ROUND_TRIP, SwapBodyTests::swapBodyRoundTrip);
        GooTestFunctions.registerTest(registrar, SWAP_BLINKS_TO_THE_AIM, SwapBodyTests::swapBlinksToTheAim);
    }

    private static final String SWAP_BLINKS_TO_THE_AIM = "swap_blinks_to_the_aim";
    private static final Identifier SWAP = Identifier.parse("goo:quantum_swap");
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(4, 1, 1);
    /** The z, relative, of the wall the caster faces south toward. */
    private static final int WALL_Z = 4;
    /** The wall's reach across and up, relative, from x 0 and y 1. */
    private static final int WALL_SPAN = 4;
    /** The yaw facing south, toward the wall. */
    private static final float FACING_SOUTH = 0.0F;
    /** Where the caster's feet stand, relative, blinked against the wall's north face: half a body short of it. */
    private static final Vec3 AIMED = new Vec3(1.5, 1, WALL_Z - 0.3);
    private static final float FIRST_HEALTH = 10.0F;
    private static final float SECOND_HEALTH = 20.0F;
    /** How near a spot a body or player stands to count as there. */
    private static final double THERE = 0.5;

    private SwapBodyTests() {
    }

    /**
     * The first cast, facing a wall across the bay, blinks the caster to the
     * spot their look reaches in sight and leaves the body where they stood,
     * never inside them (decision swap-body-stands-where-you-aim).
     *
     * @param helper the gametest helper
     */
    public static void swapBlinksToTheAim(GameTestHelper helper) {
        ServerPlayer player = facingTheWall(helper);
        Vec3 from = standAt(helper, player, FIRST);
        SelfDeliveryTests.invoke(player, GooTypes.QUANTUM, SWAP);
        Vec3 aimed = helper.absoluteVec(AIMED);
        helper.assertTrue(player.position().distanceTo(aimed) < THERE,
                "The cast should blink the caster to the aimed spot " + aimed + ", stands at " + player.position());
        helper.assertTrue(bodiesAt(helper, from).size() == 1, "The cast should leave the body where the caster stood");
        helper.assertTrue(bodiesAt(helper, player.position()).isEmpty(), "No body should stand inside the caster");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Casting twice from two places returns the player to the first, with the
     * first place's health, and leaves a body at the second.
     *
     * @param helper the gametest helper
     */
    public static void swapBodyRoundTrip(GameTestHelper helper) {
        ServerPlayer player = facingTheWall(helper);
        Vec3 first = standAt(helper, player, FIRST);
        player.setHealth(FIRST_HEALTH);
        SelfDeliveryTests.invoke(player, GooTypes.QUANTUM, SWAP);
        helper.assertTrue(bodiesAt(helper, first).size() == 1, "The first cast should leave a body where the player stood");

        Vec3 second = standAt(helper, player, SECOND);
        player.setHealth(SECOND_HEALTH);
        SelfDeliveryTests.invoke(player, GooTypes.QUANTUM, SWAP);

        helper.assertTrue(player.position().distanceTo(first) < THERE,
                "The second cast should return the player to the first spot, stands at " + player.position());
        helper.assertTrue(player.getHealth() == FIRST_HEALTH,
                "The player should take the body's held health, has " + player.getHealth());
        helper.assertTrue(bodiesAt(helper, second).size() == 1, "A fresh body should stand where the player cast from");
        helper.assertTrue(bodiesAt(helper, first).isEmpty(), "The body the player swapped into should be gone");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A survival Quantum caster who knows swap, facing south toward a stone
     * wall built across the bay.
     */
    private static ServerPlayer facingTheWall(GameTestHelper helper) {
        for (int x = 0; x <= WALL_SPAN; x++) {
            for (int y = 1; y <= WALL_SPAN; y++) {
                helper.setBlock(new BlockPos(x, y, WALL_Z), Blocks.STONE);
            }
        }
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        KnownRecipes.teachRequires(player, AbilityRegistry.of(player.level()).getAbility(SWAP));
        player.setYRot(FACING_SOUTH);
        player.setXRot(0.0F);
        return player;
    }

    private static Vec3 standAt(GameTestHelper helper, ServerPlayer player, BlockPos cell) {
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(cell));
        player.teleportTo(feet.x, feet.y, feet.z);
        return feet;
    }

    private static List<SwapBody> bodiesAt(GameTestHelper helper, Vec3 feet) {
        return helper.getLevel().getEntitiesOfClass(SwapBody.class, new AABB(feet, feet).inflate(THERE),
                SwapBody::isAlive);
    }
}
