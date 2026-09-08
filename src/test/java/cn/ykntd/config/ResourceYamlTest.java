package cn.ykntd.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceYamlTest {
    private final Yaml yaml = new Yaml();

    @Test
    void bundledYamlFilesParseAndContainRequiredRoots() {
        Map<?, ?> plugin = read("plugin.yml");
        Map<?, ?> config = read("config.yml");
        Map<?, ?> waves = read("waves.yml");
        Map<?, ?> arenas = read("arenas.yml");

        assertAll(
                () -> assertEquals("YknTD", plugin.get("name")),
                () -> assertEquals("1.4.0", plugin.get("version")),
                () -> assertEquals("cn.ykntd.YknTdPlugin", plugin.get("main")),
                () -> assertTrue(plugin.get("depend") instanceof java.util.List<?> dependencies
                        && dependencies.contains("Multiverse-Core")),
                () -> assertTrue(plugin.get("softdepend") instanceof java.util.List<?> dependencies
                        && dependencies.contains("AuraSkills")),
                () -> assertTrue(config.containsKey("dedicated-arena")),
                () -> assertEquals(6, config.get("config-version")),
                () -> assertTrue(config.get("dedicated-arena") instanceof Map<?, ?> dedicated
                        && Boolean.TRUE.equals(dedicated.get("reset-before-session"))
                        && Integer.valueOf(10).equals(dedicated.get("instance-count"))
                        && Boolean.FALSE.equals(dedicated.get("auto-create"))),
                () -> assertTrue(config.get("game") instanceof Map<?, ?> game
                        && !game.containsKey("mob-drops")),
                () -> assertTrue(config.get("sidebar") instanceof Map<?, ?> sidebar
                        && Boolean.TRUE.equals(sidebar.get("enabled"))
                        && "&b&lSKYBLOCK".equals(sidebar.get("title"))
                        && sidebar.get("lines") instanceof List<?> lines
                        && lines.size() <= 15
                        && lines.stream().anyMatch(line -> String.valueOf(line).contains("{north}"))
                        && lines.stream().anyMatch(line -> String.valueOf(line).contains("{enemies}"))),
                () -> assertTrue(config.containsKey("objectives")),
                () -> assertTrue(config.containsKey("rewards")),
                () -> assertTrue(config.get("messages") instanceof Map<?, ?> messages
                        && messages.get("joined") instanceof String joined
                        && !joined.contains("{instance}")
                        && messages.get("wave-started") instanceof String waveStarted
                        && !waveStarted.contains("{stage}")
                        && !waveStarted.toLowerCase(java.util.Locale.ROOT).contains("aura"),
                        "玩家提示不得暴露实例编号、难度阶段或 Aura 指数"),
                () -> assertEquals(3, waves.get("content-version")),
                () -> assertTrue(waves.get("waves") instanceof Map<?, ?> map && map.size() == 10),
                () -> assertTrue(arenas.containsKey("arenas"))
        );
    }

    @Test
    void tenWavesCoverFourLanesAndProgressAcrossThreePowerStages() {
        Map<?, ?> root = read("waves.yml");
        assertTrue(root.get("waves") instanceof Map<?, ?>);
        Map<?, ?> waves = (Map<?, ?>) root.get("waves");
        double previousTotalHealth = 0.0;
        int previousAuraLevel = -1;

        for (int number = 1; number <= 10; number++) {
            assertTrue(waves.get(number) instanceof Map<?, ?>, "缺少第 " + number + " 波");
            Map<?, ?> wave = (Map<?, ?>) waves.get(number);
            String waveName = String.valueOf(wave.get("name"));
            assertFalse(waveName.contains("前期") || waveName.contains("中期") || waveName.contains("后期"),
                    "第 " + number + " 波名称不得显示难度阶段");
            String expectedStage = number <= 3 ? "EARLY" : number <= 7 ? "MID" : "LATE";
            assertEquals(expectedStage, wave.get("stage"));
            assertTrue(wave.get("recommended-aura-level") instanceof Number);
            int auraLevel = ((Number) wave.get("recommended-aura-level")).intValue();
            assertTrue(auraLevel > previousAuraLevel,
                    "第 " + number + " 波 Aura 参考等级必须递增");
            previousAuraLevel = auraLevel;
            assertTrue(wave.get("groups") instanceof List<?>);

            Set<String> lanes = new HashSet<>();
            double totalHealth = 0.0;
            for (Object raw : (List<?>) wave.get("groups")) {
                assertTrue(raw instanceof Map<?, ?>);
                Map<?, ?> group = (Map<?, ?>) raw;
                assertTrue(group.get("base-health") instanceof Number);
                assertTrue(group.get("base-damage") instanceof Number);
                double health = ((Number) group.get("base-health")).doubleValue();
                assertTrue(group.get("lanes") instanceof List<?>);
                List<?> groupLanes = (List<?>) group.get("lanes");
                for (Object lane : groupLanes) lanes.add(String.valueOf(lane));
                int perLane = ((Number) group.get("amount-per-lane")).intValue();
                totalHealth += health * perLane * groupLanes.size();
            }
            assertEquals(Set.of("NORTH", "EAST", "SOUTH", "WEST"), lanes,
                    "第 " + number + " 波没有覆盖四路");
            assertTrue(totalHealth > previousTotalHealth,
                    "第 " + number + " 波单人总基础生命必须高于上一波");
            previousTotalHealth = totalHealth;
        }
    }

    private Map<?, ?> read(String name) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream, name + " should be on the test classpath");
            Object parsed = yaml.load(stream);
            assertTrue(parsed instanceof Map<?, ?>, name + " root should be a map");
            return (Map<?, ?>) parsed;
        } catch (Exception exception) {
            throw new AssertionError("Could not parse " + name, exception);
        }
    }
}
