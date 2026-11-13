package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A glove selection stores its goo type as an id that reads back to the same key through its persistent codec, a
 * datapack type's in another namespace included.
 */
class GloveSelectionTest {

    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH = ResourceKey.create(GooTypes.REGISTRY,
            Identifier.fromNamespaceAndPath("gootest", "seventeenth"));

    @Test
    void aDatapackTypesSelectionReadsBackItsTypeThroughTheCodec() {
        GloveSelection selection = GloveSelection.ofAbility(SEVENTEENTH,
                Identifier.fromNamespaceAndPath("gootest", "seventeenth_probe"));

        GloveSelection loaded = GloveSelection.CODEC.parse(JsonOps.INSTANCE,
                GloveSelection.CODEC.encodeStart(JsonOps.INSTANCE, selection).getOrThrow()).getOrThrow();

        assertEquals(SEVENTEENTH, loaded.getGooType());
    }
}
