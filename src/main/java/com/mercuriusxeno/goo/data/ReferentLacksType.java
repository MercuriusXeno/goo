package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;

/**
 * An expression read a goo type its referent, a prefab or an item, holds no amount of, so the entry holding it cannot
 * be valued: reading the type as zero let $stem.leaf on a stem holding shroom and nether value stripped stems at
 * nothing and leak nether into sticks and fences. The loader skips the entry and names the referent and the type
 * (decision type-the-referent-lacks-loads-no-value-and-reads-a-red-line).
 */
final class ReferentLacksType extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private static final String HOLDS_NO = " holds no ";

    /**
     * @param referent the prefab or item read, as the expression spelled it
     * @param type     the goo type read, which the referent holds no amount of
     */
    ReferentLacksType(String referent, ResourceKey<GooTypeDefinition> type) {
        super(message(referent, GooTypes.id(type)), null, false, false);
    }

    /**
     * @param referent the prefab or item read
     * @param type     the goo type's id
     * @return the error naming both, "stem holds no leaf"
     */
    static String message(String referent, String type) {
        return referent + HOLDS_NO + type;
    }

    /**
     * @param referent the prefab or item read, as the expression spelled it
     * @param value    the referent's value
     * @param type     the goo type read
     * @return the referent's exact amount of the type
     * @throws ReferentLacksType where the value holds no amount of the type
     */
    static GooFraction amountHeld(String referent, GooValue value, ResourceKey<GooTypeDefinition> type) {
        GooFraction amount = value.exactAll().get(type);
        if (amount == null) {
            throw new ReferentLacksType(referent, type);
        }
        return amount;
    }
}
