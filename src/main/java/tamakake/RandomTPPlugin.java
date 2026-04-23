package tamakake;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
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

        // =====================
        // クールダウン
        // =====================
        if (cooldown.containsKey(uuid)) {
            long last = cooldown.get(uuid);
            if ((now - last) < cooldownSec * 1000L) {
                player.performCommand("playsound minecraft:entity.villager.no player @s ~ ~ ~ 1 1");
                return true;
            }
        }

        cooldown.put(uuid, now);

        // 初期位置保存
        startLocation.put(uuid, player.getLocation().clone());

        // =====================
        // カウントダウン
        // =====================
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

                // 移動キャンセル
                if (before == null ||
                        before.getWorld() != nowLoc.getWorld() ||
                        before.distance(nowLoc) > 0.5) {

                    p.performCommand("playsound minecraft:entity.villager.no player @s ~ ~ ~ 1 1");

                    startLocation.remove(uuid);
                    cooldown.remove(uuid);

                    cancel();
                    return;
                }

                // =====================
                // 0秒で実行
                // =====================
                if (sec <= 0) {
                    cancel();
                    executeTeleport(p, uuid);
                    return;
                }

                // =====================
                // ActionBar
                // =====================
                p.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent("Teleport (" + sec + ")")
                );

                // =====================
                // 毎秒音（/playsound完全一致）
                // =====================
                p.performCommand("playsound minecraft:entity.enderman.teleport player @s ~ ~ ~ 1 1");

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
            player.performCommand("playsound minecraft:entity.villager.no player @s ~ ~ ~ 1 1");
            return;
        }

        // ワープ音
        player.performCommand("playsound minecraft:entity.enderman.teleport player @s ~ ~ ~ 1 1");

        player.teleport(loc);

        // 成功音
        player.performCommand("playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1 1");

        startLocation.remove(uuid);
    }

    // =========================
    // 安全な場所探索
    // =========================
    private Location findSafeLocation(World world) {

        int range = getConfig().getInt("rtp.range", 10000);

        for (int i = 0; i < 50; i++) {

            int x = ThreadLocalRandom.current().nextInt(-range, range);
            int z = ThreadLocalRandom.current().nextInt(-range, range);

            int y = world.getHighestBlockYAt(x, z);

            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);

            if (isSafe(loc)) {
                return loc;
            }
        }

        return null;
    }

    // =========================
    // 安全判定
    // =========================
    private boolean isSafe(Location loc) {

        Material block = loc.getBlock().getType();
        Material above = loc.clone().add(0, 1, 0).getBlock().getType();
        Material below = loc.clone().add(0, -1, 0).getBlock().getType();

        if (!block.isAir() || !above.isAir()) return false;
        if (below.isAir()) return false;

        if (below == Material.LAVA ||
                below == Material.FIRE ||
                below == Material.CACTUS ||
                below == Material.MAGMA_BLOCK ||
                below == Material.WATER) return false;

        return true;
    }
}