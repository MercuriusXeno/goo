package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.program.VacuumPullStep;
import com.mercuriusxeno.goo.ability.program.VacuumStep;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.HeardSounds;
import com.mercuriusxeno.goo.gametest.PacketRecorder;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
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
 * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
 * vacuum-throws-three-break-particles-a-layer
 * vacuum-sounds-a-suction-wind-and-rising-slurps
 */
@RegistersGameTests
public final class VacuumTests {

    private static final Identifier VACUUM = Identifier.parse("goo:void_vacuum");
    private static final String SPENDS_VOID_WHILE_HELD = "vacuum_spends_void_while_held";
    private static final String RETURNS_THE_GOO = "vacuum_breaks_residuals_and_returns_their_goo";
    private static final String RETURNS_THE_VOLUME = "vacuum_returns_an_attached_blocks_whole_volume";
    /** The lane cell the attached block stands in, inside the vacuum's reach. */
    private static final int ATTACHED_EAST = 3;
    private static final int ATTACHED_VOLUME = 6;
    private static final String VOLUME_OWED = "Vacuuming the attached block should return %d mb of weird goo,"
            + " returned %d";
    private static final String TAKES_A_LAYER_EACH_PACE = "vacuum_takes_a_layer_each_time_its_pace_runs";
    private static final String LEAVES_THE_REST = "vacuum_leaves_what_is_outside_its_reach";
    private static final String BREAKS_INSIDE_ITS_CONE = "vacuum_breaks_inside_its_forty_five_degree_cone";
    /** Where the player stands, the lane running east from it across the light bay. */
    private static final BlockPos STAND = new BlockPos(3, 1, 8);
    private static final float FACING_EAST = -90f;
    /**
     * Pitched down so the cone, 22.5 degrees each side of the look, spans the lane from the cell beside the
     * player, near 48 degrees below level from the eye, to the cell 7 blocks out, near 9 degrees below, and
     * reaches the middle of the husk the drag test stands 6 blocks out, near 6 degrees below.
     */
    private static final float AT_THE_LANE = 27f;
    /** Each lane cell east of the player and what it holds: slick, or goop of that many layers. */
    private static final Map<Integer, Integer> LANE = laneOfResiduals();
    /** A slick film, in the lane's map, against a goop cell's layer count. */
    private static final int SLICK = 0;
    /** The lane's goop cell 7 blocks out, inside the 8 block reach. */
    private static final int SEVEN_OUT = 7;
    /** The slick past the reach, its middle just over 8 blocks from the eye, east of the lane's last goop cell. */
    private static final int PAST_THE_REACH = 8;
    /** A standing player's eye height over its feet. */
    private static final double EYE_HEIGHT = 1.62;
    /** The goop cell behind the player. */
    private static final int BEHIND = -2;
    /** The lane's row beside which stone walls keep goop from pouring sideways, one wall stone inside the cone. */
    private static final int WALL_INSIDE_THE_CONE = 4;
    private static final String NOT_CHARGED = "A hold of %d ticks should spend void goo, spent %d";
    private static final String NOT_CLEARED = "The lane cell %d east should be air after the hold, holds %s";
    private static final String GOO_OWED = "The lane should drop %s goo, dropped %d in %s";
    private static final String NOT_STANDING = "%s should still stand after the hold, holds %s";
    private static final String PULLS_ITEMS = "vacuum_pulls_items_into_the_inventory";
    private static final String DRAGS_A_MOB = "vacuum_drags_a_mob_to_a_block_in_front";
    private static final String LEAVES_PLAYERS_AND_MOBS_BEHIND = "vacuum_leaves_players_and_mobs_behind_unmoved";
    /** The goop cell the item pull's lane holds, its weird goo pulled in once it breaks. */
    private static final int GOOP_TO_PULL = 3;
    /** The stick inside the cone. */
    private static final int ITEM_INSIDE = 5;
    /** Where the bystanding player stands, inside the cone. */
    private static final int MOB_INSIDE = 5;
    /** A hold long enough for the goop to break some fifteen ticks in, and its goo and the stick to fly in. */
    private static final int PULL_HOLD = 40;
    /** A hold long enough to drag the husk to the stop and leave it standing there a while. */
    private static final int DRAG_HOLD = 40;
    /** The ticks the dragged husk must close on the player, each nearer than the last. */
    private static final int CLOSING_TICKS = 8;
    /** A thing that moved less than this stood still. */
    private static final double STILL = 0.05;
    private static final String NOT_PICKED_UP = "The stick in the cone should land in the inventory, %d did, item %s";
    private static final String GOO_NOT_PICKED_UP = "The vacuumed goop's weird goo should land in the inventory, %d"
            + " mb did";
    private static final String MOVED = "%s should not move, was at %s, is at %s";
    private static final String ACCELERATES_FROM_REST = "vacuum_accelerates_an_item_from_rest";
    private static final String GRAVITY_OFF_WHILE_PULLED = "vacuum_pulls_an_item_with_its_gravity_off";
    /** The stick 7 blocks out, near the cone's far end, still in flight a good while into a hold. */
    private static final int ITEM_FAR = 7;
    /** Where the husk the drag test pulls stands, 6 blocks out toward the cone's far end. */
    private static final int MOB_FAR = 6;
    /** A hold that ends while the far stick is still in flight. */
    private static final int IN_FLIGHT_HOLD = 6;
    /** Rounding a speed may carry past the acceleration on the first pulled tick. */
    private static final double SPEED_SLACK = 1e-6;
    private static final String STARTS_FAST = "The stick's first pulled tick should move it toward the eye no"
            + " faster than %.3f, speeds %s";
    private static final String NOT_SPEEDING_UP = "The stick should speed toward the eye tick over tick until the"
            + " top speed, speeds %s";
    private static final String GRAVITY_ON_TICK = "tick %d: stick in the cone no gravity %b, stick behind no gravity %b";
    private static final String GRAVITY_WRONG = "The stick in the cone should fly with its gravity off and the stick"
            + " behind keep it: %s";
    private static final String NOT_IN_FLIGHT = "The stick in the cone should still be in flight after the release";
    private static final String NOT_NEARER = "The stick in the cone should end the hold nearer the player than it"
            + " began, %.2f blocks from the eye then, %.2f now";
    private static final String GRAVITY_NOT_BACK = "The stick's gravity should be on again once the pull's grace"
            + " ran out";
    private static final String NOT_CLOSING = "The husk should close on the player each tick, distances %s";
    private static final String HURT = "The dragged husk should take no damage, had %.1f, has %.1f";
    private static final String RELEASED_PART_WAY = "vacuum_released_part_way_keeps_its_count";
    private static final String RELEASED_PAST_THE_MEMORY = "vacuum_released_past_the_memory_counts_from_zero";
    /** The lane cell six blocks out that the single cell tests lay their goo in. */
    private static final int DECAYED = 6;
    /** The full goop cell the pace test lays beside the player, its middle within 2 blocks of the eye. */
    private static final int BESIDE = 1;
    /** The shallow goop cell the pace test lays 7 blocks out, near the reach's far end. */
    private static final int NEAR_THE_END = 7;
    /** A full cell's layers. */
    private static final int FULL = VolumeCellBlock.MAX_LAYERS;
    /** A shallow cell's layers. */
    private static final int SHALLOW = 3;
    /** The ticks a layer beside the player takes, the near end's pace. */
    private static final int BESIDE_PACE = 4;
    /** The deepest lane cell's layers, which the lane's holds wait out at the slowest pace. */
    private static final int LANE_DEEPEST = 5;
    /** The ticks the release tests hold before letting go: one layer taken and a tick counted on. */
    private static final int PART_WAY = 5;
    /** The ticks of its pace a cell beside the player has left to run once the first hold lets go. */
    private static final int TICKS_LEFT = 2 * BESIDE_PACE - PART_WAY;
    /** The ticks after the first hold's last that the release test inside the memory holds again. */
    private static final int BACK_INSIDE_THE_MEMORY = 10;
    /** The ticks after the first hold's last that the release test past the memory holds again. */
    private static final int BACK_PAST_THE_MEMORY = 30;
    private static final String RETURNS_THE_LAYERS_PRICE = "vacuum_returns_a_slime_cells_layers_price";
    /** Past the eight layers a cell held before a cell held fifteen. */
    private static final int VACUUMED_LAYERS = 11;
    private static final String LAYERS_PRICE_WRONG = "The vacuumed slime cell should return %d mb of weird goo,"
            + " returned %d";
    private static final String RETURNS_THE_SLICKS_PRICE = "vacuum_returns_a_slick_cells_layers_price";
    private static final String SLICKS_PRICE_WRONG = "The vacuumed slick cell should return %d mb of kinetic goo,"
            + " returned %d";
    private static final String SLURPS = "vacuum_slurps_once_at_each_cell_it_takes";
    /** The two goop cells the slurp test lays, near and far along the reach. */
    private static final int SLURPED_NEAR = 3;
    private static final int SLURPED_FAR = 6;
    /** The layers each slurped cell holds, so each slurps on the tick its last layer goes and on no earlier one. */
    private static final int SLURPED_LAYERS = 4;
    private static final String NOT_SLURPED = "One slurp should play at %s, %d did; slurps played at %s";
    private static final String LAYER_STATE = "hold tick %d: %d east holds %s, owed %d layers";
    private static final String NOT_ON_ITS_PACE = "Each goop cell should lose one layer on each multiple of its pace,"
            + " the near cell's %d and the far cell's %d, and on no other tick: %s";
    private static final String PACE_WRONG = "A layer should take %d ticks beside the player and more than that near"
            + " the reach's end, take %d and %d";
    private static final String NOT_LEFT = "The goop cell should stand at %d layers after a release part way, holds %s";
    private static final String TAKEN_NOT_RETURNED = "The layer taken before the release should return %d mb of weird"
            + " goo, returned %d";
    private static final String COUNT_NOT_KEPT = "Held again inside the memory, the cell should lose its next layer"
            + " once the ticks left of its pace run: %s";
    private static final String COUNT_KEPT = "Held again past the memory, the cell should count from zero: %s";
    /** The cone test's goop cell inside the cone, its middle 15 degrees off the look. */
    private static final BlockPos INSIDE_THE_CONE = new BlockPos(8, 1, 11);
    /**
     * The cone test's goop cell outside the cone, across the lane, near 26 degrees off the look: past the 45
     * degree cone's 22.5 and inside the 30 a 60 degree cone reached.
     */
    private static final BlockPos OUTSIDE_THE_CONE = new BlockPos(8, 1, 7);
    private static final double INSIDE_DEGREES = 15;
    /** The open floor's reach north and south of the stand, past the slabs beside the cone test's cells. */
    private static final int OPEN_FLOOR_HALF_WIDTH = 4;
    /** The cone's half angle, 22.5 degrees each side of the look; the cell outside it must stand past this. */
    private static final double HALF_CONE_DEGREES = 22.5;
    private static final String NOT_OFF_THE_LOOK = "The cells should stand %.0f and over %.0f degrees off the look,"
            + " stand %.2f and %.2f";
    private static final String INSIDE_STANDS = "The goop cell %.2f degrees off the look should be air after the"
            + " hold, holds %s";
    private static final String OUTSIDE_BROKE = "The goop cell %.2f degrees off the look should stand as laid after"
            + " the hold, holds %s";

