package tamakake;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RandomTPPlugin extends JavaPlugin {

    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, Location> startLocation = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("randomtp.use")) return true;

        UUID uuid = player.getUniqueId();

        int delay = getConfig().getInt("rtp.delay", 5);
        int cooldownSec = getConfig().getInt("rtp.cooldown", 3);

        long now = System.currentTimeMillis();

        // =========================
        // クールダウン
        // =========================
        if (cooldown.containsKey(uuid)) {

            long last = cooldown.get(uuid);

            if ((now - last) < cooldownSec * 1000L) {

                player.performCommand(
                        "playsound minecraft:entity.villager.no player @s ~ ~ ~ 1 1"
                );

                return true;
            }
        }

        cooldown.put(uuid, now);

        // 開始位置保存
        startLocation.put(uuid, player.getLocation().clone());

        // =========================
        // カウントダウン
        // =========================
        new BukkitRunnable() {

            int sec = delay;

            @Override
            public void run() {

                Player p = Bukkit.getPlayer(uuid);

                if (p == null || !p.isOnline()) {
                    cancel();
                    return;
                }

                Location before = startLocation.get(uuid);
                Location nowLoc = p.getLocation();

                // =========================
                // 動いたらキャンセル
                // =========================
                if (before == null
                        || !before.getWorld().equals(nowLoc.getWorld())
                        || before.distanceSquared(nowLoc) > 0.25) {

                    p.performCommand(
                            "playsound minecraft:entity.villager.no player @s ~ ~ ~ 1 1"
                    );

                    startLocation.remove(uuid);
                    cooldown.remove(uuid);

                    cancel();
                    return;
                }

                // =========================
                // テレポート
                // =========================
                if (sec <= 0) {

                    cancel();
                    executeTeleport(p, uuid);
                    return;
                }

                // =========================
                // ActionBar
                // =========================
                p.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent("Teleport (" + sec + ")")
                );

                // =========================
                // 毎秒音
                // =========================
                p.performCommand(
                        "playsound minecraft:entity.enderman.teleport player @s ~ ~ ~ 1 1"
                );

                sec--;
            }

        }.runTaskTimer(this, 0L, 20L);

        return true;
    }

    // =========================
    // テレポート処理
    // =========================
    private void executeTeleport(Player player, UUID uuid) {

        Location loc = findSafeLocation(player.getWorld());

        if (loc == null) {

            player.performCommand(
                    "playsound minecraft:entity.villager.no player @s ~ ~ ~ 1 1"
            );

            cooldown.remove(uuid);
            return;
        }

        // テレポート音
        player.performCommand(
                "playsound minecraft:entity.enderman.teleport player @s ~ ~ ~ 1 1"
        );

        player.teleport(loc);

        // 成功音
        player.performCommand(
                "playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1 1"
        );

        startLocation.remove(uuid);
    }

    // =========================
    // 安全地点探索
    // =========================
    private Location findSafeLocation(World world) {

        int range = getConfig().getInt("rtp.range", 10000);

        World.Environment env = world.getEnvironment();

        for (int tries = 0; tries < 20; tries++) {

            int x = ThreadLocalRandom.current().nextInt(-range, range);
            int z = ThreadLocalRandom.current().nextInt(-range, range);

            int yMin;
            int yMax;

            // =========================
            // ワールド別
            // =========================
            switch (env) {

                case NETHER -> {
                    yMin = 32;
                    yMax = 110;
                }

                case THE_END -> {
                    yMin = 50;
                    yMax = 100;
                }

                default -> {
                    yMin = 60;
                    yMax = world.getMaxHeight() - 5;
                }
            }

            // =========================
            // 上から探す
            // =========================
            for (int y = yMax; y >= yMin; y--) {

                Block block = world.getBlockAt(x, y, z);
                Block above = world.getBlockAt(x, y + 1, z);
                Block below = world.getBlockAt(x, y - 1, z);

                if (isSafeBlock(block, above, below)) {

                    return new Location(
                            world,
                            x + 0.5,
                            y,
                            z + 0.5
                    );
                }
            }
        }

        return null;
    }

    // =========================
    // 超安全判定
    // =========================
    private boolean isSafeBlock(Block block, Block above, Block below) {

        // 足元空間
        if (!block.isPassable()) return false;

        // 頭
        if (!above.isPassable()) return false;

        Material ground = below.getType();

        // 固体必須
        if (!ground.isSolid()) return false;

        // 危険ブロック
        if (ground == Material.LAVA
                || ground == Material.MAGMA_BLOCK
                || ground == Material.FIRE
                || ground == Material.SOUL_FIRE
                || ground == Material.CACTUS) {

            return false;
        }

        // =========================
        // 周囲の溶岩チェック
        // =========================
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {

                    Material nearby = below.getLocation()
                            .clone()
                            .add(x, y, z)
                            .getBlock()
                            .getType();

                    if (nearby == Material.LAVA) {
                        return false;
                    }
                }
            }
        }

        // =========================
        // 下が大穴じゃないか
        // =========================
        int solidCount = 0;

        for (int i = 1; i <= 5; i++) {

            Material down = below.getLocation()
                    .clone()
                    .subtract(0, i, 0)
                    .getBlock()
                    .getType();

            if (down.isSolid()) {
                solidCount++;
            }
        }

        return solidCount >= 2;
    }
}