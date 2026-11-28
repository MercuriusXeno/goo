package com.mercuriusxeno.goo.workbench;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The frozen sample of the last curated base_values.json and the drafts the workbench tests edit from it.
 */
public final class SampleDrafts {
    private static final String SAMPLE = "/workbench/sample_base_values.json";
    private static final String SHIPPED = "/data/goo/goo_values/base_values.json";
    private static final String DIAMOND_ENTRY =
            "\"diamond\": {\"crystal\": \"$ultra\", \"osseous\": \"24 $wealth.osseous\"}";
    private static final String DIAMOND_WITHOUT_OSSEOUS =
            "\"diamond\": {\"crystal\": \"$ultra\"}";

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
     * @return the shipped base_values.json as the model operation answers it, for tests outside this package
     * @throws IOException where the file is off the classpath
     */
    public static String shippedModelJson() throws IOException {
        try (InputStream in = SampleDrafts.class.getResourceAsStream(SHIPPED)) {
            String text = new String(Objects.requireNonNull(in, SHIPPED).readAllBytes(), StandardCharsets.UTF_8);
            return ModelJson.toJson(BaseValuesModel.parse(text), "base_values.json");
        }
    }

    /**
     * @return the sample with diamond valued in crystal alone, no osseous
     * @throws IOException where the sample is off the classpath
     */
    public static String diamondLackingOsseous() throws IOException {
        String sample = sample();
        if (!sample.contains(DIAMOND_ENTRY)) {
            throw new IllegalStateException("The sample's diamond entry no longer reads " + DIAMOND_ENTRY);
        }
        return sample.replace(DIAMOND_ENTRY, DIAMOND_WITHOUT_OSSEOUS);
    }
}
