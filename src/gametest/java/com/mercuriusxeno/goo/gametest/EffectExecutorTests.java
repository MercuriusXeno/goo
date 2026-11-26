package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.entity.CompressedHoard;
import com.mercuriusxeno.goo.entity.CompressionSphere;
import com.mercuriusxeno.goo.entity.SphereCore;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooChargePayload;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Gametests for a goo landing on a block, which runs the ability's program
 * from the tick its blob splats (decision splat-runs-the-program-no-fuse):
 * the programs the abilities declare and the compression spheres a Blast
 * leaves.
 */
@RegistersGameTests
public final class EffectExecutorTests {
    private static final String COMPRESSION_SPHERE_SPILLS_ON_TOUCH = "compression_sphere_spills_on_touch";
    private static final String LARGE_BLAST_LEAVES_A_GREEN_SPHERE = "large_blast_leaves_a_green_sphere";
    private static final String SMALL_BLAST_POPS_ITS_DROPS = "small_blast_pops_its_drops";
    private static final String GREEN_SPHERE_KEEPS_ITS_CORE = "green_sphere_keeps_its_core";
    private static final String FX_LANDED_BLAST = "fx_landed_blast_explodes";
    private static final String FX_LANDED_GLOW_CRYSTAL = "fx_landed_glow_crystal_stands";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, COMPRESSION_SPHERE_SPILLS_ON_TOUCH, EffectExecutorTests::compressionSphereSpillsOnTouch);
        GooTestFunctions.registerTest(registrar, LARGE_BLAST_LEAVES_A_GREEN_SPHERE, EffectExecutorTests::largeBlastLeavesAGreenSphere);
        GooTestFunctions.registerTest(registrar, SMALL_BLAST_POPS_ITS_DROPS, EffectExecutorTests::smallBlastPopsItsDrops);
        GooTestFunctions.registerTest(registrar, GREEN_SPHERE_KEEPS_ITS_CORE, EffectExecutorTests::greenSphereKeepsItsCoreThroughASave);
        GooTestFunctions.registerTest(registrar, FX_LANDED_BLAST, EffectExecutorTests::blastExplodesAfterLanding);
        GooTestFunctions.registerTest(registrar, FX_LANDED_GLOW_CRYSTAL, EffectExecutorTests::glowCrystalStandsAfterLanding);
    }


    /** Marker placed here, facing NORTH into the stone wall. */
    private static final BlockPos MARKER_POS = new BlockPos(3, 1, 3);
    /** Ticks a short program, or a check after an event, waits. */
    private static final int SHORT_WAIT = 5;
    /** The tick after the blob lands, where its program's first tick shows. */
    private static final int TICK_AFTER_LANDING = 1;
    private static final int NO_ENTITY = -1;
    private static final int WALL_X_MIN = 0;
    private static final int WALL_X_MAX = 5;
    private static final int WALL_Y_MAX = 3;
    private static final int WALL_Z_MAX = 2;
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    /** The gametests' own thrown blast, today's Blast before it was charge-sized, for the landing machinery. */
    private static final String ABILITY_BLAST = "goo:test_blast";
    private static final String ABILITY_GLOW_BULB = "goo:glow_bulb";
    /** Where a diamond lies: two blocks west of the marker. */
    private static final BlockPos DIAMOND_POS = MARKER_POS.west(2);
    /** Blocks south of the marker the caster stands, well clear of the blast. */
    private static final int CASTER_DISTANCE = 12;
    private static final double HALF_BLOCK = 0.5;
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
    private static final int SPILLED_STONE = 70;
    private static final String SPHERE_STAYED_SHUT = "The touched sphere is still standing";
    private static final String SPHERE_SPILLED_WRONG = "The touched sphere spilled other than 70 stone and a diamond: ";
    private static final String ABILITY_BLAST_CHARGED = "goo:unstable_blast";
    /** The smallest and largest whole radius searched for the one Blast's own expression first gathers at. */
    private static final int SMALLEST_BLAST_SIZE = 1;
    private static final int LARGEST_BLAST_SIZE = 6;
    /** How far under the gathering radius the popping Blast is charged. */
    private static final double POPPING_UNDER_GATHERING = 1;
    private static final String BLAST_SIZE_VARIABLE = "size";
    private static final String NO_GATHERING_SIZE = "Blast's own expression should gather at some radius up to %d";
    /** The unstable the Blast's caster holds, more than the gathering radius's price. */
    private static final int CASTER_UNSTABLE = 5000;
    private static final String BLAST_LEFT_SPHERES = "The gathering Blast left %d compression spheres, not one";
    private static final String BLAST_HELD_NO_COBBLESTONE = "The Blast's sphere holds no cobblestone for the stone it mined: ";
    private static final String BLAST_SPHERE_NOT_GREEN = "The Blast's sphere should draw the green core, draws %s";
    private static final String BLAST_TOOK_THE_DIAMOND = "The diamond lying in the Blast no longer lies loose: ";
    private static final String BLAST_LEFT_LOOSE_COBBLESTONE = "Cobblestone lies loose in the gathering Blast's crater: ";
    private static final String SMALL_BLAST_LEFT_A_SPHERE = "The Blast under the gathering radius left a compression sphere";
    private static final String SMALL_BLAST_POPPED_NOTHING = "No cobblestone lies in the crater of the Blast under the gathering radius: ";
    private static final String SPHERE_RELOAD_LOST = "The saved compression sphere did not load back as one";
    private static final String RELOADED_SPHERE_NOT_GREEN = "The reloaded green sphere draws %s";


    private EffectExecutorTests() {}

    // --- Glow crystal program (task glow-crystal-program) ---

    /**
     * Asserts the glow crystal stands at the landing with facing from the
     * placed face, its one property.
     *
     * @param helper the gametest helper
     * @param facing the placed face the crystal must face
     */
    private static void assertGlowCrystal(GameTestHelper helper, Direction facing) {
        helper.assertBlockPresent(GooBlocks.GLOW_CRYSTAL.get(), MARKER_POS);
        helper.assertBlockProperty(MARKER_POS, GlowCrystalBlock.FACING, facing);
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

    // --- Step programs (decision ability-params-in-datapack) ---

    /**
     * Asserts the blast has exploded: the explosion broke the stone the
     * landing faced.
     *
     * @param helper the gametest helper
     */
    private static void assertExploded(GameTestHelper helper) {
        helper.assertBlockNotPresent(Blocks.STONE, MARKER_POS.north());
    }

    /**
     * A Blast charged to the smallest whole radius its own expression gathers
     * at, against a stone wall on a barrier floor,
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
        double gathering = gatheringBlastSize(helper);
        helper.runAfterDelay(SHORT_WAIT, () -> castChargedBlast(helper, gathering));
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
     * A Blast charged a block under the gathering radius against the same stone wall pops its drops
     * in place: cobblestone lies loose in the crater and no compression
     * sphere stands (decision large-blast-gathers-its-mined-drops-into-a-green-cored-sphere).
     *
     * @param helper the gametest helper
     */
    public static void smallBlastPopsItsDrops(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        fillWall(helper, Blocks.STONE);
        layBarrierFloor(helper);
        double popping = gatheringBlastSize(helper) - POPPING_UNDER_GATHERING;
        helper.runAfterDelay(SHORT_WAIT, () -> castChargedBlast(helper, popping));
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
     * The smallest whole radius at which Blast's own explode step gathers its
     * drops, read live from the ability.
     *
     * @param helper the gametest helper
     * @return the gathering radius
     */
    private static double gatheringBlastSize(GameTestHelper helper) {
        AbilityDefinition blast = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ABILITY_BLAST_CHARGED));
        helper.assertTrue(blast != null, ABILITIES_REQUIRED);
        ExplodeStep explode = blast.behaviors().stream().filter(ExplodeStep.class::isInstance)
                .map(ExplodeStep.class::cast).findFirst().orElseThrow();
        for (int size = SMALLEST_BLAST_SIZE; size <= LARGEST_BLAST_SIZE; size++) {
            double radius = size;
            Variables sized = name -> BLAST_SIZE_VARIABLE.equals(name) ? OptionalDouble.of(radius) : OptionalDouble.empty();
            if (explode.gathersAt(sized)) {
                return radius;
            }
        }
        throw helper.assertionException(String.format(NO_GATHERING_SIZE, LARGEST_BLAST_SIZE));
    }

    /**
     * Casts Blast the way a charge's release does: a survival player holding a
     * glove and unstable goo, standing well clear, holds Blast to a size and
     * lets go aimed at the stone's south face, which explodes there at once.
     *
     * @param helper the gametest helper
     * @param size   the whole size the hold charges to
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static void castChargedBlast(GameTestHelper helper, double size) {
        helper.setBlock(MARKER_POS.north(), Blocks.STONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(MARKER_POS.south(CASTER_DISTANCE)));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, CASTER_UNSTABLE));
        AbilityDefinition blast = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ABILITY_BLAST_CHARGED));
        helper.assertTrue(blast != null, ABILITIES_REQUIRED);
        KnownRecipes.teachRequires(player, blast);
        BlockPos aimed = helper.absolutePos(MARKER_POS.north());
        GooThrowPayload target = new GooThrowPayload(GooTypes.id(GooTypes.UNSTABLE), -1, aimed,
                Direction.SOUTH.ordinal(), false, ABILITY_BLAST_CHARGED, player.getEyePosition(),
                Vec3.atCenterOf(aimed).relative(Direction.SOUTH, HALF_BLOCK));
        GooThrowHandler.releaseCharge(player, new GooChargePayload(GooTypes.id(GooTypes.UNSTABLE),
                ABILITY_BLAST_CHARGED, ticksToCharge(blast.delivery(), size), Optional.of(target)));
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /**
     * The ticks a hold takes to charge a sized delivery to a size.
     *
     * @param delivery the sized delivery
     * @param size     the size, within its charge's span
     * @return the ticks
     */
    private static int ticksToCharge(Delivery delivery, double size) {
        double span = delivery.charge().maxSize() - delivery.charge().openingSize();
        return (int) Math.round((size - delivery.charge().openingSize()) / span * delivery.chargeTicks());
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
     * Lays barrier under the marker's whole sphere, a floor no blast can
     * break.
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
     * A glow_bulb blob's crystal stands the tick after it lands.
     *
     * @param helper the gametest helper
     */
    public static void glowCrystalStandsAfterLanding(GameTestHelper helper) {
        landBlob(helper, GooTypes.GLOW, ABILITY_GLOW_BULB);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            assertGlowCrystal(helper, Direction.SOUTH);
            helper.succeed();
        });
    }
}
