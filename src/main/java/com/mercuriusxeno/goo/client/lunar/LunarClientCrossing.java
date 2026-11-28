package com.mercuriusxeno.goo.client.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarCrossingShift;
import com.mercuriusxeno.goo.network.LunarCrossingMovedPayload;
import com.mercuriusxeno.goo.network.LunarCrossingPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The client's side of a crossing of lunar's seam. Told the crossing's shift just before the teleport, the client
 * moves every chunk it holds over by that shift: each is built again at its shifted position from the same section
 * data, its block entities rebuilt there, and stored through vanilla's own load path, so a mod listening for chunk
 * loads and unloads sees an ordinary unload and load. Its light follows through the light engine's section-data queue,
 * and the entities in view move by the same shift. The client then names to the server each chunk it moved, and the
 * server skips resending those. The moved copy is a cache the server stays in charge of; a client that fails to move
 * names nothing and is sent its view again, as any teleport is.
 * decision moon-wraps-at-its-border-like-the-zone
 */
public final class LunarClientCrossing {

    private static final String MOVE_FAILED = "Lunar's crossing could not move the chunks the client holds; the server"
            + " sends them again";

    private LunarClientCrossing() {
    }

    /**
     * Moves what the client holds across the seam and names what it moved to the server.
     *
     * @param payload the crossing's shift
     * @param context the network context
     */
    public static void handle(LunarCrossingPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level.dimension() != Lunar.LEVEL) {
            return;
        }
        LunarCrossingShift shift = payload.shift();
        try {
            List<ChunkPos> moved = moveHeldChunks(level, shift);
            moveEntitiesInView(level, shift);
            moveTheCamera(minecraft, level, shift);
            LunarClientMeshes.expect(shift, moved, level);
            LunarClientMeshes.jumpTheView(shift);
            context.reply(LunarCrossingMovedPayload.of(shift, moved));
        } catch (RuntimeException failure) {
            Goo.LOGGER.warn(MOVE_FAILED, failure);
        }
    }

    private static List<ChunkPos> moveHeldChunks(ClientLevel level, LunarCrossingShift shift) {
        ClientChunkCache cache = level.getChunkSource();
        List<LevelChunk> held = heldChunks(cache);
        List<LevelChunk> moved = held.stream().map(chunk -> movedCopy(level, chunk, shift)).toList();
        for (LevelChunk chunk : held) {
            cache.drop(chunk.getPos());
        }
        ClientChunkCache.Storage storage = cache.storage;
        cache.updateViewCenter(storage.viewCenterX + shift.chunksX(), storage.viewCenterZ + shift.chunksZ());
        List<ChunkPos> positions = new ArrayList<>();
        for (LevelChunk chunk : moved) {
            ChunkPos pos = chunk.getPos();
            cache.storage.replace(cache.storage.getIndex(pos.x(), pos.z()), chunk);
            level.onChunkLoaded(pos);
            NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
            positions.add(pos);
        }
        List<ChunkPos> from = held.stream().map(LevelChunk::getPos).toList();
        level.queueLightUpdate(() -> moveLight(level, from, shift));
        return positions;
    }

    // The chunks the client holds within its view, as its storage reads them.
    private static List<LevelChunk> heldChunks(ClientChunkCache cache) {
        List<LevelChunk> held = new ArrayList<>();
        for (int index = 0; index < cache.storage.chunks.length(); index++) {
            LevelChunk chunk = cache.storage.chunks.get(index);
            if (chunk != null && cache.getChunk(chunk.getPos().x(), chunk.getPos().z(), false) == chunk) {
                held.add(chunk);
            }
        }
        return held;
    }

    // A held chunk built again at its shifted position from the same sections, heightmaps and block entities.
    private static LevelChunk movedCopy(ClientLevel level, LevelChunk held, LunarCrossingShift shift) {
        LevelChunk moved = new LevelChunk(level, shift.apply(held.getPos()), UpgradeData.EMPTY,
                new LevelChunkTicks<>(), new LevelChunkTicks<>(), held.getInhabitedTime(), held.getSections(), null,
                null);
        for (Map.Entry<Heightmap.Types, Heightmap> heightmap : held.getHeightmaps()) {
            moved.setHeightmap(heightmap.getKey(), heightmap.getValue().getRawData().clone());
        }
        moved.initializeLightSources();
        for (BlockEntity blockEntity : List.copyOf(held.getBlockEntities().values())) {
            CompoundTag tag = blockEntity.getUpdateTag(level.registryAccess());
            BlockEntity rebuilt = moved.getBlockEntity(shift.apply(blockEntity.getBlockPos()),
                    LevelChunk.EntityCreationType.IMMEDIATE);
            if (rebuilt != null && rebuilt.getType() == blockEntity.getType()) {
                rebuilt.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(),
                        tag));
            }
        }
        return moved;
    }

    // Copies each moved chunk's light to its shifted sections and clears the sections it left, as a forget does.
    private static void moveLight(ClientLevel level, List<ChunkPos> from, LunarCrossingShift shift) {
        LevelLightEngine light = level.getChunkSource().getLightEngine();
        for (ChunkPos left : from) {
            ChunkPos landed = shift.apply(left);
            for (int sectionY = light.getMinLightSection(); sectionY < light.getMaxLightSection(); sectionY++) {
                for (LightLayer layer : LightLayer.values()) {
                    DataLayer data = light.getLayerListener(layer).getDataLayerData(SectionPos.of(left, sectionY));
                    if (data != null) {
                        light.queueSectionData(layer, SectionPos.of(landed, sectionY), data.copy());
                    }
                    light.queueSectionData(layer, SectionPos.of(left, sectionY), null);
                }
            }
            light.setLightEnabled(landed, true);
            enableSections(level, light, left, landed);
        }
    }

    private static void enableSections(ClientLevel level, LevelLightEngine light, ChunkPos left, ChunkPos landed) {
        LevelChunk chunk = level.getChunkSource().getChunk(landed.x(), landed.z(), false);
        if (chunk == null) {
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
            light.updateSectionStatus(SectionPos.of(landed, sectionY), sections[sectionIndex].hasOnlyAir());
            light.updateSectionStatus(SectionPos.of(left, sectionY), true);
        }
        level.setSectionRangeDirty(landed.x() - 1, level.getMinSectionY(), landed.z() - 1, landed.x() + 1,
                level.getMaxSectionY(), landed.z() + 1);
    }

    // Moves the camera with the view, and samples the sky's surroundings there at once: the camera's probe ticks from
    // the last frame's position, which would read the side just left, where no chunk stands any more, and draw the
    // default sky for a tick.
    private static void moveTheCamera(Minecraft minecraft, ClientLevel level, LunarCrossingShift shift) {
        Camera camera = minecraft.gameRenderer.mainCamera();
        camera.setPosition(shift.apply(camera.position()));
        camera.attributeProbe().tick(level, camera.position());
    }

    // Moves every entity the client draws by the shift, with nothing to interpolate across the jump.
    private static void moveEntitiesInView(ClientLevel level, LunarCrossingShift shift) {
        List<Entity> inView = new ArrayList<>();
        level.entitiesForRendering().forEach(inView::add);
        for (Entity entity : inView) {
            entity.setPos(shift.apply(entity.position()));
            entity.setOldPosAndRot(shift.apply(entity.oldPosition()), entity.yRotO, entity.xRotO);
            InterpolationHandler interpolation = entity.getInterpolation();
            if (interpolation != null) {
                interpolation.cancel();
            }
        }
    }
}
