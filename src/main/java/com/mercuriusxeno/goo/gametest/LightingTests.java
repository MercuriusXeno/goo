package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * The type the test-resources datapack adds, which no code names.
     */
    private static final ResourceKey<GooTypeDefinition> DATAPACK_TYPE = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "seventeenth"));
    /**
     * The light_level the seventeenth type's JSON carries, against blaze's.
     */
    private static final int DATAPACK_LIGHT_LEVEL = 8;
    private static final int BLAZE_LIGHT_LEVEL = 15;
    /**
     * Half a canister, which is the saturation_fill both types name, so the
     * sqrt curve reaches 1 and emission reads each type's light_level whole.
     */
    private static final int SATURATION_VOLUME = ContainerCapacity.CANISTER_BASE / 2;

    private static final String CANISTER_EMITS = "Filled canister should report goo light emission";
    private static final String DARK_BEFORE_KICK = "Air above an unsynced canister should hold no block light";
    private static final String NEIGHBOUR_LIT_AFTER_SYNC = "Air above a filled canister should hold block light after sync";
    private static final String NEIGHBOUR_LIT_AFTER_LOAD = "Air above a loaded canister should hold block light after onLoad";
    private static final String DATAPACK_LIGHT_IGNORED =
            "A canister of the datapack type should emit the light_level its JSON names, and emitted ";
    private static final String BLAZE_LIGHT_IGNORED =
            "A canister of blaze should emit the light_level blaze's JSON names, and emitted ";
    private static final String LIGHTS_MATCH =
            "Two types whose JSONs name different light_levels should not emit the same light";

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
     * A type's light_level in its JSON is what a canister of it emits, read on
     * a type only the test-resources datapack adds and on a bundled one. Each
     * is filled to the saturation_fill both JSONs name, where the sqrt curve
     * reaches 1, so the emission reads back as that type's own light_level and
     * the two differ (decision type-json-light-fields).
     *
     * @param helper the gametest helper
     */
    public static void datapackLightLevelDrivesCanisterEmission(GameTestHelper helper) {
        int datapackLight = canisterEmissionOf(helper, DATAPACK_TYPE);
        int blazeLight = canisterEmissionOf(helper, GooTypes.BLAZE);

        helper.assertTrue(datapackLight == DATAPACK_LIGHT_LEVEL, DATAPACK_LIGHT_IGNORED + datapackLight);
        helper.assertTrue(blazeLight == BLAZE_LIGHT_LEVEL, BLAZE_LIGHT_IGNORED + blazeLight);
        helper.assertTrue(datapackLight != blazeLight, LIGHTS_MATCH);
        helper.succeed();
    }

    /**
     * Places a fresh canister block holding one canister of the given type at
     * its saturation fill and answers the emission the block entity reports.
     *
     * @param helper the gametest helper
     * @param type   the goo type the canister carries
     * @return the block light the canister block entity reports
     */
    private static int canisterEmissionOf(GameTestHelper helper, ResourceKey<GooTypeDefinition> type) {
        helper.setBlock(BE_POS, Blocks.AIR);
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);

        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setFluidContent(canister,
                new CanisterFluidContent(GooFluids.resource(type), SATURATION_VOLUME));
        be.insertCanister(CENTER_SLOT, canister, false);

        return be.gooLightEmission();
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
