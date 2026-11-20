package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.VinesStep;
import com.mercuriusxeno.goo.ability.root.RootEvents;
import com.mercuriusxeno.goo.ability.root.Rooted;
import com.mercuriusxeno.goo.ability.root.VineHold;
import com.mercuriusxeno.goo.block.ability.GoologgedSides;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Leaf's Vines through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Vines cell there, and a husk walking
 * across a full Vines cell is held in place on it; a hit on a held zombie
 * ends every vine and eats a layer of the cell that rooted it, and a zombie
 * struggling against its vines until they let go takes no harm from them;
 * a player under Barkskin standing on a full cell takes no vines from it,
 * and a held player's vines end as Barkskin begins on them;
 * Vines goo sprayed onto a wall's side grows a goologged vine there, which
 * climbs like a vine and outlives a hit eating its cell's last layer; the
 * player who sprayed the goo is spared by it.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * vines-root-is-volume-based-one-to-five-vines-by-layers
 * vines-deal-no-thorn-damage-and-attacking-the-mob-frees-it
 * entangled-mob-taking-damage-eats-a-layer-of-vines-goo
 * barkskin-makes-you-immune-to-vines
 * vines-goo-on-walls-places-goologged-vines-and-depletes
 */
@RegistersGameTests
public final class VinesTests {

