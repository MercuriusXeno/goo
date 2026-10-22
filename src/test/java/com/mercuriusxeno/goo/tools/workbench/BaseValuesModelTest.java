package com.mercuriusxeno.goo.tools.workbench;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the workbench's model of base_values.json on the shipped file: a parse and serialize hands the
 * file back byte for byte, and an edit to one entry's text changes that line alone
 * (decision workbench-authors-base-values-whole).
 */
class BaseValuesModelTest {

    private static final String SHIPPED = "/data/goo/goo_values/base_values.json";

    static byte[] shippedBytes() throws IOException {
        try (InputStream stream = BaseValuesModelTest.class.getResourceAsStream(SHIPPED)) {
            return stream.readAllBytes();
        }
    }

    /**
     * Every top-level section comes back in file order, and the serialized text is the file.
     */
    @Test
    void shippedFileRoundTripsUnchanged() throws IOException {
        byte[] shipped = shippedBytes();
        BaseValuesModel model = BaseValuesModel.parse(new String(shipped, StandardCharsets.UTF_8));
        assertArrayEquals(shipped, model.serialize().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Rewriting one constant's text changes the line that constant stands on and no other.
     */
    @Test
    void oneEditChangesOneLine() throws IOException {
        String shipped = new String(shippedBytes(), StandardCharsets.UTF_8);
        BaseValuesModel model = BaseValuesModel.parse(shipped);
        BaseValuesModel edited = model.withEntryText("_constants", "low", "\"5 $base\"");
        List<String> before = List.of(shipped.split("\r\n", -1));
        List<String> after = List.of(edited.serialize().split("\r\n", -1));
        assertEquals(before.size(), after.size());
        List<String> changed = new ArrayList<>();
        for (int line = 0; line < before.size(); line++) {
            if (!before.get(line).equals(after.get(line))) {
                changed.add(after.get(line));
            }
        }
        assertEquals(List.of("        \"low\": \"5 $base\","), changed);
    }
}
