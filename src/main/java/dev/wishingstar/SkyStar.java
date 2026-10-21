package dev.wishingstar;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

final class SkyStar {

    private static final double HEIGHT = 16;
    private static final int RISE_TICKS = 50;
    private static final int LISTEN_TICKS = 20 * 90; // 90s
    private static final float SKY_SCALE = 3.2f;
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 215, 100), 1.6f);
    private static final Particle.DustOptions PALE = new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.2f);

    private enum State { RISING, LISTENING, LEAVING, DONE }

    private final WishingStarPlugin plugin;
    private final Player player;
    private ItemDisplay star;
    private BukkitRunnable task;
    private volatile State state = State.RISING;

    private Location start;
    private Location top;
    private float angle;

    SkyStar(WishingStarPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    boolean isListening() { return state == State.LISTENING; }

    void launch() {
        start = Shrine.handPoint(player);
        top = player.getLocation().add(0, HEIGHT, 0);
        World w = start.getWorld();

        star = w.spawn(start, ItemDisplay.class);
        star.setItemStack(new ItemStack(Material.NETHER_STAR));
        star.setPersistent(false);
        star.setBillboard(Display.Billboard.FIXED);
        star.setBrightness(new Display.Brightness(15, 15));
        star.setViewRange(8f);
        star.setTeleportDuration(1);
        star.setGlowColorOverride(Color.fromRGB(255, 230, 120));
        star.setTransformation(Shrine.transform(0f, 0f, 0.3f));

        w.playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.7f);
        w.playSound(start, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.3f);
        w.spawnParticle(Particle.FIREWORK, start, 20, 0.1, 0.1, 0.1, 0.08);

        task = new BukkitRunnable() {
            int t = 0;
            Location pos = start.clone();

            @Override
            public void run() {
                if (star == null || !star.isValid() || !player.isOnline()) { destroy(); return; }
                t++;
                switch (state) {
                    case RISING: rise(t); break;
                    case LISTENING: listen(t); break;
                    default: break;
                }
            }

            private void rise(int tt) {
                float k = Math.min(1f, tt / (float) RISE_TICKS);
                float e = 1 - (1 - k) * (1 - k) * (1 - k); // ease out
                Location prev = pos;
                Vector path = top.toVector().subtract(start.toVector());
                double sw = (1 - e) * 0.8;
                pos = start.clone().add(path.clone().multiply(e))
                        .add(Math.cos(tt * 0.35) * sw, 0, Math.sin(tt * 0.35) * sw);
                star.teleport(pos);
                angle += 0.5f * (1 - e) + 0.06f;
                spin(0.3f + (SKY_SCALE - 0.3f) * e, 0f);

                World w = pos.getWorld();
                Vector step = pos.toVector().subtract(prev.toVector());
                for (int i = 0; i < 5; i++) {
                    Location tp = prev.clone().add(step.clone().multiply(i / 5.0));
                    w.spawnParticle(Particle.END_ROD, tp, 1, 0.05, 0.05, 0.05, 0.01);
                    w.spawnParticle(Particle.FIREWORK, tp, 1, 0.08, 0.08, 0.08, 0.01);
                }
                w.spawnParticle(Particle.DUST, pos, 2, 0.15, 0.15, 0.15, 0, GOLD);

                if (tt >= RISE_TICKS) {
                    state = State.LISTENING;
                    t = 0;
                    star.setGlowing(true);
                    w.playSound(pos, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.4f);
                    w.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
                    w.spawnParticle(Particle.FIREWORK, pos, 60, 0.2, 0.2, 0.2, 0.15);
                    w.spawnParticle(Particle.END_ROD, pos, 40, 0.1, 0.1, 0.1, 0.25);
                    player.sendTitle("§e✦", "§7The star is listening... §etype your wish in chat", 10, 60, 20);
                }
            }

            private void listen(int tt) {
                angle += 0.04f;
                float breathe = (float) Math.sin(tt * 0.08) * 0.15f;
                spin(SKY_SCALE + breathe, (float) Math.sin(tt * 0.04) * 0.25f);
                Location c = top;
                World w = c.getWorld();

                for (int i = 0; i < 4; i++) {
                    Vector v = randomUnit().multiply(4 + Math.random() * 4);
                    w.spawnParticle(Particle.ENCHANT, c, 0, v.getX(), v.getY(), v.getZ(), 1.0);
                }

                for (int ring = 0; ring < 3; ring++) {
                    double a = tt * 0.12 + ring * (Math.PI * 2 / 3);
                    double r = 2.4;
                    double tilt = ring * 0.9;
                    double x = Math.cos(a) * r;
                    double y = Math.sin(a) * r * Math.sin(tilt);
                    double z = Math.sin(a) * r * Math.cos(tilt);
                    w.spawnParticle(Particle.END_ROD, c.clone().add(x, y, z), 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.DUST, c.clone().add(-x, -y, -z), 1, 0, 0, 0, 0, ring == 1 ? PALE : GOLD);
                }

                if (tt % 2 == 0) {
                    Location feet = player.getLocation().add(Shrine.rand(0.6), 0.2, Shrine.rand(0.6));
                    w.spawnParticle(Particle.END_ROD, feet, 0, 0, 1, 0, 0.12);
                }
                if (tt % 5 == 0) {
                    Location around = player.getLocation().add(0, 1, 0);
                    w.spawnParticle(Particle.DUST, around, 3, 0.8, 0.8, 0.8, 0, GOLD);
                }

                if (tt % 20 == 0) w.spawnParticle(Particle.FIREWORK, c, 6, 0.3, 0.3, 0.3, 0.03);
                if (tt % 60 == 0) {
                    w.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.5f + (float) Math.random() * 0.5f);
                    w.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.6f);
                }
                if (tt % 400 == 0) player.sendTitle("", "§7The star is still listening...", 5, 40, 15);

                if (tt >= LISTEN_TICKS) fadeAway();
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    void grant(String wish) {
        if (state != State.LISTENING || star == null) return;
        state = State.LEAVING;
        if (task != null) task.cancel();

        final Location c = top.clone();
        final World w = c.getWorld();
        w.spawnParticle(Particle.FIREWORK, c, 150, 0.3, 0.3, 0.3, 0.35);
        w.spawnParticle(Particle.END_ROD, c, 80, 0.2, 0.2, 0.2, 0.4);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, c, 120, 0.5, 0.5, 0.5, 0.6);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 60, 0.4, 0.6, 0.4, 0.4);
        w.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        w.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        w.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 0.8f);
        String shown = wish.length() > 40 ? wish.substring(0, 40) + "..." : wish;
        player.sendTitle("§6§l✦ Wish heard ✦", "§e\"" + shown + "\"", 10, 70, 25);

        final Vector dir = player.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir.setX(1);
        dir.normalize().add(new Vector(0, 0.35, 0)).normalize();

        task = new BukkitRunnable() {
            int t = 0;
            Location pos = c.clone();

            @Override
            public void run() {
                if (star == null || !star.isValid()) { cancel(); return; }
                t++;
                if (t < 15) {
                    angle += 0.35f;
                    spin(SKY_SCALE * (1 + 0.25f * (float) Math.sin(t * 0.7)), 0f);
                    return;
                }
                Location prev = pos;
                double speed = Math.min(2.5, 0.2 + (t - 15) * 0.12);
                pos = pos.clone().add(dir.clone().multiply(speed));
                star.teleport(pos);
                angle += 0.5f;
                spin(Math.max(0.2f, SKY_SCALE - (t - 15) * 0.09f), 0f);
                Vector step = pos.toVector().subtract(prev.toVector());
                for (int i = 0; i < 8; i++) {
                    Location tp = prev.clone().add(step.clone().multiply(i / 8.0));
                    w.spawnParticle(Particle.END_ROD, tp, 1, 0.08, 0.08, 0.08, 0.01);
                    w.spawnParticle(Particle.FIREWORK, tp, 1, 0.1, 0.1, 0.1, 0.02);
                    w.spawnParticle(Particle.DUST, tp, 1, 0.1, 0.1, 0.1, 0, GOLD);
                }
                if (t >= 50) destroy();
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    private void fadeAway() {
        state = State.LEAVING;
        if (task != null) task.cancel();
        player.sendTitle("", "§7The star drifted away...", 5, 40, 15);
        task = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (star == null || !star.isValid()) { cancel(); return; }
                t++;
                Location pos = top.clone().add(0, t * 0.15, 0);
                star.teleport(pos);
                angle += 0.05f;
                spin(Math.max(0.05f, SKY_SCALE * (1 - t / 40f)), 0f);
                pos.getWorld().spawnParticle(Particle.END_ROD, pos, 1, 0.3, 0.3, 0.3, 0.01);
                if (t >= 40) destroy();
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    void destroy() {
        state = State.DONE;
        if (task != null) task.cancel();
        if (star != null) star.remove();
        star = null;
        plugin.forgetSkyStar(player.getUniqueId(), this);
    }

    private void spin(float scale, float yOffset) {
        star.setInterpolationDelay(0);
        star.setInterpolationDuration(1);
        star.setTransformation(Shrine.transform(angle, yOffset, scale));
    }

    private static Vector randomUnit() {
        double u = Math.random() * 2 - 1;
        double th = Math.random() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return new Vector(s * Math.cos(th), u, s * Math.sin(th));
    }
}
