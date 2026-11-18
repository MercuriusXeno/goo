package com.mercuriusxeno.goo.ability;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * A thrown metal spike trap that lingers as a field effect, the program
 * Urchin shipped before Spikes became metal's world goo, kept as a test
 * fixture so the field effect's tests run on a real trap's timings
 * (decision metal-world-goo-is-named-spikes).
 */
public final class SpikeTrapFixture {

    /** The fixture's name, the file name an ability file would carry. */
    public static final String NAME = "test_spike_trap";
    private static final String RESOURCE = NAME + ".json";

    private SpikeTrapFixture() {
    }

    /**
     * @return the spike trap, decoded as the loader decodes an ability file
     */
    public static AbilityDefinition definition() {
        try (InputStream in = SpikeTrapFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Classpath holds no " + RESOURCE + " beside " + SpikeTrapFixture.class);
            }
            return AbilityJson.decodeText(NAME, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
