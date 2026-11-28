package com.mercuriusxeno.goo.client.lunar;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarCrossingShift;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * The meshes the client already built, kept through a crossing of lunar's seam. Vanilla stores each render section by
 * section position, so the camera's jump across the seam would hand every section a new position and drop its mesh.
 * Told the crossing's shift, the client instead lifts each built mesh off its section as the camera jumps, and sets it
 * on the section now standing at the shifted position: a mesh's vertices sit relative to its section's corner, and
 * the renderer draws each section at its own corner, so the landing draws from the meshes already built. A section the
 * shift leaves without a mesh builds one as vanilla builds any.
 * decision moon-wraps-at-its-border-like-the-zone
 */
public final class LunarClientMeshes {

    /** How far, in sections, the camera may stray from the crossing's own jump and still count as making it. */
    private static final int CAMERA_SLACK = 2;
    private static final Long2ObjectMap<SectionMesh> LIFTED = new Long2ObjectOpenHashMap<>();
    private static @Nullable LunarCrossingShift expected;

    private LunarClientMeshes() {
    }

    /**
     * Readies the meshes to move with the camera's next jump by the crossing's shift.
     *
     * @param shift the crossing's shift
     */
    public static void expect(LunarCrossingShift shift) {
        expected = shift;
    }

    /**
     * Lifts each built mesh off its section as the camera jumps by the expected shift, keyed by where it lands.
     *
     * @param viewArea the sections the renderer draws
     * @param camera   the section the camera moves to
     */
    public static void liftBeforeTheJump(ViewArea viewArea, SectionPos camera) {
        LunarCrossingShift shift = expected;
        if (shift == null || !inLunar() || !jumpsBy(viewArea.getCameraSectionPos(), camera, shift)) {
            return;
        }
        expected = null;
        for (SectionRenderDispatcher.RenderSection section : viewArea.sections) {
            SectionMesh mesh = section.sectionMesh.getAndSet(CompiledSectionMesh.UNCOMPILED);
            if (mesh != CompiledSectionMesh.UNCOMPILED) {
                long node = section.getSectionNode();
                LIFTED.put(SectionPos.asLong(SectionPos.x(node) + shift.chunksX(), SectionPos.y(node),
                        SectionPos.z(node) + shift.chunksZ()), mesh);
            }
        }
    }

    /**
     * Sets each lifted mesh on the section standing where it lands, and releases any the jump left no section for.
     *
     * @param viewArea the sections the renderer draws
     */
    public static void setAfterTheJump(ViewArea viewArea) {
        if (LIFTED.isEmpty()) {
            return;
        }
        List<SectionRenderDispatcher.RenderSection> unfilled = new ArrayList<>();
        for (SectionRenderDispatcher.RenderSection section : viewArea.sections) {
            SectionMesh mesh = LIFTED.remove(section.getSectionNode());
            if (mesh != null) {
                section.sectionMesh.set(mesh);
            } else {
                unfilled.add(section);
            }
        }
        // A section's own reset releases a mesh under the dispatcher's lock, so a mesh left over is released by
        // setting it on a section still waiting for its own and resetting that.
        for (SectionMesh leftOver : LIFTED.values()) {
            if (unfilled.isEmpty()) {
                break;
            }
            SectionRenderDispatcher.RenderSection carrier = unfilled.removeLast();
            carrier.sectionMesh.set(leftOver);
            carrier.reset();
        }
        LIFTED.clear();
    }

    private static boolean jumpsBy(SectionPos from, SectionPos to, LunarCrossingShift shift) {
        return Math.abs(to.x() - from.x() - shift.chunksX()) <= CAMERA_SLACK
                && Math.abs(to.z() - from.z() - shift.chunksZ()) <= CAMERA_SLACK;
    }

    private static boolean inLunar() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.dimension() == Lunar.LEVEL;
    }
}
