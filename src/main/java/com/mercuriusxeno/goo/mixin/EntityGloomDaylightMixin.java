package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.GloomDark;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * A mob standing on Gloom is out of the daylight. Every daylight behavior a
 * mob takes reads the light at its spot through
 * getLightLevelDependentMagicValue: an undead mob's sunburn, a spider
 * dropping its target and refusing a new one, and a monster's idle clock
 * running fast in the light; on Gloom the value reads the dimension's dark,
 * so each behaves as it does at night.
 * gloom-spawns-regardless-of-light-and-mobs-on-it-ignore-daylight
 */
@Mixin(Entity.class)
public abstract class EntityGloomDaylightMixin {

    /**
     * Reads the dark where the entity stands on Gloom.
     *
     * @param value vanilla's light value at the entity's spot
     * @return the light value its daylight behaviors read
     */
    @ModifyReturnValue(method = "getLightLevelDependentMagicValue()F", at = @At("RETURN"))
    private float goo$gloomReadsTheDark(float value) {
        Entity self = (Entity) (Object) this;
        return GloomDark.daylightValue(self.level(), self.blockPosition(), value);
    }
}
