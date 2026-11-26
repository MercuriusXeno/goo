package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooInsert;
import com.mercuriusxeno.goo.item.GooStacks;
import net.minecraft.world.level.GameType;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Every store of goo serves one shared remainder ledger through GooCapabilities, and
 * a remainder credited through it survives the store's save and load.
 * decision one-remainder-capability-injected-into-every-fluid-capability
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 */
@RegistersGameTests
public final class RemainderCapabilityTests {

    private static final String REMAINDER_CAPABILITY_SURVIVES_SAVE_AND_LOAD =
            "remainder_capability_survives_save_and_load";
    private static final String VAT_GOO_SURVIVES_A_BREAK = "vat_goo_survives_a_break";
    private static final String CRUCIBLE_REMAINDER_SURVIVES_A_BREAK = "crucible_remainder_survives_a_break";
    private static final String CRUCIBLE_LOST = "The crucible placed from the broken crucible's item should read 1/3 rock, read %s";
    private static final int VAT_ROCK = 100;
    private static final String VAT_LOST = "The vat placed from the broken vat's item should hold %s, held %s";

    private static final GooFraction THIRD = GooFraction.of(1, 3);
    private static final BlockPos STORE_POS = new BlockPos(1, 1, 1);
    private static final int FIRST_SLOT = 0;
    private static final double DROP_REACH = 2;
    private static final String NOT_SERVED = "The %s should serve a remainder ledger";
    private static final String LOST = "The %s's remainder should read 1/3 rock after a save and load, read %s";
    private static final String NO_DROP = "Breaking the store should drop its item";

