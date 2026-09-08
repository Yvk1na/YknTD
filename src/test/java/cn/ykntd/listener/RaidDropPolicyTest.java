package cn.ykntd.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidDropPolicyTest {
    @Test
    void suppressesTaggedRaidCreaturesOnly() {
        assertTrue(RaidDropPolicy.suppress(true, false));
        assertFalse(RaidDropPolicy.suppress(true, true));
        assertFalse(RaidDropPolicy.suppress(false, false));
    }
}
