package cn.ykntd.hud;

import cn.ykntd.YknTdPlugin;
import cn.ykntd.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RaidSidebar implements AutoCloseable {
    private static final String ENTRY_CODES = "0123456789abcde";

    private final YknTdPlugin plugin;
    private final RaidSidebarSettings settings;
    private final Map<UUID, SidebarView> views = new HashMap<>();

    public RaidSidebar(YknTdPlugin plugin, RaidSidebarSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void show(Player player, RaidSidebarSnapshot snapshot) {
        if (!settings.enabled() || !player.isOnline()) return;
        try {
            SidebarView view = views.computeIfAbsent(player.getUniqueId(), ignored -> create(player));
            Map<String, String> values = snapshot.placeholders();
            for (int index = 0; index < settings.lines().size(); index++) {
                String line = Text.placeholders(settings.lines().get(index), values);
                view.teams().get(index).prefix(Text.component(line));
            }
            if (player.getScoreboard() != view.scoreboard()) player.setScoreboard(view.scoreboard());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("更新 " + player.getName() + " 的夜袭侧边栏失败："
                    + exception.getMessage());
        }
    }

    public void hide(Player player) {
        SidebarView view = views.remove(player.getUniqueId());
        if (view == null) return;
        try {
            if (player.getScoreboard() == view.scoreboard()) player.setScoreboard(view.previous());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("恢复 " + player.getName() + " 的原侧边栏失败："
                    + exception.getMessage());
        } finally {
            dispose(view);
        }
    }

    public void forget(Player player) {
        SidebarView view = views.remove(player.getUniqueId());
        if (view != null) dispose(view);
    }

    private SidebarView create(Player player) {
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager == null) throw new IllegalStateException("计分板管理器尚未就绪");
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("ykntd_raid", Criteria.DUMMY,
                Text.component(settings.title()));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<Team> teams = new ArrayList<>(settings.lines().size());
        for (int index = 0; index < settings.lines().size(); index++) {
            Team team = board.registerNewTeam("ykntd_line_" + index);
            String entry = "§" + ENTRY_CODES.charAt(index);
            team.addEntry(entry);
            objective.getScore(entry).setScore(settings.lines().size() - index);
            teams.add(team);
        }
        return new SidebarView(player.getScoreboard(), board, objective, List.copyOf(teams));
    }

    private static void dispose(SidebarView view) {
        try {
            view.objective().unregister();
        } catch (IllegalStateException ignored) {
            // Already unregistered by another cleanup path.
        }
        for (Team team : view.teams()) {
            try {
                team.unregister();
            } catch (IllegalStateException ignored) {
                // Already unregistered by another cleanup path.
            }
        }
    }

    @Override
    public void close() {
        for (UUID playerId : List.copyOf(views.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) hide(player);
            else {
                SidebarView view = views.remove(playerId);
                if (view != null) dispose(view);
            }
        }
    }

    private record SidebarView(Scoreboard previous, Scoreboard scoreboard,
                               Objective objective, List<Team> teams) {
    }
}
