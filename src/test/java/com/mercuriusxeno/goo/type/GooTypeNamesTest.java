package com.mercuriusxeno.goo.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.item.GooFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests that a generic goo item's name component resolves the type it
 * carries: the type's translation key is the argument of the item's key,
 * a bundled type keeps the key the enum spelled, and a datapack type's key
 * carries its namespace.
 */
class GooTypeNamesTest {

    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "seventeenth"));
    private static final String LANG_PATH = "/assets/goo/lang/en_us.json";

    /**
     * A bundled type's translation key is the one the lang file spells.
     */
    @Test
    void bundledTypeKeepsEnumTranslationKey() {
        assertEquals("goo.type.blaze", GooTypeNames.translationKey(GooTypes.BLAZE));
    }

    /**
     * A datapack type's translation key carries its namespace so two packs
     * with one id never share a name.
     */
    @Test
    void datapackTypeKeyCarriesNamespace() {
        assertEquals("goo.type.gootest.seventeenth", GooTypeNames.translationKey(SEVENTEENTH));
    }

    /**
     * The goo name and the bucket name each take the type name alone.
     */
    @Test
    void gooAndBucketNamesResolveTypeName() {
        TranslatableContents goo = translatable(GooTypeNames.gooName(GooTypes.FROST));
        assertEquals(GooTypeNames.GOO, goo.getKey());
        assertEquals("goo.type.frost", argumentKey(goo, 0));
        assertEquals(1, goo.getArgs().length);

        TranslatableContents datapack = translatable(GooTypeNames.gooName(SEVENTEENTH));
        assertEquals("goo.type.gootest.seventeenth", argumentKey(datapack, 0));

        TranslatableContents bucket = translatable(GooTypeNames.bucketName(GooTypes.ROCK));
        assertEquals(GooTypeNames.BUCKET, bucket.getKey());
        assertEquals("goo.type.rock", argumentKey(bucket, 0));
    }

    /**
     * An item carrying no type names itself untyped rather than failing.
     */
    @Test
    void untypedItemNamesItselfUntyped() {
        assertEquals(GooTypeNames.UNTYPED, argumentKey(translatable(GooTypeNames.gooName(null)), 0));
    }

    /**
     * A blaze goo stack of 16000 reads "Blaze Goo" through the lang file and
     * "16K" on its slot (decision amounts-format-by-magnitude-alone).
     */
    @Test
    void blazeGooAtSixteenThousandReadsBlazeGooOverSixteenK() throws IOException {
        JsonObject lang;
        try (InputStream in = GooTypeNamesTest.class.getResourceAsStream(LANG_PATH);
             Reader reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        TranslatableContents name = translatable(GooTypeNames.gooName(GooTypes.BLAZE));
        String typeName = lang.get(argumentKey(name, 0)).getAsString();
        assertEquals("Blaze Goo", String.format(lang.get(name.getKey()).getAsString(), typeName));
        assertEquals("16K", GooFormat.formatAmount(16_000));
    }

    private static TranslatableContents translatable(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents());
    }

    private static String argumentKey(TranslatableContents contents, int index) {
        Component argument = assertInstanceOf(Component.class, contents.getArgs()[index]);
        return translatable(argument).getKey();
    }
}
