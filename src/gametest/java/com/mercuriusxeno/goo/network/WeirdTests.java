package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.CoatStep;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.ability.weird.Wobbled;
import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Weird's abilities, each cast through its real path: a blob
 * through its landing, Coat through the stream tick.
 * weird-bounces-and-softens-harm
 */
@RegistersGameTests
public final class WeirdTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final Identifier COAT = Identifier.parse("goo:weird_coat");
    private static final String ABILITY_REQUIRED = "Ability registry must hold goo:weird_coat";
    private static final String COAT_COOLS_LAVA = "coat_cools_lava_to_magma";
    private static final String SOFTEN_TRADES_DAMAGE = "coat_softens_the_mob_it_hits";
    private static final BlockPos POOL_FLOOR = new BlockPos(2, 0, 2);
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Pitched down at the floor three and four blocks east, so both floor cells stand in Coat's cone. */
    private static final float AT_THE_FLOOR = 32f;
    /** Pitched a little down at a zombie four blocks east. */
    private static final float AT_THE_ZOMBIE = 8f;
    /** Two floor cells three and four blocks east of the streamer, inside Coat's cone pitched at the floor. */
    private static final List<BlockPos> LAVA_CELLS = List.of(new BlockPos(3, 0, 2), new BlockPos(4, 0, 2));
    private static final BlockPos ZOMBIE_POS = new BlockPos(4, 1, 2);
    private static final int COAT_HOLD_TICKS = 10;
    private static final String COAT_LAYS_A_COAT = "coat_lays_a_slime_coat_over_the_block";
    private static final String COAT_ONCE = "coat_coats_each_cell_once_per_activation";
    private static final String COAT_PAST_NINE_TENTHS_FINISHES = "coat_past_nine_tenths_finishes_after_release";
    /** Three blocks east of the streamer, at its height, on a block the test lays. */
    private static final BlockPos COATED_POS = STREAMER_POS.east(3);
    /** Pitch down from the eye toward the stone's center three blocks off. */
    private static final float AT_THE_STONE = 20.5f;
    /** weird_coat.json's coat ticks: twenty ticks of stream coat a block. */
    private static final int COAT_STEP_TICKS = 20;
    /** Ticks past a coat's worth, so a coat has surely landed. */
    private static final int COAT_SLACK_TICKS = 5;
    /** A hold three coats long, so a second coat would land without the once rule. */
    private static final int THREE_COATS = 3;
    /** A hold past nine tenths of a coat, short of the whole. */
    private static final int PAST_NINE_TENTHS_TICKS = COAT_STEP_TICKS - 1;
    private static final String LAVA_LEFT = "Coat's stream should leave magma where the lava stood at ";
    private static final String BOUNCE = "goo:weird_bounce";
    private static final String PAD_BREAKS_THE_FALL = "weird_pad_breaks_the_fall";
    /** The test bay's roof stands five blocks over the pad, so the pig starts under it. */
    private static final int DROP_START = 3;
    /** A fall a pig at ten health dies of on stone, carried in as the fall it has already made. */
    private static final double FALLEN_BLOCKS = 20;
    private static final double FALL_SPEED = -1.0;
    private static final String NO_PAD = "Weird Bounce landing on stone should lay a slime coat on it";
    private static final String NOT_LAUNCHED = "A pig falling onto the slime coat should leave it moving upward";
    private static final String HURT = "A pig falling onto the slime coat should keep its full health, has ";
    private static final String SLIME = "goo:weird_slime";
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
    private static final String NOT_WOBBLED = "A zombie in Coat's stream should be wobbled";
    private static final String HURT_BY_SOFTENED = "A softened zombie's hit should leave the player's health full, has ";
    private static final String NOT_KNOCKED = "A softened zombie's hit should knock the player back";
    private static final String FADED_STILL_SOFT = "Once the wobble has faded, the zombie's hit should hurt the player";
    private static final String SLIME_LEVELS_OUT = "slime_levels_out_beside_an_open_top";
    private static final String SLIME_POURS_OFF_A_LEDGE = "slime_pours_off_a_ledge";
    private static final String COAT_ADDS_A_LAYER = "coat_adds_a_layer_to_standing_slime";
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
    private static final String BOUNCE_SPLASHES = "bounce_splashes_k_layers";
    private static final String BREAKING_SLIME_DROPS = "breaking_slime_drops_its_goo";
    private static final int BROKEN_LAYERS = 3;
    private static final String RECLAIM_WRONG = "A broken slime cell should drop %d mb of weird goo, dropped %d in %s";
    private static final String BOUNCE_REQUIRED = "Ability registry must hold goo:weird_bounce";
    private static final String NO_SPLASH_LAYERS = "weird_bounce.json should name a coat step laying layers";
    private static final String SPLASH_WRONG = "Bounce's splash should leave %d layers over the landing cell and"
            + " its four side neighbours, left %d";

    private WeirdTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, COAT_COOLS_LAVA, WeirdTests::coatCoolsLavaToMagma);
        GooTestFunctions.registerTest(registrar, COAT_LAYS_A_COAT, WeirdTests::coatLaysASlimeCoatOverTheBlock);
        GooTestFunctions.registerTest(registrar, COAT_ONCE, WeirdTests::coatCoatsEachCellOncePerActivation);
        GooTestFunctions.registerTest(registrar, COAT_PAST_NINE_TENTHS_FINISHES,
                WeirdTests::coatPastNineTenthsFinishesAfterRelease);
        GooTestFunctions.registerTest(registrar, PAD_BREAKS_THE_FALL, WeirdTests::weirdPadBreaksTheFall);
        GooTestFunctions.registerTest(registrar, HELD_JUMPS_RAMP, WeirdTests::heldJumpsRampToTheCeiling);
        GooTestFunctions.registerTest(registrar, SOFTEN_TRADES_DAMAGE, WeirdTests::coatSoftensTheMobItHits);
        GooTestFunctions.registerTest(registrar, SLIME_BLOB_MAKES_A_SLIME, WeirdTests::slimeBlobMakesASlime);
        GooTestFunctions.registerTest(registrar, BOUNCE_SPLASHES, WeirdTests::bounceSplashesKLayers);
        GooTestFunctions.registerTest(registrar, BREAKING_SLIME_DROPS, WeirdTests::breakingSlimeDropsItsGoo);
        GooTestFunctions.registerTest(registrar, SLIME_LEVELS_OUT, WeirdTests::slimeLevelsOutBesideAnOpenTop);
        GooTestFunctions.registerTest(registrar, SLIME_POURS_OFF_A_LEDGE, WeirdTests::slimePoursOffALedge);
        GooTestFunctions.registerTest(registrar, COAT_ADDS_A_LAYER, WeirdTests::coatAddsALayerToStandingSlime);
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
     * Coat's stream held over a cell already holding one layer of slime, its
     * side neighbours holding a layer each so nothing pours, leaves that cell
     * at two layers.
     * slime-is-a-layered-cell-that-levels-out
     *
     * @param helper the gametest helper
     */
    public static void coatAddsALayerToStandingSlime(GameTestHelper helper) {
        ServerPlayer streamer = coaterOverStone(helper);
        streamer.setXRot(AT_THE_SLIME);
        BlockPos slimed = COATED_POS.above();
        helper.setBlock(slimed, slimeOf(1));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(COATED_POS.relative(side), Blocks.STONE);
            helper.setBlock(slimed.relative(side), slimeOf(1));
        }
        holdCoatFor(helper, streamer, COAT_STEP_TICKS + COAT_SLACK_TICKS);
        helper.runAfterDelay(COAT_STEP_TICKS + COAT_SLACK_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertLayers(helper, slimed, 2);
            helper.succeed();
        });
    }

    /**
     * A Weird Bounce blob lands on a stone floor: once the flow settles, the
     * landing cell and its four side neighbours hold slime whose layers sum
     * to the k weird_bounce.json's coat step names.
     * bounce-throw-dumps-layers-that-flow-out
     *
     * @param helper the gametest helper
     */
    public static void bounceSplashesKLayers(GameTestHelper helper) {
        AbilityDefinition bounce = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(BOUNCE));
        helper.assertTrue(bounce != null, BOUNCE_REQUIRED);
        int named = bounce.behaviors().stream().filter(CoatStep.class::isInstance).map(CoatStep.class::cast)
                .mapToInt(CoatStep::layers).sum();
        helper.assertTrue(named > 0, NO_SPLASH_LAYERS);
        BlockPos landing = POOL_FLOOR.above();
        List<BlockPos> splash = new java.util.ArrayList<>(List.of(landing));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            splash.add(landing.relative(side));
        }
        for (BlockPos cell : splash) {
            stoneUnder(helper, cell);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                stoneUnder(helper, cell.relative(side));
            }
        }
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, BOUNCE));
        helper.runAfterDelay(FLOW_SETTLE_TICKS, () -> {
            int laid = splash.stream().mapToInt(cell -> SlimeCoatBlock.layersOf(helper.getBlockState(cell))).sum();
            helper.assertTrue(laid == named, String.format(SPLASH_WRONG, named, laid));
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
     * A Weird Slime blob lands on stone: no slime stands anywhere while the
     * blob morphs, and once the morph ends a slime stands in the cell the
     * blob landed in.
     * slime-blob-morphs-into-the-slime-it-becomes
     *
     * @param helper the gametest helper
     */
    public static void slimeBlobMakesASlime(GameTestHelper helper) {
        helper.setBlock(POOL_FLOOR, Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, SLIME));
        helper.assertEntityNotPresent(EntityType.SLIME);
        helper.runAfterDelay(SLIME_MORPH_TICKS - 1, () -> helper.assertEntityNotPresent(EntityType.SLIME));
        helper.runAfterDelay(SLIME_MORPH_TICKS + 1, () -> {
            helper.assertEntityPresent(EntityType.SLIME, POOL_FLOOR.above());
            helper.succeed();
        });
    }

    /**
     * A zombie in Coat's stream is wobbled and strikes a survival player: the
     * player keeps its full health and is knocked back instead; once the
     * wobble has faded, the zombie's next hit hurts the player.
     *
     * @param helper the gametest helper
     */
    public static void coatSoftensTheMobItHits(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        ServerPlayer streamer = streamerAt(helper, AT_THE_ZOMBIE);
        holdCoat(helper, streamer, () -> {
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
     * A Weird Bounce blob lands on stone and lays a slime coat on it; a pig
     * falling onto the coat with twenty blocks of fall behind it keeps its
     * full health and leaves the coat moving upward.
     * bounce-ramps-jump-height-while-jump-is-held
     *
     * @param helper the gametest helper
     */
    public static void weirdPadBreaksTheFall(GameTestHelper helper) {
        helper.setBlock(POOL_FLOOR, Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, BOUNCE));
        BlockPos coat = POOL_FLOOR.above();
        helper.assertTrue(helper.getBlockState(coat).is(GooBlocks.SLIME_COAT.get()), NO_PAD);
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
     * Coat's stream held over two lava cells in a stone floor cools both to
     * magma blocks.
     *
     * @param helper the gametest helper
     */
    public static void coatCoolsLavaToMagma(GameTestHelper helper) {
        for (BlockPos cell : LAVA_CELLS) {
            helper.setBlock(cell, Blocks.LAVA);
        }
        ServerPlayer streamer = streamerAt(helper, AT_THE_FLOOR);
        holdCoat(helper, streamer, () -> {
            for (BlockPos cell : LAVA_CELLS) {
                helper.assertTrue(helper.getBlockState(cell).is(Blocks.MAGMA_BLOCK), LAVA_LEFT + cell);
            }
        });
    }

    /**
     * Coat's stream held on a stone block past one coat's worth lays a slime
     * coat on the stone's top and leaves the stone standing as stone.
     *
     * @param helper the gametest helper
     */
    public static void coatLaysASlimeCoatOverTheBlock(GameTestHelper helper) {
        ServerPlayer streamer = coaterOverStone(helper);
        holdCoatFor(helper, streamer, COAT_STEP_TICKS + COAT_SLACK_TICKS);
        helper.runAfterDelay(COAT_STEP_TICKS + COAT_SLACK_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertBlockPresent(GooBlocks.SLIME_COAT.get(), COATED_POS.above());
            helper.assertBlockPresent(Blocks.STONE, COATED_POS);
            helper.succeed();
        });
    }

    /**
     * Coat's stream held on a stone block for three coats' worth, the coat
     * cleared away once it has landed: the hold lays no second coat there.
     *
     * @param helper the gametest helper
     */
    public static void coatCoatsEachCellOncePerActivation(GameTestHelper helper) {
        ServerPlayer streamer = coaterOverStone(helper);
        int held = COAT_STEP_TICKS * THREE_COATS;
        holdCoatFor(helper, streamer, held);
        helper.runAfterDelay(COAT_STEP_TICKS + COAT_SLACK_TICKS, () -> {
            helper.assertBlockPresent(GooBlocks.SLIME_COAT.get(), COATED_POS.above());
            helper.setBlock(COATED_POS.above(), Blocks.AIR);
        });
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertBlockPresent(Blocks.AIR, COATED_POS.above());
            helper.succeed();
        });
    }

    /**
     * Coat's stream held on a stone block past nine tenths of a coat and
     * released: the coat finishes on its own.
     *
     * @param helper the gametest helper
     */
    public static void coatPastNineTenthsFinishesAfterRelease(GameTestHelper helper) {
        ServerPlayer streamer = coaterOverStone(helper);
        holdCoatFor(helper, streamer, PAST_NINE_TENTHS_TICKS);
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
     * Coat streamer aimed at it.
     *
     * @param helper the gametest helper
     * @return the streamer
     */
    private static ServerPlayer coaterOverStone(GameTestHelper helper) {
        helper.setBlock(COATED_POS.below(), Blocks.BEDROCK);
        helper.setBlock(COATED_POS, Blocks.STONE);
        ServerPlayer streamer = streamerAt(helper, AT_THE_STONE);
        AbilityDefinition coat = AbilityRegistry.of(helper.getLevel()).getAbility(COAT);
        helper.assertTrue(coat != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, coat);
        return streamer;
    }

    /**
     * Streams Coat every tick of a hold from the first tick, aimed wherever the streamer looks.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param ticks    how many ticks the hold lasts
     */
    private static void holdCoatFor(GameTestHelper helper, ServerPlayer streamer, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.WEIRD), COAT.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
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
     * Holds Coat's stream through the real stream tick, then removes the
     * streamer, runs the check and succeeds.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param check    the assertions run once the hold ends
     */
    private static void holdCoat(GameTestHelper helper, ServerPlayer streamer, Runnable check) {
        AbilityDefinition coat = AbilityRegistry.of(helper.getLevel()).getAbility(COAT);
        helper.assertTrue(coat != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, coat);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.WEIRD), COAT.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held <= COAT_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        helper.runAfterDelay(COAT_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            check.run();
            helper.succeed();
        });
    }
}
