package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.checks.CheckResult;
import com.mercuriusxeno.goo.workbench.checks.Want;
import java.util.List;

/**
 * The type-source checks' answers as the page's Checks tab reads them, and the acknowledgement Promote takes
 * over failing checks (decision type-source-checks-are-rules-that-gate-promote).
 */
final class ChecksJson {
    private static final String TITLE = "title";
    private static final String PASSED = "passed";
    private static final String WANTS = "wants";
    private static final String ITEM = "item";
    private static final String LACKS = "lacks";
    private static final String ACKNOWLEDGED = "acknowledged";

    private ChecksJson() {
    }

    /**
     * @param results the checks' answers
     * @return each check's title, whether it passed and the items it wants with what each lacks
     */
    static JsonArray toJson(List<CheckResult> results) {
        JsonArray array = new JsonArray();
        for (CheckResult result : results) {
            JsonObject check = new JsonObject();
            check.addProperty(TITLE, result.title());
            check.addProperty(PASSED, result.passed());
            JsonArray wants = new JsonArray();
            for (Want want : result.wants()) {
                JsonObject wanted = new JsonObject();
                wanted.addProperty(ITEM, want.item());
                wanted.addProperty(LACKS, want.lacks());
                wants.add(wanted);
            }
            check.add(WANTS, wants);
            array.add(check);
        }
        return array;
    }

    /**
     * @param body a promote request's body, which may be blank
     * @return whether it acknowledges the failing checks
     */
    static boolean acknowledged(String body) {
        if (body.isBlank()) {
            return false;
        }
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) {
            return false;
        }
        JsonElement acknowledged = parsed.getAsJsonObject().get(ACKNOWLEDGED);
        return acknowledged != null && acknowledged.isJsonPrimitive() && acknowledged.getAsBoolean();
    }
}
