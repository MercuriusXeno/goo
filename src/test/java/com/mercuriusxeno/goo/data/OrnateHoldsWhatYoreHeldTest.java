package com.mercuriusxeno.goo.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The shipped base values hold what the values before yore left hold once each yore term moves onto ornate, or
 * drops where ornate already stood beside it, item by item and type by type. The values before are
 * base_values.json as it stood at 25fb0499b, with shroom and deep moved to standing types as thread
 * sculk-goo-replaces-shroom-and-deep moved them, kept as a fixture. The fixture predates Opulent's rename to Ornate,
 * so its opulent terms read as ornate, and predates zoo leaving, so its zoo terms load as nothing. It predates
 * Dire, so the shipped dire terms, and the items holding dire alone, stand outside the comparison.
 * decision yore-leaves-and-rewind-moves-to-xeno-as-revert
 * decision opulent-is-renamed-ornate
 * decision dire-is-found-in-ominous-and-deadly-things
 */
class OrnateHoldsWhatYoreHeldTest {

    private static final String SHIPPED = "/data/goo/goo_values/base_values.json";
    private static final String BEFORE = "/goo_values/base_values_before_yore_left.json";
    private static final String YORE = "yore";
    private static final String OPULENT = "opulent";
    private static final String ORNATE = "ornate";
    private static final String YORE_TARGET = "-> yore";
    private static final String ORNATE_TARGET = "-> ornate";
    /**
     * Dead coral's nether read coral's zoo, and zoo left the mod; the shipped file writes each amount out, so the
     * fixture's reads do the same. Its other zoo terms load as nothing, as a type the game holds no longer does.
     * decision zoo-leaves-and-zombie-goo-replaces-it-as-z
     */
    private static final Map<String, String> CORAL_ZOO_WRITTEN_OUT = Map.of(
            "\"$coral.zoo\"", "\"2 $base\"",
            "\"$coral_fan.zoo\"", "\"$base\"",
            "\"$coral_block.zoo\"", "\"3 $base\"");

    @Test
    void everyItemHoldsWhatTheOldValuesHoldWithYoreMoved() throws IOException {
        String beforeText = read(BEFORE).replace(OPULENT, ORNATE);
        for (Map.Entry<String, String> coralRead : CORAL_ZOO_WRITTEN_OUT.entrySet()) {
            beforeText = beforeText.replace(coralRead.getKey(), coralRead.getValue());
        }
        JsonElement before = JsonParser.parseString(beforeText);
        Map<Identifier, GooValue> expected = GooBaseValuesPreview.load(yoreMoved(before).toString()).baseValues();
        Map<Identifier, Map<ResourceKey<GooTypeDefinition>, GooFraction>> shipped =
                withoutDire(GooBaseValuesPreview.load(read(SHIPPED)).baseValues());

        assertEquals(expected.keySet(), shipped.keySet());
        List<String> differing = expected.keySet().stream()
                .filter(item -> !expected.get(item).exactAll().equals(shipped.get(item)))
                .map(item -> item + " expected " + expected.get(item) + " but ships " + shipped.get(item))
                .toList();
        assertEquals(List.of(), differing);
    }

    private static Map<Identifier, Map<ResourceKey<GooTypeDefinition>, GooFraction>> withoutDire(
            Map<Identifier, GooValue> values) {
        Map<Identifier, Map<ResourceKey<GooTypeDefinition>, GooFraction>> kept = new HashMap<>();
        values.forEach((item, value) -> {
            Map<ResourceKey<GooTypeDefinition>, GooFraction> amounts = new HashMap<>(value.exactAll());
            amounts.remove(GooTypes.DIRE);
            if (!amounts.isEmpty()) {
                kept.put(item, amounts);
            }
        });
        return kept;
    }

    /**
     * The values with each yore term on ornate: renamed where its entry holds no ornate, dropped where it does,
     * and each process turning goo into yore turning it into ornate.
     */
    private static JsonElement yoreMoved(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject entry = element.getAsJsonObject();
            boolean ornateStands = entry.has(ORNATE);
            JsonObject moved = new JsonObject();
            entry.entrySet().forEach(member -> {
                if (!YORE.equals(member.getKey())) {
                    moved.add(member.getKey(), yoreMoved(member.getValue()));
                } else if (!ornateStands) {
                    moved.add(ORNATE, yoreMoved(member.getValue()));
                }
            });
            return moved;
        }
        if (element.isJsonArray()) {
            JsonArray moved = new JsonArray();
            element.getAsJsonArray().forEach(member -> moved.add(yoreMoved(member)));
            return moved;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(element.getAsString().replace(YORE_TARGET, ORNATE_TARGET));
        }
        return element;
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = OrnateHoldsWhatYoreHeldTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
