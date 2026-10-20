package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.Optional;

/**
 * Taking a goo label off a canister: the label it yields and the metadata the
 * canister keeps (decision label-comes-off-in-the-crafting-grid).
 */
public final class GooLabelRemoval {

    private GooLabelRemoval() {
    }

    /**
     * A label taken off.
     *
     * @param label the goo type of the label the vessel gives up
     * @param kept  the canister's metadata wearing no goo label, everything else kept
     */
    public record Removal(ResourceKey<GooTypeDefinition> label, CanisterMetadata kept) {
    }

    /**
     * Takes the goo label off a canister's metadata.
     *
     * @param meta the canister's metadata
     * @return the label and the metadata kept, or empty where the canister wears no goo label
     */
    public static Optional<Removal> off(CanisterMetadata meta) {
        return Optional.ofNullable(meta.gooLabel()).map(label -> new Removal(label, meta.withGooLabel(null)));
    }
}
