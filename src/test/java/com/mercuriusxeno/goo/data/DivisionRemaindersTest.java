package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for DivisionRemainders: which constant or item and which goo type a parse of base_values
 * attributes each expression remainder to.
 */
class DivisionRemaindersTest {

    private static List<DivisionRemainders.Remainder> remaindersOfParsing(String json) {
        JsonObject merged = JsonParser.parseString(json).getAsJsonObject();
        try (DivisionRemainders.Recording recording = DivisionRemainders.record()) {
            GooValueLoader.parseMergedValues(merged, GooValueLoader.ParseState.fresh());
            return recording.remainders();
        }
    }

    @Test
    void scalarConstantRemainderFallsInTheTypeConsumingIt() {
        List<DivisionRemainders.Remainder> remainders = remaindersOfParsing("""
                {"_constants": {"base": 4, "eighth": "$base / 8"},
                 "minecraft:cobblestone": {"rock": "$eighth"}}""");
        assertEquals(List.of(new DivisionRemainders.Remainder("$eighth", "$base / 8", GooTypes.ROCK, 8, 4)),
                remainders);
    }

    @Test
    void treeConstantRemainderFallsInItsOwnType() {
        List<DivisionRemainders.Remainder> remainders = remaindersOfParsing("""
                {"_constants": {"base": 4, "fuel": {"blaze": "$base / 8"}}}""");
        assertEquals(List.of(new DivisionRemainders.Remainder("$fuel", "$base / 8", GooTypes.BLAZE, 8, 4)),
                remainders);
    }

    @Test
    void itemRemainderNamesTheItem() {
        List<DivisionRemainders.Remainder> remainders = remaindersOfParsing("""
                {"_constants": {"base": 6},
                 "minecraft:stone": {"rock": "$base"},
                 "minecraft:gravel": "minecraft:stone / 4"}""");
        assertEquals(List.of(new DivisionRemainders.Remainder(
                "minecraft:gravel", "minecraft:stone / 4", GooTypes.ROCK, 4, 2)), remainders);
    }

    @Test
    void nothingRecordsOutsideARecording() {
        JsonObject merged = JsonParser.parseString("""
                {"_constants": {"base": 4, "eighth": "$base / 8"},
                 "minecraft:cobblestone": {"rock": "$eighth"}}""").getAsJsonObject();
        GooValueLoader.parseMergedValues(merged, GooValueLoader.ParseState.fresh());
        try (DivisionRemainders.Recording recording = DivisionRemainders.record()) {
            assertEquals(List.of(), recording.remainders());
        }
    }
}
