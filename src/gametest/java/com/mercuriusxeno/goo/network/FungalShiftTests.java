package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for Fungal Shift through the real throw path: released aimed
 * at a red mushroom fifteen blocks off, the player stands on it and pays the
 * cost; aimed at stone, the player stays and pays nothing
 * (decision fungal-shift-blinks-to-the-aimed-fungus).
 */
public final class FungalShiftTests {

    private static final Identifier FUNGAL_SHIFT = Identifier.parse("goo:shroom_fungal_shift");
    /** The light bay's west edge, on its floor. */
    private static final BlockPos STAND_POS = new BlockPos(0, 0, 8);
    /** Fifteen blocks east of the player, within the shift's range of sixteen. */
    private static final BlockPos AIMED_POS = STAND_POS.east(15);
    private static final double MOVE_TOLERANCE = 1e-6;
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
        helper.setBlock(AIMED_POS, aimed);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.SHROOM, FUNGAL_SHIFT);
        KnownRecipes.teachRequires(player, shift);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atLowerCornerOf(helper.absolutePos(AIMED_POS)).add(AIM_IN_BLOCK));
        return player;
    }

    private static int held(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.SHROOM, 0);
    }
}
