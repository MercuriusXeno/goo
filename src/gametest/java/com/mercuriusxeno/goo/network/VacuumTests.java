package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.KineticSlickBlock;
import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gametests for Void's Vacuum, held through the real stream tick over a lane
 * of goop and slick cells running east from the player.
 * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
 * vacuum-costs-one-mb-of-void-a-second
 */
@RegistersGameTests
public final class VacuumTests {

    private static final Identifier VACUUM = Identifier.parse("goo:void_vacuum");
    private static final String COSTS_ONE_MB_A_SECOND = "vacuum_costs_one_mb_of_void_a_second";
    private static final String RETURNS_THE_GOO = "vacuum_breaks_residuals_and_returns_their_goo";
    private static final String BREAKS_IN_A_WAVE = "vacuum_breaks_residuals_nearest_first";
    private static final String LEAVES_THE_REST = "vacuum_leaves_what_is_outside_its_reach";
    /** Where the player stands, the lane running east from it across the light bay. */
    private static final BlockPos STAND = new BlockPos(3, 1, 8);
    private static final float FACING_EAST = -90f;
    /** Pitched down so the cone, 20 degrees each side of the look, spans the lane two to seven blocks out. */
    private static final float AT_THE_LANE = 20f;
    /** Each lane cell east of the player and what it holds: slick, or goop of that many layers. */
    private static final Map<Integer, Integer> LANE = laneOfResiduals();
    /** A slick film, in the lane's map, against a goop cell's layer count. */
    private static final int SLICK = 0;
    /** The slick cell eight blocks east, its middle past the 8 block reach from the eye. */
    private static final int PAST_THE_REACH = 8;
    /** The goop cell behind the player. */
    private static final int BEHIND = -2;
    /** The lane's row beside which stone walls keep goop from pouring sideways, one wall stone inside the cone. */
    private static final int WALL_INSIDE_THE_CONE = 4;
    /** A hold long enough for the wave to cross the reach, the far end some ten ticks in. */
    private static final int WAVE_HOLD = 20;
    /** Two charges of 20 ticks, so a hold of this length costs Vacuum's 1 mb twice. */
    private static final int TWO_CHARGES = 40;
    /** The reach's far end breaks within about this many ticks of the nearest cell. */
    private static final int WAVE_TICKS = 10;
    private static final String NOT_CHARGED = "A hold of %d ticks should spend 2 mb of void goo, spent %d";
    private static final String NOT_CLEARED = "The lane cell %d east should be air after the hold, holds %s";
    private static final String GOO_OWED = "The lane should drop %d mb of %s goo, dropped %d in %s";
    private static final String OUT_OF_ORDER = "The lane's cells should turn to air nearest first, %s";
    private static final String TOO_SLOW = "The lane's far cell should break within %d ticks of the nearest, %s";
    private static final String NEVER_BROKE = "Every lane cell should turn to air within the hold, %s";
    private static final String NOT_STANDING = "%s should still stand after the hold, holds %s";

