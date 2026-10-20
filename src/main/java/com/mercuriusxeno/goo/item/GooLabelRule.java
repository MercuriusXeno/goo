package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import java.util.Set;

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
        Set<ResourceKey<GooTypeDefinition>> goo = content.gooVolumes().keySet();
        return canLabel(goo, content.portions().size() > goo.size(), meta.gooLabel(), type);
    }

    /**
     * Whether a label of a type goes onto a vat; water counts as another fluid.
     *
     * @param contents the vat's goo
     * @param water    the water the vat holds, in mB
     * @param existing the vat's goo label type, or null where it carries none
     * @param type     the label's goo type
     * @return true where the vat holds no goo label and is empty or holds that type alone
     */
    public static boolean canLabelVat(GooContents contents, int water,
                                      @Nullable ResourceKey<GooTypeDefinition> existing,
                                      ResourceKey<GooTypeDefinition> type) {
        return canLabel(contents.getAll().keySet(), water > 0, existing, type);
    }

    /**
     * The one rule for every vessel.
     * decision label-goes-on-an-empty-or-matching-vessel
     *
     * @param gooHeld         the goo types the vessel holds
     * @param holdsOtherFluid whether the vessel holds a vanilla fluid
     * @param existing        the vessel's goo label type, or null
     * @param type            the label's goo type
     * @return true where the label goes on
     */
    private static boolean canLabel(Set<ResourceKey<GooTypeDefinition>> gooHeld, boolean holdsOtherFluid,
                                    @Nullable ResourceKey<GooTypeDefinition> existing,
                                    ResourceKey<GooTypeDefinition> type) {
        return existing == null && !holdsOtherFluid && (gooHeld.isEmpty() || gooHeld.equals(Set.of(type)));
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
        return admitsType(label, CanisterFluidContent.gooTypeOf(candidate));
    }

    /**
     * The same predicate over a goo type, for inserts that name the type rather than a fluid.
     *
     * @param label the vessel's goo label type, or null where it carries none
     * @param type  the goo type arriving, or null for a vanilla fluid
     * @return true where the vessel carries no label or the type is the label's
     */
    public static boolean admitsType(@Nullable ResourceKey<GooTypeDefinition> label,
                                     @Nullable ResourceKey<GooTypeDefinition> type) {
        return label == null || label.equals(type);
    }
}
