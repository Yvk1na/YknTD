package cn.ykntd.model;

public enum RaidState {
    WAITING("等待中"),
    COUNTDOWN("准备中"),
    WAVE("防守中"),
    INTERMISSION("整备中"),
    VICTORY("胜利"),
    FAILED("失败"),
    STOPPED("已终止");

    private final String displayName;

    RaidState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean terminal() {
        return this == VICTORY || this == FAILED || this == STOPPED;
    }
}
