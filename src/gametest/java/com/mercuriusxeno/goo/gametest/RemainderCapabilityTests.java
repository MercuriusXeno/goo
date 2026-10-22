package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooFluids;
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
 * a remainder credited through it survives the store's save and load; a broken
 * plexer carries its banked remainder on its item to the plexer placed from it.
 * decision one-remainder-capability-injected-into-every-fluid-capability
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 */
@RegistersGameTests
public final class RemainderCapabilityTests {

    private static final String REMAINDER_CAPABILITY_SURVIVES_SAVE_AND_LOAD =
            "remainder_capability_survives_save_and_load";
    private static final String PLEXER_REMAINDER_SURVIVES_A_BREAK = "plexer_remainder_survives_a_break";

    private static final GooFraction THIRD = GooFraction.of(1, 3);
    private static final GooFraction TWO_THIRDS = GooFraction.of(2, 3);
    private static final BlockPos STORE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos PLEXER_POS = new BlockPos(2, 1, 2);
    private static final int FIRST_SLOT = 0;
    /** Rock enough to fabricate one smooth basalt worth 64/3 rock. */
    private static final int ROCK_SUPPLY = 100;
    private static final double DROP_REACH = 2;
    private static final String NOT_SERVED = "The %s should serve a remainder ledger";
    private static final String LOST = "The %s's remainder should read 1/3 rock after a save and load, read %s";
    private static final String NOT_FABRICATED = "The plexer should fabricate a smooth basalt from the rock above";
    private static final String NOT_BANKED = "The plexer should bank 2/3 rock, banked %s";
    private static final String NO_DROP = "Breaking the plexer should drop the plexer";
    private static final String UNCARRIED = "The dropped plexer should carry its remainder";
    private static final String NOT_RESTORED = "The placed plexer should read 2/3 rock, read %s";

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
        GooTestFunctions.registerTest(registrar, PLEXER_REMAINDER_SURVIVES_A_BREAK,
                RemainderCapabilityTests::plexerRemainderSurvivesABreak);
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

    /**
     * A plexer fabricates one smooth basalt, which the exact_value_probe datapack
     * prices at 64/3 rock, from a fresh ledger, banking 2/3 rock; broken, it drops
     * an item carrying that remainder, and the plexer placed from the item reads 2/3 rock.
     *
     * @param helper the gametest helper
     */
    public static void plexerRemainderSurvivesABreak(GameTestHelper helper) {
        helper.setBlock(PLEXER_POS, GooBlocks.PLEXER.get());
        helper.setBlock(PLEXER_POS.above(), GooBlocks.CANISTER.get());
        ItemStack rock = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setFluidContent(rock, CanisterFluidContent.of(GooFluids.resource(GooTypes.ROCK), ROCK_SUPPLY));
        helper.getBlockEntity(PLEXER_POS.above(), CanisterBlockEntity.class).insertCanister(FIRST_SLOT, rock, false);
        PlexerBlockEntity plexer = helper.getBlockEntity(PLEXER_POS, PlexerBlockEntity.class);
        plexer.setTargetItem(new ItemStack(net.minecraft.world.level.block.Blocks.SMOOTH_BASALT));
        helper.assertFalse(plexer.tryReconstitute().isEmpty(), NOT_FABRICATED);
        GooFraction banked = blockLedger(helper, PLEXER_POS, null).remainders().of(GooTypes.ROCK);
        helper.assertTrue(TWO_THIRDS.equals(banked), String.format(NOT_BANKED, banked));

        helper.getLevel().destroyBlock(helper.absolutePos(PLEXER_POS), true);
        ItemStack dropped = droppedPlexer(helper);
        helper.setBlock(PLEXER_POS, GooBlocks.PLEXER.get());
        helper.getBlockEntity(PLEXER_POS, PlexerBlockEntity.class).applyComponentsFromItemStack(dropped);

        GooFraction restored = blockLedger(helper, PLEXER_POS, null).remainders().of(GooTypes.ROCK);
        helper.assertTrue(TWO_THIRDS.equals(restored), String.format(NOT_RESTORED, restored));
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

    private static ItemStack droppedPlexer(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(PLEXER_POS);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(at).inflate(DROP_REACH), item -> item.getItem().is(GooItems.PLEXER.get()));
        helper.assertFalse(drops.isEmpty(), NO_DROP);
        ItemStack dropped = drops.getFirst().getItem().copy();
        drops.forEach(ItemEntity::discard);
        helper.assertTrue(dropped.has(GooDataComponents.REMAINDERS.get()), UNCARRIED);
        return dropped;
    }
}
