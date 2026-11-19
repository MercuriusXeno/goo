package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.TransmitStep;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Transmit through the real throw path, each on a stone floor
 * with a line of mycelial blocks on it: touching one end of an unbroken line
 * and aiming at the other, the player arrives there and pays the cost; a
 * stone block parting the line, or a player standing beside the line rather
 * than on it, leaves the player where it stands; a path of sculk, mycelium and
 * Creep cells carries the player to its far end.
 * transmit-reaches-only-connected-mycelial-blocks-you-touch
 */
@RegistersGameTests
public final class TransmitTests {
    private static final String TRANSMIT_ALONG_AN_UNBROKEN_LINE = "transmit_along_an_unbroken_line";
    private static final String TRANSMIT_STOPS_AT_A_GAP = "transmit_stops_at_a_gap";
    private static final String TRANSMIT_NEEDS_A_TOUCH = "transmit_needs_a_touch";
    private static final String TRANSMIT_THROUGH_SCULK_MYCELIUM_AND_CREEP =
            "transmit_through_sculk_mycelium_and_creep";

    private static final Identifier TRANSMIT = Identifier.parse("goo:sculk_transmit");
    private static final int FLOOR_Y = 0;
    private static final int LINE_Y = 1;
    private static final int BAY_SPAN = 16;
    private static final int LINE_Z = 8;
    /** The line's west end, where the player stands on it. */
    private static final BlockPos LINE_START = new BlockPos(0, LINE_Y, LINE_Z);
    /** The line's east end, raised a block over the line so the look reaches it clear of the line's top. */
    private static final BlockPos LINE_END = new BlockPos(BAY_SPAN - 1, LINE_Y + 1, LINE_Z);
    /** The stone parting the line in the gap test, halfway along it. */
    private static final BlockPos GAP = new BlockPos(7, LINE_Y, LINE_Z);
    /** Where the mixed path turns: sculk runs north from the start to here, then mycelium east. */
    private static final int TURN_Z = 4;
    private static final int MYCELIUM_FROM_X = 1;
    private static final int CREEP_FROM_X = 6;
    /** The mixed path's Creep run turns south at this column and ends level with the player. */
    private static final int CREEP_TURN_X = 10;
    private static final int CREEP_LAYERS = 4;
    /** Low on the aimed block, inside a Creep cell's outline of four layers. */
    private static final Vec3 AIM_LOW_IN_BLOCK = new Vec3(0.5, 0.05, 0.5);
    private static final Vec3 AIM_IN_BLOCK = new Vec3(0.5, 0.5, 0.5);
    private static final double ARRIVED_WITHIN = 1.0E-6;
    private static final String ABILITY_REQUIRED = "Ability registry must hold sculk_transmit";
    private static final String SHOULD_ARRIVE = "The player should stand on %s, stands at %s";
    private static final String SHOULD_STAY = "The player should stay at %s, stands at %s";
    private static final String SHOULD_DRAIN = "Transmit should drain some sculk, drained %d";

