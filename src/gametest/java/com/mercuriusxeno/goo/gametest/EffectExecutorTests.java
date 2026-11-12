package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.entity.CompressedHoard;
import com.mercuriusxeno.goo.entity.CompressionSphere;
import com.mercuriusxeno.goo.entity.SphereCore;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooDragCastHandler;
import com.mercuriusxeno.goo.network.GooDragCastPayload;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Gametests for ability blocks. Each test stands a marker initialized
 * through an ability, which runs the ability's program from the tick its
 * blob splats (decision splat-runs-the-program-no-fuse). Covers the
 * AbilityBlockEntity tick lifecycle, the programs the abilities
 * declare, the fall and the goo landing on a block.
 */
@RegistersGameTests
public final class EffectExecutorTests {
    private static final String FX_BLACK_HOLE_HOLDS_ITS_PLACE = "fx_black_hole_holds_its_place";
    private static final String FX_PROGRAM_MINE = "fx_program_proximity_mine";
    private static final String FX_PROGRAM_METAL_SPIKES = "fx_program_metal_spikes";
    private static final String FX_PROGRAM_CRYSTAL_CLOUD = "fx_program_crystal_cloud";
    private static final String BLACK_HOLE_LEAVES_A_SPHERE = "black_hole_leaves_a_sphere";
    private static final String COMPRESSION_SPHERE_SPILLS_ON_TOUCH = "compression_sphere_spills_on_touch";
    private static final String LARGE_BLAST_LEAVES_A_GREEN_SPHERE = "large_blast_leaves_a_green_sphere";
    private static final String SMALL_BLAST_POPS_ITS_DROPS = "small_blast_pops_its_drops";
    private static final String GREEN_SPHERE_KEEPS_ITS_CORE = "green_sphere_keeps_its_core";
    private static final String FX_LANDED_CRYSTAL_CLOUD = "fx_landed_crystal_cloud_live";
    private static final String FX_CRYSTAL_CLOUD_SHREDS_PLAYER = "fx_crystal_cloud_shreds_walking_player";
    private static final String FX_LANDED_BLAST = "fx_landed_blast_explodes";
    private static final String FX_LANDED_GLOW_CRYSTAL = "fx_landed_glow_crystal_stands";
    private static final String FX_FALLEN_MARKER_KEEPS_ABILITY = "fx_fallen_marker_keeps_ability";
    private static final String FX_OTHER_ABILITY_MARKS_CRYSTAL = "fx_other_ability_marks_crystal";
    private static final String FX_CLOUD_BLOCK_GOES = "fx_cloud_block_goes";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, FX_BLACK_HOLE_HOLDS_ITS_PLACE, EffectExecutorTests::blackHoleHoldsItsPlace);
        GooTestFunctions.registerTest(registrar, FX_PROGRAM_MINE, EffectExecutorTests::programProximityMine);
        GooTestFunctions.registerTest(registrar, FX_PROGRAM_METAL_SPIKES, EffectExecutorTests::programMetalSpikes);
        GooTestFunctions.registerTest(registrar, FX_PROGRAM_CRYSTAL_CLOUD, EffectExecutorTests::programCrystalCloud);
        GooTestFunctions.registerTest(registrar, BLACK_HOLE_LEAVES_A_SPHERE, EffectExecutorTests::blackHoleLeavesASphere);
        GooTestFunctions.registerTest(registrar, COMPRESSION_SPHERE_SPILLS_ON_TOUCH, EffectExecutorTests::compressionSphereSpillsOnTouch);
        GooTestFunctions.registerTest(registrar, LARGE_BLAST_LEAVES_A_GREEN_SPHERE, EffectExecutorTests::largeBlastLeavesAGreenSphere);
        GooTestFunctions.registerTest(registrar, SMALL_BLAST_POPS_ITS_DROPS, EffectExecutorTests::smallBlastPopsItsDrops);
        GooTestFunctions.registerTest(registrar, GREEN_SPHERE_KEEPS_ITS_CORE, EffectExecutorTests::greenSphereKeepsItsCoreThroughASave);
        GooTestFunctions.registerTest(registrar, FX_LANDED_CRYSTAL_CLOUD, EffectExecutorTests::crystalCloudLiveAfterLanding);
        GooTestFunctions.registerTest(registrar, FX_CRYSTAL_CLOUD_SHREDS_PLAYER, EffectExecutorTests::crystalCloudShredsAWalkingPlayer);
        GooTestFunctions.registerTest(registrar, FX_LANDED_BLAST, EffectExecutorTests::blastExplodesAfterLanding);
        GooTestFunctions.registerTest(registrar, FX_LANDED_GLOW_CRYSTAL, EffectExecutorTests::glowCrystalStandsAfterLanding);
        GooTestFunctions.registerTest(registrar, FX_FALLEN_MARKER_KEEPS_ABILITY, EffectExecutorTests::fallenMarkerKeepsAbility);
        GooTestFunctions.registerTest(registrar, FX_OTHER_ABILITY_MARKS_CRYSTAL, EffectExecutorTests::otherAbilityMarksCrystal);
        GooTestFunctions.registerTest(registrar, FX_CLOUD_BLOCK_GOES, EffectExecutorTests::crystalCloudBlockGoesWithItsProgram);
    }


    /** Marker placed here, facing NORTH into the stone wall. */
    private static final BlockPos MARKER_POS = new BlockPos(3, 1, 3);
    /** Ticks a short program, or a check after an event, waits. */
    private static final int SHORT_WAIT = 5;
    /** The tick after the blob lands, where its program's first tick shows. */
    private static final int TICK_AFTER_LANDING = 1;
    private static final int NO_ENTITY = -1;
    private static final String NOT_RUNNING = "The marker's program is not running the tick after its blob landed";
    private static final String BLOCK_OUTLIVED_PROGRAM = "The lingering block stood on after its program ended";
    /** Ticks a shuffling pig takes to draw the cloud's eight shreds and the cloud to contract. */
    private static final int CLOUD_LIFE_TICKS = 40;
    private static final String FIELD_NOT_LIVE = "The field effect is not live the tick after its blob landed";
    private static final int WALL_X_MIN = 0;
    private static final int WALL_X_MAX = 5;
    private static final int WALL_Y_MAX = 3;
    private static final int WALL_Z_MAX = 2;
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    /** The gametests' own thrown blast, today's Blast before it was drag-sized, for the landing machinery. */
    private static final String ABILITY_BLAST = "goo:test_blast";
    private static final String ABILITY_LURKER = "goo:unstable_lurker";
    private static final String ABILITY_GLOW_CRYSTAL = "goo:glow_crystal";
    /** Ticks an armed mine idles before the test spawns a target. */
    private static final int MINE_IDLE_TICKS = 5;
    /** Where the mine's target spawns: two blocks from the marker, inside its radius. */
    private static final BlockPos MINE_TARGET_POS = MARKER_POS.east(2);
    private static final String ABILITY_METAL_SPIKES = "goo:metal_spikes";
    /**
     * Ticks from a target entering the trap to the check that it was
     * impaled: past the strike's six-tick windup and inside the ten-tick
     * cooldown, so the trap has struck once.
     */
    private static final int SPIKE_STRIKE_WINDOW = 8;
    private static final String SPIKE_MISSED = "The metal trap left the walking pig unhurt";
    private static final String ABILITY_CRYSTAL_CLOUD = "goo:crystal_cloud";
    /** Where the black hole's diamond lies: two blocks west, inside its sphere. */
    private static final BlockPos DIAMOND_POS = MARKER_POS.west(2);
    /** Horizontal speed the moving pig is shuffled at, reversed every tick so it stays put. */
    private static final double SHUFFLE_SPEED = 0.2;
    /** Ticks per back-and-forth of the moving pig's shuffle. */
    private static final int SHUFFLE_PERIOD = 2;
    /** Ticks after the splat the cloud shreds for, several of its two-tick periods. */
    private static final int SHRED_WINDOW = 12;
    private static final String CLOUD_MISSED_MOVER = "The crystal cloud left the moving pig unhurt";
    private static final String CLOUD_MISSED_WALKER = "The crystal cloud left the walking survival player unhurt";
    /** Blocks a walking player covers in a tick, near vanilla's walking speed. */
    private static final double WALK_STEP = 0.2;
    private static final String ABILITY_BLACK_HOLE = "goo:void_dark_star";
    /** The size a black hole is dragged to, the radius its JSON cost buys. */
    private static final double HOLE_SIZE = 3;
    /** Blocks south of the marker the caster stands, past the hole's pull of three times its size. */
    private static final int CASTER_DISTANCE = 12;
    /** The void goo the caster holds, more than the reference cost. */
    private static final int CASTER_GOO = 5000;
    private static final double HALF_BLOCK = 0.5;
    private static final String HOLE_CHARGED_WRONG = "The dragged hole should charge %d mB, charged %d";
    /** Ticks the black hole expands before it consumes its sphere. */
    private static final int BLACK_HOLE_EXPAND_TICKS = 15;
    /** Ticks the black hole gathers before it expands, while its inward rush plays. */
    private static final int BLACK_HOLE_GATHER_TICKS = 20;
    /** Ticks from the splat to the tick the black hole pops: gather, expand, hold and contract. */
    private static final int BLACK_HOLE_LIFE_TICKS = 80;
    /** The barrier floor spans the one-stack sphere's footprint around the marker, three blocks each way. */
    private static final int BARRIER_FLOOR_MIN = 0;
    private static final int BARRIER_FLOOR_MAX = 6;
    /** Blocks around the marker searched for popped goo, the reach of the barrier floor. */
    private static final double ITEM_SEARCH_RADIUS = 4;
    /**
     * Blocks past the test's own bay the leftover clear reaches, under the
     * five-block gap the game test grid leaves between bays, so the clear
     * never reaches into a neighbouring test's bay.
     */
    private static final double LEFTOVER_CLEAR_MARGIN = 2;
    private static final String HOLE_LEFT_STONE = "The black hole left the stone it faced standing";
    private static final String HOLE_SPARED_ZOMBIE = "The black hole left the zombie inside it alive";
    private static final String HOLE_MOVED = "The black hole left the cell it landed in while it ran";
    private static final String HOLE_LEFT_SPHERES = "The black hole left %d compression spheres, not one";
    private static final String HOLE_HELD_NO_STONE = "The sphere holds no stone for the wall it took: ";
    private static final String HOLE_HELD_NO_DIAMOND = "The sphere holds no diamond for the item it pulled in: ";
    private static final int SPILLED_STONE = 70;
    private static final String SPHERE_STAYED_SHUT = "The touched sphere is still standing";
    private static final String SPHERE_SPILLED_WRONG = "The touched sphere spilled other than 70 stone and a diamond: ";
    private static final String HOLE_SPHERE_NOT_BLACK = "The black hole's sphere should draw the black core, draws %s";
    private static final String ABILITY_BLAST_DRAGGED = "goo:unstable_explode";
    /** The radius a Blast gathers its drops from, the smallest the decision names. */
    private static final double GATHERING_BLAST_SIZE = 3;
    /** A radius under the gathering one, whose drops pop in place. */
    private static final double POPPING_BLAST_SIZE = 2;
    /** The unstable the Blast's caster holds, more than the radius 3 price. */
    private static final int CASTER_UNSTABLE = 5000;
    private static final String BLAST_LEFT_SPHERES = "The radius 3 Blast left %d compression spheres, not one";
    private static final String BLAST_HELD_NO_COBBLESTONE = "The Blast's sphere holds no cobblestone for the stone it mined: ";
    private static final String BLAST_SPHERE_NOT_GREEN = "The Blast's sphere should draw the green core, draws %s";
    private static final String BLAST_TOOK_THE_DIAMOND = "The diamond lying in the Blast no longer lies loose: ";
    private static final String BLAST_LEFT_LOOSE_COBBLESTONE = "Cobblestone lies loose in the radius 3 Blast's crater: ";
    private static final String SMALL_BLAST_LEFT_A_SPHERE = "The radius 2 Blast left a compression sphere";
    private static final String SMALL_BLAST_POPPED_NOTHING = "No cobblestone lies in the radius 2 Blast's crater: ";
    private static final String SPHERE_RELOAD_LOST = "The saved compression sphere did not load back as one";
    private static final String RELOADED_SPHERE_NOT_GREEN = "The reloaded green sphere draws %s";

    /** The floor a falling marker lands on. */
    private static final BlockPos FALL_FLOOR_POS = new BlockPos(3, 1, 3);
    /** Where the falling marker lands: the air on top of the floor. */
    private static final BlockPos FALL_LANDING_POS = FALL_FLOOR_POS.above();
    /** The support the test breaks from under the marker. */
    private static final BlockPos FALL_SUPPORT_POS = FALL_LANDING_POS.above();
    /** Where the marker stands before its support breaks. */
    private static final BlockPos FALL_START_POS = FALL_SUPPORT_POS.above();
    /** Ticks after the support breaks by which a two-block fall has landed. */
    private static final int FALL_LANDED_TICKS = 10;
    private static final String FALL_ABILITY_LOST = "The fallen marker lost its ability id";
    private static final String FALL_PROGRAM_LOST = "The fallen marker lost its running program";
    /** Where the growth tests stand their glow crystal, on stone below it. */
    private static final BlockPos CRYSTAL_POS = new BlockPos(3, 2, 3);

    private EffectExecutorTests() {}

    /**
     * Void: a black hole that consumes the wall holding it up stays in the
     * cell it landed in until its phases end, rather than falling mid-animation.
     *
     * @param helper the gametest helper
     */
    public static void blackHoleHoldsItsPlace(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        fillWall(helper, Blocks.STONE);
        placeMarkerWithAbility(helper, GooTypes.VOID, ABILITY_BLACK_HOLE, HOLE_SIZE);
        helper.runAfterDelay(BLACK_HOLE_GATHER_TICKS + BLACK_HOLE_EXPAND_TICKS + SHORT_WAIT, () -> {
            helper.assertTrue(helper.getBlockState(MARKER_POS.north()).isAir(), HOLE_LEFT_STONE);
            helper.assertTrue(helper.getBlockState(MARKER_POS).is(GooBlocks.ABILITY_BLOCK.get()), HOLE_MOVED);
            helper.succeed();
        });
    }

    // --- Glow crystal program (task glow-crystal-program) ---

    /**
     * Asserts the glow crystal replaced the marker with facing from the
     * placed face, its one property.
     *
     * @param helper the gametest helper
     * @param facing the placed face the crystal must face
     */
    private static void assertGlowCrystal(GameTestHelper helper, Direction facing) {
        helper.assertBlockPresent(GooBlocks.GLOW_CRYSTAL.get(), MARKER_POS);
        helper.assertBlockProperty(MARKER_POS, GlowCrystalBlock.FACING, facing);
    }

    /**
     * A metal_spikes marker whose support breaks while its program runs
     * falls to the floor below and lands carrying its ability id and its
     * running program (decisions no-throw-without-ability,
     * splat-runs-the-program-no-fuse).
     *
     * @param helper the gametest helper
     */
    public static void fallenMarkerKeepsAbility(GameTestHelper helper) {
        helper.setBlock(FALL_FLOOR_POS, Blocks.STONE);
        helper.setBlock(FALL_SUPPORT_POS, Blocks.STONE);
        AbilityDefinition spikes = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ABILITY_METAL_SPIKES));
        helper.assertTrue(spikes != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(FALL_SUPPORT_POS), GooTypes.METAL, Direction.UP, spikes);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), FALL_START_POS);
        helper.setBlock(FALL_SUPPORT_POS, Blocks.AIR);
        helper.runAfterDelay(FALL_LANDED_TICKS, () -> {
            AbilityBlockEntity landed = helper.getBlockEntity(FALL_LANDING_POS, AbilityBlockEntity.class);
            helper.assertTrue(ABILITY_METAL_SPIKES.equals(landed.getAbilityId()), FALL_ABILITY_LOST);
            helper.assertTrue(landed.getBehavior() != null && landed.getBehavior().isActive(), FALL_PROGRAM_LOST);
            helper.succeed();
        });
    }

    /**
     * Stands a glow crystal on stone, facing up, and lands one goo of the
     * named ability on it.
     *
     * @param helper    the gametest helper
     * @param type      the goo type thrown
     * @param abilityId the ability the goo names
     */
    private static void landOnCrystal(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, String abilityId) {
        helper.setBlock(CRYSTAL_POS.below(), Blocks.STONE);
        helper.setBlock(CRYSTAL_POS, GooBlocks.GLOW_CRYSTAL.get().defaultBlockState()
                .setValue(GlowCrystalBlock.FACING, Direction.UP));
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(CRYSTAL_POS), type, Direction.UP, ability);
    }

    /**
     * A metal_spikes goo landing on a glow crystal leaves it as it stood
     * and places its own marker on the crystal's face.
     *
     * @param helper the gametest helper
     */
    public static void otherAbilityMarksCrystal(GameTestHelper helper) {
        landOnCrystal(helper, GooTypes.METAL, ABILITY_METAL_SPIKES);
        helper.assertBlockPresent(GooBlocks.GLOW_CRYSTAL.get(), CRYSTAL_POS);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), CRYSTAL_POS.above());
        helper.succeed();
    }

    // --- Data-driven ability path ---

    /**
     * Fills the wall region north of the marker with one block.
     *
     * @param helper the gametest helper
     * @param block  the block to fill with
     */
    private static void fillWall(GameTestHelper helper, Block block) {
        for (int x = WALL_X_MIN; x <= WALL_X_MAX; x++) {
            for (int y = 1; y <= WALL_Y_MAX; y++) {
                for (int z = 0; z <= WALL_Z_MAX; z++) {
                    helper.setBlock(new BlockPos(x, y, z), block);
                }
            }
        }
    }

    /**
     * Places a ability block initialized through an ability, facing north
     * into whatever fills the wall region, and splats it, so it runs the
     * program the ability declares from that tick.
     *
     * @param helper    the gametest helper
     * @param type      the goo type
     * @param abilityId the ability identifier string
     */
    private static void placeMarkerWithAbility(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, String abilityId) {
        placeMarkerWithAbility(helper, type, abilityId, 0);
    }

    /**
     * Places an ability block initialized through an ability at the size a
     * drag would cast it at, facing north into the wall region
     * (decision black-hole-leaves-a-compression-sphere).
     *
     * @param helper    the gametest helper
     * @param type      the goo type
     * @param abilityId the ability identifier string
     * @param size      the cast's size in blocks
     */
    private static void placeMarkerWithAbility(GameTestHelper helper, ResourceKey<GooTypeDefinition> type,
                                               String abilityId, double size) {
        helper.setBlock(MARKER_POS.north(), Blocks.STONE);
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(MARKER_POS.north()), type, Direction.SOUTH, ability,
                null, size);
    }

    // --- Step programs (decision ability-params-in-datapack) ---

    /**
     * Asserts the marker has exploded: the program ended so the marker
     * removed itself, and the explosion broke the stone it faced.
     *
     * @param helper the gametest helper
     */
    private static void assertExploded(GameTestHelper helper) {
        helper.assertBlockNotPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
        helper.assertBlockNotPresent(Blocks.STONE, MARKER_POS.north());
    }

    /**
     * Lurker as a program: armed at the splat, the marker idles on its
     * watch step until something living enters the trigger radius, then its await and
     * explode steps fire.
     *
     * @param helper the gametest helper
     */
    public static void programProximityMine(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooTypes.UNSTABLE, ABILITY_LURKER);
        helper.runAfterDelay(MINE_IDLE_TICKS, () -> {
            helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
            helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS);
        });
        helper.runAfterDelay(MINE_IDLE_TICKS + SHORT_WAIT, () -> {
            assertExploded(helper);
            helper.succeed();
        });
    }

    /**
     * Crystal cloud as a field-effect program: from the splat, a pig kept
     * moving inside the cloud is shredded.
     *
     * @param helper the gametest helper
     */
    public static void programCrystalCloud(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        placeMarkerWithAbility(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        Pig mover = spawnShufflingPig(helper, MINE_TARGET_POS);
        helper.runAfterDelay(SHRED_WINDOW, () -> {
            helper.assertTrue(mover.getHealth() < mover.getMaxHealth(), CLOUD_MISSED_MOVER);
            helper.succeed();
        });
    }

    /**
     * Spawns a pig on stone and shuffles it back and forth every tick, so
     * it counts as moving while it stays in place.
     *
     * @param helper the gametest helper
     * @param pos    where the pig stands
     * @return the pig
     */
    private static Pig spawnShufflingPig(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, pos);
        helper.onEachTick(() -> pig.setDeltaMovement(
                helper.getTick() % SHUFFLE_PERIOD == 0 ? SHUFFLE_SPEED : -SHUFFLE_SPEED,
                pig.getDeltaMovement().y(), 0));
        return pig;
    }

    /**
     * The black hole as a phased program: one stack, landed facing a
     * stone wall on a barrier floor it cannot take, with a zombie standing
     * and a diamond lying inside its sphere. Leaving expand it takes the
     * stone and kills the zombie, drops nothing until it has contracted,
     * then leaves one compression sphere holding the stone and the diamond
     * and removes its marker (decision black-hole-leaves-a-compression-sphere).
     *
     * @param helper the gametest helper
     */
    public static void blackHoleLeavesASphere(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        fillWall(helper, Blocks.STONE);
        layBarrierFloor(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, MINE_TARGET_POS);
        helper.spawnItem(Items.DIAMOND, Vec3.atBottomCenterOf(DIAMOND_POS));
        helper.runAfterDelay(SHORT_WAIT, () -> castDraggedHole(helper));
        helper.runAfterDelay(SHORT_WAIT + BLACK_HOLE_GATHER_TICKS + BLACK_HOLE_EXPAND_TICKS + SHORT_WAIT, () -> {
            helper.assertTrue(helper.getBlockState(MARKER_POS.north()).isAir(), HOLE_LEFT_STONE);
            helper.assertFalse(zombie.isAlive(), HOLE_SPARED_ZOMBIE);
        });
        helper.runAfterDelay(SHORT_WAIT + BLACK_HOLE_LIFE_TICKS + SHORT_WAIT, () -> {
            helper.assertBlockNotPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
            List<CompressionSphere> spheres = spheresAroundMarker(helper);
            helper.assertTrue(spheres.size() == 1, String.format(HOLE_LEFT_SPHERES, spheres.size()));
            List<ItemStack> held = spheres.getFirst().hoard().stacks();
            helper.assertTrue(held.stream().anyMatch(stack -> stack.is(Items.STONE)), HOLE_HELD_NO_STONE + held);
            helper.assertTrue(held.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), HOLE_HELD_NO_DIAMOND + held);
            SphereCore core = spheres.getFirst().core();
            helper.assertTrue(core == SphereCore.BLACK, String.format(HOLE_SPHERE_NOT_BLACK, core));
            helper.succeed();
        });
    }

    /**
     * A Blast dragged to radius 3 against a stone wall on a barrier floor,
     * with a diamond lying inside its radius, gathers the cobblestone it mined
     * into one green-cored compression sphere at its origin, and leaves the
     * diamond loose and no cobblestone in the crater
     * (decision large-blast-gathers-its-mined-drops-into-a-green-cored-sphere).
     *
     * @param helper the gametest helper
     */
    public static void largeBlastLeavesAGreenSphere(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        fillWall(helper, Blocks.STONE);
        layBarrierFloor(helper);
        helper.spawnItem(Items.DIAMOND, Vec3.atBottomCenterOf(DIAMOND_POS));
        helper.runAfterDelay(SHORT_WAIT, () -> castDraggedBlast(helper, GATHERING_BLAST_SIZE));
        helper.runAfterDelay(SHORT_WAIT + SHORT_WAIT, () -> {
            List<CompressionSphere> spheres = spheresAroundMarker(helper);
            helper.assertTrue(spheres.size() == 1, String.format(BLAST_LEFT_SPHERES, spheres.size()));
            List<ItemStack> held = spheres.getFirst().hoard().stacks();
            helper.assertTrue(held.stream().anyMatch(stack -> stack.is(Items.COBBLESTONE)),
                    BLAST_HELD_NO_COBBLESTONE + held);
            SphereCore core = spheres.getFirst().core();
            helper.assertTrue(core == SphereCore.GREEN, String.format(BLAST_SPHERE_NOT_GREEN, core));
            List<ItemStack> loose = itemsAroundMarker(helper).stream().map(ItemEntity::getItem).toList();
            helper.assertTrue(loose.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), BLAST_TOOK_THE_DIAMOND + loose);
            helper.assertTrue(loose.stream().noneMatch(stack -> stack.is(Items.COBBLESTONE)),
                    BLAST_LEFT_LOOSE_COBBLESTONE + loose);
            helper.succeed();
        });
    }

    /**
     * A Blast dragged to radius 2 against the same stone wall pops its drops
     * in place: cobblestone lies loose in the crater and no compression
     * sphere stands (decision large-blast-gathers-its-mined-drops-into-a-green-cored-sphere).
     *
     * @param helper the gametest helper
     */
    public static void smallBlastPopsItsDrops(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        fillWall(helper, Blocks.STONE);
        layBarrierFloor(helper);
        helper.runAfterDelay(SHORT_WAIT, () -> castDraggedBlast(helper, POPPING_BLAST_SIZE));
        helper.runAfterDelay(SHORT_WAIT + SHORT_WAIT, () -> {
            helper.assertTrue(spheresAroundMarker(helper).isEmpty(), SMALL_BLAST_LEFT_A_SPHERE);
            List<ItemStack> loose = itemsAroundMarker(helper).stream().map(ItemEntity::getItem).toList();
            helper.assertTrue(loose.stream().anyMatch(stack -> stack.is(Items.COBBLESTONE)),
                    SMALL_BLAST_POPPED_NOTHING + loose);
            helper.succeed();
        });
    }

    /**
     * A green-cored compression sphere saved the way a chunk unload saves it
     * loads back green (decision large-blast-gathers-its-mined-drops-into-a-green-cored-sphere).
     *
     * @param helper the gametest helper
     */
    public static void greenSphereKeepsItsCoreThroughASave(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        CompressedHoard hoard = new CompressedHoard();
        hoard.add(new ItemStack(Items.COBBLESTONE, SPILLED_STONE));
        CompressionSphere.leave(helper.getLevel(), helper.absoluteVec(Vec3.atCenterOf(MARKER_POS)), hoard,
                SphereCore.GREEN);
        CompressionSphere sphere = spheresAroundMarker(helper).getFirst();
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        sphere.save(saved);
        sphere.discard();
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saved.buildResult()), helper.getLevel(), EntitySpawnReason.LOAD,
                entity -> entity);
        helper.assertTrue(loaded instanceof CompressionSphere, SPHERE_RELOAD_LOST);
        SphereCore core = ((CompressionSphere) loaded).core();
        helper.assertTrue(core == SphereCore.GREEN, String.format(RELOADED_SPHERE_NOT_GREEN, core));
        helper.succeed();
    }

    /**
     * Casts Blast the way a drag's release does: a survival player holding a
     * glove and unstable goo, standing well clear, pins the stone's south face
     * and drags the Blast to a radius, which explodes at once at the pin.
     *
     * @param helper the gametest helper
     * @param size   the radius dragged
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static void castDraggedBlast(GameTestHelper helper, double size) {
        helper.setBlock(MARKER_POS.north(), Blocks.STONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(MARKER_POS.south(CASTER_DISTANCE)));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, CASTER_UNSTABLE));
        AbilityDefinition blast = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ABILITY_BLAST_DRAGGED));
        helper.assertTrue(blast != null, ABILITIES_REQUIRED);
        KnownRecipes.teachRequires(player, blast);
        BlockPos pin = helper.absolutePos(MARKER_POS.north());
        GooDragCastHandler.cast(player, new GooDragCastPayload(GooTypes.id(GooTypes.UNSTABLE), ABILITY_BLAST_DRAGGED,
                pin, Direction.SOUTH.get3DDataValue(), Vec3.atCenterOf(pin).relative(Direction.SOUTH, HALF_BLOCK),
                size));
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /**
     * Casts the black hole the way a drag's release does: a survival player
     * holding a glove and void goo, standing well clear of the hole, pins
     * the stone's south face and drags the hole to its reference size; the
     * cast charges the reference cost and stands the hole in the cell the pin
     * faces (decision black-hole-leaves-a-compression-sphere).
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static void castDraggedHole(GameTestHelper helper) {
        helper.setBlock(MARKER_POS.north(), Blocks.STONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(MARKER_POS.south(CASTER_DISTANCE)));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.VOID, CASTER_GOO));
        AbilityDefinition hole = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ABILITY_BLACK_HOLE));
        helper.assertTrue(hole != null, ABILITIES_REQUIRED);
        KnownRecipes.teachRequires(player, hole);
        BlockPos pin = helper.absolutePos(MARKER_POS.north());
        GooDragCastHandler.cast(player, new GooDragCastPayload(GooTypes.id(GooTypes.VOID), ABILITY_BLACK_HOLE,
                pin, Direction.SOUTH.get3DDataValue(), Vec3.atCenterOf(pin).relative(Direction.SOUTH, HALF_BLOCK),
                HOLE_SIZE));
        int spent = CASTER_GOO - GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.VOID, 0);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(spent == hole.cost().in(GooValueTable.EMPTY), String.format(HOLE_CHARGED_WRONG, hole.cost().in(GooValueTable.EMPTY), spent));
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
    }

    /**
     * A compression sphere a player touches pops open: it is gone, and every
     * stack it held lies there as an item entity
     * (decision black-hole-leaves-a-compression-sphere).
     *
     * @param helper the gametest helper
     */
    public static void compressionSphereSpillsOnTouch(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        CompressedHoard hoard = new CompressedHoard();
        hoard.add(new ItemStack(Items.STONE, SPILLED_STONE));
        hoard.add(new ItemStack(Items.DIAMOND));
        CompressionSphere.leave(helper.getLevel(), helper.absoluteVec(Vec3.atCenterOf(MARKER_POS)), hoard,
                SphereCore.BLACK);
        CompressionSphere sphere = spheresAroundMarker(helper).getFirst();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        sphere.playerTouch(player);
        helper.assertTrue(sphere.isRemoved(), SPHERE_STAYED_SHUT);
        List<ItemStack> spilled = itemsAroundMarker(helper).stream().map(ItemEntity::getItem).toList();
        helper.assertTrue(spilled.stream().filter(stack -> stack.is(Items.STONE)).mapToInt(ItemStack::getCount).sum()
                == SPILLED_STONE, SPHERE_SPILLED_WRONG + spilled);
        helper.assertTrue(spilled.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), SPHERE_SPILLED_WRONG + spilled);
        helper.succeed();
    }

    /**
     * Collects the compression spheres over the barrier floor around the marker.
     *
     * @param helper the gametest helper
     * @return the spheres
     */
    private static List<CompressionSphere> spheresAroundMarker(GameTestHelper helper) {
        AABB floor = new AABB(helper.absolutePos(MARKER_POS)).inflate(ITEM_SEARCH_RADIUS);
        return helper.getLevel().getEntitiesOfClass(CompressionSphere.class, floor);
    }

    /**
     * Collects the item entities over the barrier floor around the marker,
     * a box the empty test structure's own bounds do not span.
     *
     * @param helper the gametest helper
     * @return the item entities
     */
    private static List<ItemEntity> itemsAroundMarker(GameTestHelper helper) {
        AABB floor = new AABB(helper.absolutePos(MARKER_POS)).inflate(ITEM_SEARCH_RADIUS);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, floor);
    }

    /**
     * Discards every entity but a player within the test's own bay. The game
     * test server never clears a finished test, so a stray an earlier test
     * left can stand there. It runs a batch's tests side by side, five
     * blocks apart, and starts each once its own chunks load, so a neighbour
     * can start ticks after this test; a clear reaching into the neighbour's
     * bay discards what that live test spawned, as a nine-block clear once
     * discarded the diamond black_hole_leaves_a_sphere spawned.
     * decision black-hole-sphere-test-cause-found-and-removed
     *
     * @param helper the gametest helper
     */
    private static void discardLeftoverEntities(GameTestHelper helper) {
        AABB reach = helper.getBounds().inflate(LEFTOVER_CLEAR_MARGIN);
        helper.getLevel().getEntities((Entity) null, reach, entity -> !(entity instanceof Player))
                .forEach(Entity::discard);
    }

    /**
     * Lays barrier under the marker's whole sphere, the floor a black hole
     * cannot consume.
     *
     * @param helper the gametest helper
     */
    private static void layBarrierFloor(GameTestHelper helper) {
        for (int x = BARRIER_FLOOR_MIN; x <= BARRIER_FLOOR_MAX; x++) {
            for (int z = BARRIER_FLOOR_MIN; z <= BARRIER_FLOOR_MAX; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.BARRIER);
            }
        }
    }

    /**
     * Places a metal spikes marker over stone at the mine target.
     *
     * @param helper the gametest helper
     * @return the marker's block entity
     */
    private static AbilityBlockEntity placeMetalTrap(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        helper.setBlock(MINE_TARGET_POS.below(), Blocks.STONE);
        placeMarkerWithAbility(helper, GooTypes.METAL, ABILITY_METAL_SPIKES);
        return helper.getBlockEntity(MARKER_POS, AbilityBlockEntity.class);
    }

    /**
     * Metal spikes as a field-effect program: one throw's trap impales a pig
     * walking into its radius, spending at most one charge on its roll
     * (decision metal-spends-charge-by-chance).
     *
     * @param helper the gametest helper
     */
    public static void programMetalSpikes(GameTestHelper helper) {
        placeMetalTrap(helper);
        int armed = SHORT_WAIT;
        Pig[] pig = new Pig[1];
        helper.runAfterDelay(armed, () -> pig[0] = helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS));
        helper.runAfterDelay(armed + SPIKE_STRIKE_WINDOW, () -> {
            helper.assertTrue(pig[0].getHealth() < pig[0].getMaxHealth(), SPIKE_MISSED);
            pig[0].discard();
            helper.succeed();
        });
    }

    // --- The program runs the tick the blob lands (decision splat-runs-the-program-no-fuse) ---

    /**
     * Lands one blob of an ability on the stone the marker position faces:
     * queues it to arrive this tick and drains the arrivals, the path a
     * thrown goo's arrival takes on the server tick, so this tick is its
     * arrival tick.
     *
     * @param helper    the gametest helper
     * @param type      the goo type thrown
     * @param abilityId the ability the blob names
     */
    private static void landBlob(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, String abilityId) {
        fillWall(helper, Blocks.STONE);
        GooEffectScheduler arrivals = GooServerState.of(helper.getLevel().getServer()).gooEffects();
        int arrivalTick = helper.getLevel().getServer().getTickCount();
        arrivals.enqueue(new PendingEffect(arrivalTick, helper.getLevel(), null, type, NO_ENTITY,
                helper.absolutePos(MARKER_POS.north()), Direction.SOUTH, abilityId));
        arrivals.drainArrivedEffects(arrivalTick);
    }

    /**
     * Answers the marker standing where the blob landed.
     *
     * @param helper the gametest helper
     * @return the marker's block entity
     */
    private static AbilityBlockEntity landedMarker(GameTestHelper helper) {
        AbilityBlockEntity be = helper.getBlockEntity(MARKER_POS, AbilityBlockEntity.class);
        helper.assertTrue(be.getBehavior() != null && be.getBehavior().isActive(), NOT_RUNNING);
        return be;
    }

    /**
     * Removes the landed marker and passes the test, so its program, still
     * running, reaches no neighboring test's entities.
     *
     * @param helper the gametest helper
     */
    private static void removeMarkerAndSucceed(GameTestHelper helper) {
        helper.setBlock(MARKER_POS, Blocks.AIR);
        helper.succeed();
    }

    /**
     * A crystal_cloud blob's cloud is live the tick after it lands.
     *
     * @param helper the gametest helper
     */
    public static void crystalCloudLiveAfterLanding(GameTestHelper helper) {
        landBlob(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            helper.assertTrue(landedMarker(helper).getFieldEffect().fieldTicks() > 0, FIELD_NOT_LIVE);
            removeMarkerAndSucceed(helper);
        });
    }

    /**
     * A survival player walking inside a landed crystal cloud is shredded. The
     * player walks the way the server moves a real one: each tick it moves by
     * the client's step and records that step as its known movement, its
     * delta movement left to the server, so the cloud judges it as it judges
     * the operator.
     * decision diagnose-then-restore-razor-harm
     *
     * @param helper the gametest helper
     */
    public static void crystalCloudShredsAWalkingPlayer(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        helper.setBlock(MINE_TARGET_POS.below(), Blocks.STONE);
        ServerPlayer walker = standWalkingPlayer(helper, MINE_TARGET_POS);
        landBlob(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        helper.runAfterDelay(SHRED_WINDOW, () -> {
            helper.assertTrue(walker.getHealth() < walker.getMaxHealth(),
                    CLOUD_MISSED_WALKER + ": health " + walker.getHealth() + " of " + walker.getMaxHealth()
                            + ", delta " + walker.getDeltaMovement() + ", known " + walker.getKnownMovement());
            walker.discard();
            removeMarkerAndSucceed(helper);
        });
    }

    /**
     * Stands a survival server player whose client has loaded, walking back
     * and forth on the spot: each tick it moves by its step and the step is
     * recorded as its known movement, the path the server takes for a
     * client's move packet.
     *
     * @param helper the gametest helper
     * @param pos    the relative block position to stand on
     * @return the player
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer standWalkingPlayer(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.snapTo(helper.absoluteVec(Vec3.atBottomCenterOf(pos)));
        helper.onEachTick(() -> {
            Vec3 step = new Vec3(helper.getTick() % SHUFFLE_PERIOD == 0 ? WALK_STEP : -WALK_STEP, 0, 0);
            player.move(MoverType.PLAYER, step);
            player.setKnownMovement(step);
        });
        return player;
    }

    /**
     * A blast blob has exploded the tick after it lands.
     *
     * @param helper the gametest helper
     */
    public static void blastExplodesAfterLanding(GameTestHelper helper) {
        landBlob(helper, GooTypes.UNSTABLE, ABILITY_BLAST);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            assertExploded(helper);
            helper.succeed();
        });
    }

    /**
     * A glow_crystal blob's crystal stands the tick after it lands.
     *
     * @param helper the gametest helper
     */
    public static void glowCrystalStandsAfterLanding(GameTestHelper helper) {
        landBlob(helper, GooTypes.GLOW, ABILITY_GLOW_CRYSTAL);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            assertGlowCrystal(helper, Direction.SOUTH);
            helper.succeed();
        });
    }

    /**
     * A crystal cloud stands its block while a moving pig draws its eight
     * shreds, and the block is gone once the cloud has contracted
     * (decision lingering-abilities-place-their-own-thing).
     *
     * @param helper the gametest helper
     */
    public static void crystalCloudBlockGoesWithItsProgram(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        placeMarkerWithAbility(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
        spawnShufflingPig(helper, MINE_TARGET_POS);
        helper.runAfterDelay(CLOUD_LIFE_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(MARKER_POS).isAir(), BLOCK_OUTLIVED_PROGRAM);
            helper.succeed();
        });
    }

}
