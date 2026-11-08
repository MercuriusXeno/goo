package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.ScryPaintedMobs;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob a Scry front painted is outlined in the sweep's color rather than
 * its team's, on the caster's client alone; the outline takes its color
 * from the team color and the game has no event to change it.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
@Mixin(Entity.class)
public abstract class EntityScryOutlineColorMixin {

    /**
     * @param cir the callback holding vanilla's team color
     */
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void goo$colorScryPaintedOutline(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() && ScryPaintedMobs.CLIENT.isPaintedNow(self.getId())) {
            cir.setReturnValue(ScryPaintedMobs.CLIENT.outlineNow(self.getId()));
        }
    }
}
