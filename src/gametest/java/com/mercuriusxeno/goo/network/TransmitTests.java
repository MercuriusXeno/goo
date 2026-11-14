package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.TransmitStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Transmit through the real throw path: released aimed
 * at a red mushroom fifteen blocks off, the player stands on it and pays the
 * cost; aimed at stone, or standing near no fungus, the player stays and pays nothing; under fungal
 * sight it reaches a mushroom beyond its base range (decisions
 * fungal-shift-blinks-to-the-aimed-fungus and sight-lengthens-shift-and-outlines-fungus).
 */
@RegistersGameTests
public final class TransmitTests {
    private static final String TRANSMIT_TO_A_MUSHROOM = "transmit_to_a_mushroom";
    private static final String SIGHT_EXTENDS_THE_TRANSMIT = "sight_extends_the_transmit";
    private static final String SIGHT_TRANSMITS_THROUGH_A_WALL = "sight_transmits_through_a_wall";
    private static final String TRANSMIT_SNAPS_TO_A_NEAR_MISS = "transmit_snaps_to_a_near_miss";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, TRANSMIT_TO_A_MUSHROOM, TransmitTests::transmitToAMushroom);
        GooTestFunctions.registerTest(registrar, SIGHT_EXTENDS_THE_TRANSMIT, TransmitTests::sightExtendsTheTransmit);
        GooTestFunctions.registerTest(registrar, SIGHT_TRANSMITS_THROUGH_A_WALL, TransmitTests::sightTransmitsThroughAWall);
        GooTestFunctions.registerTest(registrar, TRANSMIT_SNAPS_TO_A_NEAR_MISS, TransmitTests::transmitSnapsToANearMiss);
    }


    private static final Identifier TRANSMIT = Identifier.parse("goo:shroom_transmit");
    private static final Identifier SHROOM_SIGHT = Identifier.parse("goo:shroom_sight");
    /** The light bay's west edge, on its floor. */
    private static final BlockPos STAND_POS = new BlockPos(0, 0, 8);
    /** Fifteen blocks east of the player, well within Transmit's base range. */
    private static final BlockPos AIMED_POS = STAND_POS.east(15);
    /** A stone wall halfway between the player and the aimed mushroom, taller than the player's eye. */
    private static final BlockPos WALL_POS = STAND_POS.east(7);
    private static final int WALL_HEIGHT = 4;
    /** Blocks above the bay floor searched for the framework's barrier roof. */
    private static final int CEILING_SEARCH = 8;
    /** The light bay's corner, on its floor. */
    private static final BlockPos CORNER_POS = new BlockPos(0, 0, 0);
    /** Blocks past Transmit's base range, read live, the high mushroom block stands straight above the corner. */
    private static final int PAST_BASE_RANGE = 6;
    /** Sideways off the aimed mushroom, about two degrees at fifteen blocks, so the look ray misses it. */
    private static final Vec3 NEAR_MISS = new Vec3(0.5, 0.2, 1.05);
    /** Low on the aimed block, inside a mushroom's outline, which stands six pixels tall. */
    private static final Vec3 AIM_IN_BLOCK = new Vec3(0.5, 0.2, 0.5);
    private static final String ABILITY_REQUIRED = "Ability registry must hold shroom_transmit";
    private static final String SHOULD_STAND_ON = "The player should stand on the mushroom, stands at %s";
    private static final String SHOULD_DRAIN = "Transmit should drain some shroom, drained %d";

    private TransmitTests() {
    }

    /**
     * Released aimed at a red mushroom fifteen blocks off, the player stands
     * in the mushroom's cell and some shroom drains.
     *
     * @param helper the gametest helper
     */
    public static void transmitToAMushroom(GameTestHelper helper) {
        AbilityDefinition transmit = transmit(helper);
        ServerPlayer player = casterAimedAt(helper, Blocks.RED_MUSHROOM, transmit);
        int heldBefore = held(player);

        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, TRANSMIT);

        BlockPos standing = player.blockPosition();
        int drained = heldBefore - held(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing.equals(helper.absolutePos(AIMED_POS)), String.format(SHOULD_STAND_ON, standing));
        helper.assertTrue(drained > 0, String.format(SHOULD_DRAIN, drained));
        helper.succeed();
    }

    /**
     * Released aimed about two degrees beside a red mushroom fifteen blocks
     * off, the look ray missing it, the player still stands on it: the aim
     * snaps to fungus within three degrees.
     *
     * @param helper the gametest helper
     */
    public static void transmitSnapsToANearMiss(GameTestHelper helper) {
        AbilityDefinition transmit = transmit(helper);
        ServerPlayer player = casterAimedAt(helper, Blocks.RED_MUSHROOM, transmit);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atLowerCornerOf(helper.absolutePos(AIMED_POS)).add(NEAR_MISS));

        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, TRANSMIT);

        BlockPos standing = player.blockPosition();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing.equals(helper.absolutePos(AIMED_POS)), String.format(SHOULD_STAND_ON, standing));
        helper.succeed();
    }

    /**
     * A mushroom block straight up past Transmit's base range, read live
     * from the ability: under Sight held on the glove the player transmits onto it.
     *
     * @param helper the gametest helper
     */
    public static void sightExtendsTheTransmit(GameTestHelper helper) {
        AbilityDefinition transmit = transmit(helper);
        double baseRange = transmit.behaviors().stream().filter(TransmitStep.class::isInstance).map(TransmitStep.class::cast)
                .findFirst().orElseThrow().range().evaluate(Variables.NONE);
        BlockPos highAbove = CORNER_POS.above((int) Math.ceil(baseRange) + PAST_BASE_RANGE);
        ServerPlayer player = casterAimedAt(helper, Blocks.RED_MUSHROOM_BLOCK, transmit, CORNER_POS, highAbove);
        openTheCeilingAbove(helper, CORNER_POS);

        HeartOverlayTests.invoke(player, GooTypes.SHROOM, SHROOM_SIGHT);
        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, TRANSMIT);

        BlockPos standing = player.blockPosition();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing.equals(helper.absolutePos(highAbove.above())),
                String.format(SHOULD_STAND_ON, standing));
        helper.succeed();
    }

    /**
     * Clears the barrier the gametest framework roofs the bay with, straight
     * above a spot, so a look up from there reaches past the bay.
     *
     * @param helper the gametest helper
     * @param below  the spot whose column opens
     */
    private static void openTheCeilingAbove(GameTestHelper helper, BlockPos below) {
        BlockPos.MutableBlockPos cursor = helper.absolutePos(below).mutable();
        for (int up = 0; up < CEILING_SEARCH; up++) {
            cursor.move(Direction.UP);
            if (helper.getLevel().getBlockState(cursor).is(Blocks.BARRIER)) {
                helper.getLevel().setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * A red mushroom stands behind a stone wall: under Sight held on the glove
     * the aim passes through the wall and the player transmits onto it.
     *
     * @param helper the gametest helper
     */
    public static void sightTransmitsThroughAWall(GameTestHelper helper) {
        AbilityDefinition transmit = transmit(helper);
        ServerPlayer player = casterAimedAt(helper, Blocks.RED_MUSHROOM, transmit);
        for (int y = 0; y < WALL_HEIGHT; y++) {
            helper.setBlock(WALL_POS.above(y), Blocks.STONE);
        }

        HeartOverlayTests.invoke(player, GooTypes.SHROOM, SHROOM_SIGHT);
        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, TRANSMIT);

        BlockPos standing = player.blockPosition();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing.equals(helper.absolutePos(AIMED_POS)), String.format(SHOULD_STAND_ON, standing));
        helper.succeed();
    }

    private static AbilityDefinition transmit(GameTestHelper helper) {
        AbilityDefinition transmit = AbilityRegistry.of(helper.getLevel()).getAbility(TRANSMIT);
        helper.assertTrue(transmit != null, ABILITY_REQUIRED);
        return transmit;
    }

    /**
     * A shroom-holding player at the bay's west edge looking at the block
     * stood fifteen blocks east.
     *
     * @param helper the gametest helper
     * @param aimed  the block stood at the aimed spot
     * @param transmit  the Transmit ability, whose requirements the player learns
     * @return the player
     */
    private static ServerPlayer casterAimedAt(GameTestHelper helper, Block aimed, AbilityDefinition transmit) {
        return casterAimedAt(helper, aimed, transmit, STAND_POS, AIMED_POS);
    }

    /**
     * A shroom-holding player standing at one spot looking at the block stood
     * at another.
     *
     * @param helper the gametest helper
     * @param aimed  the block stood at the aimed spot
     * @param transmit  the Transmit ability, whose requirements the player learns
     * @param from   where the player stands
     * @param at     where the aimed block stands
     * @return the player
     */
    private static ServerPlayer casterAimedAt(GameTestHelper helper, Block aimed, AbilityDefinition transmit,
                                               BlockPos from, BlockPos at) {
        helper.setBlock(at, aimed);
        // a Transmit cast starts near a fungus: one stands beside the player, off the look
        helper.setBlock(from.south(), Blocks.BROWN_MUSHROOM);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.SHROOM, TRANSMIT);
        KnownRecipes.teachRequires(player, transmit);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(from));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atLowerCornerOf(helper.absolutePos(at)).add(AIM_IN_BLOCK));
        return player;
    }

    private static int held(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.SHROOM, 0);
    }
}
