package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.fluid.MoltenIronFluid;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for molten iron, lunar's core liquid: it spreads as lava does; water cools a source of it to iron core
 * and flowing molten iron to anorthosite, as water cools lava to obsidian and cobblestone; and it burns what stands
 * in it harder and longer than lava; a mob falls and jumps in it as in lava; and a camera reads it as lava.
 * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
 * decision diagnose-and-correct-molten-iron-moving-you-as-open-air-does
 * decision diagnose-and-correct-molten-iron-showing-no-in-lava-view
 */
@RegistersGameTests
public final class MoltenIronTests {
    private static final String MOLTEN_IRON_FLOWS = "molten_iron_flows";
    private static final String WATER_COOLS_MOLTEN_IRON = "water_cools_molten_iron";
    private static final String MOLTEN_IRON_BURNS_HOTTER_THAN_LAVA = "molten_iron_burns_hotter_than_lava";

    private static final int FLOOR_Y = 0;
    private static final int LIQUID_Y = 1;
    /** test_bay's far edge: the test's chunks tick only what stands inside its 6 by 6 bounds. */
    private static final int FLOOR_EDGE = 5;
    private static final BlockPos SOURCE = new BlockPos(4, LIQUID_Y, 4);
    /** A flowing molten iron level, one step out from a source. */
    private static final int ONE_STEP_OUT = 2;
    private static final BlockPos FLOW_COOLED = new BlockPos(1, LIQUID_Y, 1);
    private static final BlockPos BESIDE_FLOW = new BlockPos(1, LIQUID_Y, 2);
    private static final BlockPos SOURCE_COOLED = new BlockPos(5, LIQUID_Y, 5);
    private static final BlockPos BESIDE_SOURCE = new BlockPos(5, LIQUID_Y, 4);
    /** Molten iron poured beside water already standing, a source and a flow, each cooling on the spot. */
    private static final BlockPos WATER_BEFORE_SOURCE = new BlockPos(3, LIQUID_Y, 3);
    private static final BlockPos SOURCE_POURED_BESIDE_WATER = new BlockPos(3, LIQUID_Y, 4);
    private static final BlockPos WATER_BEFORE_FLOW = new BlockPos(5, LIQUID_Y, 1);
    private static final BlockPos FLOW_POURED_BESIDE_WATER = new BlockPos(5, LIQUID_Y, 2);
    private static final BlockPos LAVA_POOL =new BlockPos(2, LIQUID_Y, 2);
    private static final BlockPos MOLTEN_POOL = new BlockPos(4, LIQUID_Y, 4);
    /** Long enough for several hits apart by the golem's hurt cooldown, short of either golem dying. */
    private static final int BURN_TICKS = 41;

    private static final String MOLTEN_IRON_SLOWS_A_FALL_AS_LAVA_DOES = "molten_iron_slows_a_fall_as_lava_does";
    private static final String MOLTEN_IRON_JUMP_RISES_AS_IN_LAVA = "molten_iron_jump_rises_as_in_lava";

    /**
     * The bottom cell of each walled one-block column, walls and all inside test_bay's 6 by 6 bounds, where the
     * test's chunks tick the mobs: molten iron, lava, open air, and a patch of dry floor beside them.
     */
    private static final BlockPos MOLTEN_COLUMN = new BlockPos(1, LIQUID_Y, 1);
    private static final BlockPos LAVA_COLUMN = new BlockPos(4, LIQUID_Y, 1);
    private static final BlockPos AIR_COLUMN = new BlockPos(1, LIQUID_Y, 4);
    private static final BlockPos DRY_GROUND = new BlockPos(4, LIQUID_Y, 4);
    /** The fall's liquid stands four blocks deep, y 1 to 4, its walls one block higher. */
    private static final int FALL_LIQUID_TOP = 4;
    private static final int FALL_WALL_TOP = 5;
    /** The fall starts with the mob's feet a block above the walls, over a block above the liquid's surface. */
    private static final double FALL_START_Y = 6.0;
    /**
     * The tick the fall is read at: each mob has been in its liquid five ticks, and the mob in open air is still
     * two blocks above the floor.
     */
    private static final int FALL_READ_TICK = 10;
    /** The jump's liquid stands three blocks deep, y 1 to 3, deeper than a mob's fluid jump threshold. */
    private static final int JUMP_LIQUID_TOP = 3;
    /** Ticks the jump is watched for: long enough for the mob on dry ground to leave it once. */
    private static final int JUMP_WATCH_TICKS = 8;
    /** Two mobs moving by one rule match to rounding; a thousandth of a block a tick is far below either gap. */
    private static final double SAME_SPEED_TOLERANCE = 1.0E-3;
    private static final double CELL_MIDDLE = 0.5;

