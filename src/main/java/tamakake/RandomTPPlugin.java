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

        if (cooldown.containsKey(uuid)) {
            long last = cooldown.get(uuid);
            if ((now - last) < cooldownSec * 1000L) {
                player.performCommand("playsound minecraft:entity.villager.no player @s ~ ~ ~ 1 1");
                return true;
            }
        }

        cooldown.put(uuid, now);
        startLocation.put(uuid, player.getLocation().clone());

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

                if (before == null ||
                        !before.getWorld().equals(nowLoc.getWorld()) ||
                        before.distanceSquared(nowLoc) > 0.25) {

                    p.performCommand("playsound minecraft:entity.villager.no player @s ~ ~ ~ 1 1");

                    startLocation.remove(uuid);
                    cooldown.remove(uuid);

                    cancel();
                    return;
                }

                if (sec <= 0) {
                    cancel();
                    executeTeleport(p, uuid);
                    return;
                }

                p.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent("Teleport (" + sec + ")")
                );

                p.performCommand("playsound minecraft:entity.enderman.teleport player @s ~ ~ ~ 1 1");

                sec--;
            }

        }.runTaskTimer(this, 0L, 20L);

        return true;
    }

    // =========================
    // テレポート
    // =========================
    private void executeTeleport(Player player, UUID uuid) {

        Location loc = findSafeLocation(player.getWorld());

        if (loc == null) {
            player.performCommand("playsound minecraft:entity.villager.no player @s ~ ~ ~ 1 1");
            return;
        }

        player.performCommand("playsound minecraft:entity.enderman.teleport player @s ~ ~ ~ 1 1");

        player.teleport(loc);

        player.performCommand("playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1 1");

        startLocation.remove(uuid);
    }

    // =========================
    // 安全な場所探索（軽量版）
    // =========================
    private Location findSafeLocation(World world) {

        int range = getConfig().getInt("rtp.range", 10000);
        World.Environment env = world.getEnvironment();

        for (int i = 0; i < 20; i++) {

            int x = ThreadLocalRandom.current().nextInt(-range, range);
            int z = ThreadLocalRandom.current().nextInt(-range, range);

            int yMin;
            int yMax;

            // =========================
            // ワールド別Y範囲
            // =========================
            switch (env) {

                case NETHER -> {
                    yMin = 32;
                    yMax = 110;
                }

                case THE_END -> {
                    yMin = 60;
                    yMax = 120;
                }

                default -> {
                    yMin = 64;
                    yMax = world.getMaxHeight() - 5;
                }
            }

            for (int y = yMax; y >= yMin; y--) {

                Block block = world.getBlockAt(x, y, z);
                Block above = world.getBlockAt(x, y + 1, z);
                Block below = world.getBlockAt(x, y - 1, z);

                if (isSafeBlock(block, above, below)) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }

        return null;
    }

    // =========================
    // 軽量安全判定
    // =========================
    private boolean isSafeBlock(Block block, Block above, Block below) {

        if (!block.isPassable()) return false;
        if (!above.isPassable()) return false;

        Material b = below.getType();

        return b.isSolid()
                && b != Material.LAVA
                && b != Material.MAGMA_BLOCK
                && b != Material.FIRE
                && b != Material.CACTUS;
    }
}