    private static final String VINES_DRIP_LAYS_A_LEAF_CELL = "vines_drip_lays_a_leaf_cell_on_stone";
    private static final Identifier VINES = Identifier.parse("goo:leaf_vines");
    /** The floor block a drip strikes, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block, where the drip's layer lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    private static final String ABILITY_REQUIRED = "The ability registry must hold leaf_vines";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Vines, stands %s";
    private static final String FULL_CELL_HOLDS_A_WALKER = "vines_full_cell_holds_a_walker_in_place";
    /** The lane's westmost block, where its walker starts; the lane runs east across the bay. */
    private static final BlockPos LANE = new BlockPos(0, 1, 2);
    /** How far east the lane runs from its start. */
    private static final int LANE_EAST = 5;
    /** The full Vines cell set into the lane, its top level with the path beside it. */
    private static final BlockPos FULL_CELL = LANE.east(2);
    /** Two seconds, how long after reaching the cell the walker is read. */
    private static final int HELD_TICKS = 40;
    private static final String NOT_HELD = "The husk should stand over the Vines cell at x %d two seconds after "
            + "reaching it, stands at x %.3f";
    private static final String HIT_ENDS_THE_ROOT = "vines_hit_ends_the_root_and_eats_a_layer";
    private static final String ROOT_RUNS_OUT_HARMLESS = "vines_root_runs_out_and_leaves_its_mob_unharmed";
    /** A one-layer Vines cell lying on the lane, giving one vine, so the mob it holds still moves. */
    private static final BlockPos THIN_CELL = LANE.east(2).above();
    /** Ticks a mob spawned on the cell takes to settle and be held, with room to spare. */
    private static final int SETTLE_TICKS = 5;
    /** The magic damage the held zombie is dealt, which no armor turns. */
    private static final float HIT_DAMAGE = 3f;
    private static final String SHOULD_BE_HELD = "The zombie on the full Vines cell should be held by five vines, holds %s";
    private static final String SHOULD_BE_FREED = "The hit should end every vine on the zombie, it holds %s";
    private static final String SHOULD_TAKE_THE_HIT = "The zombie should take the hit's %.1f, its health fell from %.2f to %.2f";
    private static final String SHOULD_EAT_A_LAYER = "The cell that rooted the zombie should stand at 14 layers, stands %s";
    private static final String BARKSKIN_SPARES = "vines_spare_a_player_under_barkskin";
    private static final Identifier LEAF_BARKSKIN = Identifier.parse("goo:leaf_barkskin");
    /** A second full cell set into the lane, two blocks east of the first, where the bare player stands. */
    private static final BlockPos SECOND_FULL_CELL = FULL_CELL.east(2);
    private static final String BARE_SHOULD_BE_HELD = "The player without Barkskin should be held by the cell's vines, "
            + "holds %s";
    private static final String BARKED_SHOULD_BE_SPARED = "The player under Barkskin should hold no vines, holds %s";
    private static final String BARKSKIN_ENDS_HELD_VINES = "vines_held_end_as_barkskin_begins";
    private static final String HELD_VINES_SHOULD_END = "The held player's vines should end the tick after Barkskin "
            + "begins, holds %s with its slow modifier %s";
    private static final String EATEN_CELL_KEEPS_ITS_VINES = "vines_eaten_cell_leaves_its_vines_hanging";
    private static final String GOOLOGGED_VINES_CLIMB = "vines_goologged_vines_climb_like_a_vine";
    private static final String CASTER_SPARED = "vines_spare_the_player_who_sprayed_them";
    /** A one-layer Vines cell on the bay floor with a vine hanging on the stone wall north of it. */
    private static final BlockPos VINED_CELL = new BlockPos(2, 2, 4);
    /** A one-layer Vines cell on the bay floor with no wall beside it, so it grows no vine. */
    private static final BlockPos BARE_CELL = new BlockPos(4, 2, 4);
    /** One layer's height, where a thing standing in a one-layer cell stands. */
    private static final double ONE_LAYER = 1.0 / VolumeCellBlock.MAX_LAYERS;
    private static final String SHOULD_HANG_ALONE = "The cell's last layer eaten should leave its vine hanging north, "
            + "stands %s";
    private static final String SHOULD_CLIMB = "A zombie in the vined cell should climb and one in the bare cell "
            + "should not: vined %s, bare %s";
    private static final String CASTER_SHOULD_BE_SPARED = "The player who sprayed the cell should hold no vines, "
            + "holds %s";
    private static final String OTHER_SHOULD_BE_HELD = "Another player on the cell should be held by its vine, holds %s";
    private static final String WALL_GROWS_A_VINE = "vines_on_a_wall_grow_a_goologged_vine";
    /** The stone wall the drips strike, standing on the bay floor. */
    private static final BlockPos WALL = new BlockPos(3, 2, 2);
    /** The nook beside the wall's west face, on the floor, where the drips' goo lands. */
    private static final BlockPos NOOK = WALL.west();
    /** The drips sprayed at the wall. */
    private static final int WALL_DRIPS = 4;
    /** Ticks for the nook's cell to tick and grow its vine, with room to spare. */
    private static final int GROW_TICKS = 20;
    private static final String SHOULD_GROW_A_VINE = "The nook should hold Vines goo attached to the wall with a vine "
            + "hanging on it and %d layers, stands %s";
    private static final String SHOULD_BE_UNHARMED = "The zombie should end its root at the health it began with, %.2f, "
            + "stands at %.2f";

