package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * An expression reading a goo type its referent, a prefab or an item, holds no amount of reads that type as zero:
 * the entry or constant holding it loads, the game logs nothing, and the evaluation records the first such read so
 * the workbench draws the red line naming the referent and the type beside the entry. A lacked type is a config error
 * its author fixes.
 * Decision absent-goo-type-reads-zero-in-the-game.
 * Decision type-the-referent-lacks-loads-no-value-and-reads-a-red-line.
 */
final class LackedTypeReads {
    private static final String HOLDS_NO = " holds no ";
    // The first lacked read the evaluation running under recording made, unbound where no caller records.
    private static final ScopedValue<AtomicReference<@Nullable String>> FIRST_LACKED = ScopedValue.newInstance();

    private LackedTypeReads() {
    }

    /**
     * One evaluation's answer and the first lacked read it made.
     *
     * @param value  what the evaluation answered
     * @param lacked the error naming the referent and the type of the first lacked read, or null where it made none
     * @param <T>    the answer's type
     */
    record Recorded<T>(T value, @Nullable String lacked) {
    }

    /**
     * @param referent the prefab or item read
     * @param type     the goo type's id
     * @return the error naming both, "stem holds no leaf"
     */
    static String message(String referent, String type) {
        return referent + HOLDS_NO + type;
    }

    /**
     * Runs an evaluation, recording the first lacked read it makes; a recording nested inside another hands its read
     * up to the outer one too.
     *
     * @param evaluation the evaluation to run
     * @param <T>        its answer's type
     * @return its answer and its first lacked read
     */
    static <T> Recorded<T> recording(Supplier<T> evaluation) {
        AtomicReference<@Nullable String> first = new AtomicReference<>();
        T value = ScopedValue.where(FIRST_LACKED, first).call(evaluation::get);
        String lacked = first.get();
        if (lacked != null) {
            note(lacked);
        }
        return new Recorded<>(value, lacked);
    }

    /**
     * @param evaluation the evaluation to run
     * @return the error naming the referent and the type of its first lacked read, or null where it made none
     */
    static @Nullable String lackedDuring(Runnable evaluation) {
        return recording(() -> {
            evaluation.run();
            return Boolean.TRUE;
        }).lacked();
    }

    /**
     * @param referent the prefab or item read, as the expression spelled it
     * @param value    the referent's value
     * @param type     the goo type read
     * @return the referent's exact amount of the type, zero where it holds none
     */
    static GooFraction amountHeld(String referent, GooValue value, ResourceKey<GooTypeDefinition> type) {
        GooFraction amount = value.exactAll().get(type);
        if (amount == null) {
            note(message(referent, GooTypes.id(type)));
            return GooFraction.ZERO;
        }
        return amount;
    }

    private static void note(String lacked) {
        if (FIRST_LACKED.isBound()) {
            FIRST_LACKED.get().compareAndSet(null, lacked);
        }
    }
}
