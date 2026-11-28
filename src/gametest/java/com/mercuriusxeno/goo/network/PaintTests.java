package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Ornate's Paint through the real held stream: a caster holding red dye in
 * the off hand paints the white wool and the white sheep in its cone red,
 * keeps the dye, and pays the cost ornate_paint.json names for each thing
 * painted; with no dye held the stream paints nothing and spends nothing.
 * paint-recolors-dyeable-things-to-the-off-hand-dye
 */
@RegistersGameTests
public final class PaintTests {

    private static final String PAINTS_WOOL_AND_A_SHEEP = "paint_turns_white_wool_and_a_white_sheep_red";
    private static final String PAYS_PER_THING = "paint_keeps_the_dye_and_pays_per_block_painted";
    private static final String NO_DYE_PAINTS_NOTHING = "paint_with_no_dye_paints_nothing_and_spends_nothing";
    private static final Identifier PAINT = Identifier.parse("goo:ornate_paint");
    private static final String ABILITY_REQUIRED = "The ability registry must hold ornate_paint";
    /** The caster's feet, on the bay's west side. */
    private static final BlockPos STAND = new BlockPos(0, 1, 2);
    /** Two blocks east, on the floor, where the sheep stands. */
    private static final BlockPos SHEEP_POS = STAND.east(2);
    /** Four blocks east, on the floor. */
    private static final BlockPos LOW_WOOL = STAND.east(4);
    /** Four blocks east, a block up. */
    private static final BlockPos HIGH_WOOL = LOW_WOOL.above();
    private static final Block WHITE_WOOL = Blocks.WOOL.pick(DyeColor.WHITE);
    private static final Block RED_WOOL = Blocks.WOOL.pick(DyeColor.RED);
    private static final Item RED_DYE = Items.DYE.pick(DyeColor.RED);
    private static final float FACING_EAST = -90f;
    /** Down from level toward the sheep and the low wool both. */
    private static final float TOWARD_THE_SHEEP_AND_THE_WOOL = 20f;
    /** Down from level toward the middle of the two stacked wool blocks. */
    private static final float TOWARD_THE_STACKED_WOOL = 9f;
    private static final int BAY_EDGE = 4;
    private static final int BAY_TOP = 4;
    private static final int DYE_HELD = 5;
    /** A second held: the cone's rays reach each block in it many times over. */
    private static final int HOLD_TICKS = 20;
    private static final String NOT_PAINTED = "%s should stand %s, stands %s";
    private static final String DYE_USED = "The off hand should still hold %d red dye, holds %d";
    private static final String SPENT_WRONG = "Paint should spend %d ornate goo, spent %d";

