package com.mercuriusxeno.goo;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
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
    private static final String TIER = "Puddle";

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
     * The blob name is the blob key applied to the type's name, so the
     * item shows "[Type] Blob" for any registered type.
     */
    @Test
    void blobNameResolvesTypeName() {
        TranslatableContents blob = translatable(GooTypeNames.blobName(GooTypes.BLAZE));
        assertEquals(GooTypeNames.BLOB, blob.getKey());
        assertEquals("goo.type.blaze", argumentKey(blob, 0));

        TranslatableContents datapack = translatable(GooTypeNames.blobName(SEVENTEENTH));
        assertEquals("goo.type.gootest.seventeenth", argumentKey(datapack, 0));
    }

    /**
     * The omniblob name takes the type name then the tier, and the bucket
     * name takes the type name.
     */
    @Test
    void omniblobAndBucketNamesResolveTypeName() {
        TranslatableContents omniblob = translatable(GooTypeNames.omniblobName(GooTypes.FROST, TIER));
        assertEquals(GooTypeNames.OMNIBLOB, omniblob.getKey());
        assertEquals("goo.type.frost", argumentKey(omniblob, 0));
        assertEquals(TIER, omniblob.getArgs()[1]);

        TranslatableContents bucket = translatable(GooTypeNames.bucketName(GooTypes.ROCK));
        assertEquals(GooTypeNames.BUCKET, bucket.getKey());
        assertEquals("goo.type.rock", argumentKey(bucket, 0));
    }

    /**
     * An item carrying no type names itself untyped rather than failing.
     */
    @Test
    void untypedItemNamesItselfUntyped() {
        assertEquals(GooTypeNames.UNTYPED, argumentKey(translatable(GooTypeNames.blobName(null)), 0));
    }

    private static TranslatableContents translatable(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents());
    }

    private static String argumentKey(TranslatableContents contents, int index) {
        Component argument = assertInstanceOf(Component.class, contents.getArgs()[index]);
        return translatable(argument).getKey();
    }
}
