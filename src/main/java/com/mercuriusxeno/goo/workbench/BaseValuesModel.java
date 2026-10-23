package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * base_values.json as the workbench edits it: every top-level member in file order, each section's
 * entries in file order, each value's text as the file spells it, and the whitespace around each, so a
 * save that changes nothing writes the file back byte for byte (decision workbench-authors-base-values-whole).
 * A value's text is held with its continuation lines indented relative to its entry, so text the page
 * writes lands at the file's indentation, and an entry the page adds takes the file's usual layout.
 *
 * @param members       the top-level members, in file order
 * @param lineSeparator the file's line separator
 * @param indentUnit    one level of the file's indentation
 * @param closingGap    the whitespace before the closing brace
 * @param tail          the text after the closing brace
 */
public record BaseValuesModel(List<Member> members, String lineSeparator, String indentUnit, String closingGap,
                              String tail) {

    private static final String NEWLINE = "\n";
    private static final String CRLF = "\r\n";
    private static final String QUOTE = "\"";
    private static final String COMMA = ",";
    private static final String SECTION_PREFIX = "_";
    private static final String DEFAULT_INDENT = "    ";
    private static final String DEFAULT_KEY_SEPARATOR = ": ";
    private static final String STRUCTURAL_END = ",}]\n";
    private static final String CONSTANTS = "_constants";
    // The sections the shipped file closes with; a new item entry stands above them.
    private static final Set<String> CLOSING_SECTIONS = Set.of("_restricted", "_conversions", "_post_conversions");
    private static final String GROUPS = "_groups";
    private static final Set<String> PROCESS_SECTIONS = Set.of("_conversions", "_post_conversions");
    // An assignment stacks a named process as @name.
    private static final String STACK_PREFIX = "@";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String NAMESPACE_SEPARATOR = ":";
    private static final String CONSTANT_PREFIX = "$";
    // A #group or tag key: both read as a #name chip.
    private static final String GROUP_PREFIX = "#";
    private static final String EMPTY = "";
    private static final String OPEN_BRACE = "{";
    private static final String SINGLE_QUOTE = "'";
    // Ends a $name reference so $iron does not also match $iron_ingot.
    private static final String NAME_END = "(?![A-Za-z0-9_])";
    private static final String NO_ENTRY = "No entry ";
    private static final String IN_SECTION = " in ";
    private static final String NO_SECTION = "The file holds no section ";
    private static final String NO_CONSTANT = "No constant is named $";
    private static final String MORE_TEXT = "more text";
    private static final String CONTINUATION_INDENT =
            "a value's continuation lines indented at least as deep as its entry";
    private static final String ERROR_LINE = "base_values.json line ";
    private static final String ERROR_EXPECTED = ": expected ";
    private static final int NOT_FOUND = -1;
    // A negative split limit keeps trailing empty lines, so the text round-trips.
    private static final int KEEP_TRAILING_EMPTY = -1;
    // A backslash and the character it escapes.
    private static final int ESCAPE_WIDTH = 2;

    /**
     * How a top-level member holds its value.
     */
    public enum Kind {
        /**
         * A section object, such as _constants, _groups, _conversions and _post_conversions: entries by key.
         */
        OBJECT,
        /**
         * A section array, such as _restricted: entries without keys.
         */
        ARRAY,
        /**
         * An item or #group entry: a value object, an expression string or "denied", held as text.
         */
        VALUE
    }

    /**
     * A top-level member, or an entry inside a section.
     *
     * @param key          the key as the file spells it, quotes removed, or null for an array's entry
     * @param keySeparator the colon and spaces between the key and the value, or null for the usual ": "
     * @param kind         how the member holds its value; an entry inside a section is a VALUE
     * @param text         the value's text, empty for a section
     * @param entries      a section's entries, empty otherwise
     * @param leading      the line break and indentation before the entry, or null for the usual layout
     * @param trailing     the spaces between the value and its comma, or null for none
     * @param closingGap   a section's whitespace before its closing bracket, or null for the usual layout
     */
    public record Member(@Nullable String key, @Nullable String keySeparator, Kind kind, String text,
                         List<Member> entries, @Nullable String leading, @Nullable String trailing,
                         @Nullable String closingGap) {

        /**
         * @param key  the entry's key, or null for an array's entry
         * @param text the entry's value text
         * @return an entry laid out as the file usually lays one
         */
        public static Member entry(@Nullable String key, String text) {
            return new Member(key, null, Kind.VALUE, text, List.of(), null, null, null);
        }

        /**
         * @param newKey the entry's new key
         * @return this entry under it
         */
        public Member withKey(String newKey) {
            return new Member(newKey, keySeparator, kind, text, entries, leading, trailing, closingGap);
        }

        /**
         * @param newText the value's new text
         * @return this entry holding it
         */
        public Member withText(String newText) {
            return new Member(key, keySeparator, kind, newText, entries, leading, trailing, closingGap);
        }

        /**
         * @param newEntries the section's new entries
         * @return this section holding them
         */
        public Member withEntries(List<Member> newEntries) {
            return new Member(key, keySeparator, kind, text, List.copyOf(newEntries), leading, trailing, closingGap);
        }
    }

    /**
     * @param text the file's text
     * @return the model it holds
     * @throws IllegalArgumentException where the text is not one JSON object laid out one member per line
     */
    public static BaseValuesModel parse(String text) {
        String lineSeparator = text.contains(CRLF) ? CRLF : NEWLINE;
        return new Parser(text.replace(CRLF, NEWLINE)).document(lineSeparator);
    }

    /**
     * @return the file's text
     */
    public String serialize() {
        StringBuilder out = new StringBuilder(OPEN_BRACE);
        appendEntries(out, members, 1);
        out.append(closingGap).append('}').append(tail);
        return out.toString().replace(NEWLINE, lineSeparator);
    }

    /**
     * @param section the section's key
     * @param key     the entry's key
     * @param text    the entry's new text
     * @return this model with that one entry's text replaced
     * @throws IllegalArgumentException where the section holds no such entry
     */
    public BaseValuesModel withEntryText(String section, String key, String text) {
        List<Member> changed = new ArrayList<>();
        boolean found = false;
        for (Member member : members) {
            if (!section.equals(member.key())) {
                changed.add(member);
                continue;
            }
            List<Member> entries = new ArrayList<>();
            for (Member entry : member.entries()) {
                boolean match = key.equals(entry.key());
                found |= match;
                entries.add(match ? entry.withText(text) : entry);
            }
            changed.add(member.withEntries(entries));
        }
        if (!found) {
            throw new IllegalArgumentException(NO_ENTRY + key + IN_SECTION + section);
        }
        return withMembers(changed);
    }

    /**
     * @param changedMembers the members a save sends
     * @return a model holding them in this file's layout
     */
    public BaseValuesModel withMembers(List<Member> changedMembers) {
        return new BaseValuesModel(List.copyOf(changedMembers), lineSeparator, indentUnit, closingGap, tail);
    }

    /**
     * Sets a section's entry, replacing its text where it stands and adding it last where it does not.
     *
     * @param section the section's key
     * @param key     the entry's key
     * @param text    the entry's text
     * @return the model holding it
     * @throws IllegalArgumentException where the file holds no such section
     */
    public BaseValuesModel withEntry(String section, String key, String text) {
        List<Member> changed = new ArrayList<>();
        boolean sectionFound = false;
        for (Member member : members) {
            if (!section.equals(member.key())) {
                changed.add(member);
                continue;
            }
            sectionFound = true;
            List<Member> entries = new ArrayList<>(member.entries());
            int index = indexOfKey(entries, key);
            if (index < 0) {
                entries.add(Member.entry(key, text));
            } else {
                entries.set(index, entries.get(index).withText(text));
            }
            changed.add(member.withEntries(entries));
        }
        if (!sectionFound) {
            throw new IllegalArgumentException(NO_SECTION + section);
        }
        return withMembers(changed);
    }

    /**
     * Sets a top-level item or #group entry, replacing its text where it stands and adding it above the
     * sections that close the file where it does not.
     *
     * @param key  the entry's key
     * @param text the entry's text
     * @return the model holding it
     */
    public BaseValuesModel withTopLevel(String key, String text) {
        List<Member> changed = new ArrayList<>(members);
        int index = indexOfKey(changed, key);
        if (index >= 0) {
            changed.set(index, changed.get(index).withText(text));
            return withMembers(changed);
        }
        int insertAt = changed.size();
        for (int i = changed.size() - 1; i >= 0 && CLOSING_SECTIONS.contains(changed.get(i).key()); i--) {
            insertAt = i;
        }
        changed.add(insertAt, Member.entry(key, text));
        return withMembers(changed);
    }

    /**
     * @param key a top-level entry's key
     * @return the model without that entry
     */
    public BaseValuesModel withoutTopLevel(String key) {
        return withMembers(members.stream().filter(member -> !key.equals(member.key())).toList());
    }

    /**
     * @param key a top-level key
     * @return the member under it, or null where none stands
     */
    public @Nullable Member member(String key) {
        int index = indexOfKey(members, key);
        return index < 0 ? null : members.get(index);
    }

    private static int indexOfKey(List<Member> entries, String key) {
        for (int i = 0; i < entries.size(); i++) {
            if (key.equals(entries.get(i).key())) {
                return i;
            }
        }
        return NOT_FOUND;
    }

    /**
     * Renames a constant and rewrites every reference to it, $name and $name.type alike, across the
     * constants and every entry of the file.
     *
     * @param from the constant's name
     * @param to   its new name
     * @return the renamed model
     */
    public BaseValuesModel renameConstant(String from, String to) {
        Pattern reference = referenceTo(from);
        String replacement = Matcher.quoteReplacement(CONSTANT_PREFIX + to);
        List<Member> renamed = new ArrayList<>();
        for (Member member : members) {
            List<Member> entries = new ArrayList<>();
            for (Member entry : member.entries()) {
                Member rewritten = entry.withText(reference.matcher(entry.text()).replaceAll(replacement));
                boolean renamedConstant = CONSTANTS.equals(member.key()) && from.equals(entry.key());
                entries.add(renamedConstant ? rewritten.withKey(to) : rewritten);
            }
            renamed.add(member.withText(reference.matcher(member.text()).replaceAll(replacement))
                    .withEntries(entries));
        }
        return withMembers(renamed);
    }

    /**
     * What a reader of a constant is, so Used by can draw it (decision used-by-draws-item-images-and-name-chips).
     */
    public enum ReaderKind {
        CONSTANT,
        GROUP,
        ITEM
    }

    /**
     * One reader of a constant.
     *
     * @param id   the reader's key
     * @param kind what it is
     */
    public record Reader(String id, ReaderKind kind) {
    }

    /**
     * @param constant a constant's name
     * @return each constant and entry reading it, in file order: a constant or a section's entry by its key,
     *         a top-level entry by its key
     */
    public List<String> usagesOf(String constant) {
        return readersOf(constant).stream().map(Reader::id).toList();
    }

    /**
     * @param constant a constant's name
     * @return each constant and entry reading it, in file order, each with its kind
     */
    public List<Reader> readersOf(String constant) {
        Pattern reference = referenceTo(constant);
        List<Reader> readers = new ArrayList<>();
        for (Member member : members) {
            for (Member entry : member.entries()) {
                if (entry.key() != null && reference.matcher(entry.text()).find()
                        && !(CONSTANTS.equals(member.key()) && constant.equals(entry.key()))) {
                    readers.add(new Reader(entry.key(), CONSTANTS.equals(member.key()) ? ReaderKind.CONSTANT
                            : kindOfKey(entry.key())));
                }
            }
            if (member.kind() == Kind.VALUE && reference.matcher(member.text()).find()) {
                readers.add(new Reader(member.key(), kindOfKey(member.key())));
            }
        }
        return readers;
    }

    /**
     * The items a constant cascades into, the keys between them followed and left out: a constant reader
     * through its own readers, a #group or tag reader through its members, and a process through the target
     * groups of the assignments stacking it (decision used-by-draws-the-terminus-items-alone).
     *
     * @param constant   a constant's name
     * @param tagMembers each item tag's member ids, keyed by the tag's namespaced id
     * @return the item ids reached, each once, in the order the walk reaches them
     */
    public List<String> terminusOf(String constant, Map<String, List<String>> tagMembers) {
        return new TerminusWalk(tagMembers).from(constant);
    }

    /**
     * One terminus walk: the constants pending, and the constants, groups and processes already followed.
     */
    private final class TerminusWalk {
        private final Map<String, List<String>> tagMembers;
        private final Set<String> items = new LinkedHashSet<>();
        private final Set<String> followed = new HashSet<>();
        private final Deque<String> pending = new ArrayDeque<>();

        TerminusWalk(Map<String, List<String>> tagMembers) {
            this.tagMembers = tagMembers;
        }

        List<String> from(String constant) {
            followed.add(CONSTANT_PREFIX + constant);
            pending.add(constant);
            while (!pending.isEmpty()) {
                reachReadersOf(referenceTo(pending.removeFirst()));
            }
            return List.copyOf(items);
        }

        private void reachReadersOf(Pattern reference) {
            for (Member member : members) {
                for (Member entry : member.entries()) {
                    if (entry.key() != null && reference.matcher(entry.text()).find()) {
                        reachEntry(member.key(), entry.key());
                    }
                }
                if (member.kind() == Kind.VALUE && reference.matcher(member.text()).find()) {
                    reachTopLevel(member.key());
                }
            }
        }

        private void reachEntry(@Nullable String section, String key) {
            if (CONSTANTS.equals(section)) {
                if (followed.add(CONSTANT_PREFIX + key)) {
                    pending.addLast(key);
                }
            } else if (section != null && PROCESS_SECTIONS.contains(section)) {
                reachProcess(section, key);
            }
        }

        private void reachTopLevel(String key) {
            if (key.startsWith(GROUP_PREFIX)) {
                reachGroup(key.substring(GROUP_PREFIX.length()));
            } else {
                items.add(key);
            }
        }

        // An assignment's key is its target group; any other process reaches the assignments stacking it.
        private void reachProcess(String section, String key) {
            if (key.startsWith(GROUP_PREFIX)) {
                reachGroup(key.substring(GROUP_PREFIX.length()));
                return;
            }
            if (!followed.add(section + STACK_PREFIX + key)) {
                return;
            }
            Pattern stacking = Pattern.compile(Pattern.quote(STACK_PREFIX + key) + NAME_END);
            Member block = member(section);
            for (Member entry : block == null ? List.<Member>of() : block.entries()) {
                if (entry.key() != null && stacking.matcher(entry.text()).find()) {
                    reachProcess(section, entry.key());
                }
            }
        }

        // A _groups array names its members, a #tag among them expanding in turn; a name no group holds is a tag.
        private void reachGroup(String name) {
            if (!followed.add(GROUP_PREFIX + name)) {
                return;
            }
            Optional<List<String>> groupMembers = groupMembers(name);
            if (groupMembers.isEmpty()) {
                items.addAll(tagMembers.getOrDefault(name.contains(NAMESPACE_SEPARATOR) ? name
                        : DEFAULT_NAMESPACE + name, List.of()));
                return;
            }
            for (String groupMember : groupMembers.get()) {
                reachTopLevel(groupMember);
            }
        }

        private Optional<List<String>> groupMembers(String name) {
            Member groups = member(GROUPS);
            return groups == null ? Optional.empty() : groups.entries().stream()
                    .filter(entry -> name.equals(entry.key())).findFirst().map(entry -> memberIds(entry.text()));
        }

        private static List<String> memberIds(String text) {
            try {
                JsonElement parsed = JsonParser.parseString(text);
                return parsed.isJsonArray() ? parsed.getAsJsonArray().asList().stream()
                        .filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString).toList() : List.of();
            } catch (JsonParseException e) {
                return List.of();
            }
        }
    }

    // Where an entry stands: its section's key, or null for a top-level entry, and its own key.
    private record Site(@Nullable String section, String key) {
    }

    /**
     * @param constant a constant's name
     * @return every entry deleting it removes, in the order the cascade reaches them
     * @throws IllegalArgumentException where no constant holds the name
     */
    public List<Reader> deletionCascade(String constant) {
        return List.copyOf(cascadeOf(constant).values());
    }

    /**
     * Deletes a constant with every reader whole, each removed constant's readers following, so nothing left
     * reads a removed entry (decision deleting-a-variable-inlines-its-expression-into-each-reader).
     *
     * @param constant a constant's name
     * @return the model without the constant and its cascade
     * @throws IllegalArgumentException where no constant holds the name
     */
    public BaseValuesModel deleteConstant(String constant) {
        Set<Site> gone = new HashSet<>(cascadeOf(constant).keySet());
        gone.add(new Site(CONSTANTS, constant));
        List<Member> kept = new ArrayList<>();
        for (Member member : members) {
            if (gone.contains(new Site(null, member.key()))) {
                continue;
            }
            List<Member> entries = member.entries().stream()
                    .filter(entry -> entry.key() == null || !gone.contains(new Site(member.key(), entry.key())))
                    .toList();
            kept.add(entries.size() == member.entries().size() ? member : member.withEntries(entries));
        }
        return withMembers(kept);
    }

    private Map<Site, Reader> cascadeOf(String constant) {
        Member section = member(CONSTANTS);
        if (section == null || section.entries().stream().noneMatch(entry -> constant.equals(entry.key()))) {
            throw new IllegalArgumentException(NO_CONSTANT + constant);
        }
        Map<Site, Reader> removed = new LinkedHashMap<>();
        // The visited guard: a constant already deleted, the first among them, is never read again.
        Set<String> visited = new HashSet<>(List.of(constant));
        Deque<String> pending = new ArrayDeque<>(List.of(constant));
        while (!pending.isEmpty()) {
            Pattern reference = referenceTo(pending.removeFirst());
            for (Member member : members) {
                for (Member entry : member.entries()) {
                    if (entry.key() != null && reference.matcher(entry.text()).find()) {
                        removeReader(member.key(), entry.key(), removed, visited, pending);
                    }
                }
                if (member.kind() == Kind.VALUE && reference.matcher(member.text()).find()) {
                    removeReader(null, member.key(), removed, visited, pending);
                }
            }
        }
        return removed;
    }

    private static void removeReader(@Nullable String section, String key, Map<Site, Reader> removed,
                                     Set<String> visited, Deque<String> pending) {
        boolean constant = CONSTANTS.equals(section);
        Site site = new Site(section, key);
        if ((constant && !visited.add(key)) || removed.containsKey(site)) {
            return;
        }
        removed.put(site, new Reader(key, constant ? ReaderKind.CONSTANT : kindOfKey(key)));
        if (constant) {
            pending.addLast(key);
        }
    }

    private static ReaderKind kindOfKey(String key) {
        return key.startsWith(GROUP_PREFIX) ? ReaderKind.GROUP : ReaderKind.ITEM;
    }

    private static Pattern referenceTo(String constant) {
        return Pattern.compile(Pattern.quote(CONSTANT_PREFIX + constant) + NAME_END);
    }

    private void appendEntries(StringBuilder out, List<Member> entries, int depth) {
        String indent = indentUnit.repeat(depth);
        for (int i = 0; i < entries.size(); i++) {
            Member entry = entries.get(i);
            out.append(i == 0 ? EMPTY : COMMA).append(orElse(entry.leading(), NEWLINE + indent));
            if (entry.key() != null) {
                out.append(QUOTE).append(entry.key()).append(QUOTE)
                        .append(orElse(entry.keySeparator(), DEFAULT_KEY_SEPARATOR));
            }
            out.append(body(entry, depth)).append(orElse(entry.trailing(), EMPTY));
        }
    }

    private String body(Member member, int depth) {
        return switch (member.kind()) {
            case VALUE -> indented(member.text(), indentUnit.repeat(depth));
            case OBJECT -> section(member, depth, '{', '}');
            case ARRAY -> section(member, depth, '[', ']');
        };
    }

    private String section(Member member, int depth, char open, char close) {
        StringBuilder out = new StringBuilder().append(open);
        appendEntries(out, member.entries(), depth + 1);
        String usualClosing = member.entries().isEmpty() ? EMPTY : NEWLINE + indentUnit.repeat(depth);
        // A section the file wrote as {} closes on its own line once it gains an entry.
        String closing = member.closingGap() == null || member.closingGap().isEmpty() ? usualClosing
                : member.closingGap();
        return out.append(closing).append(close).toString();
    }

    private static String orElse(@Nullable String value, String usual) {
        return value == null ? usual : value;
    }

    private static String indented(String text, String indent) {
        List<String> lines = List.of(text.split(NEWLINE, KEEP_TRAILING_EMPTY));
        StringBuilder out = new StringBuilder(lines.getFirst());
        for (String line : lines.subList(1, lines.size())) {
            out.append(NEWLINE).append(line.isEmpty() ? EMPTY : indent + line);
        }
        return out.toString();
    }

    /**
     * Reads the file one span at a time: the whitespace before each key, the key, its separator, the
     * value's text and the whitespace after it.
     */
    private static final class Parser {
        private final String text;
        private int position;
        private String indentUnit = DEFAULT_INDENT;

        Parser(String text) {
            this.text = text;
        }

        BaseValuesModel document(String lineSeparator) {
            expect('{');
            indentUnit = detectIndent();
            List<Member> members = new ArrayList<>();
            String closingGap = entries(members, '}', 1, true);
            return new BaseValuesModel(members, lineSeparator, indentUnit, closingGap, text.substring(position));
        }

        private String detectIndent() {
            int start = text.indexOf(NEWLINE, position) + 1;
            int end = start;
            while (end < text.length() && text.charAt(end) == ' ') {
                end++;
            }
            return end > start ? text.substring(start, end) : DEFAULT_INDENT;
        }

        /**
         * Reads entries up to the closing bracket and answers the whitespace before it.
         *
         * @param into  the list the entries are added to
         * @param close the closing bracket
         * @param depth the nesting depth of the entries
         * @param keyed whether each entry has a key
         * @return the whitespace before the closing bracket
         */
        private String entries(List<Member> into, char close, int depth, boolean keyed) {
            while (true) {
                String leading = whitespace();
                if (peek() == close) {
                    position++;
                    return leading;
                }
                into.add(entry(leading, depth, keyed));
                if (peek() == ',') {
                    position++;
                }
            }
        }

        private Member entry(String leading, int depth, boolean keyed) {
            String key = null;
            String separator = null;
            if (keyed) {
                String quoted = stringSpan();
                key = quoted.substring(1, quoted.length() - 1);
                separator = keySeparator();
            }
            char open = peek();
            if (opensSection(depth, key, open)) {
                position++;
                List<Member> sectionEntries = new ArrayList<>();
                String closingGap = entries(sectionEntries, open == '{' ? '}' : ']', depth + 1, open == '{');
                return new Member(key, separator, open == '{' ? Kind.OBJECT : Kind.ARRAY, EMPTY, sectionEntries,
                        leading, spaces(), closingGap);
            }
            String value = valueText(indentUnit.repeat(depth));
            return new Member(key, separator, Kind.VALUE, value, List.of(), leading, spaces(), null);
        }

        // Only a top-level _key holding an object or array is a section; anything else is held as text.
        private static boolean opensSection(int depth, @Nullable String key, char open) {
            boolean sectionKey = depth == 1 && key != null && key.startsWith(SECTION_PREFIX);
            return sectionKey && (open == '{' || open == '[');
        }

        private String whitespace() {
            int start = position;
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
            return text.substring(start, position);
        }

        private String spaces() {
            int start = position;
            while (peek() == ' ') {
                position++;
            }
            return text.substring(start, position);
        }

        /**
         * Reads the colon after a key and the spaces around it, which the file spells more than one way.
         *
         * @return the colon and its surrounding spaces as the file spells them
         */
        private String keySeparator() {
            int start = position;
            spaces();
            expect(':');
            spaces();
            return text.substring(start, position);
        }

        private String valueText(String indent) {
            int start = position;
            skipValue();
            int end = position;
            while (end > start && text.charAt(end - 1) == ' ') {
                end--;
            }
            position = end;
            List<String> lines = List.of(text.substring(start, end).split(NEWLINE, KEEP_TRAILING_EMPTY));
            StringBuilder out = new StringBuilder(lines.getFirst());
            for (String line : lines.subList(1, lines.size())) {
                if (!line.isEmpty() && !line.startsWith(indent)) {
                    throw error(CONTINUATION_INDENT);
                }
                out.append(NEWLINE).append(line.isEmpty() ? EMPTY : line.substring(indent.length()));
            }
            return out.toString();
        }

        private void skipValue() {
            char first = peek();
            if (first == '"') {
                stringSpan();
                return;
            }
            if (first == '{' || first == '[') {
                skipBracketed();
                return;
            }
            while (position < text.length() && STRUCTURAL_END.indexOf(text.charAt(position)) < 0) {
                position++;
            }
        }

        private void skipBracketed() {
            int depth = 0;
            do {
                char current = peek();
                if (current == '"') {
                    stringSpan();
                    continue;
                }
                if (current == '{' || current == '[') {
                    depth++;
                } else if (current == '}' || current == ']') {
                    depth--;
                }
                position++;
            } while (depth > 0);
        }

        private String stringSpan() {
            int start = position;
            expect('"');
            while (peek() != '"') {
                position += peek() == '\\' ? ESCAPE_WIDTH : 1;
            }
            position++;
            return text.substring(start, position);
        }

        private char peek() {
            if (position >= text.length()) {
                throw error(MORE_TEXT);
            }
            return text.charAt(position);
        }

        private void expect(char wanted) {
            if (peek() != wanted) {
                throw error(SINGLE_QUOTE + wanted + SINGLE_QUOTE);
            }
            position++;
        }

        private IllegalArgumentException error(String wanted) {
            long line = text.substring(0, Math.min(position, text.length())).chars().filter(c -> c == '\n').count() + 1;
            return new IllegalArgumentException(ERROR_LINE + line + ERROR_EXPECTED + wanted);
        }
    }
}