    private PaintTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PAINTS_WOOL_AND_A_SHEEP, PaintTests::paintTurnsWhiteWoolAndAWhiteSheepRed);
        GooTestFunctions.registerTest(registrar, PAYS_PER_THING, PaintTests::paintKeepsTheDyeAndPaysPerBlockPainted);
        GooTestFunctions.registerTest(registrar, NO_DYE_PAINTS_NOTHING,
                PaintTests::paintWithNoDyePaintsNothingAndSpendsNothing);
    }

    /**
     * A caster holding red dye holds Paint toward a white sheep and white
     * wool beyond it: the wool stands red wool and the sheep is red.
     *
     * @param helper the gametest helper
     */
    public static void paintTurnsWhiteWoolAndAWhiteSheepRed(GameTestHelper helper) {
        clearABay(helper);
        helper.setBlock(LOW_WOOL, WHITE_WOOL);
        Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, Vec3.atBottomCenterOf(SHEEP_POS));
        sheep.setColor(DyeColor.WHITE);
        ServerPlayer painter = painterAt(helper, TOWARD_THE_SHEEP_AND_THE_WOOL, new ItemStack(RED_DYE, DYE_HELD));
        holdThen(helper, painter, () -> {
            assertStands(helper, LOW_WOOL, RED_WOOL);
            helper.assertTrue(sheep.getColor() == DyeColor.RED,
                    String.format(NOT_PAINTED, "The sheep", DyeColor.RED, sheep.getColor()));
        });
    }

    /**
     * A caster holding five red dye holds Paint on two stacked white wool
     * blocks: both stand red, the off hand still holds five red dye, and the
     * caster's ornate goo is down by twice the cost ornate_paint.json names,
     * the wool already red costing nothing on the ticks after.
     *
     * @param helper the gametest helper
     */
    public static void paintKeepsTheDyeAndPaysPerBlockPainted(GameTestHelper helper) {
        clearABay(helper);
        helper.setBlock(LOW_WOOL, WHITE_WOOL);
        helper.setBlock(HIGH_WOOL, WHITE_WOOL);
        ServerPlayer painter = painterAt(helper, TOWARD_THE_STACKED_WOOL, new ItemStack(RED_DYE, DYE_HELD));
        int before = ornateHeld(painter);
        int perThing = paint(helper).cost().exactIn(GooValues.of(helper.getLevel())).ceiling();
        holdThen(helper, painter, () -> {
            assertStands(helper, LOW_WOOL, RED_WOOL);
            assertStands(helper, HIGH_WOOL, RED_WOOL);
            int dye = painter.getOffhandItem().is(RED_DYE) ? painter.getOffhandItem().getCount() : 0;
            helper.assertTrue(dye == DYE_HELD, String.format(DYE_USED, DYE_HELD, dye));
            int spent = before - ornateHeld(painter);
            helper.assertTrue(spent == 2 * perThing, String.format(SPENT_WRONG, 2 * perThing, spent));
        });
    }

    /**
     * A caster holding nothing in the off hand holds Paint on the two stacked
     * white wool blocks: both stand white and no ornate goo is spent.
     *
     * @param helper the gametest helper
     */
    public static void paintWithNoDyePaintsNothingAndSpendsNothing(GameTestHelper helper) {
        clearABay(helper);
        helper.setBlock(LOW_WOOL, WHITE_WOOL);
        helper.setBlock(HIGH_WOOL, WHITE_WOOL);
        ServerPlayer painter = painterAt(helper, TOWARD_THE_STACKED_WOOL, ItemStack.EMPTY);
        int before = ornateHeld(painter);
        holdThen(helper, painter, () -> {
            assertStands(helper, LOW_WOOL, WHITE_WOOL);
            assertStands(helper, HIGH_WOOL, WHITE_WOOL);
            int spent = before - ornateHeld(painter);
            helper.assertTrue(spent == 0, String.format(SPENT_WRONG, 0, spent));
        });
    }

    private static void clearABay(GameTestHelper helper) {
        for (int x = 0; x <= BAY_EDGE; x++) {
            for (int z = 0; z <= BAY_EDGE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= BAY_TOP; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static AbilityDefinition paint(GameTestHelper helper) {
        AbilityDefinition paint = AbilityRegistry.of(helper.getLevel()).getAbility(PAINT);
        helper.assertTrue(paint != null, ABILITY_REQUIRED);
        return paint;
    }

    /** A glove caster with ornate goo on the bay's west side facing east, pitched down, the off hand given. */
    private static ServerPlayer painterAt(GameTestHelper helper, float pitch, ItemStack offHand) {
        ServerPlayer painter = SelfDeliveryTests.invoker(helper, GooTypes.ORNATE);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND));
        painter.setPos(stand.x, stand.y, stand.z);
        painter.setYRot(FACING_EAST);
        painter.setXRot(pitch);
        painter.setItemInHand(InteractionHand.OFF_HAND, offHand);
        KnownRecipes.teachRequires(painter, paint(helper));
        return painter;
    }

    /** Holds Paint through the real stream tick for a second, then removes the caster, runs the check and succeeds. */
    private static void holdThen(GameTestHelper helper, ServerPlayer painter, Runnable check) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.ORNATE), PAINT.toString(),
                painter.getEyePosition(), painter.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(painter, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(painter);
            check.run();
            helper.succeed();
        });
    }

    private static void assertStands(GameTestHelper helper, BlockPos pos, Block block) {
        helper.assertTrue(helper.getBlockState(pos).is(block),
                String.format(NOT_PAINTED, "The wool at " + pos.toShortString(), block, helper.getBlockState(pos)));
    }

    private static int ornateHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ORNATE, 0);
    }
}