    private RemainderCapabilityTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, REMAINDER_CAPABILITY_SURVIVES_SAVE_AND_LOAD,
                RemainderCapabilityTests::remainderCapabilitySurvivesSaveAndLoad);
        GooTestFunctions.registerTest(registrar, VAT_GOO_SURVIVES_A_BREAK,
                RemainderCapabilityTests::vatGooSurvivesABreak);
        GooTestFunctions.registerTest(registrar, CRUCIBLE_REMAINDER_SURVIVES_A_BREAK,
                RemainderCapabilityTests::crucibleRemainderSurvivesABreak);
        GooTestFunctions.registerTest(registrar, POURED_GOO_HANDS_ITS_FRACTION,
                RemainderCapabilityTests::pouredGooHandsItsFractionToTheCanister);
        GooTestFunctions.registerTest(registrar, POURED_PART_ALONE_REACHES_THE_CANISTER,
                RemainderCapabilityTests::pouredPartAloneReachesTheCanister);
        GooTestFunctions.registerTest(registrar, POURED_PART_ALONE_STAYS_WHERE_REFUSED,
                RemainderCapabilityTests::pouredPartAloneStaysWhereRefused);
    }

    private static final String POURED_PART_ALONE_REACHES_THE_CANISTER = "poured_part_alone_reaches_the_canister";
    private static final String POURED_PART_ALONE_STAYS_WHERE_REFUSED = "poured_part_alone_stays_where_refused";
    private static final GooFraction TWO_THIRDS = GooFraction.of(2, 3);
    private static final int PART_ALONE_SLOT_WHOLE = 2;
    private static final String PART_ALONE_LOST = "A 1/3 mB zombie stack poured into a canister slot holding 2 mB of zombie "
            + "with a 2/3 remainder should leave it 3 mB with no remainder and the stack empty; the slot holds %s "
            + "with %s, the stack empty %s at %s";
    private static final String PART_ALONE_TAKEN = "A 1/3 mB zombie stack poured at a slot holding no canister should "
            + "stay 1/3 mB; it reads ";

    private static final String POURED_GOO_HANDS_ITS_FRACTION = "poured_goo_hands_its_fraction";
    private static final GooFraction HALF = GooFraction.of(1, 2);
    private static final GooFraction SIXTH = GooFraction.of(1, 6);
    private static final GooFraction POURED = GooFraction.of(14, 3);
    private static final int POURED_INTO = 5;
    private static final String POUR_LOST = "A goo stack at 4 and 2/3 mB poured into a canister holding a 1/2 remainder "
            + "should leave it 5 mB with a 1/6 remainder and the stack empty; the canister holds %s with %s, "
            + "the stack empty %s at %s";

    /**
     * A goo stack at 4 and 2/3 mB poured whole into a canister holding a 1/2 remainder of
     * its type, through the sink the canister block hands GooInsert, leaves the canister
     * holding 5 mB with a 1/6 remainder and the stack consumed.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     *
     * @param helper the gametest helper
     */
    public static void pouredGooHandsItsFractionToTheCanister(GameTestHelper helper) {
        helper.setBlock(STORE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity canister = helper.getBlockEntity(STORE_POS, CanisterBlockEntity.class);
        canister.insertCanister(FIRST_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        blockLedger(helper, STORE_POS, FIRST_SLOT).creditExact(GooTypes.ZOMBIE, HALF);
        ItemStack goo = GooStacks.createForOutput(GooTypes.ZOMBIE, POURED);
        GooInsert.pour(goo, helper.makeMockPlayer(GameType.SURVIVAL), canister.gooSinkFor(FIRST_SLOT));
        int held = CanisterItem.getFluidContent(canister.containerState().getCanister(FIRST_SLOT)).volumeOf(GooTypes.ZOMBIE);
        GooFraction remainder = blockLedger(helper, STORE_POS, FIRST_SLOT).remainders().of(GooTypes.ZOMBIE);
        helper.assertTrue(held == POURED_INTO && SIXTH.equals(remainder) && goo.isEmpty(),
                String.format(POUR_LOST, held, remainder, goo.isEmpty(), GooStacks.exactValueOf(goo)));
        helper.succeed();
    }

    /**
     * A goo stack holding only 1/3 mB of zombie, poured through the sink the canister block
     * hands GooInsert into a slot holding 2 mB of zombie with a 2/3 remainder, leaves the slot
     * holding 3 mB with no remainder and the stack consumed.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     *
     * @param helper the gametest helper
     */
    public static void pouredPartAloneReachesTheCanister(GameTestHelper helper) {
        CanisterBlockEntity canister = placeCanisterHolding(helper);
        canister.gooSinkFor(FIRST_SLOT).accept(GooTypes.ZOMBIE, PART_ALONE_SLOT_WHOLE);
        blockLedger(helper, STORE_POS, FIRST_SLOT).creditExact(GooTypes.ZOMBIE, TWO_THIRDS);
        ItemStack goo = GooStacks.createForOutput(GooTypes.ZOMBIE, THIRD);
        GooInsert.pour(goo, helper.makeMockPlayer(GameType.SURVIVAL), canister.gooSinkFor(FIRST_SLOT));
        int held = CanisterItem.getFluidContent(canister.containerState().getCanister(FIRST_SLOT)).volumeOf(GooTypes.ZOMBIE);
        GooFraction remainder = blockLedger(helper, STORE_POS, FIRST_SLOT).remainders().of(GooTypes.ZOMBIE);
        helper.assertTrue(held == PART_ALONE_SLOT_WHOLE + 1 && remainder.isZero() && goo.isEmpty(),
                String.format(PART_ALONE_LOST, held, remainder, goo.isEmpty(), GooStacks.exactValueOf(goo)));
        helper.succeed();
    }

    /**
     * A goo stack holding only a part, poured at a canister slot holding no canister, stays
     * in hand whole: a sink that would refuse the type is credited nothing.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     *
     * @param helper the gametest helper
     */
    public static void pouredPartAloneStaysWhereRefused(GameTestHelper helper) {
        helper.setBlock(STORE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity canister = helper.getBlockEntity(STORE_POS, CanisterBlockEntity.class);
        ItemStack goo = GooStacks.createForOutput(GooTypes.ZOMBIE, THIRD);
        GooInsert.pour(goo, helper.makeMockPlayer(GameType.SURVIVAL), canister.gooSinkFor(FIRST_SLOT));
        helper.assertTrue(THIRD.equals(GooStacks.exactValueOf(goo)),
                PART_ALONE_TAKEN + GooStacks.exactValueOf(goo));
        helper.succeed();
    }

    private static CanisterBlockEntity placeCanisterHolding(GameTestHelper helper) {
        helper.setBlock(STORE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity canister = helper.getBlockEntity(STORE_POS, CanisterBlockEntity.class);
        canister.insertCanister(FIRST_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        return canister;
    }

    /**
     * A crucible holding a remainder of 1/3 rock, broken, drops an item carrying it,
     * and the crucible placed from the item reads 1/3 rock.
     *
     * @param helper the gametest helper
     */
    public static void crucibleRemainderSurvivesABreak(GameTestHelper helper) {
        helper.setBlock(STORE_POS, GooBlocks.CRUCIBLE.get());
        blockLedger(helper, STORE_POS, null).creditExact(GooTypes.ROCK, THIRD);
        helper.getLevel().destroyBlock(helper.absolutePos(STORE_POS), true);
        ItemStack dropped = droppedItem(helper, STORE_POS, GooItems.CRUCIBLE.get());
        helper.setBlock(STORE_POS, GooBlocks.CRUCIBLE.get());
        helper.getBlockEntity(STORE_POS, com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity.class)
                .applyComponentsFromItemStack(dropped);
        GooFraction restored = blockLedger(helper, STORE_POS, null).remainders().of(GooTypes.ROCK);
        helper.assertTrue(THIRD.equals(restored), String.format(CRUCIBLE_LOST, restored));
        helper.succeed();
    }

    /**
     * A vat holding 100 rock and a remainder of 1/3 rock, broken, drops an item
     * carrying both, and the vat placed from the item holds both.
     *
     * @param helper the gametest helper
     */
    public static void vatGooSurvivesABreak(GameTestHelper helper) {
        GooContents held = new GooContents(java.util.Map.of(GooTypes.ROCK, VAT_ROCK),
                com.mercuriusxeno.goo.data.GooRemainders.EMPTY.with(GooTypes.ROCK, THIRD));
        helper.setBlock(STORE_POS, GooBlocks.VAT.get());
        helper.getBlockEntity(STORE_POS, VatBlockEntity.class).getFluidHandler().loadFrom(held);
        helper.getLevel().destroyBlock(helper.absolutePos(STORE_POS), true);
        ItemStack dropped = droppedItem(helper, STORE_POS, GooItems.VAT.get());
        helper.setBlock(STORE_POS, GooBlocks.VAT.get());
        VatBlockEntity placed = helper.getBlockEntity(STORE_POS, VatBlockEntity.class);
        placed.applyComponentsFromItemStack(dropped);
        GooContents restored = placed.getFluidHandler().toGooContents();
        helper.assertTrue(held.equals(restored), String.format(VAT_LOST, held, restored));
        helper.succeed();
    }

    /**
     * The crucible, the vat, the plexer, the canister shelf and the hub each serve a
     * ledger through REMAINDER_BLOCK, the player through REMAINDER_ENTITY and the
     * canister through REMAINDER_ITEM; 1/3 rock credited through each reads 1/3 rock
     * after the store's saved form loads back.
     *
     * @param helper the gametest helper
     */
    public static void remainderCapabilitySurvivesSaveAndLoad(GameTestHelper helper) {
        assertBlockKeeps(helper, "crucible", GooBlocks.CRUCIBLE.get(), null);
        assertBlockKeeps(helper, "vat", GooBlocks.VAT.get(), null);
        assertBlockKeeps(helper, "plexer", GooBlocks.PLEXER.get(), null);
        helper.setBlock(STORE_POS, GooBlocks.CANISTER.get());
        helper.getBlockEntity(STORE_POS, CanisterBlockEntity.class)
                .insertCanister(FIRST_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        assertLoadedBlockKeeps(helper, "canister shelf", FIRST_SLOT);
        helper.setBlock(STORE_POS, GooBlocks.HUB.get());
        helper.getBlockEntity(STORE_POS, HubBlockEntity.class)
                .insertCanister(FIRST_SLOT, new ItemStack(GooItems.CANISTER.get()));
        assertLoadedBlockKeeps(helper, "hub", FIRST_SLOT);
        assertPlayerKeeps(helper);
        assertCanisterItemKeeps(helper);
        helper.succeed();
    }

    private static void assertBlockKeeps(GameTestHelper helper, String store, Block block, @Nullable Integer slot) {
        helper.setBlock(STORE_POS, block);
        assertLoadedBlockKeeps(helper, store, slot);
    }

    /**
     * Credits 1/3 rock through the block's capability, reloads its block entity
     * from its saved form, and reads the capability again.
     */
    private static void assertLoadedBlockKeeps(GameTestHelper helper, String store, @Nullable Integer slot) {
        blockLedger(helper, STORE_POS, slot).creditExact(GooTypes.ROCK, THIRD);
        ServerLevel level = helper.getLevel();
        BlockEntity placed = level.getBlockEntity(helper.absolutePos(STORE_POS));
        CompoundTag saved = placed.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(placed.getBlockPos());
        level.setBlockEntity(BlockEntity.loadStatic(placed.getBlockPos(), placed.getBlockState(), saved,
                level.registryAccess()));
        GooFraction kept = blockLedger(helper, STORE_POS, slot).remainders().of(GooTypes.ROCK);
        helper.assertTrue(THIRD.equals(kept), String.format(LOST, store, kept));
    }

    private static IRemainderLedger blockLedger(GameTestHelper helper, BlockPos pos, @Nullable Integer slot) {
        IRemainderLedger ledger = helper.getLevel().getCapability(GooCapabilities.REMAINDER_BLOCK,
                helper.absolutePos(pos), slot);
        helper.assertTrue(ledger != null, String.format(NOT_SERVED, pos));
        return ledger;
    }

    private static void assertPlayerKeeps(GameTestHelper helper) {
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        IRemainderLedger ledger = player.getCapability(GooCapabilities.REMAINDER_ENTITY);
        helper.assertTrue(ledger != null, String.format(NOT_SERVED, "player"));
        ledger.creditExact(GooTypes.ROCK, THIRD);
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        player.saveWithoutId(saved);
        helper.getLevel().getServer().getPlayerList().remove(player);
        ServerPlayer returning = SurvivalPlayers.placeIn(helper);
        returning.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                saved.buildResult()));
        GooFraction kept = GooCapabilities.remainderLedgerOf(returning).remainders().of(GooTypes.ROCK);
        helper.getLevel().getServer().getPlayerList().remove(returning);
        helper.assertTrue(THIRD.equals(kept), String.format(LOST, "player", kept));
    }

    private static void assertCanisterItemKeeps(GameTestHelper helper) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        IRemainderLedger ledger = canister.getCapability(GooCapabilities.REMAINDER_ITEM, ItemAccess.forStack(canister));
        helper.assertTrue(ledger != null, String.format(NOT_SERVED, "canister item"));
        ledger.creditExact(GooTypes.ROCK, THIRD);
        ItemStack loaded = ItemStack.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(
                net.minecraft.nbt.NbtOps.INSTANCE), ItemStack.CODEC.encodeStart(helper.getLevel().registryAccess()
                .createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), canister).getOrThrow()).getOrThrow();
        GooFraction kept = loaded.getCapability(GooCapabilities.REMAINDER_ITEM, ItemAccess.forStack(loaded))
                .remainders().of(GooTypes.ROCK);
        helper.assertTrue(THIRD.equals(kept), String.format(LOST, "canister item", kept));
    }

    private static ItemStack droppedItem(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
        BlockPos at = helper.absolutePos(pos);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(at).inflate(DROP_REACH), entity -> entity.getItem().is(item));
        helper.assertFalse(drops.isEmpty(), NO_DROP);
        ItemStack dropped = drops.getFirst().getItem().copy();
        drops.forEach(ItemEntity::discard);
        return dropped;
    }
}
