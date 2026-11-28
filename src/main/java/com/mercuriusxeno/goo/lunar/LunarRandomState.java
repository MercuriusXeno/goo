package com.mercuriusxeno.goo.lunar;

import java.util.function.Supplier;

/**
 * Marks the random state the server seeds lunar's generation with while it is made, so that random state hands out
 * every noise repeating at the seam: the random state reads its noises as it is made, before it could be told it is
 * lunar's.
 * decision moon-wraps-at-its-border-like-the-zone
 */
public final class LunarRandomState {

    private static final ThreadLocal<Boolean> MAKING_LUNARS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private LunarRandomState() {
    }

    /**
     * Makes a level's random state, marked as lunar's while it is made where the level is lunar.
     *
     * @param lunar whether the level is lunar
     * @param make  makes the random state
     * @param <T>   the random state's type
     * @return the random state
     */
    public static <T> T make(boolean lunar, Supplier<T> make) {
        MAKING_LUNARS.set(lunar);
        try {
            return make.get();
        } finally {
            MAKING_LUNARS.remove();
        }
    }

    /**
     * @return whether the random state being made on this thread is lunar's
     */
    public static boolean makingLunars() {
        return MAKING_LUNARS.get();
    }
}