    private static final String BREAKS_EVERY_WORLD_GOO = "vacuum_breaks_every_goo_on_the_volume_cell";
    /** The ticks between one world goo's hold and the next, past the gap a hold continues across. */
    private static final int BETWEEN_HOLDS = StreamHolds.LATEST_CONTINUING_GAP + 2;
    private static final String NO_WORLD_GOO = "The block registry should hold a goo on the volume cell";
    private static final String WORLD_GOO_LEFT = "%s should break under Vacuum and return %d mb of %s, the cell"
            + " holds %s and %d mb came back";
    private static final String WORLD_GOOS_LEFT = "Every goo on the volume cell should break and return its layers'"
            + " goo: %s";

    private static final String THREE_PARTICLES_PER_LAYER = "vacuum_throws_three_break_particles_per_layer";
    /** The break particles a vacuumed cell throws for each layer it held. */
    private static final int PARTICLES_PER_LAYER = 3;
    private static final String PARTICLES_WRONG = "The vacuumed cell of %d layers should throw %d break particles,"
            + " threw %d: %d through vanilla's break event, %d sent";
    /** The vanilla break event that bursts a block's break particles on every client. */
    private static final int BREAK_PARTICLES_EVENT = 2001;
    /** The side of the grid vanilla's break burst lays its particles on, in blocks. */
    private static final double BURST_GRID = 0.25;
    /** The fewest particles vanilla's break burst lays along each side of a box. */
    private static final int BURST_FEWEST = 2;

