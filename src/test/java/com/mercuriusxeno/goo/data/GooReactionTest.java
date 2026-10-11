package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;

/**
 * The shipped goo reactions decode through the codec the loader uses, and
 * no two share an input set, which the loader logs as an error.
 */
class GooReactionTest {

    private static final String REACTIONS_DIRECTORY = "/data/goo/goo_reactions";
    private static final String JSON_SUFFIX = ".json";

    @BeforeAll
    static void standRegistries() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    /**
     * Zoo's twins of the reactions that made or ate vital load, each naming zoo
     * (decision zoo-ships-from-animal-items).
     */
    @Test
    void zooReactionsLoad() throws IOException, URISyntaxException {
        Map<String, GooReaction> shipped = shippedReactions();
        for (String name : List.of("zoo_from_unstable_shroom", "shroom_from_shroom_zoo",
                "nether_from_zoo_nether", "leaf_from_unstable_zoo")) {
            GooReaction reaction = shipped.get(name);
            assertNotNull(reaction, name + " is not shipped");
            assertTrue(names(reaction.inputs()).contains(GooTypes.ZOO) || names(reaction.outputs()).contains(GooTypes.ZOO),
                    name + " names no zoo");
        }
    }

    /**
     * Every pair of shipped reactions differs in its input set.
     */
    @Test
    void shippedReactionsShareNoInputSet() throws IOException, URISyntaxException {
        Map<Object, String> byInputs = new HashMap<>();
        for (Map.Entry<String, GooReaction> entry : shippedReactions().entrySet()) {
            String clash = byInputs.put(entry.getValue().inputTypeSet(), entry.getKey());
            assertNull(clash, entry.getKey() + " shares its inputs with " + clash);
        }
    }

    private static List<Object> names(List<GooReaction.FluidEntry> entries) {
        List<Object> names = new ArrayList<>();
        for (GooReaction.FluidEntry entry : entries) {
            names.add(entry.fluid().map(key -> key, fluid -> fluid));
        }
        return names;
    }

    private static Map<String, GooReaction> shippedReactions() throws IOException, URISyntaxException {
        URL directory = Objects.requireNonNull(GooReactionTest.class.getResource(REACTIONS_DIRECTORY), REACTIONS_DIRECTORY);
        Map<String, GooReaction> shipped = new HashMap<>();
        try (Stream<Path> files = Files.list(Path.of(directory.toURI()))) {
            for (Path file : files.filter(path -> path.toString().endsWith(JSON_SUFFIX)).toList()) {
                String name = file.getFileName().toString().replace(JSON_SUFFIX, "");
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    shipped.put(name, GooReaction.codecFor(Identifier.fromNamespaceAndPath("goo", name))
                            .parse(JsonOps.INSTANCE, json).getOrThrow());
                }
            }
        }
        return shipped;
    }
}
