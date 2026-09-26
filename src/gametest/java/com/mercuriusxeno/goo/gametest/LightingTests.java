package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import java.util.Map;

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
    /** One bucket of blaze: far below a vat's saturation, above the light floor. */
    private static final int VAT_FILL_STEP = 1000;
    /** Ticks for the light check a placement queues to drain before the next step. */
    private static final int PLACEMENT_SETTLE_TICKS = 10;

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

    private static final String STEP_ONE_FILLED = "one filled vat";
    private static final String STEP_SECOND_PLACED = "second vat placed";
    private static final String STEP_THIRD_PLACED = "third vat placed";
    private static final String STEP_STACK_FILLED = "stack filled to the third vat";
    private static final String TOP_VAT_EMPTY = "Filling the stack should land goo in the third vat";
    private static final String STACK_VAT_DARK = "After %s, the vat at y=%d emitting %d should hold block light,"
            + " and read %d at the vat and %d beside it";
    private static final String PACKET_VAT_DARK =
            "A vat whose blaze arrived by data packet should hold its own emission %d as block light, and read %d";
    private static final String PAYLOAD_NOT_FULL =
            "The replayed payload should fill the vat with blaze to full emission, and emitted %d";

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
     * The operator's repro for a vat stack, read on the server light engine
     * with no other block placed: one vat filled with blaze through its own
     * insert path, a second vat placed atop, a third atop that, then the stack
     * filled until goo reaches the third vat. After each step every vat's own
     * position and the air beside it must hold block light before the next
     * step runs.
     *
     * <p>This read held green before the fix too: the server lights the stack
     * correctly, and the stale light was the client's, which
     * {@link #vatDataPacketLightsVat} pins.
     *
     * @param helper the gametest helper
     */
    public static void vatStackLightsWithItsGoo(GameTestHelper helper) {
        BlockPos bottom = BE_POS;
        BlockPos middle = bottom.above();
        BlockPos top = middle.above();
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(bottom, GooBlocks.VAT.get());
                    vatAt(helper, bottom).insertGoo(GooTypes.BLAZE, VAT_FILL_STEP);
                })
                .thenWaitUntil(() -> assertStackLit(helper, bottom, STEP_ONE_FILLED))
                .thenExecute(() -> helper.setBlock(middle, GooBlocks.VAT.get()))
                .thenWaitUntil(() -> assertStackLit(helper, middle, STEP_SECOND_PLACED))
                .thenExecute(() -> helper.setBlock(top, GooBlocks.VAT.get()))
                .thenWaitUntil(() -> assertStackLit(helper, top, STEP_THIRD_PLACED))
                .thenExecute(() -> fillUntilTopHoldsGoo(helper, bottom, top))
                .thenWaitUntil(() -> assertStackLit(helper, top, STEP_STACK_FILLED))
                .thenSucceed();
    }

    /**
     * A vat whose contents arrive by block entity data packet lights its own
     * position, the route a client takes. The server sends light update
     * packets only to players on the view-distance edge
     * ({@code ChunkMap.getPlayers(pos, true)} from
     * {@code ChunkHolder.broadcastChanges}), so a client near the vat lights it
     * with its own engine, and the data packet is the only word it gets of new
     * contents. The test replays that packet's payload on the server vat,
     * since no client runs headless: the payload is captured with blaze
     * loaded, the vat is emptied without a kick, then {@code onDataPacket}
     * applies the payload.
     *
     * <p>Red before the fix: {@code onDataPacket} applied the contents and
     * rechecked no light, so the assertion "should hold its own emission 15
     * as block light" failed at tick 102, reading 9, the light bled in from
     * neighbouring tests.
     *
     * @param helper the gametest helper
     */
    public static void vatDataPacketLightsVat(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(BE_POS, GooBlocks.VAT.get()))
                .thenIdle(PLACEMENT_SETTLE_TICKS)
                .thenExecute(() -> replayFilledPayload(helper, vatAt(helper, BE_POS)))
                .thenWaitUntil(() -> {
                    int emission = vatAt(helper, BE_POS).gooLightEmission();
                    int atVat = blockLightAt(helper, BE_POS);
                    helper.assertTrue(atVat >= emission, String.format(PACKET_VAT_DARK, emission, atVat));
                })
                .thenSucceed();
    }

    /**
     * Captures the vat's update payload with blaze loaded, empties the vat
     * without a kick, then applies the payload through {@code onDataPacket}.
     *
     * @param helper the gametest helper
     * @param vat    the empty vat the payload lands in
     */
    private static void replayFilledPayload(GameTestHelper helper, VatBlockEntity vat) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        vat.getFluidHandler().loadFrom(new GooContents(Map.of(GooTypes.BLAZE, vat.getCapacity())));
        CompoundTag payload = vat.getUpdateTag(registries);
        vat.getFluidHandler().loadFrom(GooContents.EMPTY);

        vat.onDataPacket(null, TagValueInput.create(ProblemReporter.DISCARDING, registries, payload));

        helper.assertTrue(vat.gooLightEmission() == BLAZE_LIGHT_LEVEL,
                String.format(PAYLOAD_NOT_FULL, vat.gooLightEmission()));
    }

    /**
     * Inserts blaze into the top vat a capacity at a time, which
     * VatStackRedistributor settles bottom-up, until the top vat holds goo.
     *
     * @param helper the gametest helper
     * @param bottom the bottom vat position
     * @param top    the top vat position
     */
    private static void fillUntilTopHoldsGoo(GameTestHelper helper, BlockPos bottom, BlockPos top) {
        VatBlockEntity topVat = vatAt(helper, top);
        for (BlockPos pos = bottom; topVat.isEmpty() && pos.getY() <= top.getY(); pos = pos.above()) {
            topVat.insertGoo(GooTypes.BLAZE, topVat.getCapacity());
        }
        helper.assertTrue(!topVat.isEmpty(), TOP_VAT_EMPTY);
    }

    /**
     * Asserts block light above 0, and at least the vat's own emission, at every vat from {@link #BE_POS} up to
     * {@code top} and at the air beside each.
     *
     * @param helper the gametest helper
     * @param top    the highest vat position in the stack
     * @param step   the repro step, named in the failure
     */
    private static void assertStackLit(GameTestHelper helper, BlockPos top, String step) {
        for (BlockPos pos = BE_POS; pos.getY() <= top.getY(); pos = pos.above()) {
            int emission = vatAt(helper, pos).gooLightEmission();
            int atVat = blockLightAt(helper, pos);
            int beside = blockLightAt(helper, pos.east());
            boolean lit = atVat > 0 && atVat >= emission && beside > 0 && beside >= emission - 1;
            helper.assertTrue(lit, String.format(STACK_VAT_DARK, step, pos.getY(), emission, atVat, beside));
        }
    }

    private static VatBlockEntity vatAt(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, VatBlockEntity.class);
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
