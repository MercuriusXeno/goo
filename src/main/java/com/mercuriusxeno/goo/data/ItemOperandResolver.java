package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import java.util.Map;

/**
 * Resolves item reference operands in scalar expressions. Handles dot-notation (e.g. minecraft:coal.blaze) to
 * extract a single goo type's exact amount from an item's GooValue
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
final class ItemOperandResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DOT = ".";
    private static final String COLON = ":";
    private static final int NO_DOT = -1;
    private static final String WARN_ITEM_NO_TYPE = "Item reference without .type in scalar expression: {}";

    private ItemOperandResolver() {
    }

    /**
     * Resolves an item reference operand: dot-notation (e.g. minecraft:coal.blaze) extracts
     * a single type, bare namespaced IDs without .type produce a warning.
     *
     * @param token      the item reference token
     * @param baseValues item values for lookups
     * @return the resolved exact amount
     */
    static GooFraction resolveItemOperand(String token, Map<Identifier, GooValue> baseValues) {
        if (token.contains(DOT)) {
            GooFraction extracted = tryItemDotExtraction(token, baseValues);
            if (extracted != null) {
                return extracted;
            }
        }
        return resolveBareName(token);
    }

    // A namespaced id missing its .type warns and answers zero; a plain number reads as itself.
    private static GooFraction resolveBareName(String token) {
        if (token.contains(COLON)) {
            LOGGER.warn(WARN_ITEM_NO_TYPE, token);
            return GooFraction.ZERO;
        }
        return GooFraction.of(Long.parseLong(token));
    }

    // The item's one type, or null where the suffix names no goo type or no valid dot stands.
    private static @Nullable GooFraction tryItemDotExtraction(String token, Map<Identifier, GooValue> baseValues) {
        int dotIdx = findTypeDot(token);
        if (dotIdx < 0) {
            return null;
        }
        ResourceKey<GooTypeDefinition> type;
        try {
            type = GooTypes.parseKnown(token.substring(dotIdx + 1));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return lookupItemValue(token.substring(0, dotIdx), type, baseValues);
    }

    // The dot separating the item id from its type suffix, NO_DOT at either edge or before the colon.
    private static int findTypeDot(String token) {
        int dotIdx = token.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx >= token.length() - 1) {
            return NO_DOT;
        }
        int colonIdx = token.indexOf(COLON.charAt(0));
        return (colonIdx >= 0 && dotIdx <= colonIdx) ? NO_DOT : dotIdx;
    }

    /**
     * @param itemId     the namespaced item identifier string
     * @param type       the goo type to extract
     * @param baseValues item values for lookups
     * @return the item's exact amount for the given type
     * @throws UnresolvedItemReference where the item holds no value, which leaves the whole entry unvalued
     * @throws ReferentLacksType       where the item's value holds no amount of the type
     */
    private static GooFraction lookupItemValue(String itemId, ResourceKey<GooTypeDefinition> type,
                                               Map<Identifier, GooValue> baseValues) {
        GooValue value = baseValues.get(Identifier.parse(itemId));
        if (value == null) {
            throw new UnresolvedItemReference(itemId);
        }
        return ReferentLacksType.amountHeld(itemId, value, type);
    }
}
