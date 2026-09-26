package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.block.canister.ICanisterAttachable;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import java.util.UUID;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Shared helpers for gasket installation: blockstate flipping, registry updates,
 * player feedback, and intake availability checks. Extracted from
 * {@link ChoralGasketItem} to keep per-class method counts manageable.
 */
public final class GasketInstallHelper {

    /**
     * Block update flags: notify clients + update neighbors.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /**
     * Feedback: gasket installed successfully.
     */
    static final String MSG_GASKET_INSTALLED = "Gasket installed";

    private GasketInstallHelper() {
    }

    /**
     * Returns true if the intake is blocked by a canister attachment above.
     *
     * @param level the level
     * @param pos   the block position
     * @return true if a canister is attached on top
     */
    static boolean isIntakeBlocked(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ICanisterAttachable att
                && att.currentTopAttachments() > 0;
    }

    /**
     * The one block-level gasket install every machine takes (decision
     * machine-base-owns-the-lifecycle): raises the face's blockstate flag,
     * ensures the gasket's id and publishes its location.
     *
     * @param level  the level
     * @param pos    the machine's position
     * @param holder the machine
     * @param role   the face's role
     * @return false when the face takes no block-level gasket or already holds one
     */
    public static boolean installBlockGasket(Level level, BlockPos pos, IGasketHolder holder, GasketRole role) {
        BooleanProperty flag = holder.gasketFlag(role);
        BlockState state = level.getBlockState(pos);
        if (flag == null || state.getValue(flag)) {
            return false;
        }
        level.setBlock(pos, state.setValue(flag, true), BLOCK_UPDATE_FLAGS);
        UUID newId = holder.ensureGasketId(role);
        if (newId != null) {
            registerGasketLocation(level, pos, newId, role == GasketRole.RECEIVER, NO_SLOT);
        }
        return true;
    }

    /**
     * Installs a block-level gasket from a player's click, rejecting with the
     * message when the face already holds one.
     *
     * @param context  the use-on context
     * @param holder   the machine
     * @param role     the face's role
     * @param rejected the feedback when the face already holds a gasket
     * @param done     the feedback once installed
     * @return SUCCESS once installed, PASS when rejected
     */
    static InteractionResult installBlockGasket(UseOnContext context, IGasketHolder holder, GasketRole role,
                                                String rejected, String done) {
        if (!installBlockGasket(context.getLevel(), context.getClickedPos(), holder, role)) {
            return rejectWith(context, rejected);
        }
        return finishInstall(context, done);
    }

    /**
     * Registers a gasket UUID in the server-side GasketRegistry. No-op on client.
     *
     * @param level the level
     * @param pos   the block position
     * @param id    the gasket UUID
     * @param isTop whether this is a top-face gasket
     * @param slot  the slot index, or NO_SLOT
     */
    static void registerGasketLocation(Level level, BlockPos pos, UUID id, boolean isTop, int slot) {
        if (level instanceof ServerLevel serverLevel) {
            GasketRegistry registry = GasketRegistry.get(serverLevel);
            registry.updateLocation(id, new GasketLocation(serverLevel.dimension(), pos, isTop, slot));
        }
    }

    /**
     * Builds a BlockHitResult from the use-on context.
     *
     * @param context the use-on context
     * @param pos     the block position
     * @return the hit result
     */
    static BlockHitResult buildHit(UseOnContext context, BlockPos pos) {
        return new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, context.isInside());
    }

    /**
     * Sends an overlay message to the player from the context.
     *
     * @param context the use-on context
     * @param message the message text
     */
    static void sendOverlay(UseOnContext context, String message) {
        var player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.literal(message));
        }
    }

    /**
     * Sends a rejection overlay and returns PASS.
     *
     * @param context the use-on context
     * @param message the rejection reason
     * @return PASS
     */
    static InteractionResult rejectWith(UseOnContext context, String message) {
        sendOverlay(context, message);
        return InteractionResult.PASS;
    }

    /**
     * Consumes the gasket item (unless creative), sends feedback, and returns SUCCESS.
     *
     * @param context the use-on context
     * @param message the feedback message
     * @return SUCCESS
     */
    static InteractionResult finishInstall(UseOnContext context, String message) {
        Player player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        sendOverlay(context, message);
        return InteractionResult.SUCCESS;
    }
}
