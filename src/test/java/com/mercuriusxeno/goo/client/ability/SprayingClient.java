package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.List;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A client whose level holds one player with a glove in the right hand,
 * looking south and thirty degrees down, the local player in first person or
 * another player, the glove's selected goo type stubbed through
 * {@link GooGloveItem#getSelectedType}. Close it to restore the statics it swaps.
 */
public final class SprayingClient implements AutoCloseable {

    /** The spraying player's entity id. */
    public static final int CASTER = 7;
    /** The spraying player's eye. */
    public static final Vec3 EYE = new Vec3(3, 65.62, -2);
    /** The eye's height above the feet. */
    private static final double EYE_HEIGHT = 1.62;
    /** The look's pitch, thirty degrees down. */
    public static final float PITCH = 30f;
    /** The look's yaw, a little west of south. */
    public static final float YAW = 20f;
    /** Where the server said the glove stood with the stream's tick. */
    public static final Vec3 SENT_GLOVE = new Vec3(3.3, 65.2, -1.4);

    private final Minecraft minecraft;
    private final ClientLevel level;
    private final ItemStack glove;
    private final MockedStatic<Minecraft> client;
    private final MockedStatic<GooGloveItem> gloves;

    private SprayingClient(boolean local, ResourceKey<GooTypeDefinition> selected) {
        minecraft = mock(Minecraft.class);
        level = mock(ClientLevel.class);
        minecraft.level = level;
        AbstractClientPlayer caster = local ? mock(LocalPlayer.class) : mock(RemotePlayer.class);
        when(level.getEntity(CASTER)).thenReturn(caster);
        when(level.players()).thenReturn(List.of(caster));
        when(caster.getId()).thenReturn(CASTER);
        when(caster.getEyePosition(anyFloat())).thenReturn(EYE);
        when(caster.getViewXRot(anyFloat())).thenReturn(PITCH);
        when(caster.getViewYRot(anyFloat())).thenReturn(YAW);
        when(caster.getMainArm()).thenReturn(HumanoidArm.RIGHT);
        when(caster.getPosition(anyFloat())).thenReturn(EYE.subtract(0, EYE_HEIGHT, 0));
        glove = mock(ItemStack.class);
        when(glove.getItem()).thenReturn(mock(GooGloveItem.class));
        when(caster.getMainHandItem()).thenReturn(glove);
        when(caster.getOffhandItem()).thenReturn(mock(ItemStack.class));
        Camera camera = mock(Camera.class);
        when(camera.position()).thenReturn(EYE);
        when(camera.xRot()).thenReturn(PITCH);
        when(camera.yRot()).thenReturn(YAW);
        GameRenderer renderer = mock(GameRenderer.class);
        when(renderer.getMainCamera()).thenReturn(camera);
        Options options = mock(Options.class);
        when(options.getCameraType()).thenReturn(CameraType.FIRST_PERSON);
        setField("gameRenderer", renderer);
        setField("options", options);
        if (local) {
            minecraft.player = (LocalPlayer) caster;
        }
        client = mockStatic(Minecraft.class);
        client.when(Minecraft::getInstance).thenReturn(minecraft);
        gloves = mockStatic(GooGloveItem.class);
        gloves.when(() -> GooGloveItem.getSelectedType(glove)).thenReturn(selected);
    }

    /**
     * A client drawing the local player spraying in first person.
     *
     * @param selected the glove's selected goo type
     * @return the client
     */
    public static SprayingClient local(ResourceKey<GooTypeDefinition> selected) {
        return new SprayingClient(true, selected);
    }

    /**
     * A client drawing another player spraying.
     *
     * @param selected the glove's selected goo type
     * @return the client
     */
    public static SprayingClient another(ResourceKey<GooTypeDefinition> selected) {
        return new SprayingClient(false, selected);
    }

    /**
     * Sets the level's game time.
     *
     * @param gameTime the game time in ticks
     */
    public void at(long gameTime) {
        when(level.getGameTime()).thenReturn(gameTime);
    }

    /** @return the spraying player's glove */
    public ItemStack glove() {
        return glove;
    }

    private void setField(String name, Object value) {
        try {
            Field field = Minecraft.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(minecraft, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        gloves.close();
        client.close();
        LiftedPalmBlob.forgetEverySpray();
    }
}
