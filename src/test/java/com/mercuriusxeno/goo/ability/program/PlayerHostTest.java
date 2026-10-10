package com.mercuriusxeno.goo.ability.program;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The player host runs a self ability on the invoking player: the player is
 * its own thrower, so a thrower-look step follows the player's look, and a
 * step the host cannot serve refuses at load naming it and the player host
 * (decision self-delivery-runs-on-player).
 */
class PlayerHostTest {

    private static final String PLAYER_LABEL = "player host";
    private static final double BLINK = 8;
    private static final double X = 10;
    private static final double Y = 64;
    private static final double Z = -3;

    /**
     * Stands NeoForge's AttachmentHolder and the built-in registries before the
     * first mock of a player: both class inits ask FML whether it runs in
     * production, which a stubbed loader answers. Without it the class init
     * fails and poisons every later mock of an entity or level in the JVM.
     *
     * @throws ClassNotFoundException never, the class is on the test classpath
     */
    @BeforeAll
    static void standRegistries() throws ClassNotFoundException {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            Class.forName(AttachmentHolder.class.getName(), true, AttachmentHolder.class.getClassLoader());
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private static ServerPlayer playerLooking(Vec3 look) {
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel openAir = mock(ServerLevel.class);
        when(openAir.noCollision(any(Entity.class), any(AABB.class))).thenReturn(true);
        when(openAir.clip(any(ClipContext.class))).thenReturn(BlockHitResult.miss(Vec3.ZERO, Direction.UP,
                BlockPos.ZERO));
        when(player.level()).thenReturn(openAir);
        when(player.getLookAngle()).thenReturn(look);
        when(player.getDeltaMovement()).thenReturn(Vec3.ZERO);
        when(player.getItemBySlot(EquipmentSlot.CHEST)).thenReturn(mock(ItemStack.class));
        when(player.position()).thenReturn(new Vec3(X, Y, Z));
        when(player.getX()).thenReturn(X);
        when(player.getY()).thenReturn(Y);
        when(player.getZ()).thenReturn(Z);
        return player;
    }

    private static void run(List<Step> steps, ServerPlayer player) {
        ProgramBehavior.forHost(steps, HostKind.PLAYER).tick(new PlayerHost(mock(ServerLevel.class), player));
    }

    @Nested
    class ThrowerLook {

        @Test
        void teleportAlongThrowerLookFollowsThePlayersOwnLook() {
            ServerPlayer player = playerLooking(new Vec3(1, 0, 0));

            run(List.of(new TeleportStep(TeleportMode.THROWER_LOOK, Expr.literal(BLINK))), player);

            verify(player).teleportTo(X + BLINK, Y, Z);
        }

        @Test
        void teleportAlongThrowerLookClimbsWithThePitch() {
            ServerPlayer player = playerLooking(new Vec3(0, 0.6, 0.8));

            run(List.of(new TeleportStep(TeleportMode.THROWER_LOOK, Expr.literal(BLINK))), player);

            verify(player).teleportTo(X, Y + 0.6 * BLINK, Z + 0.8 * BLINK);
        }

        /**
         * The server's teleport lands on the point the client's blink cursor
         * resolves for the same look and range, through the one shared function
         * (decisions ripple-outline-is-the-blink-cursor, blink-lands-safely-costed-by-distance).
         */
        @Test
        void teleportLandsWhereTheBlinkCursorResolves() {
            Vec3 look = new Vec3(0.36, -0.48, 0.8);
            ServerPlayer player = playerLooking(look);

            run(List.of(new TeleportStep(TeleportMode.THROWER_LOOK, Expr.literal(BLINK))), player);

            Vec3 cursor = TeleportStep.landingAlongLook(player, new Vec3(X, Y, Z), look, BLINK, Optional.empty())
                    .orElseThrow().feet();
            verify(player).teleportTo(cursor.x(), cursor.y(), cursor.z());
        }
    }

    @Nested
    class Push {

        private static final double STRENGTH = 1.5;
        /** typhoon_jet.json's push: its speed and the shares it steers by, without and with an elytra. */
        private static final double JET_STRENGTH = 0.8;
        private static final double JET_STEER = 0.2;
        private static final double JET_ELYTRA_STEER = 0.5;
        private static final PushStep JET = new PushStep(Expr.literal(JET_STRENGTH), Expr.literal(JET_STEER),
                Expr.literal(JET_ELYTRA_STEER));
        private static final Vec3 EAST = new Vec3(1, 0, 0);
        private static final double TOLERANCE = 1e-9;

        @Test
        void pushSetsTheMotionMarksItAndResetsTheFall() {
            ServerPlayer player = playerLooking(new Vec3(1, 0, 0));
            Vec3 motion = new Vec3(0.3, 1.2, 0);

            new PlayerHost(mock(ServerLevel.class), player).push(motion);

            verify(player).setDeltaMovement(motion);
            assertTrue(player.hurtMarked);
            verify(player).resetFallDistance();
        }

        @Test
        void pushSendsThePlayerAlongItsOwnLook() {
            Vec3 look = new Vec3(0.6, 0.8, 0);
            ServerPlayer player = playerLooking(look);

            run(List.of(new PushStep(Expr.literal(STRENGTH))), player);

            verify(player).setDeltaMovement(look.scale(STRENGTH));
        }

        // decision jet-pushes-along-the-look-while-held
        @Test
        void jetTurnsAStillPlayerAFifthOfTheWayTowardItsLook() {
            ServerPlayer player = playerLooking(EAST);

            run(List.of(JET), player);

            assertEquals(JET_STRENGTH * JET_STEER, pushedAlongEast(player), TOLERANCE);
        }

        // decision jet-pushes-along-the-look-while-held
        @Test
        void jetSteersHalfTheWayForAPlayerWearingAnElytra() {
            ServerPlayer player = playerLooking(EAST);
            ItemStack elytra = mock(ItemStack.class);
            when(elytra.has(DataComponents.GLIDER)).thenReturn(true);
            when(player.getItemBySlot(EquipmentSlot.CHEST)).thenReturn(elytra);

            run(List.of(JET), player);

            assertEquals(JET_STRENGTH * JET_ELYTRA_STEER, pushedAlongEast(player), TOLERANCE);
        }

        // decision jet-pushes-along-the-look-while-held
        @Test
        void jetKeepsTheRestOfAMovingPlayersVelocity() {
            ServerPlayer player = playerLooking(EAST);
            when(player.getDeltaMovement()).thenReturn(new Vec3(0, 0, 1));

            run(List.of(JET), player);

            ArgumentCaptor<Vec3> set = ArgumentCaptor.forClass(Vec3.class);
            verify(player).setDeltaMovement(set.capture());
            assertEquals(1 - JET_STEER, set.getValue().z, TOLERANCE);
            assertEquals(JET_STRENGTH * JET_STEER, set.getValue().x, TOLERANCE);
        }

        private static double pushedAlongEast(ServerPlayer player) {
            ArgumentCaptor<Vec3> set = ArgumentCaptor.forClass(Vec3.class);
            verify(player).setDeltaMovement(set.capture());
            assertEquals(0, set.getValue().y, TOLERANCE);
            assertEquals(0, set.getValue().z, TOLERANCE);
            return set.getValue().x;
        }
    }

    @Nested
    class Load {

        @Test
        void markerOnlyStepRefusesNamingTheStepAndThePlayerHost() {
            List<Step> steps = List.of(LeafSteps.WAIT.step(Expr.literal(1)));

            ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                    () -> ProgramBehavior.forHost(steps, HostKind.PLAYER));

            assertTrue(refusal.getMessage().contains("wait"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains(PLAYER_LABEL), refusal.getMessage());
        }

        @Test
        void teleportLoadsOnThePlayerHost() {
            assertDoesNotThrow(() -> ProgramBehavior.forHost(
                    List.of(new TeleportStep(TeleportMode.THROWER_LOOK, Expr.literal(BLINK))), HostKind.PLAYER));
        }

        @Test
        void playerHostProvidesTargetExplodeEntityScanChannelBlockBreaksEffectExtendsAndFrost() {
            assertEquals(Set.of(HostCapability.TARGET, HostCapability.EXPLODE, HostCapability.ENTITY_SCAN,
                            HostCapability.CHANNEL, HostCapability.BREAK_BLOCKS, HostCapability.EXTEND_EFFECTS,
                            HostCapability.FROST),
                    HostKind.PLAYER.capabilities());
        }
    }
}
