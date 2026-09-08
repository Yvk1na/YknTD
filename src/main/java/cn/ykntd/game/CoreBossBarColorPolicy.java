package cn.ykntd.game;

final class CoreBossBarColorPolicy {
    private CoreBossBarColorPolicy() {
    }

    static Tone tone(float progress) {
        if (!Float.isFinite(progress) || progress <= 0.25f) return Tone.RED;
        return progress > 0.5f ? Tone.GREEN : Tone.PURPLE;
    }

    enum Tone {
        GREEN,
        PURPLE,
        RED
    }
}
