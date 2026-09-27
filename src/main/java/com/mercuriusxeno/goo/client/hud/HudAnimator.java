package com.mercuriusxeno.goo.client.hud;

import org.jspecify.annotations.Nullable;
import java.util.function.BiPredicate;

/**
 * Shared animation state machine for in-world machine HUD panels. Each
 * per-machine HUD renderer (canister, crucible, vat) holds one instance
 * parameterized by its target type {@code T} (the record carrying the
 * machine's position plus any per-render positional offsets).
 *
 * <p>The animator owns the emerge/retract fade and pitch interpolation and
 * the tracked-target reference. Each frame, the renderer:
 * <ol>
 *   <li>Resolves a target from the current crosshair (or {@code null}).</li>
 *   <li>Calls {@link #tick(Object)} with that target.</li>
 *   <li>If {@link #tracked()} is non-null after the tick, looks up the
 *       machine's data and renders the panel using the latest target
 *       offsets, {@link #pitch()} and {@link #opacity()}.</li>
 * </ol>
 *
 * <p>The {@code sameTarget} predicate distinguishes "still aiming at the
 * same machine slot" (refresh offsets without resetting the emerge
 * animation) from "aiming at a new machine" (start a fresh emerge). For
 * machines without per-slot tracking, pass a position-only equality
 * check.
 *
 * @param <T> the renderer's target record type
 */
public final class HudAnimator<T> {

    /** Seconds a panel takes to fade fully in or fully out (decision diagnose-then-fix-hud-panel-fade). */
    private static final float FADE_SECONDS = 0.1f;

    /** Float rounding a run of frame steps leaves short of an end of the fade, snapped to that end. */
    private static final float FADE_ROUNDING_SLACK = 1e-4f;

    private final long[] lastFrameNanos = {0};
    private final BiPredicate<T, T> sameTarget;

    private @Nullable T tracked;
    private float fadeProgress;
    private boolean retracting;

    /**
     * Creates a HUD animator with a custom "same target" predicate.
     *
     * @param sameTarget returns true when two non-null targets refer to the
     *                   same machine + slot. Determines whether a refresh
     *                   should preserve the emerge animation or restart it.
     */
    public HudAnimator(BiPredicate<T, T> sameTarget) {
        this.sameTarget = sameTarget;
    }

    /**
     * Drives the state machine once per frame. Call from the
     * {@code RenderLevelStageEvent.AfterOpaqueFeatures} hook with the
     * resolved target (or {@code null} if not aiming at this machine).
     *
     * @param target the current target, or {@code null}
     */
    public void tick(@Nullable T target) {
        tick(target, InWorldHud.computeDeltaTime(lastFrameNanos));
    }

    /**
     * Drives the state machine one frame of the given length.
     *
     * @param target the current target, or {@code null}
     * @param dt     seconds since the previous frame
     */
    void tick(@Nullable T target, float dt) {
        applyTransition(target);
        advanceFade(dt);
    }

    /** @return the currently-tracked target, or {@code null} if idle */
    public @Nullable T tracked() {
        return tracked;
    }

    /**
     * Eases the fade progress out, so the tilt rises and falls on the fade's
     * clock (decision tilt-stays-beside-the-fade).
     *
     * @return the current pitch in [0, 1]: 0 = flush, 1 = fully emerged
     */
    public float pitch() {
        float remaining = 1f - fadeProgress;
        return 1f - remaining * remaining;
    }

    /** @return the panel opacity in [0, 1]: 0 = invisible, 1 = fully faded in */
    public float opacity() {
        return fadeProgress;
    }

    /** Resets all state to idle. */
    public void clear() {
        tracked = null;
        fadeProgress = 0f;
        retracting = false;
    }

    /**
     * Applies the state transition for one frame: new target starts an
     * emerge, same target refreshes offsets, lost target begins retract.
     *
     * @param target the current target, or {@code null}
     */
    private void applyTransition(@Nullable T target) {
        if (target == null) {
            beginRetractIfTracking();
            return;
        }
        if (tracked != null && sameTarget.test(tracked, target)) {
            refreshTarget(target);
        } else {
            startEmerge(target);
        }
    }

    /** Marks the tracked target for retract on the next fade advance. */
    private void beginRetractIfTracking() {
        if (tracked != null && !retracting) {
            retracting = true;
        }
    }

    /**
     * Updates target reference (positional offsets may have shifted) and cancels any retract.
     *
     * @param target the refreshed target (same machine + slot as tracked)
     */
    private void refreshTarget(T target) {
        tracked = target;
        retracting = false;
    }

    /**
     * Starts a fresh emerge animation for a newly-acquired target.
     *
     * @param target the newly-acquired target
     */
    private void startEmerge(T target) {
        tracked = target;
        fadeProgress = 0f;
        retracting = false;
    }

    /**
     * Moves the fade linearly toward 1 while emerging and toward 0 while
     * retracting, one full fade per {@link #FADE_SECONDS}, and clears state once
     * a retracting panel has faded out.
     *
     * @param dt seconds since the previous frame
     */
    private void advanceFade(float dt) {
        if (tracked == null) {
            return;
        }
        fadeProgress = stepFade(fadeProgress, retracting ? -dt / FADE_SECONDS : dt / FADE_SECONDS);
        if (retracting && fadeProgress == 0f) {
            clear();
        }
    }

    /**
     * Moves a fade progress by one step, clamped to [0, 1] and snapped to an
     * end the step lands within rounding of.
     *
     * @param progress the fade progress
     * @param step     the signed change
     * @return the moved progress
     */
    private static float stepFade(float progress, float step) {
        float moved = progress + step;
        if (moved <= FADE_ROUNDING_SLACK) {
            return 0f;
        }
        return moved >= 1f - FADE_ROUNDING_SLACK ? 1f : moved;
    }
}
