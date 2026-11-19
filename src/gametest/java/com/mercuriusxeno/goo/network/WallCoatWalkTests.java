package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * A cow walking a lane of goo laid against a wall covers the ground its like
 * covers on the same goo laid a block off the wall, its feet at the goo's
 * top every tick, so a cell coating a wall jumps no mob crossing it.
 * diagnose-and-correct-mobs-and-players-hopping-in-goo-beside-a-wall
 */
@RegistersGameTests
public final class WallCoatWalkTests {

    private static final String STITCH_BESIDE_A_WALL = "cow_crosses_stitch_coating_a_wall_without_hopping";
    private static final String GOOP_BESIDE_A_WALL = "cow_crosses_goop_coating_a_wall_without_hopping";
    /** The wall's row, stone two blocks tall along the bay. */
    private static final int WALL_ROW = 1;
    /** The lane coating the wall's south face, each cell attached to its north side. */
    private static final int WALL_LANE = 2;
    /** The lane a block off the wall, two blocks from the wall lane so no cow brushes the other. */
    private static final int OPEN_LANE = 4;
    private static final int LANE_FROM = 2;
    private static final int LANE_TO = 30;
    /** One layer of Stitch, the layer its drip lays. */
    private static final int STITCH_LAYERS = 1;
    /** Three layers of Goop, enough for more than one layer on the wall, and under its cohesion's step. */
    private static final int GOOP_LAYERS = 3;
    /** Three seconds, how long each cow walks before its distance is read. */
    private static final int WALK_TICKS = 60;
    /** How far apart the two cows' distances may lie and still read as the same ground covered. */
    private static final double SAME_GROUND = 0.1;
    /** How far a cow's feet may stand off the goo's top on any tick. */
    private static final double AT_THE_TOP = 0.01;
    /** How far into its lane a cow stands from the lane's north edge, clear of the deepest coat here. */
    private static final double COW_Z = 0.6;
    private static final String COW_SHOULD_WALK = "The cow off the wall should walk its lane, it covered %.3f";
    private static final String COW_SHOULD_MATCH = "The cow on goo coating the wall should cover the ground the cow "
            + "off the wall covers, %.3f against %.3f";
    private static final String FEET_SHOULD_STAY = "The cow on goo coating the wall should keep its feet at the "
            + "goo's top, they stood %.3f off it at tick %d (feet %.3f, top %.3f)";

    private WallCoatWalkTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, STITCH_BESIDE_A_WALL,
                helper -> cowCrossesGooCoatingAWall(helper, GooBlocks.YARN_STITCH.get(), STITCH_LAYERS));
        GooTestFunctions.registerTest(registrar, GOOP_BESIDE_A_WALL,
                helper -> cowCrossesGooCoatingAWall(helper, GooBlocks.SLIME_COAT.get(), GOOP_LAYERS));
    }

    /**
     * Lays a lane of goo coating a stone wall and a like lane a block off
     * it, walks a cow east along each for three seconds, and reads the
     * ground each covered and, every tick, how far the wall lane's cow's
     * feet stand off the goo's top.
     *
     * @param helper the gametest helper
     * @param cell   the goo's cell block
     * @param layers the layers each cell holds
     */
    private static void cowCrossesGooCoatingAWall(GameTestHelper helper, VolumeCellBlock cell, int layers) {
        layBay(helper);
        BlockState open = cell.defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers);
        BlockState coating = open.setValue(VolumeCellBlock.coatProperty(Direction.NORTH), SideCoat.ATTACHED);
        for (int x = LANE_FROM; x <= LANE_TO; x++) {
            helper.setBlock(new BlockPos(x, 1, WALL_LANE), coating);
            helper.setBlock(new BlockPos(x, 1, OPEN_LANE), open);
        }
        Mob byTheWall = walker(helper, WALL_LANE);
        Mob offTheWall = walker(helper, OPEN_LANE);
        double start = byTheWall.getX();
        FeetOffTop worst = new FeetOffTop();
        int laneY = helper.absolutePos(new BlockPos(LANE_FROM, 1, WALL_LANE)).getY();
        helper.onEachTick(() -> worst.read(helper.getLevel(), byTheWall, laneY, helper.getTick()));
        helper.runAfterDelay(WALK_TICKS, () -> {
            double wall = byTheWall.getX() - start;
            double away = offTheWall.getX() - start;
            helper.assertTrue(away > 1.0, String.format(COW_SHOULD_WALK, away));
            helper.assertTrue(Math.abs(wall - away) <= SAME_GROUND, String.format(COW_SHOULD_MATCH, wall, away));
            helper.assertTrue(worst.off <= AT_THE_TOP,
                    String.format(FEET_SHOULD_STAY, worst.off, worst.tick, worst.feet, worst.top));
            helper.succeed();
        });
    }

    /**
     * Lays the bay's floor as stone under open air, and a stone wall two blocks tall along its north lane.
     *
     * @param helper the gametest helper
     */
    private static void layBay(GameTestHelper helper) {
        for (int x = 0; x <= LANE_TO + 2; x++) {
            for (int z = 0; z <= OPEN_LANE + 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), y <= 2 && z == WALL_ROW ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }

    /**
     * Spawns a cow with no will of its own on its lane's goo top and walks it east to the lane's end.
     *
     * @param helper the gametest helper
     * @param lane   the lane's z
     * @return the cow
     */
    private static Mob walker(GameTestHelper helper, int lane) {
        BlockPos first = new BlockPos(LANE_FROM + 1, 1, lane);
        BlockPos inWorld = helper.absolutePos(first);
        double top = first.getY() + gooTop(helper.getLevel(), inWorld) - inWorld.getY();
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(first.getX() + 0.5, top, lane + COW_Z));
        helper.walkTo(cow, new BlockPos(LANE_TO, 1, lane), 1f);
        return cow;
    }

    /**
     * The top of the goo lying on a cell's floor, in the world, from the
     * share of its volume the cell holds on its floor.
     *
     * @param level the level
     * @param pos   the cell, in the world
     * @return the goo's top
     */
    private static double gooTop(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof VolumeCellBlock cell)) {
            return pos.getY();
        }
        double floor = VolumeCellBlock.sharesOf(state, cell.onAFloor(level, pos), cell.cohesionAt(level)).floor();
        return pos.getY() + floor / VolumeCellBlock.MAX_LAYERS;
    }

    /** The furthest a cow's feet stood off the goo's top, and the tick it stood there. */
    private static final class FeetOffTop {
        private double off;
        private long tick;
        private double feet;
        private double top;

        private void read(Level level, Mob cow, int laneY, long now) {
            double under = gooTop(level, new BlockPos(cow.getBlockX(), laneY, cow.getBlockZ()));
            double gap = Math.abs(cow.getY() - under);
            if (gap > off) {
                off = gap;
                tick = now;
                feet = cow.getY();
                top = under;
            }
        }
    }
}
