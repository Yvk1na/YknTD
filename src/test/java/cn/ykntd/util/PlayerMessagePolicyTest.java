package cn.ykntd.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerMessagePolicyTest {
    private static final String FALLBACK = "&c⚔ {name}开始！ &7人数：{players}。";

    @Test
    void replacesLegacyStageAndAuraAnnouncement() {
        String legacy = "&c⚔ {name}开始！ &7阶段：{stage} | 人数：{players} | 队伍 Aura 指数：{aura}。";

        assertEquals(FALLBACK,
                PlayerMessagePolicy.sanitizeTemplate("wave-started", legacy, FALLBACK));
    }

    @Test
    void preservesCleanCustomAnnouncementAndRemovesStageFromWaveName() {
        String custom = "&6第八波：{name}，当前 {players} 人";

        assertEquals(custom,
                PlayerMessagePolicy.sanitizeTemplate("wave-started", custom, FALLBACK));
        assertEquals("&d&l无月军势",
                PlayerMessagePolicy.sanitizeWaveName("&d&l后期·无月军势"));
    }
}