    private TransmitTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, TRANSMIT_ALONG_AN_UNBROKEN_LINE,
                TransmitTests::transmitAlongAnUnbrokenLine);
        GooTestFunctions.registerTest(registrar, TRANSMIT_STOPS_AT_A_GAP, TransmitTests::transmitStopsAtAGap);
        GooTestFunctions.registerTest(registrar, TRANSMIT_NEEDS_A_TOUCH, TransmitTests::transmitNeedsATouch);
        GooTestFunctions.registerTest(registrar, TRANSMIT_THROUGH_SCULK_MYCELIUM_AND_CREEP,
                TransmitTests::transmitThroughSculkMyceliumAndCreep);
    }

    /**
     * Standing on one end of an unbroken sculk line and aiming at the other,
     * the player stands on the far block and some sculk drains.
     *
     * @param helper the gametest helper
     */
    public static void transmitAlongAnUnbrokenLine(GameTestHelper helper) {
        laySculkLine(helper);
        ServerPlayer player = casterOn(helper, LINE_START, LINE_END, AIM_IN_BLOCK);
        int heldBefore = held(player);

        SelfDeliveryTests.invoke(player, GooTypes.SCULK, TRANSMIT);

        int drained = heldBefore - held(player);
        assertArrived(helper, player, LINE_END);
        helper.assertTrue(drained > 0, String.format(SHOULD_DRAIN, drained));
        helper.succeed();
    }

    /**
     * Any break in the network is a place you can't go: with one stone block
     * parting the line, the player aiming at the sculk past the gap stays.
     *
     * @param helper the gametest helper
     */
    public static void transmitStopsAtAGap(GameTestHelper helper) {
        laySculkLine(helper);
        helper.setBlock(GAP, Blocks.STONE);
        ServerPlayer player = casterOn(helper, LINE_START, LINE_END, AIM_IN_BLOCK);
        Vec3 before = player.position();

        SelfDeliveryTests.invoke(player, GooTypes.SCULK, TRANSMIT);

        assertStayed(helper, player, before);
        helper.succeed();
    }

    /**
     * You have to be touching it, not just near it: a player standing on
     * stone one block from the line's end stays.
     *
     * @param helper the gametest helper
     */
    public static void transmitNeedsATouch(GameTestHelper helper) {
        laySculkLine(helper);
        // the line now starts one block east of the stone the player stands on
        helper.setBlock(LINE_START, Blocks.STONE);
        ServerPlayer player = casterOn(helper, LINE_START, LINE_END, AIM_IN_BLOCK);
        Vec3 before = player.position();

        SelfDeliveryTests.invoke(player, GooTypes.SCULK, TRANSMIT);

        assertStayed(helper, player, before);
        helper.succeed();
    }

    /**
     * It works on mycelium and on sculk goo itself: from the first sculk
     * block of a path running north through sculk, east through mycelium,
     * then east and south through Creep cells, the player aiming at the last
     * Creep cell stands on it.
     *
     * @param helper the gametest helper
     */
    public static void transmitThroughSculkMyceliumAndCreep(GameTestHelper helper) {
        layFloor(helper);
        for (int z = LINE_Z; z >= TURN_Z; z--) {
            helper.setBlock(new BlockPos(0, LINE_Y, z), Blocks.SCULK);
        }
        for (int x = MYCELIUM_FROM_X; x < CREEP_FROM_X; x++) {
            helper.setBlock(new BlockPos(x, LINE_Y, TURN_Z), Blocks.MYCELIUM);
        }
        BlockState creep = GooBlocks.SCULK_CREEP.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, CREEP_LAYERS);
        for (int x = CREEP_FROM_X; x <= CREEP_TURN_X; x++) {
            helper.setBlock(new BlockPos(x, LINE_Y, TURN_Z), creep);
        }
        for (int z = TURN_Z + 1; z <= LINE_Z; z++) {
            helper.setBlock(new BlockPos(CREEP_TURN_X, LINE_Y, z), creep);
        }
        BlockPos lastCell = new BlockPos(CREEP_TURN_X, LINE_Y, LINE_Z);
        ServerPlayer player = casterOn(helper, LINE_START, lastCell, AIM_LOW_IN_BLOCK);

        SelfDeliveryTests.invoke(player, GooTypes.SCULK, TRANSMIT);

        assertArrived(helper, player, lastCell);
        helper.succeed();
    }

    private static void layFloor(GameTestHelper helper) {
        BlockPos.betweenClosed(0, FLOOR_Y, 0, BAY_SPAN - 1, FLOOR_Y, BAY_SPAN - 1)
                .forEach(cell -> helper.setBlock(cell.immutable(), Blocks.STONE));
    }

    /**
     * Lays the stone floor and an unbroken sculk line along it from the
     * west end to the east, the east end raised a block.
     *
     * @param helper the gametest helper
     */
    private static void laySculkLine(GameTestHelper helper) {
        layFloor(helper);
        for (int x = 0; x < BAY_SPAN; x++) {
            helper.setBlock(new BlockPos(x, LINE_Y, LINE_Z), Blocks.SCULK);
        }
        helper.setBlock(LINE_END, Blocks.SCULK);
    }

    /**
     * A sculk-holding player who learned Transmit's requirements, standing on
     * one block and looking at a point inside another.
     *
     * @param helper the gametest helper
     * @param on     the block the player stands on
     * @param at     the block the player looks at
     * @param aimIn  where inside that block the look points
     * @return the player
     */
    private static ServerPlayer casterOn(GameTestHelper helper, BlockPos on, BlockPos at, Vec3 aimIn) {
        AbilityDefinition transmit = AbilityRegistry.of(helper.getLevel()).getAbility(TRANSMIT);
        helper.assertTrue(transmit != null, ABILITY_REQUIRED);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.SCULK, TRANSMIT);
        KnownRecipes.teachRequires(player, transmit);
        Vec3 stand = TransmitStep.standingOn(helper.getLevel(), helper.absolutePos(on));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atLowerCornerOf(helper.absolutePos(at)).add(aimIn));
        return player;
    }

    private static void assertArrived(GameTestHelper helper, ServerPlayer player, BlockPos block) {
        Vec3 standing = player.position();
        Vec3 expected = TransmitStep.standingOn(helper.getLevel(), helper.absolutePos(block));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing.distanceTo(expected) < ARRIVED_WITHIN,
                String.format(SHOULD_ARRIVE, expected, standing));
    }

    private static void assertStayed(GameTestHelper helper, ServerPlayer player, Vec3 before) {
        Vec3 standing = player.position();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing.distanceTo(before) < ARRIVED_WITHIN, String.format(SHOULD_STAY, before, standing));
    }

    private static int held(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.SCULK, 0);
    }
}
