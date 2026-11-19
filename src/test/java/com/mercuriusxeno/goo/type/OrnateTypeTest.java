package com.mercuriusxeno.goo.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.data.TestRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ornate stands where Opulent stood: a bundled type named Ornate that gold and dyes yield.
 * decision opulent-is-renamed-ornate
 */
class OrnateTypeTest {

    private static final ResourceKey<GooTypeDefinition> ORNATE = GooTypes.bundled("ornate");
    private static final String SHIPPED_VALUES = "/data/goo/goo_values/base_values.json";
    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final Identifier GOLD_INGOT = Identifier.parse("minecraft:gold_ingot");
    private static final Identifier RED_DYE = Identifier.parse("minecraft:red_dye");
    private static final String DYES_TAG = "dyes";

    @Test
    void ornateIsBundledInOpulentsPlace() {
        assertTrue(GooTypes.BUNDLED.contains(ORNATE), "ornate is not a bundled type");
        assertFalse(GooTypes.BUNDLED.contains(GooTypes.bundled("opulent")), "opulent is still bundled");
    }

    @Test
    void ornateTypeNameReadsOrnate() throws IOException {
        JsonObject lang = JsonParser.parseString(read(EN_US)).getAsJsonObject();
        assertEquals("Ornate", lang.get("goo.type.ornate").getAsString());
    }

    /**
     * The shipped values hold gold nuggets rather than gold ingots, so the ingot takes its value from the nine-nugget
     * recipe the game derives it through.
     */
    @Test
    void goldIngotAndRedDyeYieldOrnate() throws IOException {
        String[] nugget = TestRecipeBuilder.slot("minecraft:gold_nugget");
        RecipeInput ingotFromNuggets = TestRecipeBuilder.recipe(GOLD_INGOT.toString(), 1,
                nugget, nugget, nugget, nugget, nugget, nugget, nugget, nugget, nugget);
        Map<Identifier, GooValue> shipped = GooBaseValuesPreview.derive(read(SHIPPED_VALUES), List.of(ingotFromNuggets),
                tag -> DYES_TAG.equals(tag.getPath()) ? Set.of(RED_DYE) : Set.of()).effective();
        assertTrue(shipped.get(GOLD_INGOT).exactAll().containsKey(ORNATE), () -> "gold ingot ships " + shipped.get(GOLD_INGOT));
        assertTrue(shipped.get(RED_DYE).exactAll().containsKey(ORNATE), () -> "red dye ships " + shipped.get(RED_DYE));
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = OrnateTypeTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
