package com.mercuriusxeno.goo.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.Optional;

/**
 * The name and the icon an ability shows a player standing in a dimension: the face it gives that dimension where it
 * names one, else its own. Every site a player reads an ability's name or icon by in play resolves through it.
 * decision lunar-reads-as-terran-while-you-are-on-the-moon
 *
 * @param displayName the translation key for the name shown
 * @param icon        the icon texture shown
 */
public record ShownFace(String displayName, Identifier icon) {

    /**
     * The face an ability shows a player standing in a dimension.
     *
     * @param abilityId     the ability's id
     * @param displayName   the ability's own name key
     * @param icon          the ability's own icon path, empty for the convention path
     * @param dimensionFace the face it gives one dimension, empty for none
     * @param standingIn    the dimension the player stands in
     * @return the name key and the icon texture shown
     */
    public static ShownFace of(Identifier abilityId, String displayName, String icon,
            Optional<DimensionFace> dimensionFace, ResourceKey<Level> standingIn) {
        return dimensionFace.filter(face -> face.dimension().equals(standingIn))
                .map(face -> new ShownFace(face.displayName(), AbilityIconTexture.of(abilityId, face.icon())))
                .orElseGet(() -> new ShownFace(displayName, AbilityIconTexture.of(abilityId, icon)));
    }
}
