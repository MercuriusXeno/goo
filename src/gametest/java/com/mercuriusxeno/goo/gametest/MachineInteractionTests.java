package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.plexer.CutawayInteractionHelper;
import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.block.plexer.PlexerRefusalCue;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.lab.LabKit;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for machine block interactions via mock player.
 * Exercises VatInteractionHandler,
 * HubBlockHandlers, PlexerInteractionHelper, and CrucibleInteraction.
 */
@RegistersGameTests
public final class MachineInteractionTests {
    private static final String IX_VAT_GASKET = "ix_vat_gasket_apply";
    private static final String IX_HUB_INSERT = "ix_hub_canister_insert";
    private static final String IX_HUB_PICKUP = "ix_hub_canister_pickup";
    private static final String IX_HUB_POUR_AIMED_ONLY = "ix_hub_pour_aimed_only";
    private static final String IX_PLEXER_TARGET = "ix_plexer_set_target";
    private static final String IX_PLEXER_REFUSES_UNLEARNED = "ix_plexer_refuses_unlearned";
    private static final String IX_PLEXER_FIZZLES_UNTIL_LAB_LEARN = "ix_plexer_fizzles_until_lab_learn";
    private static final String IX_REACTOR_INSERT_PICKUP = "ix_reactor_insert_pickup";
    private static final String IX_CRUCIBLE_BLAZE_ROD_COLD = "ix_crucible_blaze_rod_click_leaves_cold";
    private static final String IX_CRUCIBLE_COLD_ABSORBS_NOTHING = "ix_crucible_cold_absorbs_nothing";
    private static final String IX_CRUCIBLE_BLAZE_ABSORBS = "ix_crucible_blaze_absorbs_item";
    private static final String IX_CRUCIBLE_FLINT_SPARKS = "ix_crucible_flint_and_steel_sparks";
    private static final String IX_CRUCIBLE_SPARK_MELTS_COAL = "ix_crucible_spark_melts_coal";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, IX_VAT_GASKET, MachineInteractionTests::vatGasketApply);
        GooTestFunctions.registerTest(registrar, IX_HUB_INSERT, MachineInteractionTests::hubCanisterInsert);
        GooTestFunctions.registerTest(registrar, IX_HUB_PICKUP, MachineInteractionTests::hubCanisterPickup);
        GooTestFunctions.registerTest(registrar, IX_HUB_POUR_AIMED_ONLY, MachineInteractionTests::hubPourFillsOnlyTheAimedCanister);
        GooTestFunctions.registerTest(registrar, IX_PLEXER_TARGET, MachineInteractionTests::plexerSetTarget);
        GooTestFunctions.registerTest(registrar, IX_PLEXER_REFUSES_UNLEARNED, MachineInteractionTests::plexerRefusesAnUnlearnedTarget);
        GooTestFunctions.registerTest(registrar, IX_PLEXER_FIZZLES_UNTIL_LAB_LEARN, MachineInteractionTests::plexerFizzlesOnAKitPlayerUntilLabLearn);
        GooTestFunctions.registerTest(registrar, IX_REACTOR_INSERT_PICKUP, MachineInteractionTests::reactorCanisterInsertThenSneakPickup);
        GooTestFunctions.registerTest(registrar, IX_CRUCIBLE_BLAZE_ROD_COLD, MachineInteractionTests::crucibleBlazeRodClickLeavesItCold);
        GooTestFunctions.registerTest(registrar, IX_CRUCIBLE_COLD_ABSORBS_NOTHING, MachineInteractionTests::coldCrucibleAbsorbsNothing);
        GooTestFunctions.registerTest(registrar, IX_CRUCIBLE_BLAZE_ABSORBS, MachineInteractionTests::blazeCrucibleAbsorbsItem);
        GooTestFunctions.registerTest(registrar, IX_CRUCIBLE_FLINT_SPARKS, MachineInteractionTests::flintAndSteelSparksColdCrucible);
        GooTestFunctions.registerTest(registrar, IX_CRUCIBLE_SPARK_MELTS_COAL, MachineInteractionTests::sparkedCrucibleMeltsCoalOnItsBlaze);
    }


    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final String VAT_SHOULD_HAVE_CAP = "Vat should have gasket cap after gasket apply";
    private static final String HUB_SHOULD_INSERT = "Hub should have canister after insertion";
    private static final String HUB_SHOULD_PICKUP = "Hub slot should be empty after plain-click pickup";
    private static final String HUB_SHOULD_KEEP_HELD = "Player should still hold a canister after pickup";
    private static final String HUB_SHOULD_HOLD_BOTH = "Player should hold the picked-up canister beside the held one";
    private static final int HUB_SLOT_NORTH = 0;
    private static final int HUB_PICKUP_HAND_COUNT = 2;
    private static final double PIXELS_PER_BLOCK = 16.0;
    private static final String PLEXER_SHOULD_SET = "Plexer should have target item after interaction";
    /** How far, in blocks, the packet's encoded position may sit from the cutaway center. */
    private static final double CUE_EPSILON = 1e-3;
    private static final String BLAZE_ROD_STAYS_WHOLE = "A blaze rod click should leave the held stack whole";
    private static final String BLAZE_ROD_LEAVES_COLD = "A blaze rod click should leave the crucible cold";
    private static final String COLD_CRUCIBLE_REFUSES = "A cold crucible with no fuel goo should leave the item entity standing";
    private static final String BLAZE_CRUCIBLE_ABSORBS = "A crucible holding blaze goo should absorb the item entity";
    private static final int CRUCIBLE_BLAZE_FUEL = 100;
    private static final String SPARK_SUCCEEDS = "A flint-and-steel click on a cold crucible should answer SUCCESS";
    private static final String SPARK_HEATS = "A spark should leave the crucible holding the spark's heat";
    private static final String SPARK_COSTS_ONE = "A spark should cost the flint and steel one durability";
    private static final String HOT_SPARK_CONSUMED = "A flint-and-steel click on a heated crucible should be consumed";
    private static final String HOT_SPARK_FREE = "A consumed click should leave the flint and steel undamaged";
    private static final String COAL_MELTED = "The sparked crucible should melt the coal to the end";
    private static final String COAL_LEFT_BLAZE = "The coal's blaze should stand in the reservoir";
    private static final String COAL_LEFT_ROCK = "The coal's rock should stand in the reservoir";
    private static final String COAL_ENDS_HOT = "The crucible should end able to heat";
    /** Ticks a still item dropped at the basin center takes to land, rest and be consumed. */
    private static final int ABSORB_DELAY = 10;
    private static final double BLOCK_CENTER = 0.5;
    private static final double UPPER_HIT_Y = 0.9;

    private MachineInteractionTests() {
    }

    /**
     * Creates a BlockHitResult targeting the center of the block at BE_POS.
     *
     * @param helper the gametest helper
     * @param face   the face to hit
     * @return the hit result
     */
    private static BlockHitResult hit(GameTestHelper helper, Direction face) {
        BlockPos abs = helper.absolutePos(BE_POS);
        return new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
    }

    /**
     * Vat: right-click with choral gasket on upper half applies GASKET_CAP.
     *
     * @param helper the gametest helper
     */
    public static void vatGasketApply(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CHORAL_GASKET.get()));

        BlockPos abs = helper.absolutePos(BE_POS);
        BlockHitResult topHit = new BlockHitResult(
                new Vec3(abs.getX() + BLOCK_CENTER, abs.getY() + UPPER_HIT_Y, abs.getZ() + BLOCK_CENTER),
                Direction.UP, abs, false);
        helper.useBlock(BE_POS, player, topHit);

        BlockState state = helper.getBlockState(BE_POS);
        helper.assertTrue(state.getValue(VatBlock.GASKET_CAP), VAT_SHOULD_HAVE_CAP);
        helper.succeed();
    }

    /**
     * Hub: right-click with canister item inserts into a slot.
     *
     * @param helper the gametest helper
     */
    public static void hubCanisterInsert(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CANISTER.get()));

        helper.useBlock(BE_POS, player, hit(helper, Direction.UP));

        boolean anyInserted = false;
        for (int i = 0; i < hub.containerState().maxSlots(); i++) {
            if (!hub.getCanister(i).isEmpty()) {
                anyInserted = true;
                break;
            }
        }
        helper.assertTrue(anyInserted, HUB_SHOULD_INSERT);
        helper.succeed();
    }

    /**
     * Hub: a plain click on a filled slot with a canister in hand picks that canister up
     * and leaves the held one in hand. The slot is filled through the same plain click
     * first, so both legs of the rule run through useItemOn (decision hub-plain-click-rule).
     *
     * @param helper the gametest helper
     */
    public static void hubCanisterPickup(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CANISTER.get(), HUB_PICKUP_HAND_COUNT));

        helper.useBlock(BE_POS, player, slotHit(helper, HUB_SLOT_NORTH));
        helper.assertFalse(hub.getCanister(HUB_SLOT_NORTH).isEmpty(), HUB_SHOULD_INSERT);

        helper.useBlock(BE_POS, player, slotHit(helper, HUB_SLOT_NORTH));

        helper.assertTrue(hub.getCanister(HUB_SLOT_NORTH).isEmpty(), HUB_SHOULD_PICKUP);
        helper.assertFalse(player.getMainHandItem().isEmpty(), HUB_SHOULD_KEEP_HELD);
        helper.assertTrue(
                player.getInventory().countItem(GooItems.CANISTER.get()) == HUB_PICKUP_HAND_COUNT,
                HUB_SHOULD_HOLD_BOTH);
        helper.succeed();
    }

    /**
     * Hub: a goo poured on a full canister stays in the hand; the pour goes only into
     * the canister aimed at, never into the empty one beside it.
     *
     * @param helper the gametest helper
     */
    public static void hubPourFillsOnlyTheAimedCanister(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        int full = ContainerCapacity.canisterCapacity(0);
        ItemStack fullCanister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setFluidContent(fullCanister, CanisterFluidContent.of(GooFluids.resource(GooTypes.ROCK), full));
        int beside = (HUB_SLOT_NORTH + 1) % HubBlock.SLOT_CENTERS.length;
        helper.assertTrue(hub.insertCanister(HUB_SLOT_NORTH, fullCanister), HUB_SHOULD_INSERT);
        helper.assertTrue(hub.insertCanister(beside, new ItemStack(GooItems.CANISTER.get())), HUB_SHOULD_INSERT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, GooStacks.createForOutput(GooTypes.ROCK, GooStacks.THOUSAND));

        helper.useBlock(BE_POS, player, slotHit(helper, HUB_SLOT_NORTH));

        helper.assertTrue(CanisterItem.getFluidContent(hub.getCanister(beside)).isEmpty(),
                "A pour on the full hub canister should leave the empty one beside it empty");
        helper.succeed();
    }

    /**
     * Creates a BlockHitResult on the top face at the center of the given hub slot.
     *
     * @param helper the gametest helper
     * @param slot   the hub slot index
     * @return the hit result
     */
    private static BlockHitResult slotHit(GameTestHelper helper, int slot) {
        BlockPos abs = helper.absolutePos(BE_POS);
        double[] center = HubBlock.SLOT_CENTERS[slot];
        return new BlockHitResult(
                new Vec3(abs.getX() + center[0] / PIXELS_PER_BLOCK, abs.getY() + 1.0,
                        abs.getZ() + center[1] / PIXELS_PER_BLOCK),
                Direction.UP, abs, false);
    }

    /**
     * Plexer: an item clicked into the cutaway becomes the target through the dispatcher,
     * and an empty-hand click on the cutaway clears it; neither click sends the player a
     * message (decisions plexer-target-shows-in-a-hud-element, plexer-messages-go-and-refusal-fizzles).
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void plexerSetTarget(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.PLEXER.get());
        PlexerBlockEntity plexer = helper.getBlockEntity(BE_POS, PlexerBlockEntity.class);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(helper.absolutePos(BE_POS.above())));
        player.setData(GooAttachments.KNOWN_ITEMS, KnownItems.NONE.with(PlayerKnowledge.idOf(Items.STONE)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        PacketRecorder recorder = PacketRecorder.attachTo(player);

        try {
            helper.useBlock(BE_POS, player, cutawayHit(helper));
            helper.assertTrue(plexer.getTargetItem().is(Items.STONE), PLEXER_SHOULD_SET);
            assertNoMessage(helper, recorder, "Setting the target");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.useBlock(BE_POS, player, cutawayHit(helper));
            helper.assertTrue(plexer.getTargetItem().isEmpty(), "An empty-hand cutaway click should clear the target");
            assertNoMessage(helper, recorder, "Clearing the target");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The player has been sent no chat or overlay message since the recorder attached.
     *
     * @param helper   the gametest helper
     * @param recorder the recorder on the player's connection
     * @param act      the act that should have sent none
     */
    private static void assertNoMessage(GameTestHelper helper, PacketRecorder recorder, String act) {
        helper.assertTrue(recorder.sentOf(ClientboundSystemChatPacket.class).isEmpty()
                && recorder.sentOf(ClientboundSetActionBarTextPacket.class).isEmpty(),
                act + " should send the player no message");
    }

    /**
     * Plexer: a player who has never melted stone clicks stone into the
     * cutaway and the plexer takes no target, puffing smoke at the cutaway and
     * fizzling with no message; once the player knows stone, the same click sets it
     * (decisions plexer-refuses-an-unlearned-item, plexer-messages-go-and-refusal-fizzles).
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void plexerRefusesAnUnlearnedTarget(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.PLEXER.get());
        PlexerBlockEntity plexer = helper.getBlockEntity(BE_POS, PlexerBlockEntity.class);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // The server sends particles and sounds only to players near them, so the player stands beside the plexer.
        player.setPos(Vec3.atCenterOf(helper.absolutePos(BE_POS.above())));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        PacketRecorder recorder = PacketRecorder.attachTo(player);

        try {
            helper.useBlock(BE_POS, player, cutawayHit(helper));
            helper.assertTrue(plexer.getTargetItem().isEmpty(),
                    "Plexer should refuse a target the player has not learned");
            assertRefusalCue(helper, plexer, recorder, 1);

            // Bedrock holds no goo value, so the plexer cannot take it even from a player who knows it.
            player.setData(GooAttachments.KNOWN_ITEMS, KnownItems.NONE.with(PlayerKnowledge.idOf(Items.BEDROCK)));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEDROCK));
            helper.useBlock(BE_POS, player, cutawayHit(helper));
            helper.assertTrue(plexer.getTargetItem().isEmpty(), "Plexer should refuse an item with no goo value");
            assertRefusalCue(helper, plexer, recorder, 2);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            player.setData(GooAttachments.KNOWN_ITEMS, KnownItems.NONE.with(PlayerKnowledge.idOf(Items.STONE)));
            helper.useBlock(BE_POS, player, cutawayHit(helper));
            helper.assertTrue(plexer.getTargetItem().is(Items.STONE), PLEXER_SHOULD_SET);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Plexer: a player who takes the lab kit still knows nothing, so the plexer fizzles
     * on stone; after the learn route the same click sets the target, and after the
     * forget route it fizzles again (decision lab-kit-teaches-every-recipe).
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void plexerFizzlesOnAKitPlayerUntilLabLearn(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.PLEXER.get());
        PlexerBlockEntity plexer = helper.getBlockEntity(BE_POS, PlexerBlockEntity.class);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(helper.absolutePos(BE_POS.above())));
        LabKit.give(player, helper.getLevel());
        PacketRecorder recorder = PacketRecorder.attachTo(player);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            helper.useBlock(BE_POS, player, cutawayHit(helper));
            helper.assertTrue(plexer.getTargetItem().isEmpty(), "A kit player should not know stone yet");
            assertRefusalCue(helper, plexer, recorder, 1);

            LabKit.learn(player, helper.getLevel());
            helper.useBlock(BE_POS, player, cutawayHit(helper));
            helper.assertTrue(plexer.getTargetItem().is(Items.STONE), "After learn the plexer should take stone");

            plexer.setTargetItem(ItemStack.EMPTY);
            LabKit.forget(player);
            helper.useBlock(BE_POS, player, cutawayHit(helper));
            helper.assertTrue(plexer.getTargetItem().isEmpty(), "After forget the plexer should refuse stone again");
            assertRefusalCue(helper, plexer, recorder, 2);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The refusal cue reached the player once per refusal so far: smoke at the cutaway's
     * world center, the extinguish fizzle among block sounds, and no chat or overlay message.
     *
     * @param helper    the gametest helper
     * @param plexer    the refusing plexer
     * @param recorder  the recorder on the clicking player's connection
     * @param refusals  how many refusals the player has met since the recorder attached
     */
    private static void assertRefusalCue(GameTestHelper helper, PlexerBlockEntity plexer, PacketRecorder recorder,
                                         int refusals) {
        Vec3 center = CutawayInteractionHelper.cutawayWorldCenter(plexer.getBlockPos(),
                plexer.getBlockState().getValue(PlexerBlock.FACING));
        List<ClientboundLevelParticlesPacket> puffs = recorder.sentOf(ClientboundLevelParticlesPacket.class);
        ClientboundLevelParticlesPacket puff = puffs.isEmpty() ? null : puffs.getLast();
        helper.assertTrue(puffs.size() == refusals && puff != null && puff.getParticle() == ParticleTypes.SMOKE
                && puff.getCount() == PlexerRefusalCue.SMOKE_COUNT
                && new Vec3(puff.getX(), puff.getY(), puff.getZ()).distanceTo(center) < CUE_EPSILON,
                "Each refusal should puff smoke at the cutaway's center " + center + ", sent " + puffs);
        List<ClientboundSoundPacket> sounds = recorder.sentOf(ClientboundSoundPacket.class);
        helper.assertTrue(sounds.size() == refusals && sounds.getLast().getSound().value() == SoundEvents.FIRE_EXTINGUISH
                && sounds.getLast().getSource() == SoundSource.BLOCKS,
                "Each refusal should fizzle among block sounds, sent " + sounds);
        assertNoMessage(helper, recorder, "The refusal");
    }

    /**
     * A hit inside the plexer's cutaway, found by walking the block's pixel grid with the
     * same test the plexer's click reads.
     *
     * @param helper the gametest helper
     * @return the hit result
     */
    private static BlockHitResult cutawayHit(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(BE_POS);
        BlockState state = helper.getBlockState(BE_POS);
        for (int x = 1; x < PIXELS_PER_BLOCK; x++) {
            for (int y = 1; y < PIXELS_PER_BLOCK; y++) {
                for (int z = 1; z < PIXELS_PER_BLOCK; z++) {
                    BlockHitResult hit = new BlockHitResult(new Vec3(abs.getX() + x / PIXELS_PER_BLOCK,
                            abs.getY() + y / PIXELS_PER_BLOCK, abs.getZ() + z / PIXELS_PER_BLOCK),
                            Direction.UP, abs, false);
                    if (CutawayInteractionHelper.isCutawayClick(state, abs, hit)) {
                        return hit;
                    }
                }
            }
        }
        throw new IllegalStateException("No pixel of the plexer lies in its cutaway");
    }

    /**
     * Reactor: a canister clicked into the empty hollow inserts, and a second click, sneaking
     * this time, picks it back up; no sneaking click is swallowed doing nothing.
     *
     * @param helper the gametest helper
     */
    public static void reactorCanisterInsertThenSneakPickup(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CANISTER.get(), HUB_PICKUP_HAND_COUNT));

        helper.useBlock(BE_POS, player, reactorHollowHit(helper));
        helper.assertFalse(reactor.getOutputCanister().isEmpty(), "A canister click should fill the reactor hollow");

        player.setShiftKeyDown(true);
        helper.useBlock(BE_POS, player, reactorHollowHit(helper));
        helper.assertTrue(reactor.getOutputCanister().isEmpty(), "A second canister click should pick it up");
        helper.assertTrue(player.getInventory().countItem(GooItems.CANISTER.get()) == HUB_PICKUP_HAND_COUNT,
                "The picked-up canister should join the held one");
        helper.succeed();
    }

    /**
     * A hit on the middle of the reactor's output canister box.
     *
     * @param helper the gametest helper
     * @return the hit result
     */
    private static BlockHitResult reactorHollowHit(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(BE_POS);
        Direction facing = helper.getBlockState(BE_POS).getValue(ReactorBlock.FACING);
        AABB slot = ReactorBlock.outputSlotShape(facing).bounds();
        return new BlockHitResult(new Vec3(abs.getX() + (slot.minX + slot.maxX) * BLOCK_CENTER,
                abs.getY() + (slot.minY + slot.maxY) * BLOCK_CENTER,
                abs.getZ() + (slot.minZ + slot.maxZ) * BLOCK_CENTER), facing, abs, false);
    }

    /**
     * Crucible: a blaze rod click inserts no fuel, leaving the stack whole and the
     * crucible cold (decision fuel-goo-heats-per-mb).
     *
     * @param helper the gametest helper
     */
    public static void crucibleBlazeRodClickLeavesItCold(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_ROD));

        helper.useBlock(BE_POS, player, hit(helper, Direction.UP));

        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, BLAZE_ROD_STAYS_WHOLE);
        helper.assertFalse(crucible.canHeat(), BLAZE_ROD_LEAVES_COLD);
        helper.succeed();
    }

    /**
     * Crucible: a cold crucible with no fuel goo leaves an item dropped into it standing.
     *
     * @param helper the gametest helper
     */
    public static void coldCrucibleAbsorbsNothing(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        ItemEntity dropped = spawnInBasin(helper);

        helper.runAfterDelay(ABSORB_DELAY, () -> {
            helper.assertFalse(dropped.isRemoved(), COLD_CRUCIBLE_REFUSES);
            helper.succeed();
        });
    }

    /**
     * Crucible: the same crucible holding blaze goo in its reservoir absorbs the item.
     *
     * @param helper the gametest helper
     */
    public static void blazeCrucibleAbsorbsItem(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class).insertGoo(GooTypes.BLAZE, CRUCIBLE_BLAZE_FUEL);
        ItemEntity dropped = spawnInBasin(helper);

        helper.runAfterDelay(ABSORB_DELAY, () -> {
            helper.assertTrue(dropped.isRemoved(), BLAZE_CRUCIBLE_ABSORBS);
            helper.succeed();
        });
    }

    /**
     * Crucible: a flint-and-steel click sparks a cold crucible for the spark's heat at one
     * durability; the same click on the heated crucible is consumed and costs nothing
     * (decision flint-and-steel-sparks-the-crucible).
     *
     * @param helper the gametest helper
     */
    public static void flintAndSteelSparksColdCrucible(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, flint);

        helper.assertValueEqual(sparkClick(helper, player), InteractionResult.SUCCESS, SPARK_SUCCEEDS);
        helper.assertValueEqual(crucible.heatTicks(), GooConfig.SPARK_HEAT_TICKS.get(), SPARK_HEATS);
        helper.assertValueEqual(flint.getDamageValue(), 1, SPARK_COSTS_ONE);

        helper.assertValueEqual(sparkClick(helper, player), InteractionResult.CONSUME, HOT_SPARK_CONSUMED);
        helper.assertValueEqual(flint.getDamageValue(), 1, HOT_SPARK_FREE);
        helper.succeed();
    }

    /**
     * Crucible: a sparked empty crucible absorbs a coal and melts it to the end on the
     * blaze goo the coal yields.
     *
     * @param helper the gametest helper
     */
    public static void sparkedCrucibleMeltsCoalOnItsBlaze(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        sparkClick(helper, player);
        ItemEntity coal = spawnInBasin(helper, Items.COAL);

        helper.succeedWhen(() -> {
            helper.assertTrue(coal.isRemoved(), COAL_MELTED);
            helper.assertTrue(crucible.getMeltingItem().isEmpty(), COAL_MELTED);
            helper.assertTrue(crucible.getReservoir().getVolume(GooTypes.BLAZE) > 0, COAL_LEFT_BLAZE);
            helper.assertTrue(crucible.getReservoir().getVolume(GooTypes.ROCK) > 0, COAL_LEFT_ROCK);
            helper.assertTrue(crucible.canHeat(), COAL_ENDS_HOT);
        });
    }

    /**
     * Right-clicks the crucible's top with the player's main-hand stack.
     *
     * @param helper the gametest helper
     * @param player the clicking player
     * @return the crucible's answer
     */
    private static InteractionResult sparkClick(GameTestHelper helper, Player player) {
        return helper.getBlockState(BE_POS).useItemOn(player.getItemInHand(InteractionHand.MAIN_HAND),
                helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, Direction.UP));
    }

    /**
     * Spawns a still cobblestone item entity inside the crucible basin.
     *
     * @param helper the gametest helper
     * @return the spawned entity
     */
    private static ItemEntity spawnInBasin(GameTestHelper helper) {
        return spawnInBasin(helper, Items.COBBLESTONE);
    }

    /**
     * Spawns a still item entity of one item inside the crucible basin.
     *
     * @param helper the gametest helper
     * @param item   the item the entity carries
     * @return the spawned entity
     */
    private static ItemEntity spawnInBasin(GameTestHelper helper, Item item) {
        return CrucibleSpawns.spawnInBasin(helper, new ItemStack(item));
    }
}
