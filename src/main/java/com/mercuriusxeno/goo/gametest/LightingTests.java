package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Gametests for goo light emission held in block entity contents. Proves
 * that a sync enqueues the recompute and the server light engine's own tick
 * propagates it, with no manual drain (decision light-kick-never-drains).
 */
public final class LightingTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos AIR_ABOVE = BE_POS.above();
    private static final int CENTER_SLOT = 4;
    private static final int TEST_VOLUME = 500;

    private static final String CANISTER_EMITS = "Filled canister should report goo light emission";
    private static final String DARK_BEFORE_KICK = "Air above an unsynced canister should hold no block light";
    private static final String NEIGHBOUR_LIT_AFTER_SYNC = "Air above a filled canister should hold block light after sync";
    private static final String NEIGHBOUR_LIT_AFTER_LOAD = "Air above a loaded canister should hold block light after onLoad";

    private LightingTests() {}

    /**
     * A canister holding a lit goo type brightens the adjacent air after
     * {@link BlockEntitySync#markDirtyAndSync}, once the engine ticks. The
     * threaded engine drains on its own worker, and the game test server
     * ticks without sleeping, so the assertion polls until the test's tick
     * budget runs out rather than reading at a fixed tick.
     *
     * @param helper the gametest helper
     */
    public static void filledCanisterLightsNeighbour(GameTestHelper helper) {
        CanisterBlockEntity be = placeFilledCanister(helper);

        BlockEntitySync.markDirtyAndSync(be);

        helper.succeedWhen(() -> helper.assertTrue(blockLightAt(helper, AIR_ABOVE) > 0, NEIGHBOUR_LIT_AFTER_SYNC));
    }

    /**
     * A canister whose contents arrived without a sync, the shape a chunk
     * load produces (blockstate placed, BE contents read after), brightens
     * the adjacent air once {@code onLoad} runs.
     *
     * @param helper the gametest helper
     */
    public static void loadedCanisterLightsNeighbour(GameTestHelper helper) {
        CanisterBlockEntity be = placeFilledCanister(helper);
        helper.assertTrue(blockLightAt(helper, AIR_ABOVE) == 0, DARK_BEFORE_KICK);

        be.onLoad();

        helper.succeedWhen(() -> helper.assertTrue(blockLightAt(helper, AIR_ABOVE) > 0, NEIGHBOUR_LIT_AFTER_LOAD));
    }

    /**
     * Places a canister block and seats a blaze-filled canister item in its
     * centre slot. {@code insertCanister} kicks no lighting itself, so the
     * block reports emission while its neighbours stay dark until a kick.
     *
     * @param helper the gametest helper
     * @return the canister block entity holding the filled canister
     */
    private static CanisterBlockEntity placeFilledCanister(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);

        FluidResource blazeFluid = GooFluids.resource(GooTypes.BLAZE);
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setFluidContent(canister, new CanisterFluidContent(blazeFluid, TEST_VOLUME));
        be.insertCanister(CENTER_SLOT, canister, false);
        helper.assertTrue(be.gooLightEmission() > 0, CANISTER_EMITS);
        return be;
    }

    /**
     * Reads the server light engine's block light at a structure-relative position.
     *
     * @param helper the gametest helper
     * @param pos    the structure-relative position
     * @return block light in [0, 15]
     */
    private static int blockLightAt(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(pos));
    }
}
