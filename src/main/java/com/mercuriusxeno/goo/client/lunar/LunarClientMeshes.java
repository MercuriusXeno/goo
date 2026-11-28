package com.mercuriusxeno.goo.client.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarCrossingShift;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * The meshes the client already built, kept through a crossing of lunar's seam. Vanilla stores each render section by
 * section position, so the camera's jump across the seam would hand every section a new position and drop its mesh.
 * Told the crossing's shift, the client instead lifts each built mesh off its section as the view jumps, and sets it
 * on the section now standing at the shifted position: a mesh's vertices sit relative to its section's corner, and
 * the renderer draws each section at its own corner, so the landing draws from the meshes already built. Each mesh
 * keeps the time it was first uploaded, so a later rebuild of its section draws at once, as vanilla's rebuilds do,
 * rather than fading in from the sky. A section the shift leaves without a mesh builds one as vanilla builds any.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LunarClientMeshes {

    /** How far, in sections, the camera may stray from the crossing's own jump and still count as making it. */
    private static final int CAMERA_SLACK = 2;
    /**
     * Client ticks the moved sections keep their meshes through the light's move, well past the one level update
     * that applies it.
     */
    private static final int LIGHT_MOVE_TICKS = 10;
    private static final Long2ObjectMap<Lifted> LIFTED = new Long2ObjectOpenHashMap<>();
    /** The moved chunks' sections, which keep the meshes they were built with while their light moves under them. */
    private static final LongSet KEPT = new LongOpenHashSet();
    /** The sections that took a lifted mesh. */
    private static final LongSet FILLED = new LongOpenHashSet();
    private static @Nullable LunarCrossingShift expected;
    private static int lightMoveTicksLeft;

    private LunarClientMeshes() {
    }

    /** A mesh lifted off its section, with the time its section first uploaded it. */
    private record Lifted(SectionMesh mesh, long uploadedTime) {
    }

    /**
     * Readies the meshes to move with the view's jump by the crossing's shift, and holds the moved chunks' sections
     * clear of rebuilding while their light moves: a mesh carries the light it was built with, so a rebuild before
     * the moved light lands would draw the section dark.
     *
     * @param shift the crossing's shift
     * @param moved where each moved chunk now stands
     * @param level the level the chunks stand in
     */
    public static void expect(LunarCrossingShift shift, Collection<ChunkPos> moved, ClientLevel level) {
        expected = shift;
        KEPT.clear();
        FILLED.clear();
        for (ChunkPos pos : moved) {
            for (int sectionY = level.getMinSectionY(); sectionY <= level.getMaxSectionY(); sectionY++) {
                KEPT.add(SectionPos.asLong(pos.x(), sectionY, pos.z()));
            }
        }
        lightMoveTicksLeft = LIGHT_MOVE_TICKS;
    }

    /**
     * Jumps the drawn view by the crossing's shift at once, in the crossing's own handling and before any frame draws,
     * so every built mesh still stands on its section as the view re-keys and moves with it.
     *
     * @param shift the crossing's shift
     */
    public static void jumpTheView(LunarCrossingShift shift) {
        ViewArea viewArea = Minecraft.getInstance().levelRenderer.viewArea;
        if (viewArea == null) {
            return;
        }
        SectionPos centre = viewArea.getCameraSectionPos();
        viewArea.repositionCamera(SectionPos.of(centre.x() + shift.chunksX(), centre.y(),
                centre.z() + shift.chunksZ()));
    }

    /**
     * Whether a section keeps its mesh through the light's move, so marking it dirty waits.
     *
     * @param sectionX the section's x
     * @param sectionY the section's y
     * @param sectionZ the section's z
     * @return whether the section keeps its mesh
     */
    public static boolean keepsItsMesh(int sectionX, int sectionY, int sectionZ) {
        return !KEPT.isEmpty() && KEPT.contains(SectionPos.asLong(sectionX, sectionY, sectionZ));
    }

    /**
     * Counts down the light's move once a client tick.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickTheLightMove();
    }

    /**
     * Ends the light's move: a kept section that took no lifted mesh, because the renderer's hook is missing or it had
     * none, is marked dirty then, so it builds one as vanilla's sections do.
     */
    static void tickTheLightMove() {
        if (lightMoveTicksLeft <= 0 || --lightMoveTicksLeft > 0) {
            return;
        }
        LongSet unbuilt = new LongOpenHashSet(KEPT);
        unbuilt.removeAll(FILLED);
        KEPT.clear();
        FILLED.clear();
        Minecraft minecraft = Minecraft.getInstance();
        unbuilt.forEach(node -> minecraft.levelExtractor.setSectionDirty(SectionPos.x(node), SectionPos.y(node),
                SectionPos.z(node)));
    }

    /**
     * Lifts each built mesh off its section as the view jumps by the expected shift, keyed by where it lands.
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
                        SectionPos.z(node) + shift.chunksZ()), new Lifted(mesh, section.uploadedTime));
            }
        }
    }

    /**
     * Sets each lifted mesh, with its upload time, on the section standing where it lands, and releases any the jump
     * left no section for.
     *
     * @param viewArea the sections the renderer draws
     */
    public static void setAfterTheJump(ViewArea viewArea) {
        if (LIFTED.isEmpty()) {
            return;
        }
        List<SectionRenderDispatcher.RenderSection> unfilled = new ArrayList<>();
        for (SectionRenderDispatcher.RenderSection section : viewArea.sections) {
            Lifted lifted = LIFTED.remove(section.getSectionNode());
            if (lifted != null) {
                section.sectionMesh.set(lifted.mesh());
                section.uploadedTime = lifted.uploadedTime();
                FILLED.add(section.getSectionNode());
            } else {
                unfilled.add(section);
            }
        }
        // A section's own reset releases a mesh under the dispatcher's lock, so a mesh left over is released by
        // setting it on a section still waiting for its own and resetting that.
        for (Lifted leftOver : LIFTED.values()) {
            if (unfilled.isEmpty()) {
                break;
            }
            SectionRenderDispatcher.RenderSection carrier = unfilled.removeLast();
            carrier.sectionMesh.set(leftOver.mesh());
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
