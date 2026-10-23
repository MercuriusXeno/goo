package com.mercuriusxeno.goo.tools.workbench;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
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
    private static final String CONSTANT_PREFIX = "$";
    // A #group or tag key: both read as a #name chip.
    private static final String GROUP_PREFIX = "#";

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
        StringBuilder out = new StringBuilder("{");
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
            throw new IllegalArgumentException("No entry " + key + " in " + section);
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
            throw new IllegalArgumentException("The file holds no section " + section);
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
        return -1;
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

    private static ReaderKind kindOfKey(String key) {
        return key.startsWith(GROUP_PREFIX) ? ReaderKind.GROUP : ReaderKind.ITEM;
    }

    private static Pattern referenceTo(String constant) {
        return Pattern.compile(Pattern.quote(CONSTANT_PREFIX + constant) + "(?![A-Za-z0-9_])");
    }

    private void appendEntries(StringBuilder out, List<Member> entries, int depth) {
        String indent = indentUnit.repeat(depth);
        for (int i = 0; i < entries.size(); i++) {
            Member entry = entries.get(i);
            out.append(i == 0 ? "" : COMMA).append(orElse(entry.leading(), NEWLINE + indent));
            if (entry.key() != null) {
                out.append(QUOTE).append(entry.key()).append(QUOTE)
                        .append(orElse(entry.keySeparator(), DEFAULT_KEY_SEPARATOR));
            }
            out.append(body(entry, depth)).append(orElse(entry.trailing(), ""));
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
        String usualClosing = member.entries().isEmpty() ? "" : NEWLINE + indentUnit.repeat(depth);
        // A section the file wrote as {} closes on its own line once it gains an entry.
        String closing = member.closingGap() == null || member.closingGap().isEmpty() ? usualClosing
                : member.closingGap();
        return out.append(closing).append(close).toString();
    }

    private static String orElse(@Nullable String value, String usual) {
        return value == null ? usual : value;
    }

    private static String indented(String text, String indent) {
        List<String> lines = List.of(text.split(NEWLINE, -1));
        StringBuilder out = new StringBuilder(lines.getFirst());
        for (String line : lines.subList(1, lines.size())) {
            out.append(NEWLINE).append(line.isEmpty() ? "" : indent + line);
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
            if (depth == 1 && key != null && key.startsWith(SECTION_PREFIX) && (open == '{' || open == '[')) {
                position++;
                List<Member> sectionEntries = new ArrayList<>();
                String closingGap = entries(sectionEntries, open == '{' ? '}' : ']', depth + 1, open == '{');
                return new Member(key, separator, open == '{' ? Kind.OBJECT : Kind.ARRAY, "", sectionEntries,
                        leading, spaces(), closingGap);
            }
            String value = valueText(indentUnit.repeat(depth));
            return new Member(key, separator, Kind.VALUE, value, List.of(), leading, spaces(), null);
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
            List<String> lines = List.of(text.substring(start, end).split(NEWLINE, -1));
            StringBuilder out = new StringBuilder(lines.getFirst());
            for (String line : lines.subList(1, lines.size())) {
                if (!line.isEmpty() && !line.startsWith(indent)) {
                    throw error("a value's continuation lines indented at least as deep as its entry");
                }
                out.append(NEWLINE).append(line.isEmpty() ? "" : line.substring(indent.length()));
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
                position += peek() == '\\' ? 2 : 1;
            }
            position++;
            return text.substring(start, position);
        }

        private char peek() {
            if (position >= text.length()) {
                throw error("more text");
            }
            return text.charAt(position);
        }

        private void expect(char wanted) {
            if (peek() != wanted) {
                throw error("'" + wanted + "'");
            }
            position++;
        }

        private IllegalArgumentException error(String wanted) {
            long line = text.substring(0, Math.min(position, text.length())).chars().filter(c -> c == '\n').count() + 1;
            return new IllegalArgumentException("base_values.json line " + line + ": expected " + wanted);
        }
    }
}
