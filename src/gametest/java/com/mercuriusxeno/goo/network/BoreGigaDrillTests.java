package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.MortarCellBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametest for Bore through Mortar: two players bore east for the same ticks
 * into two like stone walls, one wall fronted by a column of full Mortar
 * cells at the drill's point; the drill through the Mortar swells into a
 * giga drill, breaking more of its wall than the bare drill breaks of the
 * other, and drains the Mortar as it goes, against a like column no drill
 * reaches, which pours as the bored one does.
 * boring-through-rock-goo-gives-a-giga-drill-that-drains-it
 */
@RegistersGameTests
public final class BoreGigaDrillTests {

    private static final String BORE_THROUGH_MORTAR = "bore_through_mortar_is_a_giga_drill";
    private static final Identifier ROCK_BORE = Identifier.parse("goo:rock_bore");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_bore";
    /** The row the bare wall's borer stands on. */
    private static final int BARE_ROW = 2;
    /** The row the coated wall's borer stands on. */
    private static final int COATED_ROW = 7;
    /** The row of the column no drill reaches, laid as the coated one is. */
    private static final int CONTROL_ROW = 12;
    /** Each borer's feet, on its row, its eye in the block above. */
    private static final int STAND_HEIGHT = 1;
    /** The first column of each wall, east of its borer. */
    private static final int WALL_FROM_EAST = 2;
    private static final int WALL_TO_EAST = 5;
    private static final int WALL_LOWEST = 1;
    private static final int WALL_HIGHEST = 4;
    /** Each wall reaches one block either side of its row. */
    private static final int WALL_HALF_SPAN = 1;
    /** The Mortar read about a row reaches two blocks either side of it, where its goo pours. */
    private static final int MORTAR_HALF_SPAN = 2;
    private static final int HELD_GOO = 8;
    private static final int HOLD_TICKS = 30;
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final String SHOULD_BREAK_MORE =
            "The wall bored through Mortar should lose more blocks than the bare wall: %d against %d";
    private static final String SHOULD_DRAIN =
            "The bored Mortar should hold fewer layers than its start of %d and the unbored column's %d, holds %d";

    private BoreGigaDrillTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BORE_THROUGH_MORTAR, BoreGigaDrillTests::boreThroughMortarIsAGigaDrill);
    }

    /**
     * Two players bore east for the same ticks into two like stone walls;
     * the eye line of one passes through a full Mortar cell standing on a
     * second at the wall's face. That wall loses more blocks than the bare
     * one, and the bored Mortar holds fewer layers than it started with and
     * than a like column laid before a third wall that no one bores.
     *
     * @param helper the gametest helper
     */
    public static void boreThroughMortarIsAGigaDrill(GameTestHelper helper) {
        AbilityDefinition bore = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_BORE);
        helper.assertTrue(bore != null, ABILITY_REQUIRED);
        layWall(helper, BARE_ROW, false);
        layWall(helper, COATED_ROW, true);
        layWall(helper, CONTROL_ROW, true);
        int layersBefore = mortarLayersAbout(helper, COATED_ROW);
        ServerPlayer bare = borer(helper, bore, BARE_ROW);
        ServerPlayer coated = borer(helper, bore, COATED_ROW);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(bare, tickOf(bare));
                GooStreamHandler.streamTick(coated, tickOf(coated));
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(bare);
            helper.getLevel().getServer().getPlayerList().remove(coated);
            int bareBroken = brokenIn(helper, BARE_ROW);
            int coatedBroken = brokenIn(helper, COATED_ROW);
            helper.assertTrue(coatedBroken > bareBroken, String.format(SHOULD_BREAK_MORE, coatedBroken, bareBroken));
            int bored = mortarLayersAbout(helper, COATED_ROW);
            int unbored = mortarLayersAbout(helper, CONTROL_ROW);
            helper.assertTrue(bored < layersBefore && bored < unbored,
                    String.format(SHOULD_DRAIN, layersBefore, unbored, bored));
            helper.succeed();
        });
    }

    private static GooStreamPayload tickOf(ServerPlayer player) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.ROCK), ROCK_BORE.toString(),
                player.getEyePosition(), player.getEyePosition());
    }

    /**
     * Lays a stone wall east of a row's borer, with stone under the cell
     * before the wall at the borer's feet, and where coated a full Mortar
     * cell on that stone and a second full cell on it, at the eye line.
     *
     * @param helper the gametest helper
     * @param row    the row
     * @param coated whether the wall is fronted by Mortar
     */
    private static void layWall(GameTestHelper helper, int row, boolean coated) {
        for (int east = WALL_FROM_EAST; east <= WALL_TO_EAST; east++) {
            for (int up = WALL_LOWEST; up <= WALL_HIGHEST; up++) {
                for (int across = -WALL_HALF_SPAN; across <= WALL_HALF_SPAN; across++) {
                    helper.setBlock(new BlockPos(east, up, row + across), Blocks.STONE);
                }
            }
        }
        BlockPos beforeTheWall = new BlockPos(WALL_FROM_EAST - 1, STAND_HEIGHT, row);
        helper.setBlock(beforeTheWall.below(), Blocks.STONE);
        if (coated) {
            for (BlockPos cell : new BlockPos[] {beforeTheWall, beforeTheWall.above()}) {
                helper.setBlock(cell, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                        .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
            }
        }
    }

    private static int brokenIn(GameTestHelper helper, int row) {
        int broken = 0;
        for (int east = WALL_FROM_EAST; east <= WALL_TO_EAST; east++) {
            for (int up = WALL_LOWEST; up <= WALL_HIGHEST; up++) {
                for (int across = -WALL_HALF_SPAN; across <= WALL_HALF_SPAN; across++) {
                    broken += helper.getBlockState(new BlockPos(east, up, row + across)).is(Blocks.STONE) ? 0 : 1;
                }
            }
        }
        return broken;
    }

    /**
     * The Mortar layers standing anywhere about a row, from its borer to the
     * back of its wall, so a layer a cell pours into a neighbour still counts.
     *
     * @param helper the gametest helper
     * @param row    the row
     * @return the layers
     */
    private static int mortarLayersAbout(GameTestHelper helper, int row) {
        int layers = 0;
        for (int east = 0; east <= WALL_TO_EAST; east++) {
            for (int up = 0; up <= WALL_HIGHEST; up++) {
                for (int across = -MORTAR_HALF_SPAN; across <= MORTAR_HALF_SPAN; across++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(east, up, row + across));
                    layers += MortarCellBlock.layersAt(helper.getLevel(), cell);
                }
            }
        }
        return layers;
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer borer(GameTestHelper helper, AbilityDefinition bore, int row) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, STAND_HEIGHT, row)));
        player.setPos(feet.x, feet.y, feet.z);
        player.setYRot(FACING_EAST);
        player.setXRot(0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, bore);
        return player;
    }
}