    private VinesTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VINES_DRIP_LAYS_A_LEAF_CELL, VinesTests::vinesDripLaysALeafCellOnStone);
        GooTestFunctions.registerTest(registrar, FULL_CELL_HOLDS_A_WALKER, VinesTests::vinesFullCellHoldsAWalkerInPlace);
        GooTestFunctions.registerTest(registrar, HIT_ENDS_THE_ROOT, VinesTests::vinesHitEndsTheRootAndEatsALayer);
        GooTestFunctions.registerTest(registrar, ROOT_RUNS_OUT_HARMLESS,
                VinesTests::vinesRootRunsOutAndLeavesItsMobUnharmed);
        GooTestFunctions.registerTest(registrar, BARKSKIN_SPARES, VinesTests::vinesSpareAPlayerUnderBarkskin);
        GooTestFunctions.registerTest(registrar, WALL_GROWS_A_VINE, VinesTests::vinesOnAWallGrowAGoologgedVine);
        GooTestFunctions.registerTest(registrar, BARKSKIN_ENDS_HELD_VINES, VinesTests::vinesHeldEndAsBarkskinBegins);
        GooTestFunctions.registerTest(registrar, EATEN_CELL_KEEPS_ITS_VINES,
                VinesTests::vinesEatenCellLeavesItsVinesHanging);
        GooTestFunctions.registerTest(registrar, GOOLOGGED_VINES_CLIMB, VinesTests::vinesGoologgedVinesClimbLikeAVine);
        GooTestFunctions.registerTest(registrar, CASTER_SPARED, VinesTests::vinesSpareThePlayerWhoSprayedThem);
    }

    /**
     * One Vines drip landing on stone's top lays a Vines cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void vinesDripLaysALeafCellOnStone(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
        AbilityDefinition vines = AbilityRegistry.of(helper.getLevel()).getAbility(VINES);
        helper.assertTrue(vines != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.LEAF);
        KnownRecipes.teachRequires(caster, vines);
        LandedDrips.landOn(helper, caster, vines, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean oneLayer = BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(VINES)
                && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * A husk walking east along a dirt path lane three blocks wide toward its
     * far end crosses a fifteen layer Vines cell set into the lane's middle, and two seconds after its
     * center first stands over the cell it still stands over it.
     *
     * @param helper the gametest helper
     */
    public static void vinesFullCellHoldsAWalkerInPlace(GameTestHelper helper) {
        layLane(helper);
        layCell(helper, FULL_CELL, VolumeCellBlock.MAX_LAYERS);
        Mob husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, LANE.above());
        helper.walkTo(husk, LANE.east(LANE_EAST).above(), 1f);
        int cellX = helper.absolutePos(FULL_CELL).getX();
        long[] reachedAt = {-1};
        helper.succeedWhen(() -> {
            boolean over = husk.getBlockX() == cellX;
            if (reachedAt[0] < 0 && over) {
                reachedAt[0] = helper.getTick();
            }
            helper.assertTrue(reachedAt[0] >= 0 && helper.getTick() - reachedAt[0] >= HELD_TICKS,
                    "The husk has not stood over the cell two seconds yet");
            helper.assertTrue(over, String.format(NOT_HELD, cellX, husk.getX()));
        });
    }

    /**
     * A zombie standing on a fifteen layer Vines cell is held by five vines;
     * a magic hit of 3 leaves it holding none, 3 health lower, and the cell
     * one layer lower at 14.
     *
     * @param helper the gametest helper
     */
    public static void vinesHitEndsTheRootAndEatsALayer(GameTestHelper helper) {
        layLane(helper);
        layCell(helper, FULL_CELL, VolumeCellBlock.MAX_LAYERS);
        Mob zombie = helmeted(helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, FULL_CELL.above()));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Rooted held = zombie.getData(GooAttachments.ROOTED);
            helper.assertTrue(held.vines() == VineHold.MOST_VINES, String.format(SHOULD_BE_HELD, held));
            float before = zombie.getHealth();
            zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), HIT_DAMAGE);
            Rooted after = zombie.getData(GooAttachments.ROOTED);
            helper.assertTrue(!after.holds(helper.getLevel().getGameTime()), String.format(SHOULD_BE_FREED, after));
            helper.assertTrue(zombie.getHealth() == before - HIT_DAMAGE,
                    String.format(SHOULD_TAKE_THE_HIT, HIT_DAMAGE, before, zombie.getHealth()));
            BlockState cell = helper.getBlockState(FULL_CELL);
            helper.assertTrue(cell.is(GooBlocks.LEAF_VINES.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == VolumeCellBlock.MAX_LAYERS - 1,
                    String.format(SHOULD_EAT_A_LAYER, cell));
            helper.succeed();
        });
    }

    /**
     * A zombie walking toward the lane's far end crosses a one-layer Vines
     * cell, is held by its one vine and walks on slowed until the vine lets
     * go, its health then what it was at the spawn.
     *
     * @param helper the gametest helper
     */
    public static void vinesRootRunsOutAndLeavesItsMobUnharmed(GameTestHelper helper) {
        layLane(helper);
        // a thin cell lies only on a sturdy top, which the path's lowered top is not
        helper.setBlock(THIN_CELL.below(), Blocks.STONE);
        layCell(helper, THIN_CELL, 1);
        Mob zombie = helmeted(helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, LANE.above()));
        helper.walkTo(zombie, LANE.east(LANE_EAST).above(), 1f);
        float start = zombie.getHealth();
        boolean[] rooted = {false};
        helper.succeedWhen(() -> {
            Rooted vines = zombie.getData(GooAttachments.ROOTED);
            rooted[0] |= vines.vines() > 0;
            helper.assertTrue(rooted[0] && vines.vines() == 0, "The zombie's root has not run out yet");
            helper.assertTrue(zombie.getHealth() == start, String.format(SHOULD_BE_UNHARMED, start,
                    zombie.getHealth()));
        });
    }

    /**
     * A mock player who cast Barkskin and a bare mock player each stand on a
     * fifteen layer Vines cell for a few of their own ticks: the bare player
     * is held by the cell's vines and the barkskinned player holds none.
     *
     * @param helper the gametest helper
     */
    public static void vinesSpareAPlayerUnderBarkskin(GameTestHelper helper) {
        layLane(helper);
        layCell(helper, FULL_CELL, VolumeCellBlock.MAX_LAYERS);
        layCell(helper, SECOND_FULL_CELL, VolumeCellBlock.MAX_LAYERS);
        ServerPlayer barked = HeartOverlayTests.selfInvoked(helper, GooTypes.LEAF, LEAF_BARKSKIN);
        ServerPlayer bare = SelfDeliveryTests.invoker(helper, GooTypes.LEAF);
        // the mock player helper makes a creative player; a survival one is what the vines reach
        bare.setGameMode(GameType.SURVIVAL);
        bare.connection.markClientLoaded();
        standOn(helper, barked, FULL_CELL);
        standOn(helper, bare, SECOND_FULL_CELL);
        SelfDeliveryTests.tickFor(helper, barked, SETTLE_TICKS);
        SelfDeliveryTests.tickFor(helper, bare, SETTLE_TICKS);
        helper.runAfterDelay(SETTLE_TICKS + 1, () -> {
            Rooted onBare = bare.getData(GooAttachments.ROOTED);
            Rooted onBarked = barked.getData(GooAttachments.ROOTED);
            helper.getLevel().getServer().getPlayerList().remove(barked);
            helper.getLevel().getServer().getPlayerList().remove(bare);
            helper.assertTrue(onBare.vines() == VineHold.MOST_VINES, String.format(BARE_SHOULD_BE_HELD, onBare));
            helper.assertTrue(onBarked.vines() == 0, String.format(BARKED_SHOULD_BE_SPARED, onBarked));
            helper.succeed();
        });
    }

    /**
     * Four Vines drips sprayed at a stone wall's west face land in the nook
     * beside it, on the floor, closed in on its other sides by iron bars,
     * which hold no vine and take no pour; once the cell has ticked, a vine
     * hangs on the wall's side in the cell's own space and the cell holds
     * four layers less the vines step's layers a vine.
     *
     * @param helper the gametest helper
     */
    public static void vinesOnAWallGrowAGoologgedVine(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(WALL, Blocks.STONE);
        helper.setBlock(NOOK, Blocks.AIR);
        for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST}) {
            helper.setBlock(NOOK.relative(side), Blocks.IRON_BARS);
        }
        AbilityDefinition vines = AbilityRegistry.of(helper.getLevel()).getAbility(VINES);
        helper.assertTrue(vines != null, ABILITY_REQUIRED);
        int perVine = VinesStep.in(vines.behaviors()).orElseThrow().layersPerVine();
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.LEAF);
        KnownRecipes.teachRequires(caster, vines);
        LandedDrips.landOnSide(helper, caster, vines, WALL, Direction.WEST, WALL_DRIPS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        int left = WALL_DRIPS - perVine;
        helper.runAfterDelay(GROW_TICKS, () -> {
            BlockState nook = helper.getBlockState(NOOK);
            boolean grown = nook.is(GooBlocks.LEAF_VINES.get())
                    && VolumeCellBlock.coatOn(nook, Direction.EAST) == SideCoat.ATTACHED
                    && GoologgedSides.plantedOn(nook, Direction.EAST)
                    && nook.getValue(VolumeCellBlock.LAYERS) == left;
            helper.assertTrue(grown, String.format(SHOULD_GROW_A_VINE, left, nook));
            helper.succeed();
        });
    }

    /**
     * A bare mock player standing on a fifteen layer Vines cell is held by
     * five vines; once it casts Barkskin through the real self delivery, the
     * tick after holds no vines and its speed carries no vines slow.
     *
     * @param helper the gametest helper
     */
    public static void vinesHeldEndAsBarkskinBegins(GameTestHelper helper) {
        layLane(helper);
        layCell(helper, FULL_CELL, VolumeCellBlock.MAX_LAYERS);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.LEAF);
        // the mock player helper makes a creative player; a survival one is what the vines reach
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        standOn(helper, player, FULL_CELL);
        SelfDeliveryTests.tickFor(helper, player, SETTLE_TICKS);
        // every callback is scheduled here, at the top: one scheduled from inside another runs again
        helper.runAfterDelay(SETTLE_TICKS + 1, () -> {
            Rooted held = player.getData(GooAttachments.ROOTED);
            helper.assertTrue(held.vines() == VineHold.MOST_VINES, String.format(BARE_SHOULD_BE_HELD, held));
            HeartOverlayTests.invoke(player, GooTypes.LEAF, LEAF_BARKSKIN);
        });
        helper.runAfterDelay(SETTLE_TICKS + 2, () -> {
            Rooted after = player.getData(GooAttachments.ROOTED);
            boolean slowed = player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(RootEvents.SLOW_ID) != null;
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(!after.holds(helper.getLevel().getGameTime()) && !slowed,
                    String.format(HELD_VINES_SHOULD_END, after, slowed ? "standing" : "gone"));
            helper.succeed();
        });
    }

    /**
     * A zombie held by a one-layer Vines cell bearing a vine on its north
     * wall takes a hit that eats the cell's last layer; the vine is left
     * hanging on that wall as a vine.
     *
     * @param helper the gametest helper
     */
    public static void vinesEatenCellLeavesItsVinesHanging(GameTestHelper helper) {
        layVinedCell(helper, VINED_CELL);
        Mob zombie = helmeted(helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, onThinCell(VINED_CELL)));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Rooted held = zombie.getData(GooAttachments.ROOTED);
            helper.assertTrue(held.vines() == 1, String.format(SHOULD_BE_HELD, held));
            zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), HIT_DAMAGE);
            BlockState left = helper.getBlockState(VINED_CELL);
            helper.assertTrue(left.is(Blocks.VINE) && left.getValue(VineBlock.NORTH),
                    String.format(SHOULD_HANG_ALONE, left));
            helper.succeed();
        });
    }

    /**
     * A zombie standing in a one-layer Vines cell bearing a vine climbs as
     * on a vine, and a zombie in a one-layer cell with no wall to grow one
     * on does not.
     *
     * @param helper the gametest helper
     */
    public static void vinesGoologgedVinesClimbLikeAVine(GameTestHelper helper) {
        layVinedCell(helper, VINED_CELL);
        helper.setBlock(BARE_CELL.below(), Blocks.STONE);
        layCell(helper, BARE_CELL, 1);
        Mob onVines = helmeted(helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, onThinCell(VINED_CELL)));
        Mob onBare = helmeted(helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, onThinCell(BARE_CELL)));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(onVines.onClimbable() && !onBare.onClimbable(),
                    String.format(SHOULD_CLIMB, onVines.onClimbable(), onBare.onClimbable()));
            helper.succeed();
        });
    }

    /**
     * A mock player lands one Vines drip on stone, and stands in the cell it
     * laid beside another mock player; the cell's vine holds the other
     * player and spares the one who sprayed it.
     *
     * @param helper the gametest helper
     */
    public static void vinesSpareThePlayerWhoSprayedThem(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
        AbilityDefinition vines = AbilityRegistry.of(helper.getLevel()).getAbility(VINES);
        helper.assertTrue(vines != null, ABILITY_REQUIRED);
        ServerPlayer caster = survivor(SelfDeliveryTests.invoker(helper, GooTypes.LEAF));
        ServerPlayer other = survivor(SelfDeliveryTests.invoker(helper, GooTypes.LEAF));
        KnownRecipes.teachRequires(caster, vines);
        LandedDrips.landOn(helper, caster, vines, FLOOR, 1);
        for (ServerPlayer player : new ServerPlayer[] {caster, other}) {
            Vec3 in = inThinCell(helper, OVER_THE_FLOOR);
            player.setPos(in.x, in.y, in.z);
            SelfDeliveryTests.tickFor(helper, player, SETTLE_TICKS);
        }
        helper.runAfterDelay(SETTLE_TICKS + 1, () -> {
            Rooted onCaster = caster.getData(GooAttachments.ROOTED);
            Rooted onOther = other.getData(GooAttachments.ROOTED);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.getLevel().getServer().getPlayerList().remove(other);
            helper.assertTrue(onOther.vines() == 1, String.format(OTHER_SHOULD_BE_HELD, onOther));
            helper.assertTrue(onCaster.vines() == 0, String.format(CASTER_SHOULD_BE_SPARED, onCaster));
            helper.succeed();
        });
    }

    /**
     * Lays a one-layer Vines cell on a stone floor with a stone wall north of
     * it, the cell attached to that wall and bearing a vine on it.
     *
     * @param helper the gametest helper
     * @param cell   the cell
     */
    private static void layVinedCell(GameTestHelper helper, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
        helper.setBlock(cell.north(), Blocks.STONE);
        helper.setBlock(cell, GooBlocks.LEAF_VINES.get().defaultBlockState()
                .setValue(VolumeCellBlock.coatProperty(Direction.NORTH), SideCoat.ATTACHED)
                .setValue(GoologgedSides.NORTH, true));
    }

    /** Where a thing stands in a one-layer cell, relative to the test: on its one layer, over the cell's middle. */
    private static Vec3 onThinCell(BlockPos cell) {
        return Vec3.atBottomCenterOf(cell).add(0, ONE_LAYER, 0);
    }

    /** Where a thing stands in a one-layer cell, in the world. */
    private static Vec3 inThinCell(GameTestHelper helper, BlockPos cell) {
        return helper.absoluteVec(onThinCell(cell));
    }

    /** A survival player whose client reports loaded, whom the vines and hits reach. */
    private static ServerPlayer survivor(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        return player;
    }

    private static void standOn(GameTestHelper helper, ServerPlayer player, BlockPos cell) {
        Vec3 top = Vec3.atBottomCenterOf(helper.absolutePos(cell.above()));
        player.setPos(top.x, top.y, top.z);
    }

    /**
     * Lays a dirt path lane three blocks wide on bedrock from its start east
     * across the bay, open air over it. The path beside a cell set into the
     * lane's middle closes it in, so it pours none of its layers away, and
     * its side, short of a full block, holds no goo and bears no vine, so the
     * cell keeps every layer.
     *
     * @param helper the gametest helper
     */
    private static void layLane(GameTestHelper helper) {
        for (int east = 0; east <= LANE_EAST; east++) {
            for (int across = -1; across <= 1; across++) {
                BlockPos path = LANE.east(east).south(across);
                helper.setBlock(path.below(), Blocks.BEDROCK);
                helper.setBlock(path, Blocks.DIRT_PATH);
                helper.setBlock(path.above(), Blocks.AIR);
            }
        }
    }

    private static void layCell(GameTestHelper helper, BlockPos cell, int layers) {
        helper.setBlock(cell, GooBlocks.LEAF_VINES.get().defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers));
    }

    /** A helmet keeps the daylight from burning the zombie, and the burn from ending its root. */
    private static Mob helmeted(Mob zombie) {
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }
}
