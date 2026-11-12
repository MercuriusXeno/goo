package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.ability.weird.Wobbled;
import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Weird's abilities, each cast through its real path: a blob
 * through its landing, Goop through the stream tick.
 * weird-bounces-and-softens-harm
 */
@RegistersGameTests
public final class WeirdTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final Identifier GOOP = Identifier.parse("goo:weird_goop");
    private static final String ABILITY_REQUIRED = "Ability registry must hold goo:weird_goop";
    private static final String GOOP_COOLS_LAVA = "goop_cools_lava_to_magma";
    private static final String SOFTEN_TRADES_DAMAGE = "goop_softens_the_mob_it_hits";
    private static final BlockPos POOL_FLOOR = new BlockPos(2, 0, 2);
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Pitched down at the floor three and four blocks east, so both floor cells stand in Goop's cone. */
    private static final float AT_THE_FLOOR = 32f;
    /** Pitched a little down at a zombie four blocks east. */
    private static final float AT_THE_ZOMBIE = 8f;
    /** Two floor cells three and four blocks east of the streamer, inside Goop's cone pitched at the floor. */
    private static final List<BlockPos> LAVA_CELLS = List.of(new BlockPos(3, 0, 2), new BlockPos(4, 0, 2));
    private static final BlockPos ZOMBIE_POS = new BlockPos(4, 1, 2);
    private static final int GOOP_HOLD_TICKS = 10;
    private static final String GOOP_LAYS_A_COAT = "goop_lays_a_slime_coat_over_the_block";
    private static final String GOOP_LAYERS_WHILE_HELD = "goop_layers_a_reached_cell_again_while_held";
    private static final String GOOP_STOPS_AT_WHOLE = "goop_stops_a_reached_cell_at_a_whole_block";
    private static final String GOOP_LEFT_CELL_STOPS = "goop_lays_no_layer_on_a_cell_the_aim_left";
    private static final String GOOP_DRAINS_PER_LAYER = "goop_drains_a_layers_price_for_each_layer_it_lays";
    private static final String GOOP_LAYING_NOTHING_DRAINS_NOTHING = "goop_laying_no_layer_drains_no_goo";
    private static final String GOOP_UNPAID_LAYER_STAYS_OUT = "goop_lays_no_layer_the_streamer_cannot_pay_for";
    /** Pitched straight up, at the slab over the streamer's head. */
    private static final float STRAIGHT_UP = -90f;
    /** Blocks over the streamer's stand to the slab over its head, close enough that the whole cone meets it. */
    private static final int OVERHEAD = 3;
    private static final String DRAINED_WRONG = "The hold should drain %s mb of weird goo, drained %d";
    private static final String GOOP_PAST_NINE_TENTHS_FINISHES = "goop_past_nine_tenths_finishes_after_release";
    /** Three blocks east of the streamer, at its height, on a block the test lays. */
    private static final BlockPos COATED_POS = STREAMER_POS.east(3);
    /** Pitch down from the eye toward the stone's center three blocks off. */
    private static final float AT_THE_STONE = 20.5f;
    /** weird_goop.json's coat ticks: twenty ticks of stream coat a block. */
    private static final int COAT_STEP_TICKS = 20;
    /** Ticks past a coat's worth, so a coat has surely landed. */
    private static final int COAT_SLACK_TICKS = 5;
    /** Coats a hold lays on one cell in the layering test. */
    private static final int THREE_COATS = 3;
    /** Coats the turned-to cell takes in the two cell hold. */
    private static final int TWO_COATS = 2;
    /**
     * A hold some coats long and two slacks over, so its last layer has surely
     * landed and the next stands far short of finishing alone.
     */
    private static final int HOLD_SLACK_TICKS = 2 * COAT_SLACK_TICKS;
    /** Yaw turned off east toward a walled cell one block north or south of the axis, three blocks out. */
    private static final float OFF_THE_AXIS = 18.4f;
    /** The walled cell the two cell hold sprays first, north of the axis. */
    private static final BlockPos FIRST_SPRAYED = COATED_POS.above().north();
    /** The walled cell the two cell hold turns to, south of the axis, outside the first's cone. */
    private static final BlockPos TURNED_TO = COATED_POS.above().south();
    /** A hold past nine tenths of a coat, short of the whole. */
    private static final int PAST_NINE_TENTHS_TICKS = COAT_STEP_TICKS - 1;
    private static final String LAVA_LEFT = "Goop's stream should leave magma where the lava stood at ";
    private static final String PAD_BREAKS_THE_FALL = "weird_pad_breaks_the_fall";
    private static final String BOUNCES_ITEM_AND_ARROW = "slime_bounces_an_item_and_an_arrow";
    /** Ticks the item and the arrow are watched falling onto the coat and leaving it. */
    private static final int LANDING_WATCH_TICKS = 40;
    private static final String NOT_BOUNCED = "%s dropped on the slime coat should leave it moving upward";
    /** The test bay's roof stands five blocks over the pad, so the pig starts under it. */
    private static final int DROP_START = 3;
    /** A fall a pig at ten health dies of on stone, carried in as the fall it has already made. */
    private static final double FALLEN_BLOCKS = 20;
    private static final double FALL_SPEED = -1.0;
    private static final String NOT_LAUNCHED = "A pig falling onto the slime coat should leave it moving upward";
    private static final String HURT = "A pig falling onto the slime coat should keep its full health, has ";
    private static final String MITOSIS = "goo:weird_mitosis";
    private static final String HELD_JUMPS_RAMP = "coat_held_jumps_ramp_to_the_ceiling";
    /** A floor cell in the bay's roof, open sky above it. */
    private static final BlockPos OPEN_SKY_FLOOR = new BlockPos(2, 6, 2);
    /** Ticks a jumping pig bounces on the coat, room for a dozen bounces at the ceiling. */
    private static final int RAMP_TICKS = 600;
    /** The height a ramp at the ceiling peaks above the coat: fourteen blocks. */
    private static final double CEILING_PEAK = 14;
    /** How far the highest peak may stand from CEILING_PEAK, sampled once a tick over the coat's film. */
    private static final double CEILING_TOLERANCE = 0.5;
    private static final String RAMP_STALLED = "A pig holding jump on the coat should peak %.0f blocks up within %.1f,"
            + " peaks were %s";
    private static final String SLIME_BLOB_MAKES_A_SLIME = "slime_blob_makes_a_slime";
    /** SpawnSlimeStep's morph: the ticks the blob takes to become the slime. */
    private static final int SLIME_MORPH_TICKS = 16;
    private static final String NOT_WOBBLED = "A zombie in Goop's stream should be wobbled";
    private static final String HURT_BY_SOFTENED = "A softened zombie's hit should leave the player's health full, has ";
    private static final String NOT_KNOCKED = "A softened zombie's hit should knock the player back";
    private static final String FADED_STILL_SOFT = "Once the wobble has faded, the zombie's hit should hurt the player";
    private static final String SLIME_LEVELS_OUT = "slime_levels_out_beside_an_open_top";
    private static final String SLIME_POURS_OFF_A_LEDGE = "slime_pours_off_a_ledge";
    private static final String GOOP_ADDS_A_LAYER = "goop_adds_a_layer_to_standing_slime";
    /** Ticks the flow is given to settle, far past the handful of pours each test needs. */
    private static final int FLOW_SETTLE_TICKS = 40;
    private static final int FULL_CELL = 8;
    private static final int HALF_CELL = 4;
    /** The slime cell the flow tests fill, on a stone top in the bay's floor row. */
    private static final BlockPos FLOW_CELL = new BlockPos(2, 1, 2);
    /** The ledge cell's slime, on a stone pillar one block over the floor row. */
    private static final BlockPos LEDGE_CELL = FLOW_CELL.above();
    /** Where the ledge's layers land: the floor row's cell east of the pillar. */
    private static final BlockPos BELOW_THE_LEDGE = FLOW_CELL.east();
    /** Pitched down at the slime coat over the stone three blocks east, so the axis ray ends on it. */
    private static final float AT_THE_SLIME = 9.4f;
    private static final String LAYERS_WRONG = "Slime at %s should hold %d layers, holds %s";
    private static final String BREAKING_SLIME_DROPS = "breaking_slime_drops_its_goo";
    private static final int BROKEN_LAYERS = 3;
    private static final String RECLAIM_WRONG = "A broken slime cell should drop %d mb of weird goo, dropped %d in %s";
    private static final String PLACING_KEEPS_GOO = "placing_a_block_keeps_the_slimes_goo";
    private static final String PLACED_OVER_SLIME = "A block placed against a slime cell should leave it at %d layers"
            + " or drop %d mb of weird goo; the cell holds %s and %d mb dropped";

    private WeirdTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GOOP_COOLS_LAVA, WeirdTests::goopCoolsLavaToMagma);
        GooTestFunctions.registerTest(registrar, GOOP_LAYS_A_COAT, WeirdTests::goopLaysASlimeCoatOverTheBlock);
        GooTestFunctions.registerTest(registrar, GOOP_LAYERS_WHILE_HELD, WeirdTests::goopLayersAReachedCellAgainWhileHeld);
        GooTestFunctions.registerTest(registrar, GOOP_STOPS_AT_WHOLE, WeirdTests::goopStopsAReachedCellAtAWholeBlock);
        GooTestFunctions.registerTest(registrar, GOOP_LEFT_CELL_STOPS, WeirdTests::goopLaysNoLayerOnACellTheAimLeft);
        GooTestFunctions.registerTest(registrar, GOOP_DRAINS_PER_LAYER,
                WeirdTests::goopDrainsALayersPriceForEachLayerItLays);
        GooTestFunctions.registerTest(registrar, GOOP_LAYING_NOTHING_DRAINS_NOTHING,
                WeirdTests::goopLayingNoLayerDrainsNoGoo);
        GooTestFunctions.registerTest(registrar, GOOP_UNPAID_LAYER_STAYS_OUT,
                WeirdTests::goopLaysNoLayerTheStreamerCannotPayFor);
        GooTestFunctions.registerTest(registrar, GOOP_PAST_NINE_TENTHS_FINISHES,
                WeirdTests::goopPastNineTenthsFinishesAfterRelease);
        GooTestFunctions.registerTest(registrar, PAD_BREAKS_THE_FALL, WeirdTests::weirdPadBreaksTheFall);
        GooTestFunctions.registerTest(registrar, BOUNCES_ITEM_AND_ARROW, WeirdTests::slimeBouncesAnItemAndAnArrow);
        GooTestFunctions.registerTest(registrar, HELD_JUMPS_RAMP, WeirdTests::heldJumpsRampToTheCeiling);
        GooTestFunctions.registerTest(registrar, SOFTEN_TRADES_DAMAGE, WeirdTests::goopSoftensTheMobItHits);
        GooTestFunctions.registerTest(registrar, SLIME_BLOB_MAKES_A_SLIME, WeirdTests::slimeBlobMakesASlime);
        GooTestFunctions.registerTest(registrar, BREAKING_SLIME_DROPS, WeirdTests::breakingSlimeDropsItsGoo);
        GooTestFunctions.registerTest(registrar, PLACING_KEEPS_GOO, WeirdTests::placingABlockKeepsTheSlimesGoo);
        GooTestFunctions.registerTest(registrar, SLIME_LEVELS_OUT, WeirdTests::slimeLevelsOutBesideAnOpenTop);
        GooTestFunctions.registerTest(registrar, SLIME_POURS_OFF_A_LEDGE, WeirdTests::slimePoursOffALedge);
        GooTestFunctions.registerTest(registrar, GOOP_ADDS_A_LAYER, WeirdTests::goopAddsALayerToStandingSlime);
    }

    /**
     * Eight layers of slime set beside an open stone top, the pair walled in
     * on every other side, level out to four and four.
     * slime-is-a-layered-cell-that-levels-out
     *
     * @param helper the gametest helper
     */
    public static void slimeLevelsOutBesideAnOpenTop(GameTestHelper helper) {
        BlockPos open = FLOW_CELL.east();
        stoneUnder(helper, FLOW_CELL);
        stoneUnder(helper, open);
        wallSidesBut(helper, FLOW_CELL, Direction.EAST);
        wallSidesBut(helper, open, Direction.WEST);
        helper.setBlock(open, Blocks.AIR);
        helper.setBlock(FLOW_CELL, slimeOf(FULL_CELL));
        helper.runAfterDelay(FLOW_SETTLE_TICKS, () -> {
            assertLayers(helper, FLOW_CELL, HALF_CELL);
            assertLayers(helper, open, HALF_CELL);
            helper.succeed();
        });
    }

    /**
     * Four layers of slime on a stone pillar, open to the east over a drop
     * of one block onto a walled stone top: the layers pour off the ledge
     * onto that top, the pillar's cell keeping its last layer.
     * slime-is-a-layered-cell-that-levels-out
     *
     * @param helper the gametest helper
     */
    public static void slimePoursOffALedge(GameTestHelper helper) {
        helper.setBlock(FLOW_CELL, Blocks.STONE);
        wallSidesBut(helper, LEDGE_CELL, Direction.EAST);
        helper.setBlock(LEDGE_CELL.east(), Blocks.AIR);
        stoneUnder(helper, BELOW_THE_LEDGE);
        wallSidesBut(helper, BELOW_THE_LEDGE, Direction.WEST);
        helper.setBlock(BELOW_THE_LEDGE, Blocks.AIR);
        helper.setBlock(LEDGE_CELL, slimeOf(HALF_CELL));
        helper.runAfterDelay(FLOW_SETTLE_TICKS, () -> {
            assertLayers(helper, LEDGE_CELL, 1);
            assertLayers(helper, BELOW_THE_LEDGE, HALF_CELL - 1);
            helper.succeed();
        });
    }

    /**
     * Goop's stream held over a cell already holding one layer of slime, its
     * side neighbours holding a layer each so nothing pours, leaves that cell
     * at two layers.
     * slime-is-a-layered-cell-that-levels-out
     *
     * @param helper the gametest helper
     */
    public static void goopAddsALayerToStandingSlime(GameTestHelper helper) {
        ServerPlayer streamer = goopStreamerOverStone(helper);
        streamer.setXRot(AT_THE_SLIME);
        BlockPos slimed = COATED_POS.above();
        helper.setBlock(slimed, slimeOf(1));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(COATED_POS.relative(side), Blocks.STONE);
            helper.setBlock(slimed.relative(side), slimeOf(1));
        }
        holdGoopFor(helper, streamer, COAT_STEP_TICKS + COAT_SLACK_TICKS);
        helper.runAfterDelay(COAT_STEP_TICKS + COAT_SLACK_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertLayers(helper, slimed, 2);
            helper.succeed();
        });
    }

    /**
     * A slime cell of three layers, walled in so nothing pours, broken in a
     * live world leaves a weird goo item in its cell worth three layers of goo.
     * breaking-slime-drops-weird-goo-worth-its-layers
     *
     * @param helper the gametest helper
     */
    public static void breakingSlimeDropsItsGoo(GameTestHelper helper) {
        stoneUnder(helper, FLOW_CELL);
        wallSidesBut(helper, FLOW_CELL, null);
        helper.setBlock(FLOW_CELL, slimeOf(BROKEN_LAYERS));
        // GameTestHelper.destroyBlock breaks with drops off; the level's own break drops what the block gives
        helper.getLevel().destroyBlock(helper.absolutePos(FLOW_CELL), true);
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, FLOW_CELL, 1.0);
        int weird = drops.stream().map(ItemEntity::getItem)
                .filter(stack -> GooTypes.WEIRD.equals(GooStacks.keyOf(stack))).mapToInt(GooStacks::volumeOf).sum();
        int owed = BROKEN_LAYERS * SlimeCoatBlock.MB_PER_LAYER;
        helper.assertTrue(weird == owed, String.format(RECLAIM_WRONG, owed, weird, drops));
        helper.succeed();
    }

    /**
     * A survival player uses a stone block item against a three layer slime
     * cell, walled in so nothing pours: the cell keeps all its weird goo,
     * standing at three layers or dropping what they cost.
     * breaking-slime-drops-weird-goo-worth-its-layers
     *
     * @param helper the gametest helper
     */
    public static void placingABlockKeepsTheSlimesGoo(GameTestHelper helper) {
        stoneUnder(helper, FLOW_CELL);
        wallSidesBut(helper, FLOW_CELL, null);
        helper.setBlock(FLOW_CELL, slimeOf(BROKEN_LAYERS));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stone = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        BlockPos cell = helper.absolutePos(FLOW_CELL);
        Vec3 slimeTop = Vec3.atBottomCenterOf(cell).add(0, (double) BROKEN_LAYERS / FULL_CELL, 0);
        stone.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(slimeTop, Direction.UP, cell, false)));
        int standing = SlimeCoatBlock.layersOf(helper.getBlockState(FLOW_CELL));
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, FLOW_CELL, 1.0);
        int weird = drops.stream().map(ItemEntity::getItem)
                .filter(stack -> GooTypes.WEIRD.equals(GooStacks.keyOf(stack))).mapToInt(GooStacks::volumeOf).sum();
        int owed = BROKEN_LAYERS * SlimeCoatBlock.MB_PER_LAYER;
        helper.assertTrue(standing == BROKEN_LAYERS || weird == owed,
                String.format(PLACED_OVER_SLIME, BROKEN_LAYERS, owed, helper.getBlockState(FLOW_CELL), weird));
        helper.succeed();
    }

    private static BlockState slimeOf(int layers) {
        return GooBlocks.SLIME_COAT.get().defaultBlockState().setValue(SlimeCoatBlock.LAYERS, layers);
    }

    private static void stoneUnder(GameTestHelper helper, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
    }

    private static void wallSidesBut(GameTestHelper helper, BlockPos cell, Direction open) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side != open) {
                helper.setBlock(cell.relative(side), Blocks.STONE);
            }
        }
    }

    private static void assertLayers(GameTestHelper helper, BlockPos cell, int layers) {
        BlockState state = helper.getBlockState(cell);
        Object held = state.is(GooBlocks.SLIME_COAT.get()) ? state.getValue(SlimeCoatBlock.LAYERS) : state;
        helper.assertTrue(Integer.valueOf(layers).equals(held), String.format(LAYERS_WRONG, cell, layers, held));
    }

    /**
     * A Weird Mitosis blob lands on stone: no slime stands anywhere while the
     * blob morphs, and once the morph ends a slime stands in the cell the
     * blob landed in.
     * slime-blob-morphs-into-the-slime-it-becomes
     *
     * @param helper the gametest helper
     */
    public static void slimeBlobMakesASlime(GameTestHelper helper) {
        helper.setBlock(POOL_FLOOR, Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, MITOSIS));
        helper.assertEntityNotPresent(EntityType.SLIME);
        helper.runAfterDelay(SLIME_MORPH_TICKS - 1, () -> helper.assertEntityNotPresent(EntityType.SLIME));
        helper.runAfterDelay(SLIME_MORPH_TICKS + 1, () -> {
            helper.assertEntityPresent(EntityType.SLIME, POOL_FLOOR.above());
            helper.succeed();
        });
    }

    /**
     * A zombie in Goop's stream is wobbled and strikes a survival player: the
     * player keeps its full health and is knocked back instead; once the
     * wobble has faded, the zombie's next hit hurts the player.
     *
     * @param helper the gametest helper
     */
    public static void goopSoftensTheMobItHits(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        ServerPlayer streamer = streamerAt(helper, AT_THE_ZOMBIE);
        holdGoop(helper, streamer, () -> {
            helper.assertTrue(zombie.hasData(GooAttachments.WOBBLED), NOT_WOBBLED);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(ZOMBIE_POS.east())));
            player.setDeltaMovement(Vec3.ZERO);
            zombie.doHurtTarget(helper.getLevel(), player);
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), HURT_BY_SOFTENED + player.getHealth());
            helper.assertTrue(player.getDeltaMovement().horizontalDistance() > 0, NOT_KNOCKED);

            zombie.setData(GooAttachments.WOBBLED, new Wobbled(helper.getLevel().getGameTime()));
            player.invulnerableTime = 0;
            zombie.doHurtTarget(helper.getLevel(), player);
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), FADED_STILL_SOFT);
        });
    }

    /**
     * A slime coat on stone breaks a fall: a pig falling onto the coat with
     * twenty blocks of fall behind it keeps its full health and leaves the
     * coat moving upward.
     * bounce-ramps-jump-height-while-jump-is-held
     *
     * @param helper the gametest helper
     */
    public static void weirdPadBreaksTheFall(GameTestHelper helper) {
        helper.setBlock(POOL_FLOOR, Blocks.STONE);
        BlockPos coat = POOL_FLOOR.above();
        helper.setBlock(coat, GooBlocks.SLIME_COAT.get());
        Pig pig = helper.spawn(EntityType.PIG, coat.above(DROP_START));
        pig.fallDistance = FALLEN_BLOCKS;
        pig.setDeltaMovement(0, FALL_SPEED, 0);
        double coatBottom = helper.absolutePos(coat).getY();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(pig.getDeltaMovement().y > 0 && pig.getY() >= coatBottom, NOT_LAUNCHED);
                    helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), HURT + pig.getHealth());
                })
                .thenSucceed();
    }

    /**
     * An item and an arrow dropped onto a slime coat each fall onto it and
     * leave it moving upward: the slime bounces every entity, not living things alone.
     * neutral-bounce-is-near-lossless-and-bounces-every-entity
     *
     * @param helper the gametest helper
     */
    public static void slimeBouncesAnItemAndAnArrow(GameTestHelper helper) {
        helper.setBlock(POOL_FLOOR, Blocks.STONE);
        BlockPos coat = POOL_FLOOR.above();
        helper.setBlock(coat, GooBlocks.SLIME_COAT.get());
        stoneUnder(helper, coat.east());
        helper.setBlock(coat.east(), GooBlocks.SLIME_COAT.get());
        ItemEntity item = helper.spawnItem(Items.SLIME_BALL, Vec3.atBottomCenterOf(coat.above(DROP_START)));
        item.setDeltaMovement(0, FALL_SPEED, 0);
        Arrow arrow = helper.spawn(EntityType.ARROW, Vec3.atBottomCenterOf(coat.east().above(DROP_START)));
        arrow.setDeltaMovement(0, FALL_SPEED, 0);
        boolean[] itemBounced = {false};
        boolean[] arrowBounced = {false};
        double[] lastFall = {FALL_SPEED, FALL_SPEED};
        for (int tick = 1; tick <= LANDING_WATCH_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                itemBounced[0] |= lastFall[0] < 0 && item.getDeltaMovement().y > 0;
                arrowBounced[0] |= lastFall[1] < 0 && arrow.getDeltaMovement().y > 0;
                lastFall[0] = item.getDeltaMovement().y;
                lastFall[1] = arrow.getDeltaMovement().y;
            });
        }
        helper.runAfterDelay(LANDING_WATCH_TICKS + 1, () -> {
            helper.assertTrue(itemBounced[0], String.format(NOT_BOUNCED, "A slime ball item"));
            helper.assertTrue(arrowBounced[0], String.format(NOT_BOUNCED, "An arrow"));
            helper.succeed();
        });
    }

    /**
     * A pig holding jump on a slime coat bounces higher each landing until it
     * peaks at the ceiling, fourteen blocks over the coat.
     * bounce-ramps-jump-height-while-jump-is-held
     * ramp-tops-out-on-the-fourth-held-bounce-at-fourteen-blocks
     *
     * @param helper the gametest helper
     */
    public static void heldJumpsRampToTheCeiling(GameTestHelper helper) {
        // on the bay's roof, open sky over it, so nothing caps the climb but the ramp
        helper.setBlock(OPEN_SKY_FLOOR, Blocks.STONE);
        helper.setBlock(OPEN_SKY_FLOOR.above(), GooBlocks.SLIME_COAT.get());
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, OPEN_SKY_FLOOR.above());
        double coatTop = helper.absolutePos(OPEN_SKY_FLOOR.above()).getY();
        List<Double> peaks = new java.util.ArrayList<>();
        double[] rising = {Double.NEGATIVE_INFINITY};
        for (int tick = 1; tick <= RAMP_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                pig.getJumpControl().jump();
                double above = pig.getY() - coatTop;
                if (pig.getDeltaMovement().y > 0) {
                    rising[0] = Math.max(rising[0], above);
                } else if (rising[0] > Double.NEGATIVE_INFINITY) {
                    peaks.add(Math.max(rising[0], above));
                    rising[0] = Double.NEGATIVE_INFINITY;
                }
            });
        }
        helper.runAfterDelay(RAMP_TICKS + 1, () -> {
            double highest = peaks.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            helper.assertTrue(Math.abs(highest - CEILING_PEAK) <= CEILING_TOLERANCE,
                    String.format(RAMP_STALLED, CEILING_PEAK, CEILING_TOLERANCE, peaks));
            helper.succeed();
        });
    }

    /**
     * Goop's stream held over two lava cells in a stone floor cools both to
     * magma blocks.
     *
     * @param helper the gametest helper
     */
    public static void goopCoolsLavaToMagma(GameTestHelper helper) {
        for (BlockPos cell : LAVA_CELLS) {
            helper.setBlock(cell, Blocks.LAVA);
        }
        ServerPlayer streamer = streamerAt(helper, AT_THE_FLOOR);
        holdGoop(helper, streamer, () -> {
            for (BlockPos cell : LAVA_CELLS) {
                helper.assertTrue(helper.getBlockState(cell).is(Blocks.MAGMA_BLOCK), LAVA_LEFT + cell);
            }
        });
    }

    /**
     * Goop's stream held on a stone block past one coat's worth lays a slime
     * coat on the stone's top and leaves the stone standing as stone.
     *
     * @param helper the gametest helper
     */
    public static void goopLaysASlimeCoatOverTheBlock(GameTestHelper helper) {
        ServerPlayer streamer = goopStreamerOverStone(helper);
        holdGoopFor(helper, streamer, COAT_STEP_TICKS + COAT_SLACK_TICKS);
        helper.runAfterDelay(COAT_STEP_TICKS + COAT_SLACK_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertBlockPresent(GooBlocks.SLIME_COAT.get(), COATED_POS.above());
            helper.assertBlockPresent(Blocks.STONE, COATED_POS);
            helper.succeed();
        });
    }

    /**
     * Goop's stream held, with no release, on an open cell over stone, walled
     * so it cannot pour, for three coats' worth: the cell holds three layers.
     * goop-layers-a-reached-cell-again-while-held
     *
     * @param helper the gametest helper
     */
    public static void goopLayersAReachedCellAgainWhileHeld(GameTestHelper helper) {
        ServerPlayer streamer = goopStreamerOverStone(helper);
        streamer.setXRot(AT_THE_SLIME);
        BlockPos sprayed = COATED_POS.above();
        wallInFacingWest(helper, sprayed);
        int held = COAT_STEP_TICKS * THREE_COATS + HOLD_SLACK_TICKS;
        holdGoopFor(helper, streamer, held);
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertLayers(helper, sprayed, THREE_COATS);
            helper.succeed();
        });
    }

    /**
     * Goop's stream held for three coats' worth on a walled cell two layers
     * short of a whole block: the cell stops at a whole block.
     * goop-layers-a-reached-cell-again-while-held
     *
     * @param helper the gametest helper
     */
    public static void goopStopsAReachedCellAtAWholeBlock(GameTestHelper helper) {
        ServerPlayer streamer = goopStreamerOverStone(helper);
        streamer.setXRot(AT_THE_SLIME);
        BlockPos sprayed = COATED_POS.above();
        wallInFacingWest(helper, sprayed);
        helper.setBlock(sprayed, slimeOf(SlimeCoatBlock.MAX_LAYERS - 2));
        int held = COAT_STEP_TICKS * THREE_COATS + HOLD_SLACK_TICKS;
        holdGoopFor(helper, streamer, held);
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertLayers(helper, sprayed, SlimeCoatBlock.MAX_LAYERS);
            helper.succeed();
        });
    }

    /**
     * One Goop hold sprays a walled cell north of the axis until its first
     * layer lands, then turns to a walled cell south of the axis, outside
     * the first's cone, for two coats: the first cell keeps one layer and
     * the second takes two.
     * goop-layers-a-reached-cell-again-while-held
     *
     * @param helper the gametest helper
     */
    public static void goopLaysNoLayerOnACellTheAimLeft(GameTestHelper helper) {
        for (BlockPos cell : List.of(FIRST_SPRAYED, TURNED_TO)) {
            helper.setBlock(cell.below(), Blocks.STONE);
            wallInFacingWest(helper, cell);
        }
        ServerPlayer streamer = goopStreamerAt(helper, AT_THE_SLIME);
        streamer.setYRot(FACING_EAST - OFF_THE_AXIS);
        GooStreamPayload tick = goopTick(streamer);
        int held = COAT_STEP_TICKS * THREE_COATS + HOLD_SLACK_TICKS;
        for (int at = 1; at <= held; at++) {
            helper.runAfterDelay(at, () -> {
                // turn the tick after the first cell's layer lands, before the stream reaches it again
                if (SlimeCoatBlock.layersOf(helper.getBlockState(FIRST_SPRAYED)) > 0) {
                    streamer.setYRot(FACING_EAST + OFF_THE_AXIS);
                }
                GooStreamHandler.streamTick(streamer, tick);
            });
        }
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertLayers(helper, FIRST_SPRAYED, 1);
            assertLayers(helper, TURNED_TO, TWO_COATS);
            helper.succeed();
        });
    }

    /**
     * A Goop hold laying three layers on a walled cell drains three layers'
     * price, each an eighth of Goop's cost, and nothing for the ticks held.
     * goop-drains-per-cubic-meter-of-slime-placed
     *
     * @param helper the gametest helper
     */
    public static void goopDrainsALayersPriceForEachLayerItLays(GameTestHelper helper) {
        ServerPlayer streamer = goopStreamerOverStone(helper);
        streamer.setXRot(AT_THE_SLIME);
        BlockPos sprayed = COATED_POS.above();
        wallInFacingWest(helper, sprayed);
        int before = weirdHeld(streamer);
        int held = COAT_STEP_TICKS * THREE_COATS + HOLD_SLACK_TICKS;
        holdGoopFor(helper, streamer, held);
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertLayers(helper, sprayed, THREE_COATS);
            assertDrained(helper, layerPrice(helper).times(THREE_COATS), before - weirdHeld(streamer));
            helper.succeed();
        });
    }

    /**
     * A Goop hold aimed straight up at a bottom slab over the streamer's
     * head, whose top holds no coat, so no cell the stream reaches can take
     * one, lasting three coats, drains no weird goo.
     * goop-drains-per-cubic-meter-of-slime-placed
     *
     * @param helper the gametest helper
     */
    public static void goopLayingNoLayerDrainsNoGoo(GameTestHelper helper) {
        helper.setBlock(STREAMER_POS.above(OVERHEAD), Blocks.STONE_SLAB);
        ServerPlayer streamer = goopStreamerAt(helper, STRAIGHT_UP);
        int before = weirdHeld(streamer);
        int held = COAT_STEP_TICKS * THREE_COATS + HOLD_SLACK_TICKS;
        holdGoopFor(helper, streamer, held);
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertDrained(helper, GooFraction.ZERO, before - weirdHeld(streamer));
            helper.succeed();
        });
    }

    /**
     * A streamer holding one layer's price of weird goo, what a broken layer
     * gives back, holds Goop on a walled cell for three coats: one layer
     * lands and the goo runs out.
     * goop-drains-per-cubic-meter-of-slime-placed
     *
     * @param helper the gametest helper
     */
    public static void goopLaysNoLayerTheStreamerCannotPayFor(GameTestHelper helper) {
        ServerPlayer streamer = goopStreamerOverStone(helper);
        streamer.setXRot(AT_THE_SLIME);
        BlockPos sprayed = COATED_POS.above();
        wallInFacingWest(helper, sprayed);
        GooSourceScanner.deplete(streamer, GooTypes.WEIRD, weirdHeld(streamer) - SlimeCoatBlock.MB_PER_LAYER);
        int held = COAT_STEP_TICKS * THREE_COATS + HOLD_SLACK_TICKS;
        holdGoopFor(helper, streamer, held);
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertLayers(helper, sprayed, 1);
            helper.assertTrue(weirdHeld(streamer) == 0, "The streamer should hold no weird goo, holds "
                    + weirdHeld(streamer));
            helper.succeed();
        });
    }

    /**
     * One slime layer's price: Goop's cost, the price of a cubic meter of
     * slime, over the layers a whole block holds.
     *
     * @param helper the gametest helper
     * @return the exact price in mb
     */
    private static GooFraction layerPrice(GameTestHelper helper) {
        AbilityDefinition goop = AbilityRegistry.of(helper.getLevel()).getAbility(GOOP);
        helper.assertTrue(goop != null, ABILITY_REQUIRED);
        return goop.cost().exactIn(GooValues.of(helper.getLevel())).dividedBy(SlimeCoatBlock.MAX_LAYERS);
    }

    private static int weirdHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.WEIRD, 0);
    }

    /**
     * Asserts a hold drained the whole mb an exact price comes to, the part
     * of a mb overpaid banked in the player's remainder.
     *
     * @param helper  the gametest helper
     * @param price   the exact price the hold should pay
     * @param drained the mb the hold drained
     */
    private static void assertDrained(GameTestHelper helper, GooFraction price, int drained) {
        helper.assertTrue(drained == price.ceiling(), String.format(DRAINED_WRONG, price, drained));
    }

    /**
     * Walls a cell in so its slime cannot pour: bottom slabs on its north,
     * south and east, which hold no coat on their tops, and its west
     * neighbour left open for the stream over a bottom slab, so that cell
     * can hold no layer either.
     *
     * @param helper the gametest helper
     * @param cell   the cell
     */
    private static void wallInFacingWest(GameTestHelper helper, BlockPos cell) {
        for (Direction side : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST)) {
            helper.setBlock(cell.relative(side), Blocks.STONE_SLAB);
        }
        helper.setBlock(cell.west(), Blocks.AIR);
        helper.setBlock(cell.west().below(), Blocks.STONE_SLAB);
    }

    /**
     * Goop's stream held on a stone block past nine tenths of a coat and
     * released: the coat finishes on its own.
     *
     * @param helper the gametest helper
     */
    public static void goopPastNineTenthsFinishesAfterRelease(GameTestHelper helper) {
        ServerPlayer streamer = goopStreamerOverStone(helper);
        holdGoopFor(helper, streamer, PAST_NINE_TENTHS_TICKS);
        helper.runAfterDelay(PAST_NINE_TENTHS_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertBlockPresent(Blocks.AIR, COATED_POS.above());
        });
        helper.runAfterDelay(PAST_NINE_TENTHS_TICKS + COAT_STEP_TICKS, () -> {
            helper.assertBlockPresent(GooBlocks.SLIME_COAT.get(), COATED_POS.above());
            helper.succeed();
        });
    }

    /**
     * Lays stone on bedrock three blocks east of the streamer and stands a
     * Goop streamer aimed at it.
     *
     * @param helper the gametest helper
     * @return the streamer
     */
    private static ServerPlayer goopStreamerOverStone(GameTestHelper helper) {
        helper.setBlock(COATED_POS.below(), Blocks.BEDROCK);
        helper.setBlock(COATED_POS, Blocks.STONE);
        return goopStreamerAt(helper, AT_THE_STONE);
    }

    /**
     * Stands a Goop streamer at the bay's west side facing east, pitched down.
     *
     * @param helper the gametest helper
     * @param pitch  the look's pitch, down from level
     * @return the streamer, taught Goop
     */
    private static ServerPlayer goopStreamerAt(GameTestHelper helper, float pitch) {
        ServerPlayer streamer = streamerAt(helper, pitch);
        AbilityDefinition goop = AbilityRegistry.of(helper.getLevel()).getAbility(GOOP);
        helper.assertTrue(goop != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, goop);
        return streamer;
    }

    /**
     * One held tick of Goop's stream, aimed wherever the streamer looks.
     *
     * @param streamer the streaming player
     * @return the stream tick
     */
    private static GooStreamPayload goopTick(ServerPlayer streamer) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.WEIRD), GOOP.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
    }

    /**
     * Streams Goop every tick of a hold from the first tick, aimed wherever the streamer looks.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param ticks    how many ticks the hold lasts
     */
    private static void holdGoopFor(GameTestHelper helper, ServerPlayer streamer, int ticks) {
        GooStreamPayload tick = goopTick(streamer);
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
    }

    /**
     * A Weird invoker standing at the bay's west side facing east, pitched down.
     *
     * @param helper the gametest helper
     * @param pitch  the look's pitch, down from level
     * @return the streamer
     */
    private static ServerPlayer streamerAt(GameTestHelper helper, float pitch) {
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.WEIRD);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(pitch);
        return streamer;
    }

    /**
     * Holds Goop's stream through the real stream tick, then removes the
     * streamer, runs the check and succeeds.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param check    the assertions run once the hold ends
     */
    private static void holdGoop(GameTestHelper helper, ServerPlayer streamer, Runnable check) {
        AbilityDefinition goop = AbilityRegistry.of(helper.getLevel()).getAbility(GOOP);
        helper.assertTrue(goop != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, goop);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.WEIRD), GOOP.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held <= GOOP_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        helper.runAfterDelay(GOOP_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            check.run();
            helper.succeed();
        });
    }
}
