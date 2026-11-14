package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.ability.weird.Wobbled;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
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
    /**
     * Past the far lava cell's east side, low in the floor, where the axis drip is aimed, so its arc ends beyond
     * both lava cells and both stand in the cone out to it.
     */
    private static final Vec3 PAST_THE_LAVA = new Vec3(4.9, 0.1, 2.5);
    /** Pitched a little down at a zombie four blocks east. */
    private static final float AT_THE_ZOMBIE = 8f;
    /** Two floor cells three and four blocks east of the streamer, inside Goop's cone pitched at the floor. */
    private static final List<BlockPos> LAVA_CELLS = List.of(new BlockPos(3, 0, 2), new BlockPos(4, 0, 2));
    private static final BlockPos ZOMBIE_POS = new BlockPos(4, 1, 2);
    private static final int GOOP_HOLD_TICKS = 10;
    private static final String GOOP_LAYS_A_COAT = "goop_lays_a_slime_coat_over_the_block";
    private static final String GOOP_LAYING_NOTHING_DRAINS_NOTHING = "goop_laying_no_layer_drains_no_goo";
    /** Pitched straight up, at the slab over the streamer's head. */
    private static final float STRAIGHT_UP = -90f;
    /** Blocks over the streamer's stand to the slab over its head, close enough that the whole cone meets it. */
    private static final int OVERHEAD = 3;
    private static final String DRAINED_WRONG = "The hold should drain %s mb of weird goo, drained %d";
    /** Three blocks east of the streamer, at its height, on a block the test lays. */
    private static final BlockPos COATED_POS = STREAMER_POS.east(3);
    /** A hold one drip a tick sprays the whole fan in, a few ticks over, so every share of the cone has flown. */
    private static final int SPRAY_HOLD_TICKS = CoatRay.FAN_SHARES + 5;
    /** Ticks a hold's last drips take to fall onto a cell three blocks off, with room to spare. */
    private static final int DRIP_FLIGHT_TICKS = 10;
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
    private static final String RAMP_STALLED = "A pig holding jump on the coat should bounce higher the second time,"
            + " peaks were %s";
    private static final String SLIME_BLOB_MAKES_A_SLIME = "slime_blob_makes_a_slime";
    /** SpawnSlimeStep's morph: the ticks the blob takes to become the slime. */
    private static final int SLIME_MORPH_TICKS = 16;
    private static final String NOT_WOBBLED = "A zombie in Goop's stream should be wobbled";
    private static final String HURT_BY_SOFTENED = "A softened zombie's hit should leave the player's health full, has ";
    private static final String NOT_KNOCKED = "A softened zombie's hit should knock the player back";
    private static final String SLIME_LEVELS_OUT = "slime_levels_out_beside_an_open_top";
    private static final String SLIME_POURS_OFF_A_LEDGE = "slime_pours_off_a_ledge";
    /** Ticks the flow is given to settle, far past the handful of pours each test needs. */
    private static final int FLOW_SETTLE_TICKS = 60;
    /** The layers the levelling cell starts at, even so it settles into two equal halves. */
    private static final int EIGHT_LAYERS = 8;
    private static final int FOUR_LAYERS = 4;
    /** The layers a whole block of slime holds. */
    private static final int WHOLE_BLOCK_LAYERS = 15;
    private static final String FULL_CELL_SETTLES = "a_full_slime_cell_settles_across_open_floor";
    /** Ticks watched past the settle, several pours' worth at any pace, for a cell that still changes. */
    private static final int SETTLED_WATCH_TICKS = 20;
    private static final String NOT_SETTLED = "The cross of cells should stand within one layer of its middle"
            + " and hold fifteen layers, holds %s, the middle first";
    private static final String STILL_POURING = "A settled cross should change no cell, held %s, later %s";
    /** The slime cell the flow tests fill, on a stone top in the bay's floor row. */
    private static final BlockPos FLOW_CELL = new BlockPos(2, 1, 2);
    /** The ledge cell's slime, on a stone pillar one block over the floor row. */
    private static final BlockPos LEDGE_CELL = FLOW_CELL.above();
    /** Where the ledge's layers land: the floor row's cell east of the pillar. */
    private static final BlockPos BELOW_THE_LEDGE = FLOW_CELL.east();
    private static final String LAYERS_WRONG = "Slime at %s should hold %d layers, holds %s";
    private static final String BREAKING_SLIME_DROPS = "breaking_slime_drops_its_goo";
    /** Past the eight layers a cell held before a cell held fifteen. */
    private static final int BROKEN_LAYERS = 11;
    private static final String DRIPS_DRAIN_A_LAYER_EACH =
            "goop_drips_drain_a_layers_price_each_and_unpaid_drips_lay_nothing";
    /** The drips the drain test lands, over two ticks. */
    private static final int COUNTED_DRIPS = 4;
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
        GooTestFunctions.registerTest(registrar, GOOP_LAYING_NOTHING_DRAINS_NOTHING,
                WeirdTests::goopLayingNoLayerDrainsNoGoo);
        GooTestFunctions.registerTest(registrar, PAD_BREAKS_THE_FALL, WeirdTests::weirdPadBreaksTheFall);
        GooTestFunctions.registerTest(registrar, BOUNCES_ITEM_AND_ARROW, WeirdTests::slimeBouncesAnItemAndAnArrow);
        GooTestFunctions.registerTest(registrar, HELD_JUMPS_RAMP, WeirdTests::heldJumpsRampToTheCeiling);
        GooTestFunctions.registerTest(registrar, SOFTEN_TRADES_DAMAGE, WeirdTests::goopSoftensTheMobItHits);
        GooTestFunctions.registerTest(registrar, SLIME_BLOB_MAKES_A_SLIME, WeirdTests::slimeBlobMakesASlime);
        GooTestFunctions.registerTest(registrar, BREAKING_SLIME_DROPS, WeirdTests::breakingSlimeDropsItsGoo);
        GooTestFunctions.registerTest(registrar, PLACING_KEEPS_GOO, WeirdTests::placingABlockKeepsTheSlimesGoo);
        GooTestFunctions.registerTest(registrar, SLIME_LEVELS_OUT, WeirdTests::slimeLevelsOutBesideAnOpenTop);
        GooTestFunctions.registerTest(registrar, SLIME_POURS_OFF_A_LEDGE, WeirdTests::slimePoursOffALedge);
        GooTestFunctions.registerTest(registrar, DRIPS_DRAIN_A_LAYER_EACH, WeirdTests::goopDripsDrainALayersPriceEach);
        GooTestFunctions.registerTest(registrar, FULL_CELL_SETTLES, WeirdTests::aFullCellSettlesAcrossOpenFloor);
    }

    /**
     * A full slime cell of fifteen layers, open floor on its four sides and
     * stone walling that cross in, pours until each neighbour stands within
     * one layer of it, the five cells holding the fifteen layers it held, and
     * once settled a later tick changes no cell.
     * slime-is-a-layered-cell-that-levels-out
     * spread-decreases-the-cell-it-leaves
     *
     * @param helper the gametest helper
     */
    public static void aFullCellSettlesAcrossOpenFloor(GameTestHelper helper) {
        List<BlockPos> cross = new java.util.ArrayList<>(List.of(FLOW_CELL));
        Direction.Plane.HORIZONTAL.forEach(side -> cross.add(FLOW_CELL.relative(side)));
        for (BlockPos cell : cross) {
            stoneUnder(helper, cell);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!cross.contains(cell.relative(side))) {
                    helper.setBlock(cell.relative(side), Blocks.STONE);
                }
            }
            helper.setBlock(cell, Blocks.AIR);
        }
        helper.setBlock(FLOW_CELL, slimeOf(WHOLE_BLOCK_LAYERS));
        helper.runAfterDelay(FLOW_SETTLE_TICKS, () -> {
            List<Integer> settled = cross.stream().map(cell -> VolumeCellBlock.layersOf(helper.getBlockState(cell)))
                    .toList();
            int middle = settled.getFirst();
            helper.assertTrue(settled.stream().allMatch(layers -> Math.abs(layers - middle) <= 1)
                    && settled.stream().mapToInt(Integer::intValue).sum() == WHOLE_BLOCK_LAYERS,
                    String.format(NOT_SETTLED, settled));
            helper.runAfterDelay(SETTLED_WATCH_TICKS, () -> {
                List<Integer> later = cross.stream()
                        .map(cell -> VolumeCellBlock.layersOf(helper.getBlockState(cell))).toList();
                helper.assertTrue(later.equals(settled), String.format(STILL_POURING, settled, later));
                helper.succeed();
            });
        });
    }

    /**
     * A counted run of Goop drips landed on a walled cell drains the caster
     * one layer's price for each layer placed, a fifteenth of Goop's cost;
     * once the caster holds no weird goo, a landed drip leaves the cell as it
     * stood.
     * goop-drains-per-cubic-meter-of-slime-placed
     * drip-on-a-standing-cell-increases-it
     *
     * @param helper the gametest helper
     */
    public static void goopDripsDrainALayersPriceEach(GameTestHelper helper) {
        stoneUnder(helper, FLOW_CELL);
        wallSidesBut(helper, FLOW_CELL, null);
        ServerPlayer caster = goopStreamerAt(helper, 0);
        AbilityDefinition goop = goop(helper);
        int before = weirdHeld(caster);
        LandedDrips.landOn(helper, caster, goop, FLOW_CELL, 1);
        LandedDrips.landOn(helper, caster, goop, FLOW_CELL, COUNTED_DRIPS - 1);
        assertLayers(helper, FLOW_CELL, COUNTED_DRIPS);
        assertDrained(helper, ResidualPrices.layerPrice(helper).times(COUNTED_DRIPS), before - weirdHeld(caster));
        GooSourceScanner.deplete(caster, GooTypes.WEIRD, weirdHeld(caster));
        LandedDrips.landOn(helper, caster, goop, FLOW_CELL, 1);
        assertLayers(helper, FLOW_CELL, COUNTED_DRIPS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    private static AbilityDefinition goop(GameTestHelper helper) {
        AbilityDefinition goop = AbilityRegistry.of(helper.getLevel()).getAbility(GOOP);
        helper.assertTrue(goop != null, ABILITY_REQUIRED);
        return goop;
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
        helper.setBlock(FLOW_CELL, slimeOf(EIGHT_LAYERS));
        helper.runAfterDelay(FLOW_SETTLE_TICKS, () -> {
            assertLayers(helper, FLOW_CELL, FOUR_LAYERS);
            assertLayers(helper, open, FOUR_LAYERS);
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
        helper.setBlock(LEDGE_CELL, slimeOf(FOUR_LAYERS));
        helper.runAfterDelay(FLOW_SETTLE_TICKS, () -> {
            assertLayers(helper, LEDGE_CELL, 1);
            assertLayers(helper, BELOW_THE_LEDGE, FOUR_LAYERS - 1);
            helper.succeed();
        });
    }

    /**
     * A slime cell of eleven layers, walled in so nothing pours, broken in a
     * live world leaves a weird goo item in its cell worth eleven layers'
     * price, each a fifteenth of the loaded Goop definition's cost.
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
        int owed = ResidualPrices.layersRefund(helper, BROKEN_LAYERS);
        helper.assertTrue(weird == owed, String.format(RECLAIM_WRONG, owed, weird, drops));
        helper.succeed();
    }

    /**
     * A survival player uses a stone block item against an eleven layer slime
     * cell, walled in so nothing pours: the cell keeps all its weird goo,
     * standing at eleven layers or dropping what they cost.
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
        Vec3 slimeTop = Vec3.atBottomCenterOf(cell).add(0, (double) BROKEN_LAYERS / WHOLE_BLOCK_LAYERS, 0);
        stone.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(slimeTop, Direction.UP, cell, false)));
        int standing = VolumeCellBlock.layersOf(helper.getBlockState(FLOW_CELL));
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, FLOW_CELL, 1.0);
        int weird = drops.stream().map(ItemEntity::getItem)
                .filter(stack -> GooTypes.WEIRD.equals(GooStacks.keyOf(stack))).mapToInt(GooStacks::volumeOf).sum();
        int owed = ResidualPrices.layersRefund(helper, BROKEN_LAYERS);
        helper.assertTrue(standing == BROKEN_LAYERS || weird == owed,
                String.format(PLACED_OVER_SLIME, BROKEN_LAYERS, owed, helper.getBlockState(FLOW_CELL), weird));
        helper.succeed();
    }

    private static BlockState slimeOf(int layers) {
        return GooBlocks.SLIME_COAT.get().defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers);
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
        Object held = state.is(GooBlocks.SLIME_COAT.get()) ? state.getValue(VolumeCellBlock.LAYERS) : state;
        helper.assertTrue(Integer.valueOf(layers).equals(held), String.format(LAYERS_WRONG, cell, layers, held));
    }

    /**
     * A Weird Mitosis blob lands on stone: once the morph ends a slime stands
     * in the cell the blob landed in.
     * slime-blob-morphs-into-the-slime-it-becomes
     *
     * @param helper the gametest helper
     */
    public static void slimeBlobMakesASlime(GameTestHelper helper) {
        helper.setBlock(POOL_FLOOR, Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, MITOSIS));
        helper.runAfterDelay(SLIME_MORPH_TICKS + 1, () -> {
            helper.assertEntityPresent(EntityType.SLIME, POOL_FLOOR.above());
            helper.succeed();
        });
    }

    /**
     * A zombie in Goop's stream is wobbled and strikes a survival player: the
     * player keeps its full health and is knocked back instead.
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
     * A pig holding jump on a slime coat bounces higher on its second landing
     * than its first.
     * bounce-ramps-jump-height-while-jump-is-held
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
        // The one live read of a jumping entity on the coat: its second bounce peaks above its first.
        helper.succeedWhen(() -> helper.assertTrue(peaks.size() >= 2 && peaks.get(1) > peaks.get(0),
                String.format(RAMP_STALLED, peaks)));
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
        ServerPlayer streamer = streamerAt(helper, 0);
        aimDripAt(helper, streamer, helper.absoluteVec(PAST_THE_LAVA));
        holdGoop(helper, streamer, () -> {
            for (BlockPos cell : LAVA_CELLS) {
                helper.assertTrue(helper.getBlockState(cell).is(Blocks.MAGMA_BLOCK), LAVA_LEFT + cell);
            }
        });
    }

    /**
     * Goop's stream held on a stone block while it sprays its whole fan lays
     * a slime coat on the stone's top and leaves the stone standing as stone.
     *
     * @param helper the gametest helper
     */
    public static void goopLaysASlimeCoatOverTheBlock(GameTestHelper helper) {
        ServerPlayer streamer = goopStreamerOverStone(helper);
        int hold = SPRAY_HOLD_TICKS;
        holdGoopFor(helper, streamer, hold);
        // goop-and-slick-coat-lands-where-each-drip-arc-strikes: the hold's last drips land after it ends
        helper.runAfterDelay(hold + DRIP_FLIGHT_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertBlockPresent(GooBlocks.SLIME_COAT.get(), COATED_POS.above());
            helper.assertBlockPresent(Blocks.STONE, COATED_POS);
            helper.succeed();
        });
    }

    /**
     * A Goop hold aimed straight up at a bottom slab over the streamer's
     * head, whose top holds no coat, so no cell the stream reaches can take
     * one, lasting three sprays of the whole fan, drains no weird goo.
     * goop-drains-per-cubic-meter-of-slime-placed
     *
     * @param helper the gametest helper
     */
    public static void goopLayingNoLayerDrainsNoGoo(GameTestHelper helper) {
        helper.setBlock(STREAMER_POS.above(OVERHEAD), Blocks.STONE_SLAB);
        ServerPlayer streamer = goopStreamerAt(helper, STRAIGHT_UP);
        int before = weirdHeld(streamer);
        int held = SPRAY_HOLD_TICKS * 3;
        holdGoopFor(helper, streamer, held);
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertDrained(helper, GooFraction.ZERO, before - weirdHeld(streamer));
            helper.succeed();
        });
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
     * Lays stone on bedrock three blocks east of the streamer and stands a
     * Goop streamer aimed at it.
     *
     * @param helper the gametest helper
     * @return the streamer
     */
    private static ServerPlayer goopStreamerOverStone(GameTestHelper helper) {
        helper.setBlock(COATED_POS.below(), Blocks.BEDROCK);
        helper.setBlock(COATED_POS, Blocks.STONE);
        ServerPlayer streamer = goopStreamerAt(helper, 0);
        aimDripAtTopOf(helper, streamer, COATED_POS.above());
        return streamer;
    }

    /**
     * Aims a streamer's axis drip down onto the middle of a cell's floor or
     * of the slime standing in it, since a drip striking a side lays nothing.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param cell     the cell
     */
    private static void aimDripAtTopOf(GameTestHelper helper, ServerPlayer streamer, BlockPos cell) {
        double top = (double) VolumeCellBlock.layersOf(helper.getBlockState(cell)) / VolumeCellBlock.MAX_LAYERS;
        aimDripAt(helper, streamer, Vec3.atBottomCenterOf(helper.absolutePos(cell)).add(0, top, 0));
    }

    /**
     * Aims a streamer's axis drip at a point at the live Goop's throw speed.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param target   the point, in world space
     */
    private static void aimDripAt(GameTestHelper helper, ServerPlayer streamer, Vec3 target) {
        AbilityDefinition goop = AbilityRegistry.of(helper.getLevel()).getAbility(GOOP);
        helper.assertTrue(goop != null, ABILITY_REQUIRED);
        DripAim.at(streamer, target, goop.delivery().throwSpeed());
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
