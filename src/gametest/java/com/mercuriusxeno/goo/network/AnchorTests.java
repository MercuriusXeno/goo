package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.quantum.QuantumAnchors;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Gametest for Quantum's anchors: a player's two anchor casts pair the two
 * prisms, and a player stepping onto either arrives beside the other, both
 * ways (decision quantum-anchors-link-two-points).
 */
public final class AnchorTests {

    private static final String ANCHOR = "goo:quantum_anchor";
    private static final int NO_ENTITY = -1;
    private static final BlockPos ANCHOR_A = new BlockPos(3, 1, 3);
    private static final BlockPos ANCHOR_B = new BlockPos(12, 1, 12);
    /** Where the player waits between trips, clear of both anchors. */
    private static final BlockPos AWAY = new BlockPos(8, 1, 3);
    /** How near an anchor's cell a traveller stands once carried there. */
    private static final double BESIDE = 1.6;
    private static final int STEP_ONTO_A = 2;
    private static final int CHECK_AT_B = 4;
    private static final int STEP_AWAY = 5;
    private static final int STEP_ONTO_B = 7;
    private static final int CHECK_AT_A = 9;

    private AnchorTests() {
    }

    /**
     * Two anchors cast by one player pair; stepping onto A carries the player
     * beside B, and after stepping away, stepping onto B carries them beside A.
     *
     * @param helper the gametest helper
     */
    public static void anchorsTeleportBothWays(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        standAt(helper, player, AWAY);
        anchorAt(helper, ANCHOR_A, player);
        anchorAt(helper, ANCHOR_B, player);
        GlobalPos a = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(ANCHOR_A));
        GlobalPos b = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(ANCHOR_B));
        helper.assertTrue(QuantumAnchors.get(helper.getLevel()).partnerOf(a).equals(Optional.of(b)),
                "The player's second anchor should pair with their first");

        helper.runAfterDelay(STEP_ONTO_A, () -> standAt(helper, player, ANCHOR_A.above()));
        helper.runAfterDelay(CHECK_AT_B, () -> assertBeside(helper, player, ANCHOR_B, "A should carry the player to B"));
        helper.runAfterDelay(STEP_AWAY, () -> standAt(helper, player, AWAY));
        helper.runAfterDelay(STEP_ONTO_B, () -> standAt(helper, player, ANCHOR_B.above()));
        helper.runAfterDelay(CHECK_AT_A, () -> {
            assertBeside(helper, player, ANCHOR_A, "B should carry the player back to A");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static void anchorAt(GameTestHelper helper, BlockPos pos, ServerPlayer caster) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), caster, GooTypes.QUANTUM,
                NO_ENTITY, helper.absolutePos(pos), Direction.UP, ANCHOR));
    }

    private static void standAt(GameTestHelper helper, ServerPlayer player, BlockPos cell) {
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(cell));
        player.teleportTo(feet.x, feet.y, feet.z);
    }

    private static void assertBeside(GameTestHelper helper, ServerPlayer player, BlockPos anchor, String message) {
        double distance = player.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(anchor)));
        helper.assertTrue(distance < BESIDE, message + ", stands " + distance + " away at " + player.position());
    }
}
