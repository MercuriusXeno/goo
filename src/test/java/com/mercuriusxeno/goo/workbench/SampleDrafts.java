package com.mercuriusxeno.goo.workbench;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The frozen sample of the last curated base_values.json and the drafts the checks' tests edit from it.
 */
public final class SampleDrafts {
    private static final String SAMPLE = "/workbench/sample_base_values.json";
    private static final String DIAMOND_ENTRY =
            "\"diamond\": {\"crystal\": \"$ultra\", \"opulent\": \"24 $wealth.opulent\", \"yore\": \"$base / 4\"}";
    private static final String DIAMOND_WITHOUT_YORE =
            "\"diamond\": {\"crystal\": \"$ultra\", \"opulent\": \"24 $wealth.opulent\"}";

    private SampleDrafts() {
    }

    /**
     * @return the sample's text
     * @throws IOException where the sample is off the classpath
     */
    public static String sample() throws IOException {
        try (InputStream in = SampleDrafts.class.getResourceAsStream(SAMPLE)) {
            return new String(Objects.requireNonNull(in, SAMPLE).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * @return the sample with diamond valued in crystal and opulent alone, no yore
     * @throws IOException where the sample is off the classpath
     */
    public static String diamondLackingYore() throws IOException {
        String sample = sample();
        if (!sample.contains(DIAMOND_ENTRY)) {
            throw new IllegalStateException("The sample's diamond entry no longer reads " + DIAMOND_ENTRY);
        }
        return sample.replace(DIAMOND_ENTRY, DIAMOND_WITHOUT_YORE);
    }
}