    private VacuumTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, COSTS_ONE_MB_A_SECOND, VacuumTests::vacuumCostsOneMbOfVoidASecond);
        GooTestFunctions.registerTest(registrar, RETURNS_THE_GOO, VacuumTests::vacuumBreaksResidualsAndReturnsTheirGoo);
        GooTestFunctions.registerTest(registrar, BREAKS_IN_A_WAVE, VacuumTests::vacuumBreaksResidualsNearestFirst);
        GooTestFunctions.registerTest(registrar, LEAVES_THE_REST, VacuumTests::vacuumLeavesWhatIsOutsideItsReach);
    }

    private static Map<Integer, Integer> laneOfResiduals() {
        // slick between goop cells, so no goop cell pours into another
        Map<Integer, Integer> lane = new LinkedHashMap<>();
        lane.put(2, SLICK);
        lane.put(3, 3);
        lane.put(4, SLICK);
        lane.put(5, 5);
        lane.put(6, SLICK);
        lane.put(7, 1);
        return lane;
    }

    /**
     * Vacuum held for two charges' worth of ticks spends 2 mb of void goo.
     * vacuum-costs-one-mb-of-void-a-second
     *
     * @param helper the gametest helper
     */
    public static void vacuumCostsOneMbOfVoidASecond(GameTestHelper helper) {
        layTheLane(helper);
        ServerPlayer vacuumer = vacuumer(helper);
        int before = voidHeld(vacuumer);
        holdVacuum(helper, vacuumer, TWO_CHARGES, held -> { });
        helper.runAfterDelay(TWO_CHARGES + 1, () -> {
            int spent = before - voidHeld(vacuumer);
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(spent == 2, String.format(NOT_CHARGED, TWO_CHARGES, spent));
            helper.succeed();
        });
    }

    /**
     * Vacuum held over the lane breaks every goop and slick cell in its cone,
     * and the goo items dropped total what the cells cost: 12 mb of weird goo
     * a goop layer and 12 mb of kinetic goo a slick film.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     * breaking-slick-drops-kinetic-goo-worth-its-film
     *
     * @param helper the gametest helper
     */
    public static void vacuumBreaksResidualsAndReturnsTheirGoo(GameTestHelper helper) {
        layTheLane(helper);
        ServerPlayer vacuumer = vacuumer(helper);
        holdVacuum(helper, vacuumer, WAVE_HOLD, held -> { });
        helper.runAfterDelay(WAVE_HOLD + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            for (int east : LANE.keySet()) {
                BlockState left = helper.getBlockState(laneCell(east));
                helper.assertTrue(left.isAir(), String.format(NOT_CLEARED, east, left));
            }
            int layers = LANE.values().stream().mapToInt(Integer::intValue).sum();
            long films = LANE.values().stream().filter(held -> held == SLICK).count();
            assertDropped(helper, GooTypes.WEIRD, layers * SlimeCoatBlock.MB_PER_LAYER);
            assertDropped(helper, GooTypes.KINETIC, (int) films * KineticSlickBlock.MB_PER_FILM);
            helper.succeed();
        });
    }

    /**
     * Vacuum held over the lane breaks its cells as a wave: each cell turns to
     * air on a later tick than the cell nearer the player, and the farthest
     * within about 10 ticks of the nearest.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     *
     * @param helper the gametest helper
     */
    public static void vacuumBreaksResidualsNearestFirst(GameTestHelper helper) {
        layTheLane(helper);
        ServerPlayer vacuumer = vacuumer(helper);
        Map<Integer, Integer> brokeOn = new LinkedHashMap<>();
        holdVacuum(helper, vacuumer, WAVE_HOLD, held -> {
            for (int east : LANE.keySet()) {
                if (!brokeOn.containsKey(east) && helper.getBlockState(laneCell(east)).isAir()) {
                    brokeOn.put(east, held);
                }
            }
        });
        helper.runAfterDelay(WAVE_HOLD + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(brokeOn.size() == LANE.size(), String.format(NEVER_BROKE, brokeOn));
            List<Integer> ticks = new ArrayList<>();
            LANE.keySet().forEach(east -> ticks.add(brokeOn.get(east)));
            for (int i = 1; i < ticks.size(); i++) {
                helper.assertTrue(ticks.get(i) > ticks.get(i - 1), String.format(OUT_OF_ORDER, brokeOn));
            }
            helper.assertTrue(ticks.getLast() - ticks.getFirst() <= WAVE_TICKS,
                    String.format(TOO_SLOW, WAVE_TICKS, brokeOn));
            helper.succeed();
        });
    }

    /**
     * Vacuum held over the lane leaves a goop cell behind the player, a slick
     * cell past the reach and a stone block inside the cone as they stood.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     *
     * @param helper the gametest helper
     */
    public static void vacuumLeavesWhatIsOutsideItsReach(GameTestHelper helper) {
        layTheLane(helper);
        ServerPlayer vacuumer = vacuumer(helper);
        holdVacuum(helper, vacuumer, WAVE_HOLD, held -> { });
        helper.runAfterDelay(WAVE_HOLD + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            BlockState behind = helper.getBlockState(laneCell(BEHIND));
            helper.assertTrue(SlimeCoatBlock.layersOf(behind) == 1,
                    String.format(NOT_STANDING, "The goop behind the player", behind));
            assertStands(helper, laneCell(PAST_THE_REACH), GooBlocks.KINETIC_SLICK.get());
            assertStands(helper, laneCell(WALL_INSIDE_THE_CONE).south(), Blocks.STONE);
            helper.succeed();
        });
    }

    /**
     * Lays the lane east of the player on a bedrock floor, walled in stone on
     * both sides so no goop pours out of it: the residual cells the lane map
     * names, a slick cell past the reach, and a goop cell of one layer behind
     * the player.
     *
     * @param helper the gametest helper
     */
    private static void layTheLane(GameTestHelper helper) {
        for (int east = BEHIND - 1; east <= PAST_THE_REACH + 1; east++) {
            BlockPos cell = laneCell(east);
            helper.setBlock(cell.below(), Blocks.BEDROCK);
            for (int up = 0; up <= 2; up++) {
                helper.setBlock(cell.above(up), Blocks.AIR);
            }
            helper.setBlock(cell.north(), Blocks.STONE);
            helper.setBlock(cell.south(), Blocks.STONE);
        }
        helper.setBlock(laneCell(BEHIND - 1), Blocks.STONE);
        helper.setBlock(laneCell(BEHIND + 1), Blocks.STONE);
        helper.setBlock(laneCell(PAST_THE_REACH + 1), Blocks.STONE);
        LANE.forEach((east, held) -> helper.setBlock(laneCell(east), held == SLICK
                ? GooBlocks.KINETIC_SLICK.get().defaultBlockState() : slimeOf(held)));
        helper.setBlock(laneCell(PAST_THE_REACH), GooBlocks.KINETIC_SLICK.get());
        helper.setBlock(laneCell(BEHIND), slimeOf(1));
    }

    private static BlockPos laneCell(int east) {
        return STAND.east(east);
    }

    private static BlockState slimeOf(int layers) {
        return GooBlocks.SLIME_COAT.get().defaultBlockState().setValue(SlimeCoatBlock.LAYERS, layers);
    }

    /**
     * A Void invoker standing at the lane's west end facing east, pitched down at the lane.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer vacuumer(GameTestHelper helper) {
        ServerPlayer vacuumer = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND));
        vacuumer.setPos(stand.x, stand.y, stand.z);
        vacuumer.setYRot(FACING_EAST);
        vacuumer.setXRot(AT_THE_LANE);
        return vacuumer;
    }

    /**
     * Holds Vacuum's stream one tick at a time, running a watch after each tick.
     *
     * @param helper   the gametest helper
     * @param vacuumer the player holding it
     * @param ticks    how many ticks to hold
     * @param watch    what runs after each held tick, given the tick's count
     */
    private static void holdVacuum(GameTestHelper helper, ServerPlayer vacuumer, int ticks, Consumer<Integer> watch) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.VOID), VACUUM.toString(),
                vacuumer.getEyePosition(), vacuumer.getEyePosition());
        for (int held = 1; held <= ticks; held++) {
            int count = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(vacuumer, tick);
                watch.accept(count);
            });
        }
    }

    private static int voidHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.VOID, 0);
    }

    private static void assertDropped(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, int owed) {
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM);
        int dropped = drops.stream().map(ItemEntity::getItem).filter(stack -> type.equals(GooStacks.keyOf(stack)))
                .mapToInt(GooStacks::volumeOf).sum();
        helper.assertTrue(dropped == owed,
                String.format(GOO_OWED, owed, type.identifier().getPath(), dropped, drops));
    }

    private static void assertStands(GameTestHelper helper, BlockPos cell, Block stood) {
        BlockState now = helper.getBlockState(cell);
        helper.assertTrue(now.is(stood), String.format(NOT_STANDING, cell, now));
    }
}
