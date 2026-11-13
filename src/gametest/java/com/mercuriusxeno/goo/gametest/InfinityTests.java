package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * A vessel holding 1 mB of infinity and some blaze gains blaze each tick while
 * the infinity stays at 1 mB, and neither an extract nor an empty-hand drain
 * takes the infinity out.
 * decision infinity-sources-whatever-shares-its-vessel
 */
@RegistersGameTests
public final class InfinityTests {
    private static final String INFINITY_FEEDS_THE_VAT = "infinity_feeds_the_vat";
    private static final String INFINITY_FEEDS_THE_CRUCIBLE = "infinity_feeds_the_crucible";
    private static final String INFINITY_FEEDS_THE_CANISTER_SLOT = "infinity_feeds_the_canister_slot";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, INFINITY_FEEDS_THE_VAT, InfinityTests::infinityFeedsTheVat);
        GooTestFunctions.registerTest(registrar, INFINITY_FEEDS_THE_CRUCIBLE, InfinityTests::infinityFeedsTheCrucible);
        GooTestFunctions.registerTest(registrar, INFINITY_FEEDS_THE_CANISTER_SLOT, InfinityTests::infinityFeedsTheCanisterSlot);
    }


    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final ResourceKey<GooTypeDefinition> INFINITY = GooTypes.INFINITY;
    private static final ResourceKey<GooTypeDefinition> BLAZE = GooTypes.BLAZE;
    private static final int ONE_MB = 1;
    private static final int BLAZE_START = 100;
    private static final int CANISTER_SLOT = 4;
    /** Ticks the vessel sources before the reading; 1 mB at rate 1 adds 1 mB a tick. */
    private static final int SOURCING_TICKS = 20;
    private static final String BLAZE_GREW = "Blaze grew from the infinity: ";
    private static final String EXTRACT_REFUSED = "An extract of infinity took nothing";

    private InfinityTests() {
    }

    /**
     * A vat sources blaze from its infinity and refuses to extract the infinity.
     *
     * @param helper the gametest helper
     */
    public static void infinityFeedsTheVat(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        VatBlockEntity vat = helper.getBlockEntity(BE_POS, VatBlockEntity.class);
        vat.insertGoo(INFINITY, ONE_MB);
        vat.insertGoo(BLAZE, BLAZE_START);
        helper.runAfterDelay(SOURCING_TICKS, () -> {
            assertSourced(helper, vat::getContents);
            helper.assertValueEqual(vat.extractGoo(INFINITY, ONE_MB), 0, EXTRACT_REFUSED);
            helper.succeed();
        });
    }

    /**
     * A crucible sources blaze from its infinity and refuses to extract the
     * infinity.
     *
     * @param helper the gametest helper
     */
    public static void infinityFeedsTheCrucible(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        crucible.insertGoo(INFINITY, ONE_MB);
        crucible.insertGoo(BLAZE, BLAZE_START);
        helper.runAfterDelay(SOURCING_TICKS, () -> {
            assertSourced(helper, crucible::getReservoir);
            helper.assertValueEqual(crucible.extractGoo(INFINITY, ONE_MB), 0, EXTRACT_REFUSED);
            helper.succeed();
        });
    }

    /**
     * A canister in a canister block's slot sources blaze from its infinity, on the block's tick.
     *
     * @param helper the gametest helper
     */
    public static void infinityFeedsTheCanisterSlot(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity canister = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        canister.insertCanister(CANISTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        canister.insertGoo(CANISTER_SLOT, INFINITY, ONE_MB);
        canister.insertGoo(CANISTER_SLOT, BLAZE, BLAZE_START);
        Supplier<GooContents> held = () -> new GooContents(canister.getSlotFluidContent(CANISTER_SLOT).gooVolumes());
        helper.runAfterDelay(SOURCING_TICKS, () -> {
            assertSourced(helper, held);
            helper.succeed();
        });
    }

    private static void assertSourced(GameTestHelper helper, Supplier<GooContents> held) {
        GooContents contents = held.get();
        int blaze = contents.getVolume(BLAZE);
        helper.assertTrue(blaze > BLAZE_START, BLAZE_GREW + blaze);
    }

}
