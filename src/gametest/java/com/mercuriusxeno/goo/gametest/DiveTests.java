package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.network.HeartOverlayTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametest for Deep's dive held from the glove: a survival player standing
 * inside a block of stone swims a whole block through it and, after two
 * seconds buried, has lost no health to suffocation (decision
 * dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks).
 * The client moves a real player, so the test moves the mock player the way
 * the client would and ticks it as its connection would. The stone floats
 * above the test's own bay.
 */
@RegistersGameTests
public final class DiveTests {
    private static final String DIVE_SWIMS = "dive_swims_through_stone";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DIVE_SWIMS, DiveTests::diveSwimsThroughStone);
    }

    private static final Identifier DEEP_DIVE = Identifier.parse("goo:deep_dive");
    /** How far above the bay's floor the stone's middle floats. */
    private static final int LIFT = 12;
    /** The stone reaches this far from its middle cell on every axis. */
    private static final int STONE_HALF_WIDTH = 2;
    /** Ticks the player stays buried, long enough for suffocation to strike several times. */
    private static final int BURIED_TICKS = 40;
    /** One stroke of the swim, a whole block east, the way the client would move the player. */
    private static final Vec3 STROKE = new Vec3(1, 0, 0);
    private static final double SWUM_AT_LEAST = 0.99;
    private static final String SHOULD_SWIM = "The diver should swim a block east through the stone, moved %.2f";
    private static final String SHOULD_BREATHE = "The diver should take no suffocation buried in stone, health %.1f of %.1f";

    private DiveTests() {
    }

    /**
     * A survival player holding dive, set inside stone, ticks two seconds
     * there, then swims a block east: it moves the whole block and stands at
     * full health.
     *
     * @param helper the gametest helper
     */
    public static void diveSwimsThroughStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos middle = helper.absolutePos(new BlockPos(3, LIFT, 3));
        fillStone(level, middle, Blocks.STONE);
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.DEEP, DEEP_DIVE);
        player.teleportTo(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5);
        for (int tick = 0; tick < BURIED_TICKS; tick++) {
            player.doTick();
        }
        double before = player.getX();
        player.move(MoverType.SELF, STROKE);
        double swum = player.getX() - before;
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        level.getServer().getPlayerList().remove(player);
        fillStone(level, middle, Blocks.AIR);
        helper.assertTrue(swum >= SWUM_AT_LEAST, String.format(SHOULD_SWIM, swum));
        helper.assertTrue(health >= maxHealth, String.format(SHOULD_BREATHE, health, maxHealth));
        helper.succeed();
    }

    private static void fillStone(ServerLevel level, BlockPos middle, Block block) {
        for (BlockPos cell : BlockPos.betweenClosed(middle.offset(-STONE_HALF_WIDTH, -STONE_HALF_WIDTH, -STONE_HALF_WIDTH),
                middle.offset(STONE_HALF_WIDTH, STONE_HALF_WIDTH, STONE_HALF_WIDTH))) {
            level.setBlock(cell, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