    private static final String SHOULD_SINK_AS_IN_LAVA =
            "A mob should sink in molten iron as in lava: at tick %d molten iron %.4f, lava %.4f, open air %.4f";
    private static final String SHOULD_SINK_SLOWER_THAN_AIR =
            "A mob should sink in molten iron slower than it falls through open air: molten iron %.4f, open air %.4f";
    private static final String SHOULD_RISE_AS_IN_LAVA =
            "A mob jumping in molten iron should rise as in lava: molten iron %.4f, lava %.4f, ground jump %.4f";
    private static final String SHOULD_RISE_SLOWER_THAN_GROUND_JUMP =
            "A mob jumping in molten iron should rise slower than its jump from the ground: molten iron %.4f, ground %.4f";
    private static final String MOLTEN_IRON_READS_AS_LAVA_TO_THE_CAMERA = "molten_iron_reads_as_lava_to_the_camera";
    private static final BlockPos CAMERA_IN_SOURCE = new BlockPos(1, LIQUID_Y, 1);
    private static final BlockPos CAMERA_IN_FLOW = new BlockPos(4, LIQUID_Y, 4);
    private static final String SHOULD_READ_AS_LAVA = "A camera in molten iron at %s should read lava: %s, lava tag %b,"
            + " surface %.3f above camera %.3f";
    private static final String SHOULD_FLOW ="Molten iron should spread beside its source to %s, reads %s";
    private static final String SHOULD_COOL = "Water should cool molten iron at %s to %s, reads %s";
    private static final String SHOULD_HURT_MORE = "Molten iron should hurt harder than lava: lava took %.1f, molten iron %.1f";
    private static final String SHOULD_BURN_LONGER = "Molten iron should set a longer fire than lava: lava %d ticks, molten iron %d";

    private static final String MOLTEN_IRON_HURTS_BY_ITS_OWN_DAMAGE_TYPE = "molten_iron_hurts_by_its_own_damage_type";
    private static final String MOLTEN_IRON_SPARES_A_FIRE_IMMUNE_MOB = "molten_iron_spares_a_fire_immune_mob";
    /** Health enough that the pig outlives every scald the test waits through. */
    private static final float HARDY_HEALTH = 1024f;
    private static final int FIRE_IMMUNE_WATCH_TICKS = 60;
    private static final String SHOULD_HURT_AS_MOLTEN_IRON =
            "A pig in molten iron should be hurt by goo:molten_iron naming no entity, last hit by %s";
    private static final String SHOULD_SPARE_THE_FIRE_IMMUNE =
            "A strider in molten iron should keep its whole health: full %.1f, holds %.1f";

