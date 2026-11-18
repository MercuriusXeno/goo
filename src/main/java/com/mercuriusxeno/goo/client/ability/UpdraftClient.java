package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.block.ability.UpdraftCellBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.Optional;

/**
 * The client's half of an Updraft cell: the client moves its own player, so
 * after each of its ticks over an Updraft cell it carries the player itself,
 * rising in the push column and falling no faster than the cap in the glide
 * zone over it, and in midair turns its sideways motion toward its keys. The
 * server writes no motion onto a player, so walking over the goo keeps its
 * pace. Creative flight, water, lava and a mount keep their own motion.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-goo-lifts-one-block-per-layer
 * operator report 2026-11-17: moving over updraft goo stalled and hitched the player
 * operator ruling 2026-11-17: a tall zone above the push smooths transitions with slow fall and air control
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class UpdraftClient {

    private UpdraftClient() {
    }

    /**
     * Carries and steers the local player over the Updraft cell under it.
     *
     * @param event the player tick
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !movesOnItsOwn(player)) {
            return;
        }
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(
                CellBinding.UPDRAFT.layingAbility().toString());
        Optional<UpdraftStep> updraft = ability == null ? Optional.empty() : UpdraftStep.in(ability.behaviors());
        updraft.ifPresent(step -> carry(player, step));
    }

    /**
     * Carries the player by the cell under it, where one stands within the
     * reach of a full cell's column and glide zone with nothing solid between.
     *
     * @param player the local player
     * @param step   the updraft step
     */
    private static void carry(LocalPlayer player, UpdraftStep step) {
        Optional<BlockPos> cell = cellUnder(player.level(), player.position(), step);
        if (cell.isEmpty()) {
            return;
        }
        Vec3 floor = Vec3.atBottomCenterOf(cell.get());
        Vec3 feet = player.position();
        if (Math.abs(feet.x - floor.x) > step.halfWidth() || Math.abs(feet.z - floor.z) > step.halfWidth()) {
            return;
        }
        int layers = VolumeCellBlock.layersOf(player.level().getBlockState(cell.get()));
        step.carried(player.getDeltaMovement(), feet.y - floor.y, layers).ifPresent(carried -> {
            Vec3 input = AirbornClient.flightInput(player.input.keyPresses);
            player.setDeltaMovement(player.onGround() ? carried : step.steered(carried, input, player.getYRot()));
        });
    }

    /**
     * The Updraft cell straight under a point, found down through blocks
     * nothing collides with, as far as a full cell's column and glide zone
     * reach.
     *
     * @param level the level
     * @param feet  the point, a player's feet
     * @param step  the updraft step, naming the glide zone's height
     * @return the cell, empty where something solid or no cell stands first
     */
    static Optional<BlockPos> cellUnder(Level level, Vec3 feet, UpdraftStep step) {
        int reach = (int) Math.ceil(UpdraftStep.pushHeight(VolumeCellBlock.MAX_LAYERS) + step.glideHeight());
        BlockPos.MutableBlockPos at = BlockPos.containing(feet).mutable();
        for (int down = 0; down <= reach; down++) {
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof UpdraftCellBlock) {
                return Optional.of(at.immutable());
            }
            if (!state.getCollisionShape(level, at).isEmpty() || !state.getFluidState().isEmpty()) {
                return Optional.empty();
            }
            at.move(Direction.DOWN);
        }
        return Optional.empty();
    }

    private static boolean movesOnItsOwn(LocalPlayer player) {
        boolean swims = player.isInWater() || player.isInLava();
        return !player.getAbilities().flying && !swims && !player.isPassenger() && !player.isFallFlying();
    }
}
