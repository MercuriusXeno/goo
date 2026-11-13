package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.block.plexer.CutawayInteractionHelper;
import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for where a canister item places a new canister block: on a top face covering the
 * central 12x12 pixels, and nowhere else (decision canister-support-is-rigid-top-face).
 */
@RegistersGameTests
public final class CanisterPlacementTests {
    private static final String IX_CANISTER_VAT_TOP_PLACES = "ix_canister_vat_top_places";
    private static final String IX_CANISTER_STONE_TOP_PLACES = "ix_canister_stone_top_places";
    private static final String IX_CANISTER_SNEAK_VAT_TOP_PLACES = "ix_canister_sneak_vat_top_places";
    private static final String IX_CANISTER_HUB_ATTACH_SPOT = "ix_canister_hub_attach_spot";
    private static final String IX_CANISTER_PLEXER_TOP_PLACES = "ix_canister_plexer_top_places";
    private static final String IX_CANISTER_PLEXER_CUTAWAY_PLACES = "ix_canister_plexer_cutaway_places";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, IX_CANISTER_VAT_TOP_PLACES, CanisterPlacementTests::vatTopTakesCanister);
        GooTestFunctions.registerTest(registrar, IX_CANISTER_STONE_TOP_PLACES, CanisterPlacementTests::stoneTopTakesCanister);
        GooTestFunctions.registerTest(registrar, IX_CANISTER_SNEAK_VAT_TOP_PLACES, CanisterPlacementTests::sneakClickVatTopTakesCanister);
        GooTestFunctions.registerTest(registrar, IX_CANISTER_HUB_ATTACH_SPOT, CanisterPlacementTests::hubAttachSpotTakesCanisterAbove);
        GooTestFunctions.registerTest(registrar, IX_CANISTER_PLEXER_TOP_PLACES, CanisterPlacementTests::plexerTopTakesCanister);
        GooTestFunctions.registerTest(registrar, IX_CANISTER_PLEXER_CUTAWAY_PLACES,
                CanisterPlacementTests::canisterAimedThroughThePlexerCutawayPlacesInFront);
    }


    private static final BlockPos SUPPORT_POS = new BlockPos(1, 1, 1);
    private static final BlockPos CANISTER_POS = SUPPORT_POS.above();
    private static final int AIMED_SLOT = 0;
    private static final double PIXELS_PER_BLOCK = 16.0;
    private static final double HALF_BLOCK = 0.5;
    private static final int CENTRE_SLOT = 4;

    private CanisterPlacementTests() {}

    /**
     * A canister used on an empty vat's top at slot 0's pixel center places a canister block
     * above the vat holding that canister in slot 0.
     *
     * @param helper the gametest helper
     */
    public static void vatTopTakesCanister(GameTestHelper helper) {
        helper.setBlock(SUPPORT_POS, GooBlocks.VAT.get());
        helper.useBlock(SUPPORT_POS, playerHoldingCanister(helper), aimedSlotTopHit(helper));
        assertCanisterInAimedSlot(helper);
        helper.succeed();
    }

    /**
     * A sneaking player's canister use on an empty vat's top places a canister block on the vat.
     * Runs the server's own click path, ServerPlayerGameMode.useItemOn, which skips the vat's own
     * click for a sneaking player holding an item (decision diagnose-then-fix-sneak-place-above-canister).
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void sneakClickVatTopTakesCanister(GameTestHelper helper) {
        helper.setBlock(SUPPORT_POS, GooBlocks.VAT.get());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(helper.absolutePos(SUPPORT_POS.east(2))));
        player.setShiftKeyDown(true);
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, canister);
        player.gameMode.useItemOn(player, helper.getLevel(), canister, InteractionHand.MAIN_HAND,
            aimedSlotTopHit(helper));
        helper.getLevel().getServer().getPlayerList().remove(player);
        assertCanisterInAimedSlot(helper);
        helper.succeed();
    }

    /**
     * A standing canister click on the hub's intake top, its attach spot, places a canister
     * block above the hub in the centre slot rather than filling a hub slot.
     *
     * @param helper the gametest helper
     */
    public static void hubAttachSpotTakesCanisterAbove(GameTestHelper helper) {
        helper.setBlock(SUPPORT_POS, GooBlocks.HUB.get());
        BlockPos abs = helper.absolutePos(SUPPORT_POS);
        BlockHitResult intakeTop = new BlockHitResult(
            new Vec3(abs.getX() + HALF_BLOCK, abs.getY() + 1.0, abs.getZ() + HALF_BLOCK), Direction.UP, abs, false);
        helper.useBlock(SUPPORT_POS, playerHoldingCanister(helper), intakeTop);
        helper.assertBlockPresent(GooBlocks.CANISTER.get(), CANISTER_POS);
        CanisterBlockEntity canister = helper.getBlockEntity(CANISTER_POS, CanisterBlockEntity.class);
        helper.assertFalse(canister.containerState().getCanister(CENTRE_SLOT).isEmpty(),
            "The canister above the hub should fill the centre slot");
        helper.succeed();
    }

    /**
     * A canister used on the plexer's top face where a slot is allowed places a canister block there,
     * the click passing to the canister rather than opening the plexer's menu
     * (decision canister-placement-wins-over-the-menu).
     *
     * @param helper the gametest helper
     */
    public static void plexerTopTakesCanister(GameTestHelper helper) {
        helper.setBlock(SUPPORT_POS, GooBlocks.PLEXER.get());
        helper.useBlock(SUPPORT_POS, playerHoldingCanister(helper), aimedSlotTopHit(helper));
        assertCanisterInAimedSlot(helper);
        helper.succeed();
    }

    /**
     * A canister aimed through the plexer's cutaway face places a canister block in front of it, where
     * the placement resolver, and so the green preview, puts it; the cutaway takes no click of its own
     * (decision canister-placement-wins-over-the-menu).
     *
     * @param helper the gametest helper
     */
    public static void canisterAimedThroughThePlexerCutawayPlacesInFront(GameTestHelper helper) {
        helper.setBlock(SUPPORT_POS, GooBlocks.PLEXER.get());
        BlockPos abs = helper.absolutePos(SUPPORT_POS);
        Direction facing = helper.getBlockState(SUPPORT_POS).getValue(PlexerBlock.FACING);
        // The cell in front of the cutaway needs a top a canister stands on, as any canister block does.
        helper.setBlock(SUPPORT_POS.relative(facing).below(), Blocks.STONE);
        BlockHitResult cutaway = new BlockHitResult(CutawayInteractionHelper.cutawayWorldCenter(abs, facing), facing,
                abs, false);
        helper.useBlock(SUPPORT_POS, playerHoldingCanister(helper), cutaway);
        helper.assertBlockPresent(GooBlocks.CANISTER.get(), SUPPORT_POS.relative(facing));
        helper.succeed();
    }

    /**
     * A canister used on a stone top places a canister block above it.
     *
     * @param helper the gametest helper
     */
    public static void stoneTopTakesCanister(GameTestHelper helper) {
        helper.setBlock(SUPPORT_POS, Blocks.STONE);
        helper.useBlock(SUPPORT_POS, playerHoldingCanister(helper), aimedSlotTopHit(helper));
        assertCanisterInAimedSlot(helper);
        helper.succeed();
    }

    private static Player playerHoldingCanister(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CANISTER.get()));
        return player;
    }

    /**
     * A hit on the support block's top face at the aimed slot's pixel center.
     *
     * @param helper the gametest helper
     * @return the hit result
     */
    private static BlockHitResult aimedSlotTopHit(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(SUPPORT_POS);
        float[] center = CanisterSlotLayout.SLOT_CENTERS[AIMED_SLOT];
        Vec3 location = new Vec3(abs.getX() + center[0] / PIXELS_PER_BLOCK, abs.getY() + 1.0,
            abs.getZ() + center[1] / PIXELS_PER_BLOCK);
        return new BlockHitResult(location, Direction.UP, abs, false);
    }

    private static void assertCanisterInAimedSlot(GameTestHelper helper) {
        helper.assertBlockPresent(GooBlocks.CANISTER.get(), CANISTER_POS);
        CanisterBlockEntity canister = helper.getBlockEntity(CANISTER_POS, CanisterBlockEntity.class);
        helper.assertFalse(canister.containerState().getCanister(AIMED_SLOT).isEmpty(),
            "The placed canister should fill the aimed slot");
    }
}
