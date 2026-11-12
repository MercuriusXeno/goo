package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.KineticSlickBlock;
import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.HeardSounds;
import com.mercuriusxeno.goo.gametest.PacketRecorder;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * vacuum-pulls-items-to-pickup-and-drags-mobs
 * vacuumed-residuals-decay-through-mingle-noise-before-breaking
 * vacuum-sounds-a-suction-wind-and-rising-slurps
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
    private static final String PULLS_ITEMS = "vacuum_pulls_items_into_the_inventory";
    private static final String DRAGS_A_MOB = "vacuum_drags_a_mob_to_a_block_in_front";
    private static final String LEAVES_PLAYERS_AND_MOBS_BEHIND = "vacuum_leaves_players_and_mobs_behind_unmoved";
    /** The goop cell the item pull's lane holds, its weird goo pulled in once it breaks. */
    private static final int GOOP_TO_PULL = 3;
    /** The stick inside the cone. */
    private static final int ITEM_INSIDE = 5;
    /** Where the dragged husk stands, and the bystanding player, inside the cone. */
    private static final int MOB_INSIDE = 5;
    /** A hold long enough for the goop to break and its goo and the stick to fly in. */
    private static final int PULL_HOLD = 30;
    /** A hold long enough to drag the husk to the stop and leave it standing there a while. */
    private static final int DRAG_HOLD = 40;
    /** The ticks the dragged husk must close on the player, each nearer than the last. */
    private static final int CLOSING_TICKS = 8;
    /** void_vacuum.json's vacuum_pull stop distance: about a block in front of the player. */
    private static final double STOP_DISTANCE = 1.5;
    /** How far past the stop a settled husk may stand, one tick's drag and its friction. */
    private static final double SETTLE_SLACK = 0.3;
    /** A thing that moved less than this stood still. */
    private static final double STILL = 0.05;
    private static final String NOT_PICKED_UP = "The stick in the cone should land in the inventory, %d did, item %s";
    private static final String GOO_NOT_PICKED_UP = "The vacuumed goop's %d mb of weird goo should land in the"
            + " inventory, %d did";
    private static final String MOVED = "%s should not move, was at %s, is at %s";
    private static final String NOT_CLOSING = "The husk should close on the player each tick, distances %s";
    private static final String NOT_SETTLED = "The husk should settle about %.1f blocks from the player, settled %.2f";
    private static final String HURT = "The dragged husk should take no damage, had %.1f, has %.1f";
    private static final String DECAYS_BEFORE_BREAKING = "vacuum_decays_a_residual_before_breaking_it";
    private static final String RELEASED_EARLY = "vacuum_released_early_leaves_a_far_cell_whole";
    /** The goop cell the decay test lays, six blocks out, far enough to decay its whole decay. */
    private static final int DECAYED = 6;
    private static final int DECAYED_LAYERS = 3;
    /** The lane's farthest goop cell. */
    private static final int FAR_GOOP = 7;
    /** A release while the far goop cell is part way decayed, short of nine tenths. */
    private static final int EARLY_RELEASE = 6;
    /** Ticks past a release for an unfinished exposure to cool away to nothing. */
    private static final int COOL_TICKS = 60;
    private static final String SLURPS = "vacuum_slurps_once_at_each_cell_it_takes";
    /** The two goop cells the slurp test lays, near and far along the wave. */
    private static final int SLURPED_NEAR = 3;
    private static final int SLURPED_FAR = 6;
    private static final String NOT_SLURPED = "One slurp should play at %s, %d did; slurps played at %s";
    /** The share past which a held stream's cell finishes alone after a release: nine tenths. */
    private static final float FINISHES_ALONE = 0.9f;
    /** The decay must show on at least this many ticks while the cell stands. */
    private static final int SEVERAL_TICKS = 3;
    private static final String NOT_DECAYED = "The goop cell should decay over at least %d ticks while it stands,"
            + " its shares %s";
    private static final String NOT_PART_DECAYED = "The far goop cell should stand part way decayed at the release,"
            + " its shares %s";
    private static final String NOT_WHOLE = "The far goop cell should stand whole at %d layers after an early"
            + " release, holds %s at share %.2f";

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
        GooTestFunctions.registerTest(registrar, DECAYS_BEFORE_BREAKING, VacuumTests::vacuumDecaysAResidualBeforeBreakingIt);
        GooTestFunctions.registerTest(registrar, RELEASED_EARLY, VacuumTests::vacuumReleasedEarlyLeavesAFarCellWhole);
        GooTestFunctions.registerTest(registrar, SLURPS, VacuumTests::vacuumSlurpsOnceAtEachCellItTakes);
        GooTestFunctions.registerTest(registrar, PULLS_ITEMS, VacuumTests::vacuumPullsItemsIntoTheInventory);
        GooTestFunctions.registerTest(registrar, DRAGS_A_MOB, VacuumTests::vacuumDragsAMobToABlockInFront);
        GooTestFunctions.registerTest(registrar, LEAVES_PLAYERS_AND_MOBS_BEHIND,
                VacuumTests::vacuumLeavesPlayersAndMobsBehindUnmoved);
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
        // the player ticks as its connection would, so the pull's goo lands in the inventory rather than flying on past
        holdVacuum(helper, vacuumer, WAVE_HOLD, held -> vacuumer.doTick());
        helper.runAfterDelay(WAVE_HOLD + 1, () -> {
            for (int east : LANE.keySet()) {
                BlockState left = helper.getBlockState(laneCell(east));
                helper.assertTrue(left.isAir(), String.format(NOT_CLEARED, east, left));
            }
            int layers = LANE.values().stream().mapToInt(Integer::intValue).sum();
            long films = LANE.values().stream().filter(held -> held == SLICK).count();
            assertDropped(helper, vacuumer, GooTypes.WEIRD, layers * SlimeCoatBlock.MB_PER_LAYER);
            assertDropped(helper, vacuumer, GooTypes.KINETIC, (int) films * KineticSlickBlock.MB_PER_FILM);
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
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
        layTheFloor(helper);
        helper.setBlock(laneCell(BEHIND - 1), Blocks.STONE);
        helper.setBlock(laneCell(BEHIND + 1), Blocks.STONE);
        helper.setBlock(laneCell(PAST_THE_REACH + 1), Blocks.STONE);
        LANE.forEach((east, held) -> helper.setBlock(laneCell(east), held == SLICK
                ? GooBlocks.KINETIC_SLICK.get().defaultBlockState() : slimeOf(held)));
        helper.setBlock(laneCell(PAST_THE_REACH), GooBlocks.KINETIC_SLICK.get());
        helper.setBlock(laneCell(BEHIND), slimeOf(1));
    }

    /**
     * Lays the lane's bedrock floor east and west of the player, open above
     * and walled in stone on both sides.
     *
     * @param helper the gametest helper
     */
    private static void layTheFloor(GameTestHelper helper) {
        for (int east = BEHIND - 2; east <= PAST_THE_REACH + 1; east++) {
            BlockPos cell = laneCell(east);
            helper.setBlock(cell.below(), Blocks.BEDROCK);
            for (int up = 0; up <= 2; up++) {
                helper.setBlock(cell.above(up), Blocks.AIR);
            }
            helper.setBlock(cell.north(), Blocks.STONE);
            helper.setBlock(cell.south(), Blocks.STONE);
        }
    }

    /**
     * Vacuum held over a lane holding a stick, a one layer goop cell and a
     * second stick behind the player pulls the stick and the goop's weird goo
     * into the player's inventory, the player ticking as its connection would,
     * and leaves the stick behind lying where it lay.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param helper the gametest helper
     */
    public static void vacuumPullsItemsIntoTheInventory(GameTestHelper helper) {
        layTheFloor(helper);
        helper.setBlock(laneCell(GOOP_TO_PULL), slimeOf(1));
        ItemEntity inside = lieAt(helper, ITEM_INSIDE);
        ItemEntity behind = lieAt(helper, BEHIND);
        ServerPlayer vacuumer = vacuumer(helper);
        Vec3 behindAt = behind.position();
        holdVacuum(helper, vacuumer, PULL_HOLD, held -> vacuumer.doTick());
        helper.runAfterDelay(PULL_HOLD + 1, () -> {
            int sticks = vacuumer.getInventory().countItem(Items.STICK);
            int weird = GooSourceScanner.aggregateAvailable(vacuumer).getOrDefault(GooTypes.WEIRD, 0);
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(sticks == 1, String.format(NOT_PICKED_UP, sticks, inside.isAlive()
                    ? inside.position() : "gone"));
            helper.assertTrue(weird == SlimeCoatBlock.MB_PER_LAYER,
                    String.format(GOO_NOT_PICKED_UP, SlimeCoatBlock.MB_PER_LAYER, weird));
            helper.assertTrue(behind.isAlive() && behind.position().distanceTo(behindAt) < STILL,
                    String.format(MOVED, "The stick behind the player", behindAt, behind.position()));
            helper.succeed();
        });
    }

    /**
     * Vacuum held at a husk standing in its cone drags it toward the player:
     * its distance falls tick over tick, it settles no nearer than the stop
     * distance, and its health stays whole.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param helper the gametest helper
     */
    public static void vacuumDragsAMobToABlockInFront(GameTestHelper helper) {
        layTheFloor(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, laneCell(MOB_INSIDE));
        float health = husk.getHealth();
        ServerPlayer vacuumer = vacuumer(helper);
        List<Double> distances = new ArrayList<>();
        holdVacuum(helper, vacuumer, DRAG_HOLD, held -> distances.add(levelDistance(husk, vacuumer)));
        helper.runAfterDelay(DRAG_HOLD + 1, () -> {
            double settled = levelDistance(husk, vacuumer);
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            for (int i = 1; i <= CLOSING_TICKS; i++) {
                helper.assertTrue(distances.get(i) < distances.get(i - 1), String.format(NOT_CLOSING, distances));
            }
            helper.assertTrue(settled >= STOP_DISTANCE - STILL && settled <= STOP_DISTANCE + SETTLE_SLACK,
                    String.format(NOT_SETTLED, STOP_DISTANCE, settled));
            helper.assertTrue(husk.getHealth() == health, String.format(HURT, health, husk.getHealth()));
            helper.succeed();
        });
    }

    /**
     * Vacuum held with a second player standing inside its cone and a husk
     * behind the caster moves neither, nor gives either any motion.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void vacuumLeavesPlayersAndMobsBehindUnmoved(GameTestHelper helper) {
        layTheFloor(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, laneCell(BEHIND));
        ServerPlayer bystander = helper.makeMockServerPlayerInLevel();
        Vec3 inCone = Vec3.atBottomCenterOf(helper.absolutePos(laneCell(MOB_INSIDE)));
        bystander.setPos(inCone.x, inCone.y, inCone.z);
        ServerPlayer vacuumer = vacuumer(helper);
        Vec3 huskAt = husk.position();
        holdVacuum(helper, vacuumer, DRAG_HOLD, held -> { });
        helper.runAfterDelay(DRAG_HOLD + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.getLevel().getServer().getPlayerList().remove(bystander);
            helper.assertTrue(level(husk.position()).distanceTo(level(huskAt)) < STILL,
                    String.format(MOVED, "The husk behind the caster", huskAt, husk.position()));
            helper.assertTrue(bystander.position().distanceTo(inCone) < STILL
                            && level(bystander.getDeltaMovement()).length() < STILL,
                    String.format(MOVED, "The player in the cone", inCone, bystander.position()));
            helper.succeed();
        });
    }

    /**
     * Vacuum held over a goop cell decays it before it breaks: the cell's
     * exposure toward nothing builds over several ticks while the cell still
     * stands, and then the cell is air and its weird goo dropped.
     * vacuumed-residuals-decay-through-mingle-noise-before-breaking
     *
     * @param helper the gametest helper
     */
    public static void vacuumDecaysAResidualBeforeBreakingIt(GameTestHelper helper) {
        layTheFloor(helper);
        BlockPos cell = laneCell(DECAYED);
        helper.setBlock(cell, slimeOf(DECAYED_LAYERS));
        ServerPlayer vacuumer = vacuumer(helper);
        List<Float> sharesWhileStanding = new ArrayList<>();
        holdVacuum(helper, vacuumer, WAVE_HOLD, held -> {
            vacuumer.doTick();
            float share = exposureAt(helper, cell);
            if (!helper.getBlockState(cell).isAir() && share > 0f) {
                sharesWhileStanding.add(share);
            }
        });
        helper.runAfterDelay(WAVE_HOLD + 1, () -> {
            helper.assertTrue(sharesWhileStanding.size() >= SEVERAL_TICKS,
                    String.format(NOT_DECAYED, SEVERAL_TICKS, sharesWhileStanding));
            for (int i = 1; i < sharesWhileStanding.size(); i++) {
                helper.assertTrue(sharesWhileStanding.get(i) > sharesWhileStanding.get(i - 1),
                        String.format(NOT_DECAYED, SEVERAL_TICKS, sharesWhileStanding));
            }
            BlockState left = helper.getBlockState(cell);
            helper.assertTrue(left.isAir(), String.format(NOT_CLEARED, DECAYED, left));
            assertDropped(helper, vacuumer, GooTypes.WEIRD, DECAYED_LAYERS * SlimeCoatBlock.MB_PER_LAYER);
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.succeed();
        });
    }

    /**
     * Vacuum released before a far goop cell has decayed nine tenths of the
     * way leaves the cell whole, holding every layer it held, once its
     * exposure has cooled away.
     * vacuumed-residuals-decay-through-mingle-noise-before-breaking
     *
     * @param helper the gametest helper
     */
    public static void vacuumReleasedEarlyLeavesAFarCellWhole(GameTestHelper helper) {
        layTheLane(helper);
        BlockPos far = laneCell(FAR_GOOP);
        ServerPlayer vacuumer = vacuumer(helper);
        List<Float> shares = new ArrayList<>();
        holdVacuum(helper, vacuumer, EARLY_RELEASE, held -> shares.add(exposureAt(helper, far)));
        helper.runAfterDelay(EARLY_RELEASE + COOL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            float atRelease = shares.getLast();
            helper.assertTrue(atRelease > 0f && atRelease < FINISHES_ALONE,
                    String.format(NOT_PART_DECAYED, shares));
            BlockState stood = helper.getBlockState(far);
            helper.assertTrue(SlimeCoatBlock.layersOf(stood) == LANE.get(FAR_GOOP),
                    String.format(NOT_WHOLE, LANE.get(FAR_GOOP), stood, exposureAt(helper, far)));
            helper.succeed();
        });
    }

    /**
     * Vacuum held over two goop cells plays one slurp at each cell as it takes it.
     * vacuum-sounds-a-suction-wind-and-rising-slurps
     *
     * @param helper the gametest helper
     */
    public static void vacuumSlurpsOnceAtEachCellItTakes(GameTestHelper helper) {
        layTheFloor(helper);
        List<BlockPos> cells = List.of(laneCell(SLURPED_NEAR), laneCell(SLURPED_FAR));
        cells.forEach(cell -> helper.setBlock(cell, slimeOf(1)));
        ServerPlayer vacuumer = vacuumer(helper);
        PacketRecorder heard = PacketRecorder.attachTo(vacuumer);
        holdVacuum(helper, vacuumer, WAVE_HOLD, held -> { });
        helper.runAfterDelay(WAVE_HOLD + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            for (BlockPos cell : cells) {
                Vec3 middle = Vec3.atCenterOf(helper.absolutePos(cell));
                long slurps = HeardSounds.at(heard, GooSoundIds.VACUUM_SLURP, middle);
                helper.assertTrue(slurps == 1, String.format(NOT_SLURPED, middle, slurps,
                        HeardSounds.describe(HeardSounds.of(heard, GooSoundIds.VACUUM_SLURP))));
            }
            helper.succeed();
        });
    }

    private static float exposureAt(GameTestHelper helper, BlockPos cell) {
        return GooServerState.of(helper.getLevel().getServer()).blockExposures()
                .shareAt(helper.getLevel(), helper.absolutePos(cell));
    }

    private static ItemEntity lieAt(GameTestHelper helper, int east) {
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(laneCell(east)));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.STICK));
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    private static double levelDistance(Mob mob, ServerPlayer player) {
        return level(mob.position()).distanceTo(level(player.position()));
    }

    private static Vec3 level(Vec3 point) {
        return new Vec3(point.x, 0, point.z);
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

    /**
     * Asserts the goo of a type standing as item entities and in the player's
     * inventory, where the pull lands what it reaches, totals what is owed.
     */
    private static void assertDropped(GameTestHelper helper, ServerPlayer player, ResourceKey<GooTypeDefinition> type,
                                      int owed) {
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM);
        int dropped = drops.stream().map(ItemEntity::getItem).filter(stack -> type.equals(GooStacks.keyOf(stack)))
                .mapToInt(GooStacks::volumeOf).sum() + GooSourceScanner.aggregateAvailable(player).getOrDefault(type, 0);
        helper.assertTrue(dropped == owed,
                String.format(GOO_OWED, owed, type.identifier().getPath(), dropped, drops.stream()
                        .map(drop -> GooStacks.volumeOf(drop.getItem())).toList()
                        + " lying, inventory " + GooSourceScanner.aggregateAvailable(player)));
    }

    private static void assertStands(GameTestHelper helper, BlockPos cell, Block stood) {
        BlockState now = helper.getBlockState(cell);
        helper.assertTrue(now.is(stood), String.format(NOT_STANDING, cell, now));
    }
}
