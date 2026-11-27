package com.mercuriusxeno.goo.ability;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * A metal field effect on a real trap's timings, the program Urchin shipped
 * before Spikes became metal's world goo, kept as a test fixture whose
 * program is the field effect itself, so the field effect's tests run on
 * those timings with no block standing it (decisions
 * metal-world-goo-is-named-spikes, ability-marker-leaves-the-mod-whole).
 */
public final class FieldEffectFixture {

    /** The fixture's name, the file name an ability file would carry. */
    public static final String NAME = "field_effect_fixture";
    private static final String RESOURCE = NAME + ".json";

    private FieldEffectFixture() {
    }

    /**
     * @return the field effect, decoded as the loader decodes an ability file
     */
    public static AbilityDefinition definition() {
        try (InputStream in = FieldEffectFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Classpath holds no " + RESOURCE + " beside " + FieldEffectFixture.class);
            }
            return AbilityJson.decodeText(NAME, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
