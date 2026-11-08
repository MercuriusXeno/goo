package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.deep.SculkerHearing;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries each sound the server plays to the Sculkers submerged in blocks
 * that vanilla's range leaves it short of, out to render distance.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSculkerHearingMixin {

    /**
     * Carries a sound played at a point.
     *
     * @param except the player vanilla leaves out
     * @param x      where it plays, east
     * @param y      where it plays, up
     * @param z      where it plays, south
     * @param sound  the sound
     * @param source its category
     * @param volume its volume
     * @param pitch  its pitch
     * @param seed   its seed
     * @param info   the callback
     */
    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void goo$carryToSculkers(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound,
                                     SoundSource source, float volume, float pitch, long seed, CallbackInfo info) {
        SculkerHearing.carry((ServerLevel) (Object) this, except, new Vec3(x, y, z), sound.value().getRange(volume), volume);
    }

    /**
     * Carries a sound played from an entity.
     *
     * @param except       the player vanilla leaves out
     * @param sourceEntity the entity it plays from
     * @param sound        the sound
     * @param source       its category
     * @param volume       its volume
     * @param pitch        its pitch
     * @param seed         its seed
     * @param info         the callback
     */
    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void goo$carryEntitySoundToSculkers(@Nullable Entity except, Entity sourceEntity, Holder<SoundEvent> sound,
                                                SoundSource source, float volume, float pitch, long seed,
                                                CallbackInfo info) {
        SculkerHearing.carry((ServerLevel) (Object) this, except, sourceEntity.position(),
                sound.value().getRange(volume), volume);
    }
}
