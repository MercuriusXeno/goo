package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import net.minecraft.client.renderer.oit.OitStage;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * The far-to-near draws a level pass's OIT phase draws: collected once, at the phase's first stage, since a visual's
 * collect drains its queue, drawn in each of the phase's stages, then handed to AfterLevel, which replays what carries
 * no OIT set. The first stage collects afresh, so a pass whose AfterLevel never took them, a portal's among them,
 * leaves nothing to the next.
 * decision goo-translucency-draws-through-order-independent-transparency
 */
final class OitDrawReplay {

    private @Nullable List<SortedDraw> held;

    /**
     * @param stage   the OIT stage drawing now
     * @param collect collects the pass's draws, far to near
     * @return the draws this stage draws
     */
    List<SortedDraw> atOitStage(OitStage stage, Supplier<List<SortedDraw>> collect) {
        if (stage == OitStage.DEPTH_BOUNDS || held == null) {
            held = collect.get();
        }
        return held;
    }

    /**
     * @return the draws the pass's OIT phase drew, for AfterLevel to replay, or null where no OIT phase drew this pass
     */
    @Nullable List<SortedDraw> takeHeld() {
        List<SortedDraw> taken = held;
        held = null;
        return taken;
    }
}
