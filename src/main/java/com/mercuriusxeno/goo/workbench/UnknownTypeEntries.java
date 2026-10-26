package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The datafix a draft load runs: each item, group and prefab entry whose text names a goo type outside the set the
 * game holds drops whole, and every other member stands as the file spelled it, so a retired type never reaches the
 * manager and no list of retired types exists (decision draft-load-drops-entries-naming-an-unknown-type).
 */
final class UnknownTypeEntries {
    private static final String CONSTANTS = "_constants";

    private UnknownTypeEntries() {
    }

    /**
     * @param model the draft's model
     * @param known the goo types the game holds
     * @return the model without each entry naming another type, the same model where none does
     */
    static BaseValuesModel dropped(BaseValuesModel model, Collection<ResourceKey<GooTypeDefinition>> known) {
        List<BaseValuesModel.Member> kept = new ArrayList<>();
        boolean changed = false;
        for (BaseValuesModel.Member member : model.members()) {
            if (member.kind() == BaseValuesModel.Kind.VALUE && namesUnknownType(member.text(), known)) {
                changed = true;
                continue;
            }
            BaseValuesModel.Member fixed = CONSTANTS.equals(member.key()) ? withKnownPrefabs(member, known) : member;
            changed |= fixed != member;
            kept.add(fixed);
        }
        return changed ? model.withMembers(kept) : model;
    }

    /**
     * @param constants the _constants section
     * @param known     the goo types the game holds
     * @return the section without each prefab naming another type, the same section where none does
     */
    private static BaseValuesModel.Member withKnownPrefabs(BaseValuesModel.Member constants,
                                                           Collection<ResourceKey<GooTypeDefinition>> known) {
        List<BaseValuesModel.Member> prefabs = constants.entries().stream()
                .filter(prefab -> !namesUnknownType(prefab.text(), known)).toList();
        return prefabs.size() == constants.entries().size() ? constants : constants.withEntries(prefabs);
    }

    private static boolean namesUnknownType(String text, Collection<ResourceKey<GooTypeDefinition>> known) {
        try {
            return GooTypeNames.namedIn(JsonParser.parseString(text)).stream().map(GooTypes::byId)
                    .anyMatch(type -> type == null || !known.contains(type));
        } catch (JsonParseException e) {
            // Text no JSON reader accepts names no type; the loader's own warnings carry it.
            return false;
        }
    }
}
