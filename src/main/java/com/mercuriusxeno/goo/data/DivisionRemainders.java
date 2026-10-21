package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Records the remainders the value expressions' integer divisions truncate, each with the constant or
 * item it fell in, the expression and the goo type, so the least base search can read them. The
 * evaluators report into it always; it keeps nothing unless a {@link #record()} runs on the thread,
 * so a server load records nothing.
 * Decision audit-searches-the-least-base-per-type.
 *
 * <p>A scalar constant holds no type, so its remainders wait on the constant and fall in the type of
 * each entry that reads it; a remainder no typed entry reads stays untyped.</p>
 */
final class DivisionRemainders {

    /**
     * The owner a remainder carries when no constant or item parse encloses it.
     */
    static final String NO_OWNER = "";
    private static final String CONSTANT_PREFIX = "$";
    private static final ThreadLocal<@Nullable Recording> ACTIVE = new ThreadLocal<>();

    private DivisionRemainders() {
    }

    /**
     * Starts recording on this thread; closing the recording stops it.
     *
     * @return the recording, to read and close
     */
    static Recording record() {
        Recording recording = new Recording();
        ACTIVE.set(recording);
        return recording;
    }

    /**
     * @return true while a recording runs on this thread
     */
    static boolean isRecording() {
        return ACTIVE.get() != null;
    }

    /**
     * Parses one constant as the owner of the remainders it truncates.
     *
     * @param name            the constant's name, without its $ prefix
     * @param parse           the parse
     * @param parsedAsScalar  answers, after the parse, whether the constant resolved to a scalar
     */
    static void underConstant(String name, Runnable parse, BooleanSupplier parsedAsScalar) {
        Recording recording = ACTIVE.get();
        if (recording == null) {
            parse.run();
            return;
        }
        Frame frame = recording.pushOwner(CONSTANT_PREFIX + name);
        try {
            parse.run();
        } finally {
            recording.popOwner(frame, parsedAsScalar.getAsBoolean() ? name : null);
        }
    }

    /**
     * Assigns one item's value as the owner of the remainders it truncates.
     *
     * @param item   the item's id
     * @param assign the assignment
     */
    static void underItem(String item, Runnable assign) {
        Recording recording = ACTIVE.get();
        if (recording == null) {
            assign.run();
            return;
        }
        Frame frame = recording.pushOwner(item);
        try {
            assign.run();
        } finally {
            recording.popOwner(frame, null);
        }
    }

    /**
     * Evaluates one expression, the text its remainders carry.
     *
     * @param expression the expression text
     * @param evaluate   the evaluation
     * @param <T>        the evaluation's result
     * @return what the evaluation answers
     */
    static <T> T underExpression(String expression, Supplier<T> evaluate) {
        Recording recording = ACTIVE.get();
        if (recording == null) {
            return evaluate.get();
        }
        Frame parent = recording.frames.peek();
        recording.frames.push(new Frame(parent.owner, expression, parent.type, parent.pending));
        try {
            return evaluate.get();
        } finally {
            recording.frames.pop();
        }
    }

    /**
     * Evaluates one goo type's amount, the type its remainders fall in.
     *
     * @param type     the goo type
     * @param evaluate the evaluation
     * @param <T>      the evaluation's result
     * @return what the evaluation answers
     */
    static <T> T underType(ResourceKey<GooTypeDefinition> type, Supplier<T> evaluate) {
        Recording recording = ACTIVE.get();
        if (recording == null) {
            return evaluate.get();
        }
        Frame parent = recording.frames.peek();
        recording.frames.push(new Frame(parent.owner, parent.expression, type, parent.pending));
        try {
            return evaluate.get();
        } finally {
            recording.frames.pop();
        }
    }

    /**
     * Reports a division in the enclosing type; one leaving no remainder records nothing.
     *
     * @param dividend the dividend
     * @param divisor  the divisor
     */
    static void reportDivision(int dividend, int divisor) {
        Recording recording = ACTIVE.get();
        if (recording != null) {
            recording.report(dividend, divisor, recording.frames.peek().type);
        }
    }

    /**
     * Reports a division of one goo type's amount; one leaving no remainder records nothing.
     *
     * @param dividend the dividend
     * @param divisor  the divisor
     * @param type     the goo type the amount belongs to
     */
    static void reportDivision(int dividend, int divisor, ResourceKey<GooTypeDefinition> type) {
        Recording recording = ACTIVE.get();
        if (recording != null) {
            recording.report(dividend, divisor, type);
        }
    }

    /**
     * Carries the remainders a scalar constant truncated into the expression reading it.
     *
     * @param name the constant's name, without its $ prefix
     */
    static void readScalarConstant(String name) {
        Recording recording = ACTIVE.get();
        if (recording == null) {
            return;
        }
        Frame frame = recording.frames.peek();
        for (Remainder untyped : recording.scalarConstantRemainders.getOrDefault(name, List.of())) {
            recording.place(untyped.inType(frame.type), frame);
        }
    }

    /**
     * One truncated division.
     *
     * @param owner      the constant ($name) or item the division fell in, or {@link #NO_OWNER}
     * @param expression the expression holding the division
     * @param type       the goo type the remainder fell in, or null where no type reads it
     * @param divisor    the divisor
     * @param remainder  the remainder the division truncated, never zero
     */
    record Remainder(String owner, String expression, @Nullable ResourceKey<GooTypeDefinition> type,
                     int divisor, int remainder) {

        private Remainder inType(@Nullable ResourceKey<GooTypeDefinition> newType) {
            return new Remainder(owner, expression, newType, divisor, remainder);
        }
    }

    /**
     * One enclosing parse: its owner, expression, type, and the untyped remainders waiting on its owner.
     */
    private record Frame(String owner, String expression, @Nullable ResourceKey<GooTypeDefinition> type,
                         List<Remainder> pending) {
    }

    /**
     * A recording on one thread: what it holds, read through {@link #remainders()}.
     */
    static final class Recording implements AutoCloseable {
        private final List<Remainder> recorded = new ArrayList<>();
        private final Map<String, List<Remainder>> scalarConstantRemainders = new HashMap<>();
        private final Deque<Frame> frames = new ArrayDeque<>();
        private final Frame root = new Frame(NO_OWNER, NO_OWNER, null, new ArrayList<>());

        private Recording() {
            frames.push(root);
        }

        /**
         * @return every remainder recorded, the untyped ones no constant holds included
         */
        List<Remainder> remainders() {
            List<Remainder> all = new ArrayList<>(recorded);
            all.addAll(root.pending);
            return all;
        }

        @Override
        public void close() {
            ACTIVE.remove();
        }

        private Frame pushOwner(String owner) {
            Frame frame = new Frame(owner, NO_OWNER, null, new ArrayList<>());
            frames.push(frame);
            return frame;
        }

        private void popOwner(Frame frame, @Nullable String scalarConstantName) {
            frames.pop();
            if (scalarConstantName != null) {
                scalarConstantRemainders.put(scalarConstantName, List.copyOf(frame.pending));
            } else {
                recorded.addAll(frame.pending);
            }
        }

        private void report(int dividend, int divisor, @Nullable ResourceKey<GooTypeDefinition> type) {
            if (divisor == 0 || dividend % divisor == 0) {
                return;
            }
            Frame frame = frames.peek();
            place(new Remainder(frame.owner, frame.expression, type, divisor, Math.abs(dividend % divisor)), frame);
        }

        private void place(Remainder remainder, Frame frame) {
            if (remainder.type() == null) {
                frame.pending.add(remainder);
            } else {
                recorded.add(remainder);
            }
        }
    }
}
