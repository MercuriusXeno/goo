package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Blaze's Ignite: its blob lights a fire where it lands and breaks nothing,
 * and landing on a standing Lurker sets the Lurker off as a fiery explosion
 * that breaks the ground and lights fires in its sphere.
 * ignite-fire-and-explosive-combo
 */
@RegistersGameTests
public final class IgniteTests {

    private static final String IGNITE_LIGHTS_A_FIRE = "ignite_lights_a_fire";
    private static final String IGNITE_DETONATES_A_LURKER = "ignite_detonates_a_lurker";
    private static final Identifier IGNITE = Identifier.parse("goo:blaze_ignite");
    private static final Identifier LURKER = Identifier.parse("goo:unstable_lurker");
    private static final int FLOOR_SPAN = 16;
    private static final int FLOOR_TOP = 2;
    private static final int BAY_HEIGHT = 5;
    /** The floor block in the middle of the bay the blobs land on. */
    private static final BlockPos FLOOR = new BlockPos(8, FLOOR_TOP, 8);
    /** The cell over the floor block, where a blob landing on the floor lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    /** The tick after the landing, once the explosion's fires stand. */
    private static final int SETTLE_TICKS = 1;
    private static final String ABILITY_REQUIRED = "The ability registry must hold %s";
    private static final String SHOULD_LIGHT = "Ignite should light a fire where it lands, stands %s";
    private static final String SHOULD_SPARE_FLOOR = "Ignite alone should break nothing, the floor stands %s";
    private static final String SHOULD_STAND_LURKER = "The lurker should stand on the floor before Ignite lands";
    private static final String SHOULD_SET_OFF = "Ignite should set the lurker off, its cell stands %s";
    private static final String SHOULD_BREAK_FLOOR = "The fiery explosion should break the floor under the lurker";
    private static final String SHOULD_BURN = "The fiery explosion should light fires in its sphere";
    private static final String IGNITE_PLAYS_NO_BURNOUT = "ignite_plays_no_burnout";
    private static final String REMOVAL = "removal";
    private static final Identifier BLAST = Identifier.parse("goo:unstable_explode");
    /** A floor block well apart from the first, where the control Blast lands. */
    private static final BlockPos BLAST_FLOOR = new BlockPos(3, FLOOR_TOP, 3);
    /** The chunks the watcher tracks around the bay, enough to hold both landings. */
    private static final int WATCH_CHUNKS = 2;
    private static final String SHOULD_HEAR_BLAST = "The watcher should hear the Blast's burnout, heard %s";
    private static final String SHOULD_PLAY_NONE = "Ignite's landing should send no burnout, sent %s";

    private IgniteTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, IGNITE_LIGHTS_A_FIRE, IgniteTests::igniteLightsAFire);
        GooTestFunctions.registerTest(registrar, IGNITE_DETONATES_A_LURKER, IgniteTests::igniteDetonatesALurker);
        GooTestFunctions.registerTest(registrar, IGNITE_PLAYS_NO_BURNOUT, IgniteTests::ignitePlaysNoBurnout);
    }

    /**
     * Ignite landing on the floor lights a fire in the cell over it and
     * leaves the floor standing.
     *
     * @param helper the gametest helper
     */
    public static void igniteLightsAFire(GameTestHelper helper) {
        stoneFloor(helper);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(FLOOR), GooTypes.BLAZE, Direction.UP,
                ability(helper, IGNITE));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(OVER_THE_FLOOR).getBlock() instanceof BaseFireBlock,
                    String.format(SHOULD_LIGHT, helper.getBlockState(OVER_THE_FLOOR)));
            helper.assertTrue(helper.getBlockState(FLOOR).is(Blocks.STONE),
                    String.format(SHOULD_SPARE_FLOOR, helper.getBlockState(FLOOR)));
            helper.succeed();
        });
    }

    /**
     * Ignite landing on a Lurker standing on the floor sets it off: the
     * Lurker is gone, the floor under it broken, and fires stand in the bay.
     *
     * @param helper the gametest helper
     */
    public static void igniteDetonatesALurker(GameTestHelper helper) {
        stoneFloor(helper);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(FLOOR), GooTypes.UNSTABLE, Direction.UP,
                ability(helper, LURKER));
        helper.assertTrue(helper.getBlockState(OVER_THE_FLOOR).is(GooBlocks.ABILITY_BLOCK.get()), SHOULD_STAND_LURKER);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(OVER_THE_FLOOR), GooTypes.BLAZE, Direction.UP,
                ability(helper, IGNITE));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertFalse(helper.getBlockState(OVER_THE_FLOOR).is(GooBlocks.ABILITY_BLOCK.get()),
                    String.format(SHOULD_SET_OFF, helper.getBlockState(OVER_THE_FLOOR)));
            helper.assertFalse(helper.getBlockState(FLOOR).is(Blocks.STONE), SHOULD_BREAK_FLOOR);
            helper.assertTrue(firesInTheBay(helper) > 0, SHOULD_BURN);
            helper.succeed();
        });
    }

    /**
     * Ignite landing on the floor sends its watchers no burnout, while a Blast
     * landing beside it sends one to the same watcher, so the watcher hears
     * burnouts at all.
     * ignite-plays-no-burnout
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void ignitePlaysNoBurnout(GameTestHelper helper) {
        stoneFloor(helper);
        ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
        viewer.setGameMode(GameType.CREATIVE);
        Vec3 over = Vec3.atCenterOf(helper.absolutePos(OVER_THE_FLOOR));
        viewer.teleportTo(over.x(), over.y() + 2, over.z());
        // a mock player tracks no chunk of its own, and burnouts go to the players tracking the landing's chunk
        viewer.setChunkTrackingView(ChunkTrackingView.of(ChunkPos.containing(helper.absolutePos(FLOOR)), WATCH_CHUNKS));
        BurnoutRecorder recorder = BurnoutRecorder.attachTo(viewer);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(FLOOR), GooTypes.BLAZE, Direction.UP,
                ability(helper, IGNITE));
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(BLAST_FLOOR), GooTypes.UNSTABLE, Direction.UP,
                ability(helper, BLAST));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(viewer);
            List<ChainBurnoutPayload> burnouts = recorder.burnouts();
            helper.assertTrue(burnouts.stream().anyMatch(b -> BLAST.toString().equals(b.abilityId())),
                    String.format(SHOULD_HEAR_BLAST, burnouts));
            helper.assertTrue(burnouts.stream().noneMatch(b -> IGNITE.toString().equals(b.abilityId())),
                    String.format(SHOULD_PLAY_NONE, burnouts));
            helper.succeed();
        });
    }

    private static long firesInTheBay(GameTestHelper helper) {
        return BlockPos.betweenClosedStream(0, 0, 0, FLOOR_SPAN - 1, BAY_HEIGHT + FLOOR_TOP, FLOOR_SPAN - 1)
                .filter(cell -> helper.getBlockState(cell).getBlock() instanceof BaseFireBlock).count();
    }

    private static void stoneFloor(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, FLOOR_TOP, FLOOR_SPAN - 1)
                .forEach(cell -> helper.setBlock(cell, Blocks.STONE));
    }

    private static AbilityDefinition ability(GameTestHelper helper, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, id));
        return ability;
    }
}
