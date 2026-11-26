package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
 * gametests-waiting-on-a-random-draw-are-found-and-made-deterministic
 */
public final class HeldAbilitySteps {

    private static final String TYPE = "type";

    private HeldAbilitySteps() {
    }

    /**
     * The shipped abilities with every step of a type in one ability carrying
     * the given fields, by the ability's definition encoded, those steps'
     * fields rewritten and the definition decoded again. A step is reached
     * wherever it stands: in the ability's behaviors or its on_blocks, or in
     * the steps another step holds, as a branch's then.
     *
     * @param shipped  the shipped abilities
     * @param ability  the ability whose steps are rewritten
     * @param stepType the type of the steps rewritten
     * @param fields   each field's name and the number or expression it carries
     * @return the abilities with the ability's steps rewritten
     */
    public static AbilityRegistry withStepFields(AbilityRegistry shipped, Identifier ability, String stepType,
                                          Map<String, ?> fields) {
        Codec<AbilityDefinition> codec = AbilityDefinition.codecFor(ability);
        JsonObject json = codec.encodeStart(JsonOps.INSTANCE, shipped.getAbility(ability)).getOrThrow()
                .getAsJsonObject();
        rewriteSteps(json, stepType, fields);
        Map<Identifier, AbilityDefinition> abilities = new HashMap<>();
        shipped.all().forEach(held -> abilities.put(held.id(), held));
        abilities.put(ability, codec.parse(JsonOps.INSTANCE, json).getOrThrow());
        return new AbilityRegistry(abilities);
    }

    // Rewrites the fields of every object of the step type under a JSON element, the element itself included.
    private static void rewriteSteps(JsonElement element, String stepType, Map<String, ?> fields) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(member -> rewriteSteps(member, stepType, fields));
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            object.entrySet().forEach(entry -> rewriteSteps(entry.getValue(), stepType, fields));
            JsonElement type = object.get(TYPE);
            if (type != null && type.isJsonPrimitive() && stepType.equals(type.getAsString())) {
                fields.forEach((name, value) -> object.add(name, value instanceof Number number
                        ? new JsonPrimitive(number) : new JsonPrimitive(value.toString())));
            }
        }
    }
}
