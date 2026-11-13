package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCreativeTabs;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for the generic goo items (decision generic-goo-items): a goo
 * created for a type throws as that type and lands that type's ability, and
 * the creative tab offers gooStacks, a one-goo one among them, and a bucket
 * for every type the registry holds, a datapack's included
 * (decision thousands-become-gooStacks).
 */
@RegistersGameTests
public final class GooItemTests {
    private static final String ITEM_THROWN_GOO_OWN_TYPE = "item_thrown_goo_own_type";
    private static final String ITEM_TAB_DATAPACK_TYPE = "item_tab_datapack_type";
    private static final String EXO_GAUNTLET_KEEPS_BENEFITS = "exo_gauntlet_keeps_benefits";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ITEM_THROWN_GOO_OWN_TYPE, GooItemTests::thrownGooLandOwnType);
        GooTestFunctions.registerTest(registrar, ITEM_TAB_DATAPACK_TYPE, GooItemTests::creativeTabOffersDatapackType);
        GooTestFunctions.registerTest(registrar, EXO_GAUNTLET_KEEPS_BENEFITS, GooItemTests::exoGauntletKeepsBenefits);
        GooTestFunctions.registerTest(registrar, ITEM_GOO_JOINS_FROM_TWO_BLOCKS, GooItemTests::gooTwoBlocksApartJoins);
        GooTestFunctions.registerTest(registrar, ITEM_GOO_BOUNCES_BY_ELASTICITY, GooItemTests::gooBouncesByElasticity);
        GooTestFunctions.registerTest(registrar, ITEM_GOO_PICKED_UP, GooItemTests::gooIsPickedUp);
        GooTestFunctions.registerTest(registrar, ITEM_GOO_CLINGS_BY_VISCOSITY, GooItemTests::gooClingsByViscosity);
        GooTestFunctions.registerTest(registrar, ITEM_GOO_CLINGING_STILL_JOINS, GooItemTests::clingingGooStillJoins);
        GooTestFunctions.registerTest(registrar, ITEM_GOO_RESTS_STILL, GooItemTests::restingElasticGooStaysStill);
    }

    private static final String ITEM_GOO_RESTS_STILL = "item_goo_rests_still";
    /** Ticks a goo item set on the floor takes to settle before it is watched. */
    private static final int SETTLE_TICKS = 20;
    /** Ticks it is watched resting, past vanilla's every-fourth-tick move on the ground many times over. */
    private static final int REST_WATCH_TICKS = 60;
    /** How far above the floor a resting goo item may stand at the most. */
    private static final double RESTING_RISE = 0.01;
    private static final String HOPPED = "A goo item resting on the floor should stay on it; it rose above the floor by ";

    /**
     * An elastic goo item set on a floor stays on it: once settled it never
     * rises off the floor while it rests.
     * decision flecks-move-by-flexible-goo-physics-and-stay-items
     *
     * @param helper the gametest helper
     */
    public static void restingElasticGooStaysStill(GameTestHelper helper) {
        layStoneFloor(helper);
        ItemEntity resting = CrucibleSpawns.spawnAt(helper, GooStacks.createForOutput(GooTypes.ASTRAL, DROPPED_VOLUME),
                PICKUP_SPOT);
        double floor = helper.absoluteVec(FLOOR_TOP).y;
        double[] highest = {0};
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0]++ >= SETTLE_TICKS) {
                highest[0] = Math.max(highest[0], resting.getY() - floor);
            }
        });
        helper.runAfterDelay(SETTLE_TICKS + REST_WATCH_TICKS, () -> {
            helper.assertTrue(highest[0] < RESTING_RISE, HOPPED + highest[0]);
            helper.succeed();
        });
    }

    private static final String ITEM_GOO_CLINGS_BY_VISCOSITY = "item_goo_clings_by_viscosity";
    private static final String ITEM_GOO_CLINGING_STILL_JOINS = "item_goo_clinging_still_joins";
    /** The test datapack's thick type, viscosity 6000; blaze art states 1000. */
    private static final ResourceKey<GooTypeDefinition> THICK_TYPE = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "molasses"));
    /** The wall's column, test-relative x, standing from the floor up. */
    private static final int WALL_X = 4;
    private static final int WALL_HEIGHT = 4;
    private static final Vec3 THICK_THROW = new Vec3(2.5, 3.5, 1.5);
    private static final Vec3 RUNNY_THROW = new Vec3(2.5, 3.5, 3.5);
    private static final Vec3 TOWARD_THE_WALL = new Vec3(0.5, 0, 0);
    /** Ticks past the throw when a runny fleck has dripped off the wall and a thick one still clings. */
    private static final int CLING_READ_TICK = 45;
    /** How far above the floor a fleck still on the wall stands, at the least. */
    private static final double ABOVE_THE_FLOOR = 0.5;
    /** How near the wall's face a clinging fleck stands, test-relative x. */
    private static final double AT_THE_WALL = WALL_X - 0.5;
    private static final Vec3 SECOND_GOO = new Vec3(2.5, 1, 1.5);
    private static final String THICK_NOT_ON_WALL = "The thick goo should still cling to the wall, standing at ";
    private static final String RUNNY_NOT_ON_FLOOR = "The runny goo should have dripped to the floor, standing at ";

    /**
     * Two goo items thrown at a wall with one velocity, one of a thick type
     * and one of a runny type: at one later tick the thick one still stands
     * on the wall above the floor while the runny one rests on the floor.
     * decision flecks-move-by-flexible-goo-physics-and-stay-items
     * decision each-goo-type-carries-elasticity-and-viscosity
     *
     * @param helper the gametest helper
     */
    public static void gooClingsByViscosity(GameTestHelper helper) {
        layStoneFloor(helper);
        layWall(helper);
        ItemEntity thick = throwAtTheWall(helper, THICK_TYPE, THICK_THROW);
        ItemEntity runny = throwAtTheWall(helper, DEAD_TYPE, RUNNY_THROW);
        double floor = helper.absoluteVec(FLOOR_TOP).y;
        helper.runAfterDelay(CLING_READ_TICK, () -> {
            Vec3 thickAt = helper.relativeVec(thick.position());
            Vec3 runnyAt = helper.relativeVec(runny.position());
            helper.assertTrue(thick.getY() > floor + ABOVE_THE_FLOOR && thickAt.x > AT_THE_WALL,
                    THICK_NOT_ON_WALL + thickAt);
            helper.assertTrue(runny.getY() < floor + AT_THE_FLOOR, RUNNY_NOT_ON_FLOOR + runnyAt);
            helper.succeed();
        });
    }

    /**
     * A thick goo item clinging to a wall and a same-type goo item two blocks
     * from it stand as one goo item holding their summed volume.
     * decision flecks-move-by-flexible-goo-physics-and-stay-items
     * decision combining-flecks-join-into-one-cuboid
     *
     * @param helper the gametest helper
     */
    public static void clingingGooStillJoins(GameTestHelper helper) {
        layStoneFloor(helper);
        layWall(helper);
        throwAtTheWall(helper, THICK_TYPE, THICK_THROW);
        CrucibleSpawns.spawnAt(helper, GooStacks.createForOutput(THICK_TYPE, FAR_VOLUME), SECOND_GOO);
        helper.succeedWhen(() -> {
            List<Integer> volumes = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6),
                            item -> item.isAlive() && THICK_TYPE.equals(GooStacks.keyOf(item.getItem())))
                    .stream().map(item -> GooStacks.volumeOf(item.getItem())).toList();
            helper.assertTrue(volumes.equals(List.of(DROPPED_VOLUME + FAR_VOLUME)), GOO_NOT_JOINED + volumes);
        });
    }

    private static ItemEntity throwAtTheWall(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, Vec3 from) {
        ItemEntity goo = CrucibleSpawns.spawnAt(helper, GooStacks.createForOutput(type, DROPPED_VOLUME), from);
        goo.setDeltaMovement(TOWARD_THE_WALL);
        return goo;
    }

    private static void layWall(GameTestHelper helper) {
        for (int y = 1; y <= WALL_HEIGHT; y++) {
            for (int z = 0; z < 6; z++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.STONE);
            }
        }
    }

    private static final String ITEM_GOO_BOUNCES_BY_ELASTICITY = "item_goo_bounces_by_elasticity";
    private static final String ITEM_GOO_PICKED_UP = "item_goo_picked_up";
    /** The test datapack's types: the seventeenth states elasticity 0.9, blaze art 0.1. */
    private static final ResourceKey<GooTypeDefinition> ELASTIC_TYPE = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "seventeenth"));
    private static final ResourceKey<GooTypeDefinition> DEAD_TYPE = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "blaze_art"));
    /** One height above the floor, apart from each other so the two types never touch. */
    private static final Vec3 ELASTIC_DROP = new Vec3(1.5, 4, 2.5);
    private static final Vec3 DEAD_DROP = new Vec3(4.5, 4, 2.5);
    private static final int DROPPED_VOLUME = 10;
    /** The stone floor's top face, test-relative. */
    private static final Vec3 FLOOR_TOP = new Vec3(0, 1, 0);
    /** How near the floor's top a falling goo item stands once it has met it. */
    private static final double AT_THE_FLOOR = 0.1;
    /** Ticks for a drop of three blocks to land and its rebound to peak. */
    private static final int BOUNCE_TICKS = 50;
    private static final String NOT_HIGHER = "The more elastic goo should rebound higher off the floor; peaks ";
    private static final Vec3 PICKUP_SPOT = new Vec3(2.5, 1, 2.5);
    private static final String NOT_PICKED_UP = "A player reaching a dropped goo item should take it whole: ";

    /**
     * Two goo items dropped from one height, one of a type stating elasticity
     * 0.9 and one stating 0.1: the first's peak after it first meets the floor
     * stands above the second's.
     * decision each-goo-type-carries-elasticity-and-viscosity
     * decision flecks-move-by-flexible-goo-physics-and-stay-items
     *
     * @param helper the gametest helper
     */
    public static void gooBouncesByElasticity(GameTestHelper helper) {
        layStoneFloor(helper);
        ItemEntity elastic = CrucibleSpawns.spawnAt(helper, GooStacks.createForOutput(ELASTIC_TYPE, DROPPED_VOLUME),
                ELASTIC_DROP);
        ItemEntity dead = CrucibleSpawns.spawnAt(helper, GooStacks.createForOutput(DEAD_TYPE, DROPPED_VOLUME),
                DEAD_DROP);
        double floor = helper.absoluteVec(FLOOR_TOP).y;
        double[] peaks = {Double.NaN, Double.NaN};
        helper.onEachTick(() -> {
            peaks[0] = peakSinceFloor(elastic, floor, peaks[0]);
            peaks[1] = peakSinceFloor(dead, floor, peaks[1]);
        });
        helper.runAfterDelay(BOUNCE_TICKS, () -> {
            helper.assertTrue(peaks[0] > peaks[1], NOT_HIGHER + peaks[0] + " against " + peaks[1]);
            helper.succeed();
        });
    }

    /**
     * The highest a goo item has stood since it first came within reach of
     * the floor's top, NaN until it has.
     */
    private static double peakSinceFloor(ItemEntity item, double floor, double peak) {
        if (Double.isNaN(peak)) {
            return item.getY() < floor + AT_THE_FLOOR ? item.getY() : peak;
        }
        return Math.max(peak, item.getY());
    }

    /**
     * A mock player who reaches a dropped goo item past its pickup delay takes
     * it: the player's inventory holds a goo of its type and volume and the
     * item entity is gone.
     * decision flecks-move-by-flexible-goo-physics-and-stay-items
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void gooIsPickedUp(GameTestHelper helper) {
        layStoneFloor(helper);
        ItemEntity goo = CrucibleSpawns.spawnAt(helper, GooStacks.createForOutput(GooTypes.ZOO, DROPPED_VOLUME),
                PICKUP_SPOT);
        goo.setNoPickUpDelay();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 reach = helper.absoluteVec(PICKUP_SPOT);
        player.setPos(reach.x, reach.y, reach.z);
        helper.onEachTick(() -> {
            if (goo.isAlive()) {
                goo.playerTouch(player);
            }
        });
        helper.succeedWhen(() -> {
            boolean held = player.getInventory().getNonEquipmentItems().stream().anyMatch(stack ->
                    GooTypes.ZOO.equals(GooStacks.keyOf(stack)) && GooStacks.volumeOf(stack) == DROPPED_VOLUME);
            helper.assertTrue(held && goo.isRemoved(), NOT_PICKED_UP + "held " + held + ", removed "
                    + goo.isRemoved());
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static void layStoneFloor(GameTestHelper helper) {
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static final String ITEM_GOO_JOINS_FROM_TWO_BLOCKS = "item_goo_joins_from_two_blocks";
    /** Two blocks apart on the floor, farther than vanilla's own item merge reaches. */
    private static final Vec3 NEAR_GOO = new Vec3(1.5, 1, 2.5);
    private static final Vec3 FAR_GOO = new Vec3(3.5, 1, 2.5);
    private static final int NEAR_VOLUME = 30;
    private static final int FAR_VOLUME = 12;
    private static final String GOO_NOT_JOINED = "Two same-type goo items two blocks apart should stand as one "
            + "goo item holding their summed volume; the goo items standing hold ";

    /**
     * Two same-type goo items dropped two blocks apart on a stone floor draw
     * together and stand as one goo item entity holding their summed volume.
     * decision combining-flecks-join-into-one-cuboid
     *
     * @param helper the gametest helper
     */
    public static void gooTwoBlocksApartJoins(GameTestHelper helper) {
        layStoneFloor(helper);
        CrucibleSpawns.spawnAt(helper, GooStacks.createForOutput(GooTypes.ZOO, NEAR_VOLUME), NEAR_GOO);
        CrucibleSpawns.spawnAt(helper, GooStacks.createForOutput(GooTypes.ZOO, FAR_VOLUME), FAR_GOO);
        helper.succeedWhen(() -> {
            List<Integer> volumes = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6),
                            item -> item.isAlive() && GooTypes.ZOO.equals(GooStacks.keyOf(item.getItem())))
                    .stream().map(item -> GooStacks.volumeOf(item.getItem())).toList();
            helper.assertTrue(volumes.equals(List.of(NEAR_VOLUME + FAR_VOLUME)), GOO_NOT_JOINED + volumes);
        });
    }


    private static final BlockPos CRYSTAL_WALL = new BlockPos(1, 1, 1);
    private static final BlockPos METAL_WALL = new BlockPos(3, 1, 1);
    private static final BlockPos PLAYER_POS = new BlockPos(2, 1, 5);
    private static final Direction THROW_FACE = Direction.SOUTH;
    private static final int NO_TARGET_ENTITY = -1;
    private static final String CRYSTAL_CLOUD = "goo:crystal_cloud";
    private static final String METAL_SPIKES = "goo:metal_spikes";
    private static final String REMOVAL = "removal";
    /**
     * Ticks past the arc from the player to either wall, four blocks at one
     * and a half blocks a tick.
     */
    private static final int ARRIVAL_TICKS = 10;

    private static final String TEST_PACK_NAMESPACE = "gootest";
    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath(TEST_PACK_NAMESPACE, "seventeenth"));

    private static final String GOO_NOT_SPENT = "Throwing should spend the goo of the thrown type: ";
    private static final String NO_MARKER = "Thrown goo should land a ability block beside the wall at ";
    private static final String WRONG_MARKER_TYPE = "Ability block should carry the thrown goo's type at ";
    private static final String EXO_NOT_FIRE_RESISTANT = "A fresh exo gauntlet should resist fire damage";
    private static final String TAB_LACKS_THOUSAND = "Creative tab should offer a one-goo of the datapack type";
    private static final String TAB_LACKS_GOO = "Creative tab should offer a goo of the datapack type";
    private static final String TAB_LACKS_BUCKET = "Creative tab should offer a bucket of the datapack type";

    private GooItemTests() {
    }

    /**
     * A player holding a glove, with crystal goo and metal goo made through
     * GooStacks to each ability's live cost, throws each at its own stone
     * wall; each throw spends that goo and lands a ability block of that type
     * beside its wall.
     *
     * @param helper the gametest helper
     */
    public static void thrownGooLandOwnType(GameTestHelper helper) {
        throwGooFrom(helper, GooItems.GOO_GLOVE.get());
    }

    /**
     * The exo gauntlet keeps the goo gauntlet's benefits (decision
     * exo-gauntlet-smithed-with-exorite): a fresh one resists fire. It is a
     * GooGloveItem, as GooItems types it, so it throws as the glove does.
     *
     * @param helper the gametest helper
     */
    public static void exoGauntletKeepsBenefits(GameTestHelper helper) {
        DamageResistant resistant = new ItemStack(GooItems.EXO_GAUNTLET.get()).get(DataComponents.DAMAGE_RESISTANT);
        helper.assertTrue(resistant != null && resistant.types().unwrapKey().filter(DamageTypeTags.IS_FIRE::equals).isPresent(), EXO_NOT_FIRE_RESISTANT);
        helper.succeed();
    }

    /**
     * A mock player holding the thrower throws a crystal and a metal goo, each
     * holding its ability's live cost, each at its own stone wall, then leaves the level once the markers are
     * read, so a later thrower's tracking broadcast never reaches its mock
     * connection, which never negotiated the mod's channels.
     *
     * @param helper  the gametest helper
     * @param thrower the glove or gauntlet the player holds
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    private static void throwGooFrom(GameTestHelper helper, Item thrower) {
        helper.setBlock(CRYSTAL_WALL, Blocks.STONE);
        helper.setBlock(METAL_WALL, Blocks.STONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(PLAYER_POS);
        player.setPos(stand.getX(), stand.getY(), stand.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(thrower));
        AbilityRegistry abilities = AbilityRegistry.of(helper.getLevel());
        AbilityDefinition cloud = abilities.getAbility(Identifier.parse(CRYSTAL_CLOUD));
        AbilityDefinition spikes = abilities.getAbility(Identifier.parse(METAL_SPIKES));
        ItemStack crystal = GooStacks.createForOutput(GooTypes.CRYSTAL, cloud.cost().in(GooValueTable.EMPTY));
        ItemStack metal = GooStacks.createForOutput(GooTypes.METAL, spikes.cost().in(GooValueTable.EMPTY));
        player.getInventory().add(crystal);
        player.getInventory().add(metal);
        KnownRecipes.teachRequires(player, cloud);
        KnownRecipes.teachRequires(player, spikes);

        GooThrowHandler.execute(player, throwAt(helper, GooTypes.CRYSTAL, CRYSTAL_CLOUD, CRYSTAL_WALL));
        GooThrowHandler.execute(player, throwAt(helper, GooTypes.METAL, METAL_SPIKES, METAL_WALL));
        helper.assertTrue(crystal.isEmpty(), GOO_NOT_SPENT + GooTypes.id(GooTypes.CRYSTAL));
        helper.assertTrue(metal.isEmpty(), GOO_NOT_SPENT + GooTypes.id(GooTypes.METAL));

        helper.runAfterDelay(ARRIVAL_TICKS, () -> {
            assertMarker(helper, CRYSTAL_WALL.relative(THROW_FACE), GooTypes.CRYSTAL);
            assertMarker(helper, METAL_WALL.relative(THROW_FACE), GooTypes.METAL);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * The goo creative tab, built against the level's registries, holds a
     * one-goo, a goo and a bucket whose GOO_TYPE component names the
     * seventeenth type the test datapack adds.
     *
     * @param helper the gametest helper
     */
    public static void creativeTabOffersDatapackType(GameTestHelper helper) {
        CreativeModeTab tab = GooCreativeTabs.GOO_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess()));
        Collection<ItemStack> shown = tab.getDisplayItems();
        helper.assertTrue(shown.stream().anyMatch(stack -> stack.is(GooItems.GOO.get())
                && SEVENTEENTH.equals(GooStacks.keyOf(stack))
                && GooStacks.volumeOf(stack) == GooStacks.THOUSAND), TAB_LACKS_THOUSAND);
        helper.assertTrue(holdsTyped(shown, GooItems.GOO.get(), SEVENTEENTH), TAB_LACKS_GOO);
        helper.assertTrue(holdsTyped(shown, GooItems.GOO_BUCKET.get(), SEVENTEENTH), TAB_LACKS_BUCKET);
        helper.succeed();
    }

    private static boolean holdsTyped(Collection<ItemStack> stacks, Item item, ResourceKey<GooTypeDefinition> key) {
        return stacks.stream().anyMatch(stack -> stack.is(item) && key.equals(stack.get(GooDataComponents.GOO_TYPE.get())));
    }

    private static GooThrowPayload throwAt(GameTestHelper helper, ResourceKey<GooTypeDefinition> type,
                                            String abilityId, BlockPos wall) {
        return new GooThrowPayload(GooTypes.id(type), NO_TARGET_ENTITY, helper.absolutePos(wall),
                THROW_FACE.ordinal(), false, abilityId, Vec3.ZERO);
    }

    private static void assertMarker(GameTestHelper helper, BlockPos pos, ResourceKey<GooTypeDefinition> type) {
        helper.assertTrue(helper.getBlockState(pos).is(GooBlocks.ABILITY_BLOCK.get()), NO_MARKER + pos);
        AbilityBlockEntity marker = helper.getBlockEntity(pos, AbilityBlockEntity.class);
        helper.assertTrue(Objects.equals(type, marker.getGooType()), WRONG_MARKER_TYPE + pos);
    }
}
