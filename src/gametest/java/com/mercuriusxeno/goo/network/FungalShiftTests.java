package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
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

/**
 * Gametests for Fungal Shift through the real throw path: released aimed
 * at a red mushroom fifteen blocks off, the player stands on it and pays the
 * cost; aimed at stone, the player stays and pays nothing; under fungal
 * sight it reaches a mushroom beyond its base range (decisions
 * fungal-shift-blinks-to-the-aimed-fungus and sight-lengthens-shift-and-outlines-fungus).
 */
public final class FungalShiftTests {

    private static final Identifier FUNGAL_SHIFT = Identifier.parse("goo:shroom_fungal_shift");
    /** The light bay's west edge, on its floor. */
    private static final BlockPos STAND_POS = new BlockPos(0, 0, 8);
    /** Fifteen blocks east of the player, within the shift's range of sixteen. */
    private static final BlockPos AIMED_POS = STAND_POS.east(15);
    private static final double MOVE_TOLERANCE = 1e-6;
    /** A stone wall halfway between the player and the aimed mushroom, taller than the player's eye. */
    private static final BlockPos WALL_POS = STAND_POS.east(7);
    private static final int WALL_HEIGHT = 4;
    /** Blocks above the bay floor searched for the framework's barrier roof. */
    private static final int CEILING_SEARCH = 8;
    /** The light bay's corner, on its floor. */
    private static final BlockPos CORNER_POS = new BlockPos(0, 0, 0);
    /** Straight above the corner, past the shift's base range of sixty-four. */
    private static final BlockPos HIGH_ABOVE = CORNER_POS.above(70);
    /** shroom_fungal_shift.json's range. */
    private static final double BASE_RANGE = 64;
    /** Sideways off the aimed mushroom, about two degrees at fifteen blocks, so the look ray misses it. */
    private static final Vec3 NEAR_MISS = new Vec3(0.5, 0.2, 1.05);
    private static final String FAR_BEYOND_BASE = "The high mushroom block should stand beyond the base range";
    /** Low on the aimed block, inside a mushroom's outline, which stands six pixels tall. */
    private static final Vec3 AIM_IN_BLOCK = new Vec3(0.5, 0.2, 0.5);
    private static final String ABILITY_REQUIRED = "Ability registry must hold shroom_fungal_shift";
    private static final String SHOULD_STAND_ON = "The player should stand on the mushroom, stands at %s";
    private static final String SHOULD_DRAIN_COST = "The shift should drain %d mB, drained %d";
    private static final String SHOULD_STAY = "Aimed at stone the player should stay put, moved %.3f";
    private static final String SHOULD_DRAIN_NOTHING = "Aimed at stone the shift should drain nothing, drained %d";

    private FungalShiftTests() {
    }

    /**
     * Released aimed at a red mushroom fifteen blocks off, the player stands
     * in the mushroom's cell and the cost drains.
     *
     * @param helper the gametest helper
     */
    public static void fungalShiftToAMushroom(GameTestHelper helper) {
        AbilityDefinition shift = fungalShift(helper);
        ServerPlayer player = shifterAimedAt(helper, Blocks.RED_MUSHROOM, shift);
        int heldBefore = held(player);

        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, FUNGAL_SHIFT);

