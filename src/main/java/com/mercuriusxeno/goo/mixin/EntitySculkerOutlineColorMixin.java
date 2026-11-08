package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.SculkerSenses;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * Draws a remembered entity's outline in sculk teal for the local Sculker,
 * the color its vision lets through bright.
 * sculker-sight-persists-where-echoes-have-touched
 */
@Mixin(Entity.class)
public abstract class EntitySculkerOutlineColorMixin {

    /**
     * Answers the outline color: sculk teal for a remembered entity, its team's otherwise.
     *
     * @param original the team's color
     * @return the outline's color
     */
    @ModifyReturnValue(method = "getTeamColor", at = @At("RETURN"))
    private int goo$sculkTealWhenRemembered(int original) {
        Entity self = (Entity) (Object) this;
        return SculkerSenses.outlines(self) ? SculkerSenses.outlineColor(self) : original;
    }
}
