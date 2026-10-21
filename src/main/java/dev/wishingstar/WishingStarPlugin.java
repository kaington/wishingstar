package dev.wishingstar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WishingStarPlugin extends JavaPlugin implements Listener, CommandExecutor {

    public static final float DEFAULT_SCALE = 1.8f;

    private NamespacedKey starKey;
    private final Map<String, Shrine> shrines = new HashMap<>();
    private final Map<UUID, SkyStar> skyStars = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        starKey = new NamespacedKey(this, "wishing_star");
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("wishingstar").setExecutor(this);
    }

    @Override
    public void onDisable() {
        for (Shrine s : shrines.values()) s.destroy(false);
        shrines.clear();
        for (SkyStar s : skyStars.values()) s.destroy();
        skyStars.clear();
    }

    public ItemStack createStarItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§l✦ Wishing Star ✦");
        List<String> lore = new ArrayList<>();
        lore.add("§7It hums softly in your hand...");
        lore.add("§bRight-click §7to send it to the sky");
        lore.add("§7and make a wish.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(starKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isStarItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(starKey, PersistentDataType.BYTE);
    }

    public void giveStar(Player p) {
        ItemStack star = createStarItem();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            p.getInventory().setItemInMainHand(star);
        } else {
            for (ItemStack left : p.getInventory().addItem(star).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }

    static String key(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    Shrine shrineAt(Block b) {
        return shrines.get(key(b));
    }

    Shrine shrineForEntity(Entity e) {
        for (Shrine s : shrines.values()) if (s.owns(e)) return s;
        return null;
    }

    void forgetSkyStar(UUID id, SkyStar star) {
        skyStars.remove(id, star);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            giveStar(p);
            p.sendMessage("§e✦ §7You got a Wishing Star.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("remove")) {
            Shrine nearest = null;
            double best = 36;
            for (Shrine s : shrines.values()) {
                Location l = s.getBlock().getLocation();
                if (l.getWorld() != p.getWorld()) continue;
                double d = l.distanceSquared(p.getLocation());
                if (d < best) { best = d; nearest = s; }
            }
            if (nearest == null) {
                p.sendMessage("§cNo wishing star within 6 blocks.");
            } else {
                nearest.destroy(true);
                shrines.remove(key(nearest.getBlock()));
                p.sendMessage("§e✦ §7Wishing star removed.");
            }
            return true;
        }

        float scale = DEFAULT_SCALE;
        if (args.length > 0) {
            try {
                scale = Math.max(0.3f, Math.min(6f, Float.parseFloat(args[0])));
            } catch (NumberFormatException ex) {
                p.sendMessage("§cUsage: /wishingstar [size] | remove | give");
                return true;
            }
        }

        Block target = p.getTargetBlockExact(6);
        Block lodestone;
        if (target != null && target.getType() == Material.LODESTONE) {
            lodestone = target;
        } else if (target != null) {
            lodestone = target.getRelative(0, 1, 0);
        } else {
            lodestone = p.getLocation().getBlock();
        }
        if (shrines.containsKey(key(lodestone))) {
            p.sendMessage("§cThere is already a wishing star there.");
            return true;
        }
        lodestone.setType(Material.LODESTONE);
        Shrine shrine = new Shrine(this, lodestone, scale);
        shrines.put(key(lodestone), shrine);
        shrine.spawnStar(true);
        p.sendMessage("§e✦ §7A wishing star appears. §8(right-click it)");
        return true;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Shrine s = shrineForEntity(e.getRightClicked());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        s.pickUp(e.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();

        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Shrine s = shrineAt(e.getClickedBlock());
            if (s != null) {
                e.setCancelled(true); // stops compass linking
                if (s.hasStar()) {
                    s.pickUp(p);
                } else if (isStarItem(e.getItem())) {
                    takeOneFromHand(p);
                    s.spawnStar(true);
                }
                return;
            }
        }

        if (!isStarItem(e.getItem())) return;
        e.setCancelled(true);
        if (skyStars.containsKey(p.getUniqueId())) {
            p.sendMessage("§e✦ §7Your star is already listening... type your wish in chat.");
            return;
        }
        takeOneFromHand(p);
        SkyStar star = new SkyStar(this, p);
        skyStars.put(p.getUniqueId(), star);
        star.launch();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        SkyStar star = skyStars.get(e.getPlayer().getUniqueId());
        if (star == null || !star.isListening()) return;
        final String wish = e.getMessage();
        Bukkit.getScheduler().runTask(this, () -> star.grant(wish));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        SkyStar star = skyStars.remove(e.getPlayer().getUniqueId());
        if (star != null) star.destroy();
    }

    private void takeOneFromHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand);
        } else {
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
    }
}
