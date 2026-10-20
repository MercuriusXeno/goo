package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * The goo label's two rules over a vessel: when a label goes on, and what a
 * labeled vessel takes in.
 */
public final class GooLabelRule {

    private GooLabelRule() {
    }

    /**
     * Whether a label of a type goes onto a canister.
     *
     * @param content the canister's content
     * @param meta    the canister's metadata
     * @param type    the label's goo type
     * @return true where the canister holds no goo label and is empty or holds that type alone
     */
    public static boolean canLabel(CanisterFluidContent content, CanisterMetadata meta,
                                   ResourceKey<GooTypeDefinition> type) {
        // decision label-goes-on-an-empty-or-matching-vessel
        return meta.gooLabel() == null && (content.isEmpty()
                || content.portions().size() == 1 && type.equals(content.portions().getFirst().gooType()));
    }

    /**
     * The one predicate every labeled vessel's inserts share.
     *
     * @param label     the vessel's goo label type, or null where it carries none
     * @param candidate the fluid arriving
     * @return true where the vessel carries no label or the fluid is goo of the label's type
     */
    public static boolean admits(@Nullable ResourceKey<GooTypeDefinition> label, FluidResource candidate) {
        // decision labeled-vessel-accepts-only-its-label-type
        return label == null || label.equals(CanisterFluidContent.gooTypeOf(candidate));
    }
}
