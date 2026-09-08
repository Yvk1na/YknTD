package cn.ykntd.listener;

final class RaidDropPolicy {
    private RaidDropPolicy() {
    }

    static boolean suppress(boolean raidTagged, boolean player) {
        return raidTagged && !player;
    }
}
