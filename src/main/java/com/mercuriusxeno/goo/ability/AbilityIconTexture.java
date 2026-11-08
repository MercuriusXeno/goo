package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;

/**
 * The texture an ability's icon draws from, which the radial, the crosshair panel and the
 * ability's held effect all draw: the icon the JSON names, else the convention path.
 * decision every-brew-effect-draws-its-abilitys-icon
 */
public final class AbilityIconTexture {

    private static final String ABILITY_ICON_PREFIX = "textures/goo/ability/";
    private static final String ICON_SUFFIX = ".png";
    private static final char NAMESPACE_SEPARATOR = ':';

    private AbilityIconTexture() {
    }

    /**
     * Resolves an ability's icon texture.
     *
     * @param abilityId the ability's id
     * @param override  the icon the JSON names, empty for the convention path
     * @return the icon texture identifier
     */
    public static Identifier of(Identifier abilityId, String override) {
        if (!override.isEmpty()) {
            // diagnose-then-fix-radial-icon-id: a namespaced icon keeps its namespace rather than taking a second goo:
            return override.indexOf(NAMESPACE_SEPARATOR) >= 0 ? Identifier.parse(override)
                    : Identifier.fromNamespaceAndPath(Goo.MODID, override);
        }
        return Identifier.fromNamespaceAndPath(Goo.MODID, ABILITY_ICON_PREFIX + abilityId.getPath() + ICON_SUFFIX);
    }
}
