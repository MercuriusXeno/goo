package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;

/**
 * The state a ability block carries through a fall, taken when its support
 * breaks and restored where it lands: its whole saved state, so the landed
 * marker runs on with the same ability's program from where it stood
 * (decisions no-throw-without-ability, splat-runs-the-program-no-fuse).
 *
 * @param gooType   the marker's goo type, which its flight draws
 * @param abilityId the ability the marker runs, which its flight draws
 * @param saved     the marker's saved state, its running program included
 */
public record AbilityBlockSnapshot(ResourceKey<GooTypeDefinition> gooType, String abilityId, CompoundTag saved) {

    /**
     * Takes the snapshot of a standing marker.
     *
     * @param be the marker's block entity
     * @return the marker's state
     */
    public static AbilityBlockSnapshot of(AbilityBlockEntity be) {
        CompoundTag saved = be.getLevel() != null ? be.saveCustomOnly(be.getLevel().registryAccess()) : new CompoundTag();
        return new AbilityBlockSnapshot(be.getGooType(), be.getAbilityId(), saved);
    }
}
