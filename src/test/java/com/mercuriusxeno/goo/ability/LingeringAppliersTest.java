package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the record remembering who applied each lingering effect: it saves and loads with the afflicted mob,
 * holding each applier's identity. Pure: it encodes through the attachment's codec with no registry behind it.
 * lingering-goo-effect-credits-whoever-applied-it
 */
class LingeringAppliersTest {

    private static final Identifier TOXIN = Identifier.fromNamespaceAndPath("goo", "bio_toxin");
    private static final Identifier THORNS = Identifier.fromNamespaceAndPath("goo", "root_thorns");
    private static final UUID APPLIER = new UUID(0x1234L, 0x5678L);
    private static final UUID ROOTER = new UUID(0x9ABCL, 0xDEF0L);

    @Test
    void appliersSurviveASave() {
        LingeringAppliers appliers = LingeringAppliers.NONE.appliedBy(TOXIN, APPLIER).appliedBy(THORNS, ROOTER);

        JsonElement saved = LingeringAppliers.CODEC.codec().encodeStart(JsonOps.INSTANCE, appliers).getOrThrow();
        LingeringAppliers loaded = LingeringAppliers.CODEC.codec().parse(JsonOps.INSTANCE, saved).getOrThrow();

        assertEquals(appliers, loaded);
        assertEquals(APPLIER, loaded.appliers().get(TOXIN));
    }
}