        BlockPos standing = player.blockPosition();
        int drained = heldBefore - held(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing.equals(helper.absolutePos(AIMED_POS)), String.format(SHOULD_STAND_ON, standing));
        helper.assertTrue(drained == shift.cost(), String.format(SHOULD_DRAIN_COST, shift.cost(), drained));
        helper.succeed();
    }

    /**
     * Released aimed at stone, the player stays where it stood and no goo
     * drains.
     *
     * @param helper the gametest helper
     */
    public static void fungalShiftRefusesStone(GameTestHelper helper) {
        AbilityDefinition shift = fungalShift(helper);
        ServerPlayer player = shifterAimedAt(helper, Blocks.STONE, shift);
        Vec3 before = player.position();
        int heldBefore = held(player);

        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, FUNGAL_SHIFT);

        double moved = player.position().distanceTo(before);
        int drained = heldBefore - held(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(moved < MOVE_TOLERANCE, String.format(SHOULD_STAY, moved));
        helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING, drained));
        helper.succeed();
    }

    /**
     * Released aimed about two degrees beside a red mushroom fifteen blocks
     * off, the look ray missing it, the player still stands on it: the aim
     * snaps to fungus within three degrees.
     *
     * @param helper the gametest helper
     */
    public static void fungalShiftSnapsToANearMiss(GameTestHelper helper) {
        AbilityDefinition shift = fungalShift(helper);
        ServerPlayer player = shifterAimedAt(helper, Blocks.RED_MUSHROOM, shift);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atLowerCornerOf(helper.absolutePos(AIMED_POS)).add(NEAR_MISS));

        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, FUNGAL_SHIFT);

        BlockPos standing = player.blockPosition();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(standing.equals(helper.absolutePos(AIMED_POS)), String.format(SHOULD_STAND_ON, standing));
        helper.succeed();
    }

    /**
     * A mushroom block seventy blocks straight up, past the shift's base
     * range of sixty-four: aimed at without sight the player stays put, and
     * under the shroom brew's sight the player shifts onto it.
     *
     * @param helper the gametest helper
     */
    public static void sightExtendsTheShift(GameTestHelper helper) {
        AbilityDefinition shift = fungalShift(helper);
        helper.assertTrue(HIGH_ABOVE.getY() - CORNER_POS.getY() > BASE_RANGE, FAR_BEYOND_BASE);
        ServerPlayer player = shifterAimedAt(helper, Blocks.RED_MUSHROOM_BLOCK, shift, CORNER_POS, HIGH_ABOVE);
        openTheCeilingAbove(helper, CORNER_POS);
        Vec3 before = player.position();

        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, FUNGAL_SHIFT);
        double movedWithoutSight = player.position().distanceTo(before);
        BrewEffectTests.drinkBrew(player, GooTypes.SHROOM);
        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, FUNGAL_SHIFT);

        BlockPos standing = player.blockPosition();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(movedWithoutSight < MOVE_TOLERANCE, String.format(SHOULD_STAY, movedWithoutSight));
        helper.assertTrue(standing.equals(helper.absolutePos(HIGH_ABOVE.above())),
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
     * A red mushroom stands behind a stone wall: aimed at without sight the
     * player stays put, and under the shroom brew's sight the aim passes
     * through the wall and the player shifts onto it.
     *
     * @param helper the gametest helper
     */
    public static void sightShiftsThroughAWall(GameTestHelper helper) {
        AbilityDefinition shift = fungalShift(helper);
        ServerPlayer player = shifterAimedAt(helper, Blocks.RED_MUSHROOM, shift);
        for (int y = 0; y < WALL_HEIGHT; y++) {
            helper.setBlock(WALL_POS.above(y), Blocks.STONE);
        }
        Vec3 before = player.position();

        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, FUNGAL_SHIFT);
        double movedWithoutSight = player.position().distanceTo(before);
        BrewEffectTests.drinkBrew(player, GooTypes.SHROOM);
        SelfDeliveryTests.invoke(player, GooTypes.SHROOM, FUNGAL_SHIFT);

        BlockPos standing = player.blockPosition();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(movedWithoutSight < MOVE_TOLERANCE, String.format(SHOULD_STAY, movedWithoutSight));
        helper.assertTrue(standing.equals(helper.absolutePos(AIMED_POS)), String.format(SHOULD_STAND_ON, standing));
        helper.succeed();
    }

    private static AbilityDefinition fungalShift(GameTestHelper helper) {
        AbilityDefinition shift = AbilityRegistry.of(helper.getLevel()).getAbility(FUNGAL_SHIFT);
        helper.assertTrue(shift != null, ABILITY_REQUIRED);
        return shift;
    }

    /**
     * A shroom-holding player at the bay's west edge looking at the block
     * stood fifteen blocks east.
     *
     * @param helper the gametest helper
     * @param aimed  the block stood at the aimed spot
     * @param shift  the fungal shift ability, whose requirements the player learns
     * @return the player
     */
    private static ServerPlayer shifterAimedAt(GameTestHelper helper, Block aimed, AbilityDefinition shift) {
        return shifterAimedAt(helper, aimed, shift, STAND_POS, AIMED_POS);
    }

    /**
     * A shroom-holding player standing at one spot looking at the block stood
     * at another.
     *
     * @param helper the gametest helper
     * @param aimed  the block stood at the aimed spot
     * @param shift  the fungal shift ability, whose requirements the player learns
     * @param from   where the player stands
     * @param at     where the aimed block stands
     * @return the player
     */
    private static ServerPlayer shifterAimedAt(GameTestHelper helper, Block aimed, AbilityDefinition shift,
                                               BlockPos from, BlockPos at) {
        helper.setBlock(at, aimed);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.SHROOM, FUNGAL_SHIFT);
        KnownRecipes.teachRequires(player, shift);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(from));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atLowerCornerOf(helper.absolutePos(at)).add(AIM_IN_BLOCK));
        return player;
    }

    private static int held(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.SHROOM, 0);
    }
}
