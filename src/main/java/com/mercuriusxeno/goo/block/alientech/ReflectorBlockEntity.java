package com.mercuriusxeno.goo.block.alientech;

import com.mercuriusxeno.goo.ability.world.LightRailEnd;
import com.mercuriusxeno.goo.ability.world.Reflectors;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A reflector's state: the reflectors its rails join, the light its network carries, which its renderer draws the
 * beams by, and the player who placed it, whom its rails credit. It refreshes its rails as it first ticks and every
 * second after, so a rail follows the blocks set across it and the light around its network.
 * decision reflector-rails-carry-the-brightest-light
 */
public class ReflectorBlockEntity extends GooSyncedBlockEntity implements LightRailEnd {

    /** Ticks between rail refreshes. */
    static final int REFRESH_TICKS = 20;
    private static final String TAG_LINKS = "Links";
    private static final String TAG_LINK_LIGHT = "LinkLight";
    private static final String TAG_PLACER = "Placer";

    private List<BlockPos> links = List.of();
    private int linkLight;
    private @Nullable UUID placer;
    private boolean refreshed;

    /**
     * Creates the reflector's block entity.
     *
     * @param pos   its position
     * @param state its state
     */
    public ReflectorBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.REFLECTOR.get(), pos, state);
    }

    /**
     * Refreshes the reflector's rails on its first tick and every second after.
     *
     * @param level     the level
     * @param pos       the reflector's position
     * @param state     its state
     * @param reflector its block entity
     */
    static void serverTick(Level level, BlockPos pos, BlockState state, ReflectorBlockEntity reflector) {
        if (level instanceof ServerLevel server && (!reflector.refreshed || level.getGameTime() % REFRESH_TICKS == 0)) {
            reflector.refreshed = true;
            Reflectors.refresh(server, pos);
        }
    }

    /**
     * Records the player who placed the reflector.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param player the player's id
     */
    void placedBy(UUID player) {
        placer = player;
        setChanged();
    }

    /**
     * Every lens is open save the one toward the face the reflector attaches by.
     *
     * @param toward the side
     * @return true on every side but the attached one
     */
    @Override
    public boolean lensOpen(Direction toward) {
        return toward != ReflectorBlock.attachedSide(getBlockState());
    }

    /**
     * Records the links and the light, syncing the reflector when either changed so its renderer draws the beams.
     *
     * @param partners the reflectors linked by rail
     * @param light    the network's light
     */
    @Override
    public void setLinks(List<BlockPos> partners, int light) {
        if (!links.equals(partners) || linkLight != light) {
            links = List.copyOf(partners);
            linkLight = light;
            BlockEntitySync.markDirtyAndSync(this);
        }
    }

    @Override
    public List<BlockPos> getLinks() {
        return links;
    }

    /**
     * @return the light its network's rails carry
     */
    public int getLinkLight() {
        return linkLight;
    }

    @Override
    public @Nullable UUID railPlacer() {
        return placer;
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        links = input.read(TAG_LINKS, BlockPos.CODEC.listOf()).orElse(List.of());
        linkLight = input.getIntOr(TAG_LINK_LIGHT, 0);
        placer = input.read(TAG_PLACER, UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_LINKS, BlockPos.CODEC.listOf(), links);
        output.putInt(TAG_LINK_LIGHT, linkLight);
        if (placer != null) {
            output.store(TAG_PLACER, UUIDUtil.CODEC, placer);
        }
    }
}
