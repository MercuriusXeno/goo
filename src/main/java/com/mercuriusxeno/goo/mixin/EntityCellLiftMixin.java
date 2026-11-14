package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.CellLift;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A thing moving under its own power with its feet inside a volume cell is
 * lifted onto the cell's top first, so its move steps up rather than sliding
 * along the cells beside.
 * operator report 2026-11-14: hitting goo redirects the runner off the track and hitches it
 */
@Mixin(Entity.class)
public abstract class EntityCellLiftMixin {

    /**
     * Lifts the thing onto a volume cell risen around its feet before it moves.
     *
     * @param type     what moves the thing
     * @param movement the move
     * @param info     the callback
     */
    @Inject(method = "move", at = @At("HEAD"))
    private void goo$liftsOntoRisenCells(MoverType type, Vec3 movement, CallbackInfo info) {
        if (type == MoverType.SELF || type == MoverType.PLAYER) {
            CellLift.liftOntoRisenCells((Entity) (Object) this);
        }
    }
}
