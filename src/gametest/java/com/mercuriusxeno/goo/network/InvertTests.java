package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for nether Invert cast through the real self delivery: in a
 * sealed room three blocks high the player falls up and rests against the
 * ceiling, grounded there; moved out from under the roof to open sky, the
 * held effect ends and the player's gravity is its own again.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@RegistersGameTests
public final class InvertTests {
    private static final String INVERT_RESTS_ON_THE_CEILING = "invert_rests_on_the_ceiling";
    private static final String INVERT_ENDS_UNDER_OPEN_SKY = "invert_ends_under_open_sky";

    private static final Identifier NETHER_INVERT = Identifier.parse("goo:nether_invert");
    /** The room's one open cell, over its floor block. */
    private static final BlockPos ROOM = new BlockPos(1, 1, 1);
    /** The room's height in open cells. */
    private static final int ROOM_HEIGHT = 3;
    /** A cell on the bay's open floor, under nothing. */
    private static final BlockPos OPEN = new BlockPos(4, 1, 4);
    /** Long past the fall up a three high room takes. */
    private static final int FALL_TICKS = 20;
    /** How close the body's top rests to the ceiling's underside. */
    private static final double RESTS = 1e-5;

    private static final String SHOULD_REST = "An inverted player should rest with its top at the ceiling's underside"
            + " y %.3f, its top stands at %.3f";
    private static final String SHOULD_BE_GROUNDED = "An inverted player resting on the ceiling should read grounded";
    private static final String SHOULD_HOLD = "Invert should hold under the roof";
    private static final String SHOULD_END = "Invert should end under open sky, held %b, its top at %.3f under a roof"
            + " height %d";
    private static final String SHOULD_FALL_DOWN = "Under open sky the player's gravity should be its own %.3f,"
            + " reads %.3f";

    private InvertTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, INVERT_RESTS_ON_THE_CEILING, InvertTests::invertRestsOnTheCeiling);
        GooTestFunctions.registerTest(registrar, INVERT_ENDS_UNDER_OPEN_SKY, InvertTests::invertEndsUnderOpenSky);
    }

    /**
     * A player casts Invert standing on the floor of a sealed room three
     * blocks high: after the fall up, its body's top rests at the ceiling's
     * underside and it reads grounded.
     *
     * @param helper the gametest helper
     */
    public static void invertRestsOnTheCeiling(GameTestHelper helper) {
        ServerPlayer player = invertedInARoom(helper);
        SelfDeliveryTests.tickFor(helper, player, FALL_TICKS);
        helper.runAfterDelay(FALL_TICKS + 1, () -> {
            double ceiling = helper.absolutePos(ROOM.above(ROOM_HEIGHT)).getY();
            double top = player.getBoundingBox().maxY;
            boolean grounded = player.onGround();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(Math.abs(top - ceiling) < RESTS, String.format(SHOULD_REST, ceiling, top));
            helper.assertTrue(grounded, SHOULD_BE_GROUNDED);
            helper.succeed();
        });
    }

    /**
     * An inverted player resting on its room's ceiling is moved out to the
     * bay's open floor: the tick after, Invert has ended and its gravity is
     * its own, pulling it down.
     *
     * @param helper the gametest helper
     */
    public static void invertEndsUnderOpenSky(GameTestHelper helper) {
        ServerPlayer player = invertedInARoom(helper);
        double ownGravity = player.getAttributeBaseValue(Attributes.GRAVITY);
        helper.setBlock(OPEN.below(), Blocks.STONE);
        SelfDeliveryTests.tickFor(helper, player, FALL_TICKS);
        helper.runAfterDelay(FALL_TICKS + 1, () -> {
            helper.assertTrue(HeldEffectsEvents.holds(player, NETHER_INVERT), SHOULD_HOLD);
            Vec3 open = Vec3.atBottomCenterOf(helper.absolutePos(OPEN));
            player.setPos(open.x, open.y, open.z);
            player.doTick();
            boolean held = HeldEffectsEvents.holds(player, NETHER_INVERT);
            double top = player.getBoundingBox().maxY;
            int roof = helper.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(),
                    player.getBlockZ());
            double gravity = player.getAttributeValue(Attributes.GRAVITY);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(held, String.format(SHOULD_END, held, top, roof));
            helper.assertTrue(Math.abs(gravity - ownGravity) < RESTS && gravity > 0,
                    String.format(SHOULD_FALL_DOWN, ownGravity, gravity));
            helper.succeed();
        });
    }

    /**
     * A survival player who has cast Invert through the glove, standing on
     * the floor of a sealed one by one room three blocks high, stone on
     * every side, so a roof stands over it.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer invertedInARoom(GameTestHelper helper) {
        buildRoom(helper);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(ROOM));
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.NETHER, NETHER_INVERT);
        player.setPos(stand.x, stand.y, stand.z);
        player.setDeltaMovement(Vec3.ZERO);
        return player;
    }

    private static void buildRoom(GameTestHelper helper) {
        helper.setBlock(ROOM.below(), Blocks.STONE);
        helper.setBlock(ROOM.above(ROOM_HEIGHT), Blocks.STONE);
        for (int up = 0; up < ROOM_HEIGHT; up++) {
            BlockPos cell = ROOM.above(up);
            helper.setBlock(cell.north(), Blocks.STONE);
            helper.setBlock(cell.south(), Blocks.STONE);
            helper.setBlock(cell.east(), Blocks.STONE);
            helper.setBlock(cell.west(), Blocks.STONE);
        }
    }
}
