package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.network.LurkerPulsePayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests that a lurker pulses ever nearer as a zombie walks up to it and
 * explodes once the zombie enters its trigger radius.
 * decision lurker-blob-brightens-then-detonates
 */
@RegistersGameTests
public final class LurkerTests {
    private static final String LURKER_PULSES_THEN_EXPLODES = "lurker_pulses_then_explodes";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LURKER_PULSES_THEN_EXPLODES, LurkerTests::lurkerPulsesThenExplodes);
    }


    private static final String REMOVAL = "removal";
    private static final Identifier LURKER = Identifier.fromNamespaceAndPath("goo", "unstable_lurker");
    private static final BlockPos MARKER_POS = new BlockPos(3, 1, 3);
    private static final BlockPos WALL_POS = MARKER_POS.north();
    /** The zombie's start, nine blocks south of the marker, where the lurker watches it walk in. */
    private static final double START_OFFSET = 9.0;
    private static final double STEP_PER_TICK = 0.25;
    private static final double LEFTOVER_CLEAR_RADIUS = 12;
    private static final double PLAYER_OFFSET = 6.0;
    /**
     * Where the walk ends, a block from the marker, well inside the lurker's trigger radius, so the zombie's
     * height above the marker's center cannot keep it out.
     */
    private static final double END_OFFSET = 1.0;
    private static final int TICKS_TO_TRIGGER = (int) Math.ceil((START_OFFSET - END_OFFSET) / STEP_PER_TICK);
    private static final int SETTLE_TICKS = 5;
    private static final String NO_LURKER = "The ability registry should load unstable_lurker";
    private static final String NO_PULSE = "The viewer should receive the Lurker's pulse";
    private static final String NOT_EXPLODED = "The lurker should explode once the zombie enters its trigger radius";

    private LurkerTests() {
    }

    /**
     * Stands a lurker on a wall, walks a zombie toward it a quarter block a
     * tick, and asserts its pulse reached the viewer and it exploded once the
     * zombie came close.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void lurkerPulsesThenExplodes(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
        viewer.setGameMode(GameType.CREATIVE);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(MARKER_POS));
        viewer.teleportTo(center.x() + PLAYER_OFFSET, center.y(), center.z());
        LurkerPulseRecorder recorder = LurkerPulseRecorder.attachTo(viewer);
        standLurker(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, MARKER_POS.south((int) START_OFFSET));
        zombie.setNoGravity(true);
        zombie.teleportTo(center.x(), center.y() - 0.5, center.z() + START_OFFSET);
        zombie.setInvulnerable(true);
        for (int tick = 0; tick <= TICKS_TO_TRIGGER; tick++) {
            double offset = START_OFFSET - tick * STEP_PER_TICK;
            helper.runAfterDelay(tick + 1L, () -> zombie.teleportTo(center.x(), center.y() - 0.5, center.z() + offset));
        }
        helper.runAfterDelay(TICKS_TO_TRIGGER + SETTLE_TICKS, () -> {
            assertAPulseReachedTheViewer(helper, recorder.pulses());
            helper.assertTrue(helper.getBlockState(MARKER_POS).isAir(), NOT_EXPLODED);
            helper.assertBlockNotPresent(Blocks.STONE, WALL_POS);
            zombie.discard();
            helper.getLevel().getServer().getPlayerList().remove(viewer);
            helper.succeed();
        });
    }

    /**
     * Lands the lurker on a stone wall, facing south toward the zombie's path.
     *
     * @param helper the gametest helper
     */
    private static void standLurker(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        AbilityDefinition lurker = AbilityRegistry.of(helper.getLevel()).getAbility(LURKER);
        helper.assertTrue(lurker != null, NO_LURKER);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(WALL_POS), GooTypes.UNSTABLE, Direction.SOUTH, lurker);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
    }

    /**
     * Asserts a pulse reached the viewer; WatchStepTest holds each pulse's distance and the trigger.
     *
     * @param helper the gametest helper
     * @param pulses the pulses the viewer received
     */
    private static void assertAPulseReachedTheViewer(GameTestHelper helper, List<LurkerPulsePayload> pulses) {
        helper.assertTrue(!pulses.isEmpty(), NO_PULSE);
    }
    /**
     * Discards every non-player entity a neighboring test left near the marker.
     *
     * @param helper the gametest helper
     */
    private static void discardLeftoverEntities(GameTestHelper helper) {
        AABB reach = new AABB(helper.absolutePos(MARKER_POS)).inflate(LEFTOVER_CLEAR_RADIUS);
        helper.getLevel().getEntities((Entity) null, reach, entity -> !(entity instanceof Player))
                .forEach(Entity::discard);
    }
}
