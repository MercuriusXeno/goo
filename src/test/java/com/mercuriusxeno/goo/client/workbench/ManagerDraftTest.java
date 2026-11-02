package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the change set the draft posts against the members the last model reply or save answered, with no Screen
 * (decision requests-post-only-the-changed-members).
 */
class ManagerDraftTest {
    private static final String MODEL = """
            {"path": "base_values.json", "members": [
              {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "4", "entries": [],
               "leading": null, "trailing": null, "closingGap": null},
              {"key": "torch", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
               "leading": null, "trailing": null, "closingGap": null},
              {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                 {"key": "base", "keySeparator": null, "kind": "VALUE", "text": "12", "entries": [],
                  "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null}
            ]}
            """;

    private ManagerDraft draft;

    @BeforeEach
    void readTheModel() {
        draft = new ManagerDraft();
        draft.read(MODEL);
    }

    private List<String> changedKeys() {
        return draft.changeSet().getAsJsonArray("changed").asList().stream()
                .map(member -> member.getAsJsonObject().get("key").getAsString()).toList();
    }

    private List<String> removedKeys() {
        return draft.changeSet().getAsJsonArray("removed").asList().stream().map(JsonElement::getAsString).toList();
    }

    private JsonObject member(int index) {
        return draft.members().get(index).getAsJsonObject();
    }

    /**
     * A draft read from a model and left alone posts no member and no removal.
     */
    @Test
    void unchangedDraftPostsAnEmptyChangeSet() {
        assertEquals(List.of(), changedKeys());
        assertEquals(List.of(), removedKeys());
    }

    /**
     * Editing one member's text posts that member alone, as the draft now holds it.
     */
    @Test
    void editedMemberIsTheChangeSetAlone() {
        member(1).addProperty("text", "9");
        assertEquals(List.of("torch"), changedKeys());
        assertEquals("9", draft.changeSet().getAsJsonArray("changed").get(0).getAsJsonObject().get("text")
                .getAsString());
    }

    /**
     * Editing one entry of a section posts the section member whole.
     */
    @Test
    void editedSectionEntryPostsTheSectionWhole() {
        draft.sectionEntries("_constants").get(0).getAsJsonObject().addProperty("text", "13");
        assertEquals(List.of("_constants"), changedKeys());
        assertEquals(1, draft.changeSet().getAsJsonArray("changed").get(0).getAsJsonObject()
                .getAsJsonArray("entries").size());
    }

    /**
     * Removing a member names its key as removed and posts no member.
     */
    @Test
    void removedMemberIsNamedByKey() {
        draft.members().remove(0);
        assertEquals(List.of(), changedKeys());
        assertEquals(List.of("stick"), removedKeys());
    }

    /**
     * A member the draft gains posts with the key it follows, so the server places it where the draft holds it.
     */
    @Test
    void addedMemberPostsTheKeyItFollows() {
        JsonObject bread = member(0).deepCopy();
        bread.addProperty("key", "bread");
        draft.members().add(bread);
        draft.members().set(3, member(2));
        draft.members().set(2, bread);
        JsonObject posted = draft.changeSet().getAsJsonArray("changed").get(0).getAsJsonObject();
        assertEquals("bread", posted.get("key").getAsString());
        assertEquals("torch", posted.get("after").getAsString());
    }

    /**
     * Once a save answers, the draft holds what the server holds and posts an empty change set again.
     */
    @Test
    void saveReplyEmptiesTheChangeSet() {
        member(1).addProperty("text", "9");
        draft.read(draft.text());
        assertEquals(List.of(), changedKeys());
        assertEquals(List.of(), removedKeys());
    }

    /**
     * A rename's or a delete's reply edits the draft without saving it, so its changes still post.
     */
    @Test
    void editedReplyKeepsItsChangesPosting() {
        String renamed = MODEL.replace("\"text\": \"8\"", "\"text\": \"\\\"$standard\\\"\"");
        draft.readEdited(renamed);
        assertEquals(List.of("torch"), changedKeys());
    }
}
