package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.CellRiseTolerance;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * The server lets a player's move stand where its only new overlap is a
 * volume cell risen within a step of their feet, a cell that rose before the
 * player's client saw it, rather than snapping the player back and zeroing
 * their speed.
 * operator report 2026-11-14: running on slick and spraying straight ahead hitches several times a second
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerRisenCellMixin {

    /**
     * Re-reads a move vanilla finds newly colliding, setting aside volume cells risen within a step.
     *
     * @param colliding whether vanilla finds the move newly colliding
     * @param level     the level
     * @param entity    the moving player or vehicle
     * @param oldBox    its box before the move
     * @param newX      the move's x
     * @param newY      the move's y
     * @param newZ      the move's z
     * @return true where the move newly collides with anything but a risen cell
     */
    @ModifyReturnValue(method = "isEntityCollidingWithAnythingNew", at = @At("RETURN"))
    private boolean goo$letsARisenCellStand(boolean colliding, LevelReader level, Entity entity, AABB oldBox,
                                            double newX, double newY, double newZ) {
        if (!colliding) {
            return false;
        }
        AABB newBox = entity.getBoundingBox().move(newX - entity.getX(), newY - entity.getY(), newZ - entity.getZ());
        return CellRiseTolerance.collidesBesidesRisenCells(level, entity, oldBox, newBox);
    }
}
