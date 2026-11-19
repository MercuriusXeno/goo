package com.mercuriusxeno.goo.client.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Under Sculker the sense view draws sculk and shroom blocks with no echo, at any noise level, and leaves the rest
 * to the echoes: the shroom_network tag, read through the classpath, names which.
 * decision sculker-absorbs-sight-as-shroom-vision
 */
class ShroomVisionTest {

    private static final String TAG_JSON = "/data/goo/tags/block/%s.json";
    private static final String NAMESPACE = "goo:";
    private static final BlockPos SCULK_AT = new BlockPos(1, 64, 0);
    private static final BlockPos MUSHROOM_BLOCK_AT = new BlockPos(2, 64, 0);
    private static final BlockPos STONE_AT = new BlockPos(3, 64, 0);
    private static final float PRESENCE = 1f;

    @Test
    void sculkAndShroomBlocksDrawWithNoEchoAndStoneDoesNot() throws IOException {
        Set<String> network = members("shroom_network");
        BlockState sculk = mock(BlockState.class);
        BlockState mushroomBlock = mock(BlockState.class);
        BlockState stone = mock(BlockState.class);
        Map<BlockState, String> ids = Map.of(sculk, "minecraft:sculk", mushroomBlock, "minecraft:brown_mushroom_block",
                stone, "minecraft:stone");
        Map<BlockPos, BlockState> nearby = Map.of(SCULK_AT, sculk, MUSHROOM_BLOCK_AT, mushroomBlock, STONE_AT, stone);

        Map<BlockPos, Float> drawn = ShroomVision.drawn(Map.of(), nearby, state -> network.contains(ids.get(state)),
                PRESENCE);

        assertEquals(Set.of(SCULK_AT, MUSHROOM_BLOCK_AT), drawn.keySet());
    }

    /** The block ids a goo block tag names, its own tags' members folded in. */
    private static Set<String> members(String tag) throws IOException {
        String path = TAG_JSON.formatted(tag);
        Set<String> ids = new HashSet<>();
        try (InputStream in = ShroomVisionTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should ship");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (JsonElement value : json.getAsJsonArray("values")) {
                String id = value.getAsString();
                if (id.startsWith("#" + NAMESPACE)) {
                    ids.addAll(members(id.substring(1 + NAMESPACE.length())));
                } else {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
