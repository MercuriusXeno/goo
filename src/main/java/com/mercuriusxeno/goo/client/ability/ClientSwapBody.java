package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.SwapBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A swap body as the client draws it: its owner's skin, looked up from the
 * profile the server synced the way vanilla's client mannequin looks up its
 * own, so the player renderer draws the body as its owner.
 * swap-body-stands-until-reverted
 */
public class ClientSwapBody extends SwapBody implements ClientAvatarEntity {

    private static final String LOG_SKIN_LOOKUP_FAILED = "Swap body skin lookup failed";

    private final ClientAvatarState avatarState = new ClientAvatarState();
    private final PlayerSkinRenderCache skinRenderCache;
    private @Nullable CompletableFuture<Optional<PlayerSkin>> skinLookup;
    private PlayerSkin skin = ClientMannequin.DEFAULT_SKIN;

    /**
     * Creates a client body.
     *
     * @param type            the swap body's type
     * @param level           the client level
     * @param skinRenderCache the cache skins are looked up through
     */
    public ClientSwapBody(EntityType<? extends SwapBody> type, Level level, PlayerSkinRenderCache skinRenderCache) {
        super(type, level);
        this.skinRenderCache = skinRenderCache;
    }

    /**
     * Installs the factory that builds a client body on a client level and
     * the plain body elsewhere, as vanilla does for its mannequin.
     */
    public static void install() {
        SwapBody.buildWith((type, level) -> level instanceof ClientLevel
                ? new ClientSwapBody(type, level, Minecraft.getInstance().playerSkinRenderCache())
                : new SwapBody(type, level));
    }

    @Override
    public void tick() {
        super.tick();
        avatarState.tick(position(), getDeltaMovement());
        if (skinLookup != null && skinLookup.isDone()) {
            try {
                skinLookup.get().ifPresent(found -> skin = found);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException e) {
                Goo.LOGGER.error(LOG_SKIN_LOOKUP_FAILED, e);
            }
            skinLookup = null;
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor.equals(DATA_PROFILE)) {
            if (skinLookup != null) {
                skinLookup.cancel(false);
            }
            skinLookup = skinRenderCache.lookup(getProfile())
                    .thenApply(info -> info.map(PlayerSkinRenderCache.RenderInfo::playerSkin));
        }
    }

    @Override
    public ClientAvatarState avatarState() {
        return avatarState;
    }

    @Override
    public PlayerSkin getSkin() {
        return skin;
    }

    @Override
    public @Nullable Component belowNameDisplay() {
        return null;
    }

    @Override
    public Parrot.@Nullable Variant getParrotVariantOnShoulder(boolean left) {
        return null;
    }

    @Override
    public boolean showExtraEars() {
        return false;
    }
}