    private MoltenIronTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MOLTEN_IRON_FLOWS, MoltenIronTests::moltenIronFlows);
        GooTestFunctions.registerTest(registrar, WATER_COOLS_MOLTEN_IRON, MoltenIronTests::waterCoolsMoltenIron);
        GooTestFunctions.registerTest(registrar, MOLTEN_IRON_BURNS_HOTTER_THAN_LAVA,
                MoltenIronTests::moltenIronBurnsHotterThanLava);
        GooTestFunctions.registerTest(registrar, MOLTEN_IRON_SLOWS_A_FALL_AS_LAVA_DOES,
                MoltenIronTests::moltenIronSlowsAFallAsLavaDoes);
        GooTestFunctions.registerTest(registrar, MOLTEN_IRON_JUMP_RISES_AS_IN_LAVA,
                MoltenIronTests::moltenIronJumpRisesAsInLava);
        GooTestFunctions.registerTest(registrar, MOLTEN_IRON_READS_AS_LAVA_TO_THE_CAMERA,
                MoltenIronTests::moltenIronReadsAsLavaToTheCamera);
        GooTestFunctions.registerTest(registrar, MOLTEN_IRON_HURTS_BY_ITS_OWN_DAMAGE_TYPE,
                MoltenIronTests::moltenIronHurtsByItsOwnDamageType);
        GooTestFunctions.registerTest(registrar, MOLTEN_IRON_SPARES_A_FIRE_IMMUNE_MOB,
                MoltenIronTests::moltenIronSparesAFireImmuneMob);
    }

    /**
     * A source and a flowing molten iron block each answer what Camera.getFluidInCamera reads to answer FogType.LAVA:
     * a fluid in the {@code minecraft:lava} tag whose surface stands above a point inside the block. A camera there
     * takes lava's fog through LavaFogEnvironment.
     * decision diagnose-and-correct-molten-iron-showing-no-in-lava-view
     *
     * @param helper the gametest helper
     */
    public static void moltenIronReadsAsLavaToTheCamera(GameTestHelper helper) {
        layStoneFloor(helper);
        helper.setBlock(CAMERA_IN_SOURCE, GooBlocks.MOLTEN_IRON.get());
        helper.setBlock(CAMERA_IN_FLOW, GooBlocks.MOLTEN_IRON.get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, ONE_STEP_OUT));
        assertReadsAsLavaToTheCamera(helper, CAMERA_IN_SOURCE);
        assertReadsAsLavaToTheCamera(helper, CAMERA_IN_FLOW);
        helper.succeed();
    }

    /** Camera.getFluidInCamera's lava test, at a camera point halfway up the block. */
    private static void assertReadsAsLavaToTheCamera(GameTestHelper helper, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        FluidState fluid = helper.getLevel().getFluidState(absolute);
        double surface = absolute.getY() + fluid.getHeight(helper.getLevel(), absolute);
        double cameraY = absolute.getY() + CELL_MIDDLE;
        helper.assertTrue(fluid.is(FluidTags.LAVA) && cameraY <= surface,
                String.format(SHOULD_READ_AS_LAVA, pos, fluid, fluid.is(FluidTags.LAVA), surface, cameraY));
    }

    /**
     * Three zombified piglins, fire immune so the burn leaves their motion alone, drop from one height into a column
     * of molten iron, a column of lava and an empty column. At one tick, the one in molten iron sinks as fast as the
     * one in lava and slower than the one falling through open air.
     * decision diagnose-and-correct-molten-iron-moving-you-as-open-air-does
     *
     * @param helper the gametest helper
     */
    public static void moltenIronSlowsAFallAsLavaDoes(GameTestHelper helper) {
        layStoneFloor(helper);
        buildColumn(helper, MOLTEN_COLUMN, FALL_WALL_TOP, FALL_LIQUID_TOP, GooBlocks.MOLTEN_IRON.get());
        buildColumn(helper, LAVA_COLUMN, FALL_WALL_TOP, FALL_LIQUID_TOP, Blocks.LAVA);
        buildColumn(helper, AIR_COLUMN, FALL_WALL_TOP, 0, Blocks.AIR);
        Mob inMolten = dropAt(helper, MOLTEN_COLUMN, FALL_START_Y);
        Mob inLava = dropAt(helper, LAVA_COLUMN, FALL_START_Y);
        Mob inAir = dropAt(helper, AIR_COLUMN, FALL_START_Y);
        double[] lastY = new double[3];
        helper.runAfterDelay(FALL_READ_TICK - 1, () -> {
            lastY[0] = inMolten.getY();
            lastY[1] = inLava.getY();
            lastY[2] = inAir.getY();
        });
        helper.runAfterDelay(FALL_READ_TICK, () -> {
            double moltenSink = lastY[0] - inMolten.getY();
            double lavaSink = lastY[1] - inLava.getY();
            double airSink = lastY[2] - inAir.getY();
            helper.assertTrue(Math.abs(moltenSink - lavaSink) <= SAME_SPEED_TOLERANCE,
                    String.format(SHOULD_SINK_AS_IN_LAVA, FALL_READ_TICK, moltenSink, lavaSink, airSink));
            helper.assertTrue(moltenSink < airSink, String.format(SHOULD_SINK_SLOWER_THAN_AIR, moltenSink, airSink));
            helper.succeed();
        });
    }

    /**
     * Three zombified piglins hold jump each tick: one on the floor of a column of molten iron, one on the floor of a
     * column of lava as deep, one on dry stone. Over the watch, the fastest rise the one in molten iron makes in a
     * tick matches the one in lava and falls short of the jump from the ground.
     * decision diagnose-and-correct-molten-iron-moving-you-as-open-air-does
     *
     * @param helper the gametest helper
     */
    public static void moltenIronJumpRisesAsInLava(GameTestHelper helper) {
        layStoneFloor(helper);
        buildColumn(helper, MOLTEN_COLUMN, JUMP_LIQUID_TOP, JUMP_LIQUID_TOP, GooBlocks.MOLTEN_IRON.get());
        buildColumn(helper, LAVA_COLUMN, JUMP_LIQUID_TOP, JUMP_LIQUID_TOP, Blocks.LAVA);
        RiseWatch inMolten = new RiseWatch(dropAt(helper, MOLTEN_COLUMN, LIQUID_Y));
        RiseWatch inLava = new RiseWatch(dropAt(helper, LAVA_COLUMN, LIQUID_Y));
        RiseWatch onGround = new RiseWatch(dropAt(helper, DRY_GROUND, LIQUID_Y));
        helper.onEachTick(() -> {
            inMolten.jumpAndSample();
            inLava.jumpAndSample();
            onGround.jumpAndSample();
        });
        helper.runAfterDelay(JUMP_WATCH_TICKS, () -> {
            helper.assertTrue(Math.abs(inMolten.fastestRise - inLava.fastestRise) <= SAME_SPEED_TOLERANCE,
                    String.format(SHOULD_RISE_AS_IN_LAVA, inMolten.fastestRise, inLava.fastestRise,
                            onGround.fastestRise));
            helper.assertTrue(inMolten.fastestRise < onGround.fastestRise,
                    String.format(SHOULD_RISE_SLOWER_THAN_GROUND_JUMP, inMolten.fastestRise, onGround.fastestRise));
            helper.succeed();
        });
    }

    /**
     * A mob held jumping, and the fastest it has risen in one tick since the watch began. Its navigation floats, so
     * a jump in a liquid adds the small push a player's does; a mob that cannot float gets Mob.jumpInLiquidInternal's
     * kick of 0.3 instead.
     */
    private static final class RiseWatch {
        private final Mob mob;
        private double lastY;
        private double fastestRise;

        RiseWatch(Mob mob) {
            this.mob = mob;
            mob.getNavigation().setCanFloat(true);
            this.lastY = mob.getY();
        }

        void jumpAndSample() {
            mob.getJumpControl().jump();
            fastestRise = Math.max(fastestRise, mob.getY() - lastY);
            lastY = mob.getY();
        }
    }

    /**
     * Walls the one-block column standing on {@code column} in stone from the floor up to {@code wallTop}, and fills
     * it from the floor up to {@code liquidTop} with {@code liquid}.
     */
    private static void buildColumn(GameTestHelper helper, BlockPos column, int wallTop, int liquidTop, Block liquid) {
        for (int y = LIQUID_Y; y <= wallTop; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        helper.setBlock(new BlockPos(column.getX() + dx, y, column.getZ() + dz), Blocks.STONE);
                    }
                }
            }
        }
        for (int y = LIQUID_Y; y <= liquidTop; y++) {
            helper.setBlock(new BlockPos(column.getX(), y, column.getZ()), liquid);
        }
    }

    /** A zombified piglin with no goals, its feet at {@code y} in the middle of the column standing on {@code column}. */
    private static Mob dropAt(GameTestHelper helper, BlockPos column, double y) {
        return helper.spawnWithNoFreeWill(EntityTypes.ZOMBIFIED_PIGLIN,
                new Vec3(column.getX() + CELL_MIDDLE, y, column.getZ() + CELL_MIDDLE));
    }

    /**
     * A molten iron source on stone spreads flowing molten iron onto the stone beside it.
     *
     * @param helper the gametest helper
     */
    public static void moltenIronFlows(GameTestHelper helper) {
        layStoneFloor(helper);
        helper.setBlock(SOURCE, GooBlocks.MOLTEN_IRON.get());
        BlockPos beside = SOURCE.east();
        helper.succeedWhen(() -> {
            BlockState state = helper.getBlockState(beside);
            helper.assertTrue(state.getFluidState().getType() == GooFluids.MOLTEN_IRON_FLOWING.get(),
                    String.format(SHOULD_FLOW, beside, state));
        });
    }

    /**
     * Water laid beside a molten iron source cools it to iron core, and beside flowing molten iron cools it to
     * anorthosite; molten iron poured beside water already standing cools the same way.
     *
     * @param helper the gametest helper
     */
    public static void waterCoolsMoltenIron(GameTestHelper helper) {
        layStoneFloor(helper);
        helper.setBlock(SOURCE_COOLED, GooBlocks.MOLTEN_IRON.get());
        helper.setBlock(FLOW_COOLED, GooBlocks.MOLTEN_IRON.get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, ONE_STEP_OUT));
        helper.setBlock(BESIDE_SOURCE, Blocks.WATER);
        helper.setBlock(BESIDE_FLOW, Blocks.WATER);
        helper.setBlock(WATER_BEFORE_SOURCE, Blocks.WATER);
        helper.setBlock(WATER_BEFORE_FLOW, Blocks.WATER);
        helper.setBlock(SOURCE_POURED_BESIDE_WATER, GooBlocks.MOLTEN_IRON.get());
        helper.setBlock(FLOW_POURED_BESIDE_WATER, GooBlocks.MOLTEN_IRON.get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, ONE_STEP_OUT));
        helper.succeedWhen(() -> {
            assertCooled(helper, SOURCE_COOLED, GooBlocks.IRON_CORE.get().defaultBlockState());
            assertCooled(helper, FLOW_COOLED, GooBlocks.ANORTHOSITE.get().defaultBlockState());
            assertCooled(helper, SOURCE_POURED_BESIDE_WATER, GooBlocks.IRON_CORE.get().defaultBlockState());
            assertCooled(helper, FLOW_POURED_BESIDE_WATER, GooBlocks.ANORTHOSITE.get().defaultBlockState());
        });
    }

    /**
     * Of two iron golems held still, one in lava and one in molten iron, the one in molten iron has taken more
     * damage and carries a longer fire, read once several hits have landed and waited on through the test's ticks, so
     * a golem whose chunk starts ticking late still burns before the test reads it.
     *
     * @param helper the gametest helper
     */
    public static void moltenIronBurnsHotterThanLava(GameTestHelper helper) {
        layStoneFloor(helper);
        helper.setBlock(LAVA_POOL, Blocks.LAVA);
        helper.setBlock(MOLTEN_POOL, GooBlocks.MOLTEN_IRON.get());
        IronGolem inLava = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, LAVA_POOL);
        IronGolem inMolten = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, MOLTEN_POOL);
        float full = inLava.getMaxHealth();
        helper.succeedWhen(() -> {
            float lavaTook = full - inLava.getHealth();
            float moltenTook = full - inMolten.getHealth();
            helper.assertTrue(helper.getTick() >= BURN_TICKS, String.format(SHOULD_HURT_MORE, lavaTook, moltenTook));
            helper.assertTrue(moltenTook > lavaTook, String.format(SHOULD_HURT_MORE, lavaTook, moltenTook));
            helper.assertTrue(inMolten.getRemainingFireTicks() > inLava.getRemainingFireTicks(),
                    String.format(SHOULD_BURN_LONGER, inLava.getRemainingFireTicks(), inMolten.getRemainingFireTicks()));
        });
    }

    /**
     * A pig held still in a molten iron source is hurt by molten iron's own damage type, naming no entity, since
     * molten iron is environmental and credits nobody.
     * molten-iron-death-reads-wanted-a-cast-of-molten-metal
     *
     * @param helper the gametest helper
     */
    public static void moltenIronHurtsByItsOwnDamageType(GameTestHelper helper) {
        layStoneFloor(helper);
        helper.setBlock(MOLTEN_POOL, GooBlocks.MOLTEN_IRON.get());
        Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, MOLTEN_POOL);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HARDY_HEALTH);
        pig.setHealth(HARDY_HEALTH);
        helper.succeedWhen(() -> {
            DamageSource last = pig.getLastDamageSource();
            helper.assertTrue(last != null && last.is(MoltenIronFluid.MOLTEN_IRON_DAMAGE)
                            && last.getEntity() == null && last.getDirectEntity() == null,
                    String.format(SHOULD_HURT_AS_MOLTEN_IRON, last));
        });
    }

    /**
     * A strider, fire immune, held in a molten iron source for {@link #FIRE_IMMUNE_WATCH_TICKS} ticks keeps its
     * whole health, since molten iron's damage is fire as lava's is.
     * molten-iron-death-reads-wanted-a-cast-of-molten-metal
     *
     * @param helper the gametest helper
     */
    public static void moltenIronSparesAFireImmuneMob(GameTestHelper helper) {
        layStoneFloor(helper);
        helper.setBlock(MOLTEN_POOL, GooBlocks.MOLTEN_IRON.get());
        Strider strider = helper.spawnWithNoFreeWill(EntityTypes.STRIDER, MOLTEN_POOL);
        float full = strider.getMaxHealth();
        helper.startSequence()
                .thenIdle(FIRE_IMMUNE_WATCH_TICKS)
                .thenExecute(() -> helper.assertTrue(strider.getHealth() == full,
                        String.format(SHOULD_SPARE_THE_FIRE_IMMUNE, full, strider.getHealth())))
                .thenSucceed();
    }

    private static void assertCooled(GameTestHelper helper, BlockPos pos, BlockState cooled) {
        BlockState found = helper.getBlockState(pos);
        helper.assertTrue(found == cooled, String.format(SHOULD_COOL, pos, cooled, found));
    }

    private static void layStoneFloor(GameTestHelper helper) {
        for (int x = 0; x <= FLOOR_EDGE; x++) {
            for (int z = 0; z <= FLOOR_EDGE; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
            }
        }
    }
}
