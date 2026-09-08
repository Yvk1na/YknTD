package cn.ykntd.command;

import cn.ykntd.YknTdPlugin;
import cn.ykntd.arena.ArenaDefinition;
import cn.ykntd.arena.ArenaRepository;
import cn.ykntd.game.GameManager;
import cn.ykntd.game.RaidSession;
import cn.ykntd.model.Lane;
import cn.ykntd.stats.PlayerStats;
import cn.ykntd.util.Text;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class YknTdCommand implements CommandExecutor, TabCompleter {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##");
    private final YknTdPlugin plugin;

    public YknTdCommand(YknTdPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "status" -> status(sender, args);
            case "stats" -> stats(sender, args);
            case "top" -> top(sender);
            case "start" -> start(sender, args);
            case "stop" -> stop(sender, args);
            case "reload" -> reload(sender);
            case "arena" -> arena(sender, args);
            default -> reply(sender, "&c未知子命令。使用 &f/ykntd help &c查看帮助。");
        }
        return true;
    }

    private void help(CommandSender sender) {
        reply(sender, "&d&lYknTD 浮岛夜袭");
        reply(sender, "&f/ykntd join [场地] &7- 自动分配空闲竞技场");
        reply(sender, "&f/ykntd leave &7- 离开夜袭");
        reply(sender, "&f/ykntd status [场地] &7- 查看战况");
        reply(sender, "&f/ykntd stats [玩家] &7- 查看统计");
        reply(sender, "&f/ykntd top &7- 查看贡献榜");
        if (sender.hasPermission("ykntd.admin")) {
            reply(sender, "&c管理员：&f/ykntd arena|start|stop|reload");
        }
    }

    private void join(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "ykntd.play")) return;
        String arenaId;
        if (args.length >= 2) {
            arenaId = args[1];
        } else {
            LinkedHashSet<String> ready = new LinkedHashSet<>();
            if (plugin.arenaWorlds().settings().enabled()) {
                ready.add(plugin.arenaWorlds().settings().arenaId());
            }
            plugin.arenas().all().stream()
                    .filter(value -> !isInternalDedicatedId(value.id()))
                    .filter(value -> value.problems(plugin.games().settings().boundaryRadius()).isEmpty())
                    .map(ArenaDefinition::id)
                    .forEach(ready::add);
            if (ready.size() != 1) {
                reply(sender, "&c请指定场地：&f/ykntd join <场地>&c。可用：&f" +
                        String.join(", ", ready));
                return;
            }
            arenaId = ready.getFirst();
        }
        GameManager.ActionResult result = plugin.games().join(player, arenaId);
        if (!result.success()) reply(sender, "&c" + result.message());
    }

    private void leave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "ykntd.play")) return;
        GameManager.ActionResult result = plugin.games().leave(player);
        if (!result.success()) reply(sender, "&c" + result.message());
    }

    private void status(CommandSender sender, String[] args) {
        RaidSession session;
        if (args.length >= 2) {
            if (isInternalDedicatedId(args[1])) {
                reply(sender, "&e没有找到对应的夜袭场次。");
                return;
            }
            session = plugin.games().session(args[1]).orElse(null);
        } else {
            session = sender instanceof Player player ? plugin.games().session(player).orElse(null) : null;
        }
        if (session == null) {
            if (args.length >= 2 && plugin.arenaWorlds().settings().arenaId()
                    .equals(ArenaRepository.normalize(args[1]))) {
                reply(sender, "&d进行中场次 &f" + plugin.games().activeDedicatedCount()
                        + " &8| &d场地上限 &f" + plugin.arenaWorlds().settings().instanceCount()
                        + " &8| &d已加载场地 &f"
                        + plugin.arenaWorlds().loadedInstanceCount());
                return;
            }
            reply(sender, "&e没有找到对应的夜袭场次。");
            return;
        }
        reply(sender, "&d场地 &f" + session.arenaId() + " &8| &d状态 &f" + session.state().displayName()
                + " &8| &d玩家 &f" + session.participantCount());
        reply(sender, "&d波次 &f" + session.waveNumber() + "/" + session.totalWaves()
                + " &8| &d敌人 &f" + session.enemyCount()
                + " &8| &d核心 &f" + NUMBER.format(session.coreHealth()));
        reply(sender, "&b北路 &f" + ward(session, Lane.NORTH)
                + " &8| &6东路 &f" + ward(session, Lane.EAST)
                + " &8| &a南路 &f" + ward(session, Lane.SOUTH)
                + " &8| &d西路 &f" + ward(session, Lane.WEST));
    }

    private String ward(RaidSession session, Lane lane) {
        return session.breached(lane) ? "&4失守" : NUMBER.format(session.wardHealth(lane));
    }

    private void stats(CommandSender sender, String[] args) {
        PlayerStats found;
        if (args.length >= 2) found = plugin.games().stats().findByName(args[1]).orElse(null);
        else if (sender instanceof Player player) found = plugin.games().stats().get(player.getUniqueId()).orElse(null);
        else {
            reply(sender, "&c控制台必须指定玩家名。");
            return;
        }
        if (found == null) {
            reply(sender, "&e该玩家还没有夜袭记录。");
            return;
        }
        reply(sender, "&d" + found.name() + " &7的夜袭记录：&f" + found.games() + " 场 &8| &a"
                + found.wins() + " 胜 &8| &e最高第 " + found.bestWave() + " 波 &8| &b总贡献 "
                + NUMBER.format(found.totalScore()));
    }

    private void top(CommandSender sender) {
        List<PlayerStats> top = plugin.games().stats().top(10);
        reply(sender, "&d&l浮岛夜袭贡献榜");
        if (top.isEmpty()) {
            reply(sender, "&7暂无记录。");
            return;
        }
        for (int index = 0; index < top.size(); index++) {
            PlayerStats value = top.get(index);
            reply(sender, "&e#" + (index + 1) + " &f" + value.name() + " &8- &b"
                    + NUMBER.format(value.totalScore()) + " &7(" + value.wins() + " 胜)");
        }
    }

    private void start(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            reply(sender, "&e用法：/ykntd start <场地>");
            return;
        }
        showResult(sender, plugin.games().start(args[1]));
    }

    private void stop(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            reply(sender, "&e用法：/ykntd stop <场地>");
            return;
        }
        showResult(sender, plugin.games().stop(args[1], "管理员终止"));
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        YknTdPlugin.ReloadResult result = plugin.reloadPluginData();
        reply(sender, result.success() ? "&a" + result.message() : "&c" + result.message());
    }

    private void arena(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            arenaHelp(sender);
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("generate") || action.equals("reset")) {
            YknTdPlugin.ArenaOperationResult result = plugin.generateDedicatedArena(action.equals("reset"));
            reply(sender, (result.success() ? "&a" : "&c") + result.message());
            return;
        }
        if (action.equals("dedicated")) {
            var manager = plugin.arenaWorlds();
            var settings = manager.settings();
            reply(sender, "&d专属场地 &f" + settings.arenaId() + " &8| &d实例 &f"
                    + plugin.games().activeDedicatedCount() + "/" + settings.instanceCount()
                    + " &8| &d已加载 &f" + manager.loadedInstanceCount());
            reply(sender, "&7半径 " + settings.radius() + "，地面 Y=" + settings.floorY()
                    + "，自动创建=" + settings.autoCreate());
            return;
        }
        if (action.equals("list")) {
            Collection<ArenaDefinition> all = plugin.arenas().all();
            LinkedHashSet<String> listed = new LinkedHashSet<>();
            if (plugin.arenaWorlds().settings().enabled()) {
                listed.add(plugin.arenaWorlds().settings().arenaId() + "&a✓&f(实例池)");
            }
            all.stream().filter(value -> !value.id().equals(plugin.arenaWorlds().settings().arenaId()))
                    .filter(value -> !isInternalDedicatedId(value.id()))
                    .map(value -> value.id() + (value.complete() ? "&a✓&f" : "&c✗&f"))
                    .forEach(listed::add);
            reply(sender, "&d场地：&f" + (listed.isEmpty() ? "暂无" : String.join(", ", listed)));
            return;
        }
        if (args.length < 3) {
            arenaHelp(sender);
            return;
        }
        String id = ArenaRepository.normalize(args[2]);
        boolean reservedDedicatedId = id.equals(plugin.arenaWorlds().settings().arenaId())
                || plugin.arenaWorlds().settings().isInternalInstanceId(id);
        if (action.equals("create")) {
            if (reservedDedicatedId) reply(sender, "&c该 ID 由专属竞技场实例池保留。");
            else if (!ArenaRepository.validId(id)) reply(sender, "&c场地 ID 只能包含小写字母、数字、_、-，长度 1～32。");
            else if (plugin.arenas().create(id)) reply(sender, "&a已创建场地 &f" + id + "&a。");
            else reply(sender, "&c场地已经存在。");
            return;
        }
        ArenaDefinition definition = plugin.arenas().find(id).orElse(null);
        if (definition == null) {
            if (reservedDedicatedId) {
                reply(sender, "&c该 ID 属于专属实例池；请使用 &f/ykntd arena dedicated&c。");
                return;
            }
            reply(sender, "&c不存在场地：&f" + id);
            return;
        }
        if (action.equals("delete")) {
            if (reservedDedicatedId) {
                reply(sender, "&c专属生成场地不能删除；可在 config.yml 中关闭 dedicated-arena。");
            } else if (plugin.games().hasSession(id)) reply(sender, "&c该场地有夜袭场次，必须先停止。");
            else if (plugin.arenas().delete(id)) reply(sender, "&a已删除场地 &f" + id + "&a。");
            return;
        }
        if (action.equals("info") || action.equals("validate")) {
            info(sender, definition);
            return;
        }
        if (plugin.games().hasSession(id)) {
            reply(sender, "&c该场地有等待或进行中的夜袭，必须先停止或让玩家离开。");
            return;
        }
        if (reservedDedicatedId) {
            reply(sender, "&c专属生成场地坐标固定；请使用 &f/ykntd arena reset &c重建。");
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) return;
        switch (action) {
            case "setlobby" -> {
                plugin.arenas().setLobby(definition, player.getLocation());
                setDone(sender, definition, "大厅");
            }
            case "setcore" -> {
                plugin.arenas().setCore(definition, player.getLocation());
                setDone(sender, definition, "核心");
            }
            case "setspawn", "setward", "addwaypoint", "clearwaypoints" ->
                    setLanePoint(sender, player, definition, action, args);
            default -> arenaHelp(sender);
        }
    }

    private void setLanePoint(CommandSender sender, Player player, ArenaDefinition arena,
                              String action, String[] args) {
        if (args.length < 4) {
            reply(sender, "&e用法：/ykntd arena " + action + " " + arena.id()
                    + " <north|east|south|west>");
            return;
        }
        Lane lane = Lane.parse(args[3]).orElse(null);
        if (lane == null) {
            reply(sender, "&c路线必须是 north、east、south 或 west。");
            return;
        }
        switch (action) {
            case "setspawn" -> plugin.arenas().setSpawn(arena, lane, player.getLocation());
            case "setward" -> plugin.arenas().setWard(arena, lane, player.getLocation());
            case "addwaypoint" -> plugin.arenas().addWaypoint(arena, lane, player.getLocation());
            case "clearwaypoints" -> plugin.arenas().clearWaypoints(arena, lane);
            default -> { return; }
        }
        reply(sender, "&a已更新场地 &f" + arena.id() + " &a的 &f" + lane.displayName() + " " + action + "&a。");
    }

    private void info(CommandSender sender, ArenaDefinition arena) {
        List<String> problems = arena.problems(plugin.games().settings().boundaryRadius());
        reply(sender, "&d场地 &f" + arena.id() + " &8- " + (problems.isEmpty() ? "&a配置完整" : "&c配置未完成"));
        if (!problems.isEmpty()) reply(sender, "&c缺少或错误：&f" + String.join("、", problems));
        for (Lane lane : Lane.values()) {
            reply(sender, "&f" + lane.displayName() + " &7路点：&e" + arena.waypoints(lane).size()
                    + " &8| 出生：" + location(arena.spawn(lane)) + " &8| 结界：" + location(arena.ward(lane)));
        }
    }

    private void setDone(CommandSender sender, ArenaDefinition arena, String target) {
        reply(sender, "&a已把场地 &f" + arena.id() + " &a的" + target + "设为当前位置。");
    }

    private void arenaHelp(CommandSender sender) {
        reply(sender, "&d场地管理：");
        reply(sender, "&f/ykntd arena list");
        reply(sender, "&f/ykntd arena dedicated|generate|reset");
        reply(sender, "&f/ykntd arena create|delete|info|validate <场地>");
        reply(sender, "&f/ykntd arena setlobby|setcore <场地>");
        reply(sender, "&f/ykntd arena setspawn|setward <场地> <north|east|south|west>");
        reply(sender, "&f/ykntd arena addwaypoint|clearwaypoints <场地> <north|east|south|west>");
    }

    private void showResult(CommandSender sender, GameManager.ActionResult result) {
        reply(sender, (result.success() ? "&a" : "&c") + result.message());
    }

    private boolean requireAdmin(CommandSender sender) {
        return require(sender, "ykntd.admin");
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        reply(sender, "&c你没有权限执行此操作。");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        reply(sender, "&c该命令只能由玩家执行。");
        return null;
    }

    private void reply(CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&5&l夜袭 &8» &r");
        sender.sendMessage(Text.component(prefix + message));
    }

    private static String location(Location location) {
        if (location == null || location.getWorld() == null) return "&c未设置";
        return "&f" + location.getWorld().getName() + " " + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("help", "join", "leave", "status", "stats", "top"));
            if (sender.hasPermission("ykntd.admin")) values.addAll(List.of("arena", "start", "stop", "reload"));
            return filter(values, args[0]);
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("join") || action.equals("status")) {
                return filter(publicArenaIds(), args[1]);
            }
            if ((action.equals("start") || action.equals("stop")) && sender.hasPermission("ykntd.admin")) {
                return filter(sessionAndArenaIds(), args[1]);
            }
        }
        if (args[0].equalsIgnoreCase("arena")) {
            if (args.length == 2) return filter(List.of("list", "create", "delete", "info", "validate",
                    "dedicated", "generate", "reset", "setlobby", "setcore", "setspawn", "setward",
                    "addwaypoint", "clearwaypoints"), args[1]);
            if (args.length == 3 && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("list")) {
                return filter(arenaIds(), args[2]);
            }
            if (args.length == 4 && List.of("setspawn", "setward", "addwaypoint", "clearwaypoints")
                    .contains(args[1].toLowerCase(Locale.ROOT))) {
                return filter(List.of("north", "east", "south", "west"), args[3]);
            }
        }
        return List.of();
    }

    private List<String> arenaIds() {
        return publicArenaIds();
    }

    private List<String> publicArenaIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (plugin.arenaWorlds().settings().enabled()) {
            var settings = plugin.arenaWorlds().settings();
            ids.add(settings.arenaId());
        }
        plugin.arenas().all().stream().map(ArenaDefinition::id)
                .filter(id -> !isInternalDedicatedId(id))
                .forEach(ids::add);
        return List.copyOf(ids);
    }

    private List<String> sessionAndArenaIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>(publicArenaIds());
        ids.addAll(plugin.games().sessionKeys());
        return List.copyOf(ids);
    }

    private boolean isInternalDedicatedId(String rawId) {
        return plugin.arenaWorlds().settings().isInternalInstanceId(rawId);
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
