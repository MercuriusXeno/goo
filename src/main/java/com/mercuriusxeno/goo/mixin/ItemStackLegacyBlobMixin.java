package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.item.GooOmniblobItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Normalizes a goo:goo_blob stack saved by an earlier build as it loads
 * (decision blobs-become-omniblobs). The registry alias loads it as an omniblob
 * of count n with no BLOB_VOLUME; the ItemStack codec and stream codec build every
 * loaded stack through the {@code (Holder, int, DataComponentPatch)} constructor,
 * so rewriting it there makes it one omniblob of n x 1,000 mB before any chest,
 * cursor or machine reads it. Copies go through a private constructor and are
 * left alone.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackLegacyBlobMixin {

    /**
     * Rewrites a loaded legacy blob stack into one omniblob of its volume.
     *
     * @param ci the mixin callback info
     */
    @Inject(method = "<init>(Lnet/minecraft/core/Holder;ILnet/minecraft/core/component/DataComponentPatch;)V",
            at = @At("RETURN"))
    private void goo$normalizeLegacyBlob(CallbackInfo ci) {
        GooOmniblobItem.normalizeLegacyStack((ItemStack) (Object) this);
    }
}