    private VacuumTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPENDS_VOID_WHILE_HELD, VacuumTests::vacuumSpendsVoidWhileHeld);
        GooTestFunctions.registerTest(registrar, RETURNS_THE_GOO, VacuumTests::vacuumBreaksResidualsAndReturnsTheirGoo);
        GooTestFunctions.registerTest(registrar, RETURNS_THE_VOLUME, VacuumTests::vacuumReturnsAnAttachedBlocksWholeVolume);
        GooTestFunctions.registerTest(registrar, TAKES_A_LAYER_EACH_PACE,
                VacuumTests::vacuumTakesALayerEachTimeItsPaceRuns);
        GooTestFunctions.registerTest(registrar, LEAVES_THE_REST, VacuumTests::vacuumLeavesWhatIsOutsideItsReach);
        GooTestFunctions.registerTest(registrar, BREAKS_INSIDE_ITS_CONE,
                VacuumTests::vacuumBreaksInsideItsFortyFiveDegreeCone);
        GooTestFunctions.registerTest(registrar, RELEASED_PART_WAY, VacuumTests::vacuumReleasedPartWayKeepsItsCount);
        GooTestFunctions.registerTest(registrar, RELEASED_PAST_THE_MEMORY,
                VacuumTests::vacuumReleasedPastTheMemoryCountsFromZero);
        GooTestFunctions.registerTest(registrar, SLURPS, VacuumTests::vacuumSlurpsOnceAtEachCellItTakes);
        GooTestFunctions.registerTest(registrar, PULLS_ITEMS, VacuumTests::vacuumPullsItemsIntoTheInventory);
        GooTestFunctions.registerTest(registrar, ACCELERATES_FROM_REST, VacuumTests::vacuumAcceleratesAnItemFromRest);
        GooTestFunctions.registerTest(registrar, GRAVITY_OFF_WHILE_PULLED, VacuumTests::vacuumPullsAnItemWithItsGravityOff);
        GooTestFunctions.registerTest(registrar, DRAGS_A_MOB, VacuumTests::vacuumDragsAMobToABlockInFront);
        GooTestFunctions.registerTest(registrar, LEAVES_PLAYERS_AND_MOBS_BEHIND,
                VacuumTests::vacuumLeavesPlayersAndMobsBehindUnmoved);
        GooTestFunctions.registerTest(registrar, RETURNS_THE_LAYERS_PRICE, VacuumTests::vacuumReturnsASlimeCellsLayersPrice);
        GooTestFunctions.registerTest(registrar, RETURNS_THE_SLICKS_PRICE, VacuumTests::vacuumReturnsASlickCellsLayersPrice);
        GooTestFunctions.registerTest(registrar, BREAKS_EVERY_WORLD_GOO, VacuumTests::vacuumBreaksEveryGooOnTheVolumeCell);
        GooTestFunctions.registerTest(registrar, THREE_PARTICLES_PER_LAYER,
                VacuumTests::vacuumThrowsThreeBreakParticlesPerLayer);
    }

    /**
     * Vacuum held over one slime cell of eleven layers throws three break
     * particles for each layer it takes, counting both the particles sent and
     * the burst each vanilla break event lays on a client.
     *
     * @param helper the gametest helper
     */
    public static void vacuumThrowsThreeBreakParticlesPerLayer(GameTestHelper helper) {
        layTheFloor(helper);
        helper.setBlock(laneCell(DECAYED - 1).below(), Blocks.STONE_SLAB);
        helper.setBlock(laneCell(DECAYED + 1).below(), Blocks.STONE_SLAB);
        BlockPos cell = laneCell(DECAYED);
        helper.setBlock(cell, slimeOf(VACUUMED_LAYERS));
        ServerPlayer vacuumer = vacuumer(helper);
        PacketRecorder seen = PacketRecorder.attachTo(vacuumer);
        int hold = drainHold(helper, DECAYED, VACUUMED_LAYERS);
        holdVacuum(helper, vacuumer, hold, held -> { });
        helper.runAfterDelay(hold + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            BlockPos at = helper.absolutePos(cell);
            int burst = seen.sentOf(ClientboundLevelEventPacket.class).stream()
                    .filter(event -> event.getType() == BREAK_PARTICLES_EVENT && event.getPos().equals(at))
                    .mapToInt(event -> vanillaBurstOf(helper, Block.stateById(event.getData()), at)).sum();
            int sent = seen.sentOf(ClientboundLevelParticlesPacket.class).stream()
                    .filter(particles -> particles.getParticle() instanceof BlockParticleOption
                            && BlockPos.containing(particles.getX(), particles.getY(), particles.getZ()).equals(at))
                    .mapToInt(ClientboundLevelParticlesPacket::getCount).sum();
            int owed = VACUUMED_LAYERS * PARTICLES_PER_LAYER;
            helper.assertTrue(burst + sent == owed, String.format(PARTICLES_WRONG, VACUUMED_LAYERS, owed,
                    burst + sent, burst, sent));
            helper.succeed();
        });
    }

    /**
     * The break particles vanilla's break event lays on a client for a state:
     * a grid over each box of its shape, a quarter block apart and at least
     * two along each side.
     */
    private static int vanillaBurstOf(GameTestHelper helper, BlockState state, BlockPos at) {
        int[] particles = new int[1];
        state.getShape(helper.getLevel(), at).forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                particles[0] += burstAlong(maxX - minX) * burstAlong(maxY - minY) * burstAlong(maxZ - minZ));
        return particles[0];
    }

    private static int burstAlong(double side) {
        return Math.max(BURST_FEWEST, (int) Math.ceil(Math.min(1, side) / BURST_GRID));
    }

    /**
     * Vacuum held in turn over one cell of each goo on the volume cell the
     * block registry holds, eleven layers deep beside open cells it cannot
     * pour into, breaks each to air and returns the goo its layers cost, so a
     * world goo shipping later joins this test with no edit here.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     *
     * @param helper the gametest helper
     */
    public static void vacuumBreaksEveryGooOnTheVolumeCell(GameTestHelper helper) {
        List<VolumeCellBlock> worldGoos = BuiltInRegistries.BLOCK.stream().filter(VolumeCellBlock.class::isInstance)
                .map(VolumeCellBlock.class::cast).toList();
        helper.assertTrue(!worldGoos.isEmpty(), NO_WORLD_GOO);
        layTheFloor(helper);
        helper.setBlock(laneCell(DECAYED - 1).below(), Blocks.STONE_SLAB);
        helper.setBlock(laneCell(DECAYED + 1).below(), Blocks.STONE_SLAB);
        // Molten clings to the cell's walls and cooks stone (molten-heats-every-block-its-cell-touches), and Vines
        // spends a layer on each wall a vine can hang on (vines-goo-on-walls-places-goologged-vines-and-depletes), so
        // iron bars, which neither melt nor cook and whose sides hold no vine, wall it and every layer stands for Vacuum
        helper.setBlock(laneCell(DECAYED).north(), Blocks.IRON_BARS);
        helper.setBlock(laneCell(DECAYED).south(), Blocks.IRON_BARS);
        ServerPlayer vacuumer = vacuumer(helper);
        List<String> left = new ArrayList<>();
        int turn = drainHold(helper, DECAYED, VACUUMED_LAYERS) + 1 + BETWEEN_HOLDS;
        for (int i = 0; i < worldGoos.size(); i++) {
            vacuumOneWorldGoo(helper, vacuumer, worldGoos.get(i), i * turn, left::add);
        }
        helper.runAfterDelay((long) worldGoos.size() * turn, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(left.isEmpty(), String.format(WORLD_GOOS_LEFT, left));
            helper.succeed();
        });
    }

    /**
     * Lays one world goo's cell at a tick, holds Vacuum over it, and reports
     * the cell where it stands afterward or returned other than its layers' goo.
     */
    private static void vacuumOneWorldGoo(GameTestHelper helper, ServerPlayer vacuumer, VolumeCellBlock worldGoo,
                                          int from, Consumer<String> report) {
        BlockPos cell = laneCell(DECAYED);
        ResourceKey<GooTypeDefinition> type = worldGoo.binding().gooType();
        int[] before = new int[1];
        helper.runAfterDelay(from, () -> {
            helper.setBlock(cell, worldGoo.defaultBlockState().setValue(VolumeCellBlock.LAYERS, VACUUMED_LAYERS));
            before[0] = gooReturned(helper, vacuumer, type);
        });
        int hold = drainHold(helper, DECAYED, VACUUMED_LAYERS);
        holdVacuum(helper, vacuumer, from, hold, held -> vacuumer.doTick());
        helper.runAfterDelay(from + hold + 1, () -> {
            BlockState stood = helper.getBlockState(cell);
            int returned = gooReturned(helper, vacuumer, type) - before[0];
            int owed = ResidualPrices.worldGooRefund(helper, worldGoo, VACUUMED_LAYERS);
            if (!stood.isAir() || returned != owed) {
                report.accept(String.format(WORLD_GOO_LEFT, BuiltInRegistries.BLOCK.getKey(worldGoo), owed,
                        type.identifier().getPath(), stood, returned));
            }
        });
    }

    /**
     * Vacuum held over one slick cell of eleven layers, beside open cells it
     * cannot pour into, takes it a layer at a time and returns, lying or pulled
     * into the inventory, the kinetic goo eleven layers cost, each a
     * fifteenth of Slick's cost, as breaking the cell whole returns.
     * breaking-slick-drops-kinetic-goo-worth-its-film
     *
     * @param helper the gametest helper
     */
    public static void vacuumReturnsASlickCellsLayersPrice(GameTestHelper helper) {
        layTheFloor(helper);
        // open cells east and west over bottom slabs, whose tops hold no slick, keep the layers in their cell
        // and leave the line from the eye clear
        helper.setBlock(laneCell(DECAYED - 1).below(), Blocks.STONE_SLAB);
        helper.setBlock(laneCell(DECAYED + 1).below(), Blocks.STONE_SLAB);
        helper.setBlock(laneCell(DECAYED), GooBlocks.KINETIC_SLICK.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VACUUMED_LAYERS));
        ServerPlayer vacuumer = vacuumer(helper);
        int hold = drainHold(helper, DECAYED, VACUUMED_LAYERS);
        holdVacuum(helper, vacuumer, hold, held -> vacuumer.doTick());
        helper.runAfterDelay(hold + 1, () -> {
            int returned = gooReturned(helper, vacuumer, GooTypes.KINETIC);
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            int owed = ResidualPrices.slickLayersRefund(helper, VACUUMED_LAYERS);
            helper.assertTrue(returned == owed, String.format(SLICKS_PRICE_WRONG, owed, returned));
            helper.succeed();
        });
    }

    /**
     * Vacuum held over one slime cell of eleven layers, beside open cells it
     * cannot pour into, takes it a layer at a time and returns, lying or pulled
     * into the inventory, the weird goo eleven layers cost, each a fifteenth
     * of Goop's cost: a layer's price is fractional, so the layers sum to
     * what breaking the cell whole returns only where no layer's mb is lost.
     * breaking-slime-drops-weird-goo-worth-its-layers
     *
     * @param helper the gametest helper
     */
    public static void vacuumReturnsASlimeCellsLayersPrice(GameTestHelper helper) {
        layTheFloor(helper);
        // open cells east and west over bottom slabs, whose tops hold no slime, keep the layers in their cell
        // and leave the line from the eye clear
        helper.setBlock(laneCell(DECAYED - 1).below(), Blocks.STONE_SLAB);
        helper.setBlock(laneCell(DECAYED + 1).below(), Blocks.STONE_SLAB);
        helper.setBlock(laneCell(DECAYED), slimeOf(VACUUMED_LAYERS));
        ServerPlayer vacuumer = vacuumer(helper);
        int hold = drainHold(helper, DECAYED, VACUUMED_LAYERS);
        holdVacuum(helper, vacuumer, hold, held -> vacuumer.doTick());
        helper.runAfterDelay(hold + 1, () -> {
            int returned = gooReturned(helper, vacuumer, GooTypes.WEIRD);
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            int owed = ResidualPrices.layersRefund(helper, VACUUMED_LAYERS);
            helper.assertTrue(returned == owed, String.format(LAYERS_PRICE_WRONG, owed, returned));
            helper.succeed();
        });
    }

    private static Map<Integer, Integer> laneOfResiduals() {
        // slick between goop cells, so no goop cell pours into another
        Map<Integer, Integer> lane = new LinkedHashMap<>();
        lane.put(2, SLICK);
        lane.put(3, 3);
        lane.put(4, SLICK);
        lane.put(5, 5);
        lane.put(6, SLICK);
        lane.put(SEVEN_OUT, 1);
        return lane;
    }

    /**
     * Vacuum held for two charges' worth of ticks spends void goo.
     * vacuum-costs-one-mb-of-void-a-second
     *
     * @param helper the gametest helper
     */
    public static void vacuumSpendsVoidWhileHeld(GameTestHelper helper) {
        layTheLane(helper);
        ServerPlayer vacuumer = vacuumer(helper);
        int before = voidHeld(vacuumer);
        int twoCharges = 2 * vacuum(helper).delivery().ticksPerCharge();
        holdVacuum(helper, vacuumer, twoCharges, held -> { });
        helper.runAfterDelay(twoCharges + 1, () -> {
            int spent = before - voidHeld(vacuumer);
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(spent > 0, String.format(NOT_CHARGED, twoCharges, spent));
            helper.succeed();
        });
    }

    /**
     * Vacuum held over the lane breaks every goop and slick cell in its reach,
     * and drops weird goo for the goop and kinetic goo for the slick.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     * breaking-slick-drops-kinetic-goo-worth-its-film
     *
     * @param helper the gametest helper
     */
    public static void vacuumBreaksResidualsAndReturnsTheirGoo(GameTestHelper helper) {
        layTheLane(helper);
        ServerPlayer vacuumer = vacuumer(helper);
        // a slick cell empties before the goop beside it, which pours a layer into the emptied cell, and that
        // layer is taken in its turn, so the hold runs two lane holds long
        int hold = 2 * laneHold(helper);
        // the player ticks as its connection would, so the pull's goo lands in the inventory rather than flying on past
        holdVacuum(helper, vacuumer, hold, held -> vacuumer.doTick());
        helper.runAfterDelay(hold + 1, () -> {
            for (int east : laneInReach(helper)) {
                BlockState left = helper.getBlockState(laneCell(east));
                helper.assertTrue(left.isAir(), String.format(NOT_CLEARED, east, left));
            }
            assertDropped(helper, vacuumer, GooTypes.WEIRD);
            assertDropped(helper, vacuumer, GooTypes.KINETIC);
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.succeed();
        });
    }

    /**
     * A block of Goop of volume 6 attached to the stone wall north of it,
     * walled in by stone and, on the vacuum's side, an open cell over a
     * bottom slab, so nothing pours, is vacuumed a layer at a time, and the goo
     * returned is six layers' worth: the volume is all the goo the block holds.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     *
     * @param helper the gametest helper
     */
    public static void vacuumReturnsAnAttachedBlocksWholeVolume(GameTestHelper helper) {
        layTheFloor(helper);
        BlockPos block = laneCell(ATTACHED_EAST);
        helper.setBlock(block.north(), Blocks.STONE);
        helper.setBlock(block.south(), Blocks.STONE);
        helper.setBlock(block.east(), Blocks.STONE);
        // an open cell on the vacuum's side over a bottom slab, whose top holds no goop, takes no pour and leaves the
        // line from the eye clear; a slick there broke before the block and let a layer pour in
        helper.setBlock(block.west().below(), Blocks.STONE_SLAB);
        helper.setBlock(block, GooBlocks.SLIME_COAT.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, ATTACHED_VOLUME)
                .setValue(VolumeCellBlock.coatProperty(Direction.NORTH), SideCoat.ATTACHED));
        ServerPlayer vacuumer = vacuumer(helper);
        int before = gooReturned(helper, vacuumer, GooTypes.WEIRD);
        int hold = drainHold(helper, ATTACHED_EAST, ATTACHED_VOLUME);
        holdVacuum(helper, vacuumer, hold, held -> vacuumer.doTick());
        helper.runAfterDelay(hold + 1, () -> {
            int returned = gooReturned(helper, vacuumer, GooTypes.WEIRD) - before;
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            int owed = ResidualPrices.layersRefund(helper, ATTACHED_VOLUME);
            helper.assertTrue(returned == owed, String.format(VOLUME_OWED, owed, returned));
            helper.succeed();
        });
    }

    /**
     * Vacuum held over a full goop cell beside the player and a shallow one
     * near the reach's far end takes a layer from each every time the ticks
     * its distance sets have run, the first once they have run: the full cell
     * loses one on ticks 4, 8 and on to 60, and the far cell one on each
     * multiple of its slower pace, each on no other tick.
     * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     *
     * @param helper the gametest helper
     */
    public static void vacuumTakesALayerEachTimeItsPaceRuns(GameTestHelper helper) {
        layTheFloor(helper);
        // open cells east and west of each goop cell over bottom slabs keep its layers in their cell and leave
        // the line clear
        for (int east : List.of(BESIDE - 1, BESIDE + 1, NEAR_THE_END - 1, NEAR_THE_END + 1)) {
            helper.setBlock(laneCell(east).below(), Blocks.STONE_SLAB);
        }
        int nearPace = paceAt(helper, BESIDE);
        int farPace = paceAt(helper, NEAR_THE_END);
        helper.assertTrue(nearPace == BESIDE_PACE && farPace > BESIDE_PACE,
                String.format(PACE_WRONG, BESIDE_PACE, nearPace, farPace));
        Map<Integer, Integer> laid = new LinkedHashMap<>();
        laid.put(BESIDE, FULL);
        laid.put(NEAR_THE_END, SHALLOW);
        Map<Integer, Integer> paces = Map.of(BESIDE, nearPace, NEAR_THE_END, farPace);
        laid.forEach((east, layers) -> helper.setBlock(laneCell(east), slimeOf(layers)));
        ServerPlayer vacuumer = vacuumer(helper);
        List<String> wrong = new ArrayList<>();
        int hold = FULL * nearPace;
        holdVacuum(helper, vacuumer, hold, held -> laid.forEach((east, layers) -> {
            int owed = Math.max(0, layers - held / paces.get(east));
            BlockState stood = helper.getBlockState(laneCell(east));
            if (owed == 0 ? !stood.isAir() : VolumeCellBlock.layersOf(stood) != owed) {
                wrong.add(String.format(LAYER_STATE, held, east, stood, owed));
            }
        }));
        helper.runAfterDelay(hold + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(wrong.isEmpty(), String.format(NOT_ON_ITS_PACE, nearPace, farPace, wrong));
            helper.succeed();
        });
    }

    /**
     * Vacuum held over the lane breaks the goop cell 7 blocks out and leaves
     * a goop cell behind the player, a slick cell just over 8 blocks out
     * and a stone block inside the cone as they stood.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     *
     * @param helper the gametest helper
     */
    public static void vacuumLeavesWhatIsOutsideItsReach(GameTestHelper helper) {
        layTheLane(helper);
        ServerPlayer vacuumer = vacuumer(helper);
        holdVacuum(helper, vacuumer, laneHold(helper), held -> { });
        helper.runAfterDelay(laneHold(helper) + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            BlockState sevenOut = helper.getBlockState(laneCell(SEVEN_OUT));
            helper.assertTrue(sevenOut.isAir(), String.format(NOT_CLEARED, SEVEN_OUT, sevenOut));
            BlockState behind = helper.getBlockState(laneCell(BEHIND));
            helper.assertTrue(VolumeCellBlock.layersOf(behind) == 1,
                    String.format(NOT_STANDING, "The goop behind the player", behind));
            BlockState pastTheReach = helper.getBlockState(laneCell(PAST_THE_REACH));
            helper.assertTrue(standsAsLaid(pastTheReach, GooBlocks.KINETIC_SLICK.get().defaultBlockState()),
                    String.format(NOT_STANDING, "The slick past the reach", pastTheReach));
            assertStands(helper, laneCell(WALL_INSIDE_THE_CONE).south(), Blocks.STONE);
            helper.succeed();
        });
    }

    /**
     * Vacuum held with its look turned 15 degrees off one goop cell, toward
     * a second goop cell across the lane, breaks the first, inside the
     * cone's 22.5 degrees each side of the look, and leaves the second, near
     * 26 degrees off, as laid; both cells stand in the reach in clear sight
     * of the eye.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     *
     * @param helper the gametest helper
     */
    public static void vacuumBreaksInsideItsFortyFiveDegreeCone(GameTestHelper helper) {
        List<BlockPos> cells = List.of(INSIDE_THE_CONE, OUTSIDE_THE_CONE);
        layAnOpenFloorUnder(helper, cells);
        BlockState laid = slimeOf(SHALLOW);
        cells.forEach(cell -> helper.setBlock(cell, laid));
        ServerPlayer vacuumer = vacuumer(helper);
        Vec3 eye = vacuumer.getEyePosition();
        Vec3 inside = Vec3.atCenterOf(helper.absolutePos(INSIDE_THE_CONE)).subtract(eye);
        Vec3 outside = Vec3.atCenterOf(helper.absolutePos(OUTSIDE_THE_CONE)).subtract(eye);
        lookAlong(vacuumer, turnedToward(inside, outside, INSIDE_DEGREES));
        double insideOff = degreesBetween(vacuumer.getLookAngle(), inside);
        double outsideOff = degreesBetween(vacuumer.getLookAngle(), outside);
        helper.assertTrue(Math.abs(insideOff - INSIDE_DEGREES) < 1 && outsideOff > HALF_CONE_DEGREES + 1,
                String.format(NOT_OFF_THE_LOOK, INSIDE_DEGREES, HALF_CONE_DEGREES, insideOff, outsideOff));
        holdVacuum(helper, vacuumer, laneHold(helper), held -> { });
        helper.runAfterDelay(laneHold(helper) + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            BlockState insideNow = helper.getBlockState(INSIDE_THE_CONE);
            helper.assertTrue(insideNow.isAir(), String.format(INSIDE_STANDS, insideOff, insideNow));
            BlockState outsideNow = helper.getBlockState(OUTSIDE_THE_CONE);
            helper.assertTrue(standsAsLaid(outsideNow, laid), String.format(OUTSIDE_BROKE, outsideOff, outsideNow));
            helper.succeed();
        });
    }

    /**
     * Lays a bedrock floor under open air across the bay's middle, the
     * player's stand on it, with a bottom slab under each cell beside a goop
     * cell, whose top holds no goop, so each keeps its layers in its cell.
     */
    private static void layAnOpenFloorUnder(GameTestHelper helper, List<BlockPos> goopCells) {
        for (int x = STAND.getX() - 1; x <= STAND.getX() + PAST_THE_REACH; x++) {
            for (int z = STAND.getZ() - OPEN_FLOOR_HALF_WIDTH; z <= STAND.getZ() + OPEN_FLOOR_HALF_WIDTH; z++) {
                BlockPos cell = new BlockPos(x, STAND.getY(), z);
                helper.setBlock(cell.below(), Blocks.BEDROCK);
                for (int up = 0; up <= 2; up++) {
                    helper.setBlock(cell.above(up), Blocks.AIR);
                }
            }
        }
        for (BlockPos goop : goopCells) {
            for (BlockPos beside : List.of(goop.east(), goop.west(), goop.north(), goop.south())) {
                helper.setBlock(beside.below(), Blocks.STONE_SLAB);
            }
        }
    }

    /** A direction turned from one direction by an angle, in their plane, toward a second direction. */
    private static Vec3 turnedToward(Vec3 from, Vec3 toward, double degrees) {
        Vec3 unit = from.normalize();
        Vec3 second = toward.normalize();
        Vec3 across = second.subtract(unit.scale(second.dot(unit))).normalize();
        double turn = Math.toRadians(degrees);
        return unit.scale(Math.cos(turn)).add(across.scale(Math.sin(turn)));
    }

    /** Turns a player to look along a direction, as its yaw and pitch carry it. */
    private static void lookAlong(ServerPlayer player, Vec3 direction) {
        Vec3 unit = direction.normalize();
        player.setYRot((float) Math.toDegrees(Math.atan2(-unit.x, unit.z)));
        player.setXRot((float) Math.toDegrees(-Math.asin(unit.y)));
    }

    private static double degreesBetween(Vec3 first, Vec3 second) {
        return Math.toDegrees(Math.acos(Math.clamp(first.normalize().dot(second.normalize()), -1, 1)));
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
            helper.assertTrue(weird > 0, String.format(GOO_NOT_PICKED_UP, weird));
            helper.assertTrue(behind.isAlive() && behind.position().distanceTo(behindAt) < STILL,
                    String.format(MOVED, "The stick behind the player", behindAt, behind.position()));
            helper.succeed();
        });
    }

    /**
     * Vacuum held over a stick lying at rest near the cone's far end starts it
     * slow and speeds it up: its speed toward the player's eye on the first
     * pulled tick is no more than the pull's acceleration, and rises tick over
     * tick after it.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param helper the gametest helper
     */
    public static void vacuumAcceleratesAnItemFromRest(GameTestHelper helper) {
        layTheFloor(helper);
        ItemEntity stick = lieAt(helper, ITEM_FAR);
        ServerPlayer vacuumer = vacuumer(helper);
        double acceleration = vacuumPullStep(helper).itemAcceleration();
        double topSpeed = vacuumPullStep(helper).itemTopSpeed();
        List<Double> speeds = new ArrayList<>();
        holdVacuum(helper, vacuumer, CLOSING_TICKS + 1, held -> speeds.add(speedTowardTheEye(stick, vacuumer)));
        helper.runAfterDelay(CLOSING_TICKS + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(speeds.getFirst() <= acceleration + SPEED_SLACK,
                    String.format(STARTS_FAST, acceleration, speeds));
            // the speed rises until the pull's top speed holds it
            for (int i = 1; i < speeds.size() && speeds.get(i - 1) < topSpeed - SPEED_SLACK; i++) {
                helper.assertTrue(speeds.get(i) > speeds.get(i - 1), String.format(NOT_SPEEDING_UP, speeds));
            }
            helper.succeed();
        });
    }

    /**
     * Vacuum held over a stick in its cone and a stick behind the player
     * turns the first stick's gravity off while the pull holds it and leaves
     * the second stick's on; released while the first stick is still in
     * flight, the first stick's gravity is on again once the pull's grace
     * has run out.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param helper the gametest helper
     */
    public static void vacuumPullsAnItemWithItsGravityOff(GameTestHelper helper) {
        layTheFloor(helper);
        ItemEntity inside = lieAt(helper, ITEM_FAR);
        ItemEntity behind = lieAt(helper, BEHIND);
        ServerPlayer vacuumer = vacuumer(helper);
        List<String> wrong = new ArrayList<>();
        double began = inside.position().distanceTo(vacuumer.getEyePosition());
        double[] ended = new double[1];
        holdVacuum(helper, vacuumer, IN_FLIGHT_HOLD, held -> {
            if (!inside.isNoGravity() || behind.isNoGravity()) {
                wrong.add(String.format(GRAVITY_ON_TICK, held, inside.isNoGravity(), behind.isNoGravity()));
            }
            ended[0] = inside.position().distanceTo(vacuumer.getEyePosition());
        });
        helper.runAfterDelay(IN_FLIGHT_HOLD + GrabEvents.HOLD_GRACE_TICKS + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(wrong.isEmpty(), String.format(GRAVITY_WRONG, wrong));
            helper.assertTrue(ended[0] < began, String.format(NOT_NEARER, began, ended[0]));
            helper.assertTrue(inside.isAlive(), NOT_IN_FLIGHT);
            helper.assertTrue(!inside.isNoGravity(), GRAVITY_NOT_BACK);
            helper.succeed();
        });
    }

    /** An item's speed toward a player's eye: its motion along the line to the eye. */
    private static double speedTowardTheEye(ItemEntity item, ServerPlayer player) {
        return item.getDeltaMovement().dot(player.getEyePosition().subtract(item.position()).normalize());
    }

    /**
     * Vacuum held at a husk standing 6 blocks out in its cone drags it toward
     * the player: its distance falls tick over tick and its health stays whole.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param helper the gametest helper
     */
    public static void vacuumDragsAMobToABlockInFront(GameTestHelper helper) {
        layTheFloor(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, laneCell(MOB_FAR));
        float health = husk.getHealth();
        ServerPlayer vacuumer = vacuumer(helper);
        List<Double> distances = new ArrayList<>();
        holdVacuum(helper, vacuumer, DRAG_HOLD, held -> distances.add(levelDistance(husk, vacuumer)));
        helper.runAfterDelay(DRAG_HOLD + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            for (int i = 1; i <= CLOSING_TICKS; i++) {
                helper.assertTrue(distances.get(i) < distances.get(i - 1), String.format(NOT_CLOSING, distances));
            }
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
     * Vacuum held 5 ticks over a full goop cell beside the player, where a
     * layer takes 4 ticks, and released leaves the cell standing at 14 layers
     * with the weird goo of the layer taken returned; held again 10 ticks
     * later, inside the count's 20 ticks of memory, it keeps the count's 1
     * tick: the cell stands at 14 through the second hold's tick 2 and holds
     * 13 on its tick 3.
     * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
     *
     * @param helper the gametest helper
     */
    public static void vacuumReleasedPartWayKeepsItsCount(GameTestHelper helper) {
        BlockPos cell = layAFullCellBesideThePlayer(helper);
        ServerPlayer vacuumer = vacuumer(helper);
        holdVacuum(helper, vacuumer, PART_WAY, held -> vacuumer.doTick());
        helper.runAfterDelay(PART_WAY + 1, () -> {
            BlockState stood = helper.getBlockState(cell);
            helper.assertTrue(stood.is(GooBlocks.SLIME_COAT.get()) && VolumeCellBlock.layersOf(stood) == FULL - 1,
                    String.format(NOT_LEFT, FULL - 1, stood));
            int returned = gooReturned(helper, vacuumer, GooTypes.WEIRD);
            int owed = ResidualPrices.layersRefund(helper, FULL) - ResidualPrices.layersRefund(helper, FULL - 1);
            helper.assertTrue(returned == owed, String.format(TAKEN_NOT_RETURNED, owed, returned));
        });
        List<String> wrong = new ArrayList<>();
        holdVacuum(helper, vacuumer, PART_WAY + BACK_INSIDE_THE_MEMORY, TICKS_LEFT, held -> {
            int owed = held < TICKS_LEFT ? FULL - 1 : FULL - 2;
            BlockState stood = helper.getBlockState(cell);
            if (VolumeCellBlock.layersOf(stood) != owed) {
                wrong.add(String.format(LAYER_STATE, held, BESIDE, stood, owed));
            }
        });
        helper.runAfterDelay(PART_WAY + BACK_INSIDE_THE_MEMORY + TICKS_LEFT + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(wrong.isEmpty(), String.format(COUNT_NOT_KEPT, wrong));
            helper.succeed();
        });
    }

    /**
     * Vacuum held 5 ticks over a full goop cell beside the player and held
     * again 30 ticks later, past the count's 20 ticks of memory, counts from
     * zero: the cell stands at 14 layers through the second hold's tick 3 and
     * holds 13 on its tick 4.
     * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
     *
     * @param helper the gametest helper
     */
    public static void vacuumReleasedPastTheMemoryCountsFromZero(GameTestHelper helper) {
        BlockPos cell = layAFullCellBesideThePlayer(helper);
        ServerPlayer vacuumer = vacuumer(helper);
        holdVacuum(helper, vacuumer, PART_WAY, held -> { });
        List<String> wrong = new ArrayList<>();
        holdVacuum(helper, vacuumer, PART_WAY + BACK_PAST_THE_MEMORY, BESIDE_PACE, held -> {
            int owed = held < BESIDE_PACE ? FULL - 1 : FULL - 2;
            BlockState stood = helper.getBlockState(cell);
            if (VolumeCellBlock.layersOf(stood) != owed) {
                wrong.add(String.format(LAYER_STATE, held, BESIDE, stood, owed));
            }
        });
        helper.runAfterDelay(PART_WAY + BACK_PAST_THE_MEMORY + BESIDE_PACE + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(vacuumer);
            helper.assertTrue(wrong.isEmpty(), String.format(COUNT_KEPT, wrong));
            helper.succeed();
        });
    }

    /**
     * Lays the lane's floor and a full goop cell beside the player, over open
     * cells on bottom slabs east and west of it that take no pour.
     */
    private static BlockPos layAFullCellBesideThePlayer(GameTestHelper helper) {
        layTheFloor(helper);
        helper.setBlock(laneCell(BESIDE - 1).below(), Blocks.STONE_SLAB);
        helper.setBlock(laneCell(BESIDE + 1).below(), Blocks.STONE_SLAB);
        BlockPos cell = laneCell(BESIDE);
        helper.setBlock(cell, slimeOf(FULL));
        return cell;
    }

    /** The ticks a layer of the lane cell takes, as the live step paces it from the stand's eye. */
    private static int paceAt(GameTestHelper helper, int east) {
        return vacuumStep(helper).layerTicks(fromTheEye(helper, east), vacuum(helper).delivery().range());
    }

    /** A hold long enough for the live step to take every layer of a lane cell holding that many. */
    private static int drainHold(GameTestHelper helper, int east, int layers) {
        return layers * paceAt(helper, east);
    }

    /** A hold long enough for the live step to take the lane's deepest cell at its slowest pace. */
    private static int laneHold(GameTestHelper helper) {
        return LANE_DEEPEST * vacuumStep(helper).farLayerTicks() + 1;
    }

    /** Whether a cell holds the block laid there at the layers laid, whatever faces it joins its neighbors on. */
    private static boolean standsAsLaid(BlockState stood, BlockState laid) {
        return stood.is(laid.getBlock()) && VolumeCellBlock.layersOf(stood) == VolumeCellBlock.layersOf(laid);
    }

    /**
     * Vacuum held over two goop cells of four layers plays one slurp at each
     * cell, on the tick it takes the cell's last layer.
     * vacuum-sounds-a-suction-wind-and-rising-slurps
     *
     * @param helper the gametest helper
     */
    public static void vacuumSlurpsOnceAtEachCellItTakes(GameTestHelper helper) {
        layTheFloor(helper);
        List<BlockPos> cells = List.of(laneCell(SLURPED_NEAR), laneCell(SLURPED_FAR));
        // open cells east and west over bottom slabs keep each cell's layers in it
        for (int east : List.of(SLURPED_NEAR - 1, SLURPED_NEAR + 1, SLURPED_FAR - 1, SLURPED_FAR + 1)) {
            helper.setBlock(laneCell(east).below(), Blocks.STONE_SLAB);
        }
        cells.forEach(cell -> helper.setBlock(cell, slimeOf(SLURPED_LAYERS)));
        ServerPlayer vacuumer = vacuumer(helper);
        PacketRecorder heard = PacketRecorder.attachTo(vacuumer);
        holdVacuum(helper, vacuumer, laneHold(helper), held -> { });
        helper.runAfterDelay(laneHold(helper) + 1, () -> {
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
        return GooBlocks.SLIME_COAT.get().defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers);
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
        holdVacuum(helper, vacuumer, 0, ticks, watch);
    }

    /**
     * Holds Vacuum's stream one tick at a time from a tick of the test on,
     * running a watch after each tick.
     *
     * @param helper   the gametest helper
     * @param vacuumer the player holding it
     * @param from     the test tick the hold's first tick follows
     * @param ticks    how many ticks to hold
     * @param watch    what runs after each held tick, given the tick's count
     */
    private static void holdVacuum(GameTestHelper helper, ServerPlayer vacuumer, int from, int ticks,
                                   Consumer<Integer> watch) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.VOID), VACUUM.toString(),
                vacuumer.getEyePosition(), vacuumer.getEyePosition());
        for (int held = 1; held <= ticks; held++) {
            int count = held;
            helper.runAfterDelay(from + held, () -> {
                GooStreamHandler.streamTick(vacuumer, tick);
                watch.accept(count);
            });
        }
    }

    private static AbilityDefinition vacuum(GameTestHelper helper) {
        return AbilityRegistry.of(helper.getLevel()).getAbility(VACUUM);
    }

    /** The live vacuum step, whose pace the tests read rather than copy. */
    private static VacuumStep vacuumStep(GameTestHelper helper) {
        return vacuum(helper).behaviors().stream().filter(VacuumStep.class::isInstance).map(VacuumStep.class::cast)
                .findFirst().orElseThrow();
    }

    /** The live pull step, whose item acceleration the tests read rather than copy. */
    private static VacuumPullStep vacuumPullStep(GameTestHelper helper) {
        return vacuum(helper).behaviors().stream().filter(VacuumPullStep.class::isInstance)
                .map(VacuumPullStep.class::cast).findFirst().orElseThrow();
    }

    /** How far a lane cell's middle stands from the eye of a player at the stand. */
    private static double fromTheEye(GameTestHelper helper, int east) {
        Vec3 eye = Vec3.atBottomCenterOf(helper.absolutePos(STAND)).add(0, EYE_HEIGHT, 0);
        return Vec3.atCenterOf(helper.absolutePos(laneCell(east))).distanceTo(eye);
    }

    private static boolean inReach(GameTestHelper helper, int east) {
        return fromTheEye(helper, east) <= vacuum(helper).delivery().range();
    }

    /** The lane's cells inside the live reach, nearest first. */
    private static List<Integer> laneInReach(GameTestHelper helper) {
        return LANE.keySet().stream().filter(east -> inReach(helper, east)).toList();
    }

    private static int voidHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.VOID, 0);
    }

    /**
     * Asserts goo of a type stands as item entities or in the player's
     * inventory, where the pull lands what it reaches.
     */
    private static void assertDropped(GameTestHelper helper, ServerPlayer player, ResourceKey<GooTypeDefinition> type) {
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM);
        int dropped = gooReturned(helper, player, type);
        helper.assertTrue(dropped > 0,
                String.format(GOO_OWED, type.identifier().getPath(), dropped, drops.stream()
                        .map(drop -> GooStacks.volumeOf(drop.getItem())).toList()
                        + " lying, inventory " + GooSourceScanner.aggregateAvailable(player)));
    }

    /**
     * The goo of a type standing as item entities in the test or in the
     * player's inventory, where the pull lands what it reaches.
     *
     * @param helper the gametest helper
     * @param player the vacuuming player
     * @param type   the goo type
     * @return the mb of it
     */
    private static int gooReturned(GameTestHelper helper, ServerPlayer player, ResourceKey<GooTypeDefinition> type) {
        return helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .filter(stack -> type.equals(GooStacks.keyOf(stack))).mapToInt(GooStacks::volumeOf).sum()
                + GooSourceScanner.aggregateAvailable(player).getOrDefault(type, 0);
    }

    private static void assertStands(GameTestHelper helper, BlockPos cell, Block stood) {
        BlockState now = helper.getBlockState(cell);
        helper.assertTrue(now.is(stood), String.format(NOT_STANDING, cell, now));
    }
}
