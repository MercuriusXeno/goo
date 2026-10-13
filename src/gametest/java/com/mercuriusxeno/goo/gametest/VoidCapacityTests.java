package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.lab.LabKit;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for void held as ordinary goo growing a placed vat's capacity, the
 * void drain it refuses while the rest needs that void, and the grown capacity
 * through a save and the item form (decisions void-goo-grows-a-container and
 * each-void-mb-adds-n-plus-one-capacity).
 */
@RegistersGameTests
public final class VoidCapacityTests {
    private static final String VOID_GROWS_A_VAT = "void_grows_a_vat";
    private static final String LAB_KIT_VAT_HOLDS_VOID = "lab_kit_vat_holds_void";

    private static final BlockPos VAT_POS = new BlockPos(1, 1, 1);
    /** The void that first grows a canister to a vat's base; here it grows the vat past its own. */
    private static final int VOID_HELD = 8_062;
    /** The rock past the vat's base that keeps 45 mB of void needed. */
    private static final int ROCK_OVER_BASE = 1_000;
    /** The least void whose growth past its own volume covers ROCK_OVER_BASE: 45*46/2 = 1,035. */
    private static final int VOID_NEEDED = 45;
    /** The capacity the kit vat's void grows it to. */
    private static final int KIT_VAT_CAPACITY = 1 << 30;

    private static final String VOID_ACCEPTED = "The vat takes the void whole";
    private static final String CAPACITY_GROWN = "The vat's capacity grows by the void it holds";
    private static final String FILLED_PAST_BASE = "The vat fills past its base capacity";
    private static final String DRAIN_REFUSED = "A void drain stops where the rest would overflow";
    private static final String RELOAD_REQUIRED = "The saved vat reloads";
    private static final String RELOADED_CAPACITY = "The reloaded vat keeps the capacity its void grows";
    private static final String ITEM_CAPACITY = "The vat item keeps the capacity its void grows";
    private static final String ITEM_ROOM = "The vat item takes only the room its void leaves";
    private static final String KIT_HOLDS_VOID = "The kit vat holds the void that grows it to 2^30 mB";
    private static final String KIT_FITS = "The kit vat holds no more than its capacity";

    private VoidCapacityTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VOID_GROWS_A_VAT, VoidCapacityTests::voidGrowsAVat);
        GooTestFunctions.registerTest(registrar, LAB_KIT_VAT_HOLDS_VOID, VoidCapacityTests::labKitVatHoldsVoid);
    }

    /**
     * Void entering a placed vat grows it past its base, rock fills that room,
     * a void drain keeps the void the rock needs, and a save and the item form
     * keep the capacity the remaining void grows.
     *
     * @param helper the gametest helper
     */
    public static void voidGrowsAVat(GameTestHelper helper) {
        helper.setBlock(VAT_POS, GooBlocks.VAT.get());
        VatBlockEntity vat = helper.getBlockEntity(VAT_POS, VatBlockEntity.class);

        helper.assertValueEqual(vat.insertGoo(GooTypes.VOID, VOID_HELD), VOID_HELD, VOID_ACCEPTED);
        helper.assertValueEqual(vat.getCapacity(), ContainerCapacity.vatCapacity(VOID_HELD), CAPACITY_GROWN);
        vat.insertGoo(GooTypes.ROCK, ContainerCapacity.VAT_BASE + ROCK_OVER_BASE);
        helper.assertTrue(vat.getFluidHandler().totalVolume() > ContainerCapacity.VAT_BASE, FILLED_PAST_BASE);

        helper.assertValueEqual(vat.extractGoo(GooTypes.VOID, VOID_HELD), VOID_HELD - VOID_NEEDED, DRAIN_REFUSED);
        int capacity = ContainerCapacity.vatCapacity(VOID_NEEDED);
        helper.assertValueEqual(vat.getCapacity(), capacity, CAPACITY_GROWN);

        BlockPos at = helper.absolutePos(VAT_POS);
        CompoundTag saved = vat.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity reloaded = BlockEntity.loadStatic(at, helper.getLevel().getBlockState(at), saved,
                helper.getLevel().registryAccess());
        helper.assertTrue(reloaded instanceof VatBlockEntity, RELOAD_REQUIRED);
        helper.assertValueEqual(((VatBlockEntity) reloaded).getCapacity(), capacity, RELOADED_CAPACITY);

        ItemStack item = new ItemStack(GooItems.VAT.get());
        item.applyComponents(vat.collectComponents());
        GooContents carried = VatBlockItem.getGooContents(item);
        helper.assertValueEqual(carried.capacityOver(ContainerCapacity.VAT_BASE), capacity, ITEM_CAPACITY);
        long room = capacity - carried.totalVolume();
        helper.assertValueEqual((long) VatBlockItem.addGoo(item, GooTypes.ROCK, ROCK_OVER_BASE), room, ITEM_ROOM);
        helper.succeed();
    }

    /**
     * The lab kit's vat holds the void that grows it to 2^30 mB and every
     * other type within that capacity.
     *
     * @param helper the gametest helper
     */
    public static void labKitVatHoldsVoid(GameTestHelper helper) {
        GooContents kit = VatBlockItem.getGooContents(LabKit.stockedVat(helper.getLevel()));
        int capacity = kit.capacityOver(ContainerCapacity.VAT_BASE);

        helper.assertValueEqual(kit.getVolume(GooTypes.VOID),
                ContainerCapacity.voidToReach(ContainerCapacity.VAT_BASE, KIT_VAT_CAPACITY), KIT_HOLDS_VOID);
        helper.assertTrue(kit.totalVolume() <= capacity, KIT_FITS);
        helper.succeed();
    }
}
