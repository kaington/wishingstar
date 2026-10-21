package dev.wishingstar;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class Shrine {

    private static final double STAR_HEIGHT = 1.85;
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 220, 110), 1.0f);

    private final WishingStarPlugin plugin;
    private final Block block;
    private final float scale;

    private ItemDisplay star;
    private Interaction hitbox;
    private BukkitRunnable task;
    private boolean animating;

    Shrine(WishingStarPlugin plugin, Block block, float scale) {
        this.plugin = plugin;
        this.block = block;
        this.scale = scale;
    }

    Block getBlock() { return block; }

    boolean hasStar() { return star != null && !animating; }

    boolean owns(Entity e) { return e != null && (e.equals(hitbox) || e.equals(star)); }

    private Location starHome() {
        return block.getLocation().add(0.5, STAR_HEIGHT, 0.5);
    }

    void spawnStar(boolean withEffect) {
        if (star != null) return;
        World w = block.getWorld();
        Location home = starHome();

        star = w.spawn(home, ItemDisplay.class);
        star.setItemStack(new ItemStack(Material.NETHER_STAR));
        star.setPersistent(false);
        star.setBillboard(Display.Billboard.FIXED);
        star.setBrightness(new Display.Brightness(15, 15));
        star.setViewRange(3f);
        star.setTeleportDuration(1);
        star.setTransformation(transform(0f, 0f, withEffect ? 0.01f : scale));

        float hb = Math.max(0.9f, scale * 0.7f);
        hitbox = w.spawn(home.clone().add(0, -hb / 2.0, 0), Interaction.class);
        hitbox.setPersistent(false);
        hitbox.setInteractionWidth(hb);
        hitbox.setInteractionHeight(hb);
        hitbox.setResponsive(true);

        if (withEffect) {
            w.playSound(home, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.8f);
            w.playSound(home, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.6f);
            w.spawnParticle(Particle.END_ROD, home, 30, 0.1, 0.1, 0.1, 0.12);
        }
        startIdle(withEffect);
    }

    private void startIdle(boolean growIn) {
        if (task != null) task.cancel();
        task = new BukkitRunnable() {
            int t = 0;
            float angle = 0f;

            @Override
            public void run() {
                if (star == null || !star.isValid()) { cancel(); return; }
                t++;
                angle += 0.07f;
                float bob = (float) Math.sin(t * 0.06) * 0.12f;
                float s = scale;
                if (growIn && t < 20) s = scale * easeOutBack(t / 20f);
                star.setInterpolationDelay(0);
                star.setInterpolationDuration(1);
                star.setTransformation(transform(angle, bob, s));

                Location c = starHome().add(0, bob, 0);
                World w = c.getWorld();
                if (t % 4 == 0) w.spawnParticle(Particle.END_ROD, c, 1, 0.35 * scale, 0.35 * scale, 0.35 * scale, 0.01);
                if (t % 6 == 0) w.spawnParticle(Particle.DUST, c, 2, 0.4 * scale, 0.4 * scale, 0.4 * scale, 0, GOLD);
                if (t % 3 == 0) {
                    Location top = block.getLocation().add(0.5, 1.05, 0.5);
                    Vector v = c.toVector().subtract(top.toVector());
                    w.spawnParticle(Particle.ENCHANT, top, 0, v.getX() + rand(0.4), v.getY(), v.getZ() + rand(0.4), 1.0);
                }
                if (t % 80 == 0) w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    void pickUp(Player p) {
        if (star == null || animating) return;
        animating = true;
        if (task != null) task.cancel();
        if (hitbox != null) { hitbox.remove(); hitbox = null; }

        final ItemDisplay s = star;
        final Location start = starHome();
        final World w = start.getWorld();
        w.playSound(start, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
        w.playSound(start, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.5f);

        task = new BukkitRunnable() {
            int t = 0;
            float angle = 0f;
            Location pos = start.clone();

            @Override
            public void run() {
                if (!s.isValid() || !p.isOnline()) { finish(false); return; }
                t++;

                if (t <= 12) {
                    angle += 0.1f + t * 0.06f;
                    float pulse = scale * (1f + 0.15f * (float) Math.sin(t * 0.8));
                    pos = start.clone().add(0, t * 0.03, 0);
                    s.teleport(pos);
                    s.setInterpolationDelay(0);
                    s.setInterpolationDuration(1);
                    s.setTransformation(transform(angle, 0f, pulse));
                    for (int i = 0; i < 3; i++) {
                        double a = t * 0.9 + i * (Math.PI * 2 / 3);
                        double r = 1.1 * scale * (1 - t / 14.0);
                        w.spawnParticle(Particle.END_ROD, pos.clone().add(Math.cos(a) * r, Math.sin(t * 0.5) * 0.3, Math.sin(a) * r), 1, 0, 0, 0, 0);
                    }
                    w.spawnParticle(Particle.ENCHANT, pos, 6, 0.6, 0.6, 0.6, 0.6);
                    return;
                }

                int ft = t - 12;
                int dur = 16;
                float k = Math.min(1f, ft / (float) dur);
                float e = k * k * (3 - 2 * k); // smoothstep
                Location hand = handPoint(p);
                Location from = start.clone().add(0, 0.36, 0);
                Vector path = hand.toVector().subtract(from.toVector());
                double arc = Math.sin(e * Math.PI) * 0.8;
                Location prev = pos;
                pos = from.clone().add(path.clone().multiply(e)).add(0, arc, 0);
                s.teleport(pos);
                angle += 0.9f;
                s.setInterpolationDelay(0);
                s.setInterpolationDuration(1);
                s.setTransformation(transform(angle, 0f, scale + (0.25f - scale) * e));

                Vector step = pos.toVector().subtract(prev.toVector());
                for (int i = 0; i < 4; i++) {
                    Location tp = prev.clone().add(step.clone().multiply(i / 4.0));
                    w.spawnParticle(Particle.END_ROD, tp, 1, 0.03, 0.03, 0.03, 0.005);
                    w.spawnParticle(Particle.DUST, tp, 1, 0.05, 0.05, 0.05, 0, GOLD);
                }
                if (ft % 3 == 0) w.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.2f + k);

                if (ft >= dur) finish(true);
            }

            private void finish(boolean give) {
                cancel();
                s.remove();
                star = null;
                animating = false;
                if (give && p.isOnline()) {
                    Location hand = handPoint(p);
                    plugin.giveStar(p);
                    w.spawnParticle(Particle.FIREWORK, hand, 25, 0.05, 0.05, 0.05, 0.12);
                    w.spawnParticle(Particle.END_ROD, hand, 15, 0.2, 0.2, 0.2, 0.05);
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, hand, 20, 0.2, 0.3, 0.2, 0.3);
                    w.playSound(hand, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f);
                    w.playSound(hand, Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
                    p.sendTitle("", "§e✦ You caught the Wishing Star ✦", 5, 40, 15);
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    void destroy(boolean removeBlock) {
        if (task != null) task.cancel();
        if (star != null) star.remove();
        if (hitbox != null) hitbox.remove();
        star = null;
        hitbox = null;
        if (removeBlock && block.getType() == Material.LODESTONE) block.setType(Material.AIR);
    }

    static Location handPoint(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX());
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        return eye.clone().add(dir.clone().multiply(0.6)).add(right.multiply(0.35)).add(0, -0.4, 0);
    }

    static Transformation transform(float angle, float yOffset, float scale) {
        Quaternionf rot = new Quaternionf().rotateY(angle);
        return new Transformation(new Vector3f(0f, yOffset, 0f), rot, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return (float) (1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
    }

    static double rand(double r) {
        return (Math.random() * 2 - 1) * r;
    }
}
