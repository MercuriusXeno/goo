package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;

/**
 * The shipped abilities with one ability's step fields rewritten, for a
 * gametest that holds a chance or a period at a sure value for one tick so a
 * live path runs once with no wait on a random roll.
 * shatter-last-layer-test-is-made-fully-deterministic
 */
final class HeldAbilitySteps {

    private static final String BEHAVIORS = "behaviors";
    private static final String TYPE = "type";

    private HeldAbilitySteps() {
    }

    /**
     * The shipped abilities with every step of a type in one ability carrying
     * the given fields, by the ability's definition encoded, those steps'
     * fields rewritten and the definition decoded again.
     *
     * @param shipped  the shipped abilities
     * @param ability  the ability whose steps are rewritten
     * @param stepType the type of the steps rewritten
     * @param fields   each field's name and the number it carries
     * @return the abilities with the ability's steps rewritten
     */
    static AbilityRegistry withStepFields(AbilityRegistry shipped, Identifier ability, String stepType,
                                          Map<String, Number> fields) {
        Codec<AbilityDefinition> codec = AbilityDefinition.codecFor(ability);
        JsonObject json = codec.encodeStart(JsonOps.INSTANCE, shipped.getAbility(ability)).getOrThrow()
                .getAsJsonObject();
        for (JsonElement behavior : json.getAsJsonArray(BEHAVIORS)) {
            JsonObject step = behavior.getAsJsonObject();
            if (stepType.equals(step.get(TYPE).getAsString())) {
                fields.forEach(step::addProperty);
            }
        }
        Map<Identifier, AbilityDefinition> abilities = new HashMap<>();
        shipped.all().forEach(held -> abilities.put(held.id(), held));
        abilities.put(ability, codec.parse(JsonOps.INSTANCE, json).getOrThrow());
        return new AbilityRegistry(abilities);
    }
}
