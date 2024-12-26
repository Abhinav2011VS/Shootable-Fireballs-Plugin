package net.abhinav.sf;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class ShootableFireballs extends JavaPlugin implements Listener {

    private static ShootableFireballs instance;
    private FileConfiguration config;
    private Map<Fireball, BukkitRunnable> fireballTimers;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.config = getConfig();
        fireballTimers = new HashMap<>();

        getServer().getPluginManager().registerEvents(this, this);

        // Registering the command and its tab completer
        getCommand("fireballconfig").setExecutor(this::handleConfigCommand);
        getCommand("fireballconfig").setTabCompleter(new FireballConfigTabCompleter());
    }

    public static ShootableFireballs getInstance() {
        return instance;
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }

    private boolean handleConfigCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can run this command.");
            return false;
        }
        Player player = (Player) sender;

        if (args.length != 2) {
            player.sendMessage("Usage: /fireballconfig <setting> <value>");
            return false;
        }

        String setting = args[0].toLowerCase();
        String value = args[1];

        switch (setting) {
            case "speed":
                try {
                    // Speed can be any decimal number
                    double speed = Double.parseDouble(value);
                    config.set("fireball.speed", speed);
                    player.sendMessage("Fireball speed set to " + speed);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid value for speed. Please provide a valid number.");
                    return false;
                }
                break;
            case "radius":
                try {
                    int radius = Integer.parseInt(value);
                    config.set("fireball.explosion_radius", radius);
                    player.sendMessage("Explosion radius set to " + radius);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid value for radius. Please provide a valid number.");
                    return false;
                }
                break;
            case "damage":
                try {
                    int damage = Integer.parseInt(value);
                    config.set("fireball.damage", damage);
                    player.sendMessage("Fireball damage set to " + damage);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid value for damage. Please provide a valid number.");
                    return false;
                }
                break;
            case "breakblocks":
                boolean canBreakBlocks = Boolean.parseBoolean(value);
                config.set("fireball.can_break_blocks", canBreakBlocks);
                player.sendMessage("Fireballs breaking blocks: " + canBreakBlocks);
                break;
            case "cooldown":
                try {
                    double cooldown = Double.parseDouble(value);
                    config.set("fireball.cooldown", cooldown);
                    player.sendMessage("Fireball cooldown set to " + cooldown + " seconds.");
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid value for cooldown. Please provide a valid number.");
                    return false;
                }
                break;
            case "lifespan":
                try {
                    // Lifespan should only accept integers
                    int lifespan = Integer.parseInt(value);
                    config.set("fireball.lifespan", lifespan);
                    player.sendMessage("Fireball lifespan set to " + lifespan + " seconds.");
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid value for lifespan. Please provide a valid integer.");
                    return false;
                }
                break;
            default:
                player.sendMessage("Unknown setting: " + setting);
                return false;
        }

        saveConfig();
        return true;
    }

    @EventHandler
    public void onPlayerUseFireball(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check if the player right-clicks with a fire charge
        if (itemInHand.getType() == Material.FIRE_CHARGE && event.getAction().toString().contains("RIGHT_CLICK")) {

            // Consume the fire charge
            itemInHand.setAmount(itemInHand.getAmount() - 1);

            // Create and shoot the fireball
            shootFireball(player);
        }
    }

    private void shootFireball(Player player) {
        Fireball fireball = player.getWorld().spawn(player.getLocation().add(0, 1.5, 0), Fireball.class);

        // Set fireball speed based on the config
        Vector direction = player.getLocation().getDirection();
        fireball.setVelocity(direction.multiply(config.getDouble("fireball.speed", 1.0)));

        // Set fireball explosion strength
        fireball.setYield(config.getInt("fireball.explosion_strength", 4));
        fireball.setIsIncendiary(false);  // Prevent fireball from starting fires unless configured

        // Set the fireball's lifespan and start the timer
        int lifespanSeconds = config.getInt("fireball.lifespan", 30);  // Default 30 seconds lifespan
        startLifespanTimer(fireball, lifespanSeconds);
    }

    // Start a timer to despawn the fireball after its lifespan ends
    private void startLifespanTimer(Fireball fireball, int lifespanSeconds) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                // If the fireball still exists and hasn't hit anything, make it explode
                if (!fireball.isDead()) {
                    World world = fireball.getWorld();
                    world.createExplosion(fireball.getLocation(), config.getInt("fireball.explosion_strength", 4), config.getBoolean("fireball.can_break_blocks", true));
                    fireball.remove();  // Remove the fireball from the world
                }
                fireballTimers.remove(fireball);  // Clean up the timer from the map
            }
        };
        task.runTaskLater(this, lifespanSeconds * 20L);  // 20L = 1 tick, so lifespanSeconds * 20 = lifespan in ticks
        fireballTimers.put(fireball, task);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Fireball) {
            Fireball fireball = (Fireball) event.getEntity();

            // Only trigger explosion if the fireball is shot by a player
            if (fireball.getShooter() instanceof Player) {
                Player shooter = (Player) fireball.getShooter();

                // Explosion properties
                int explosionRadius = config.getInt("fireball.explosion_radius", 3);
                boolean canBreakBlocks = config.getBoolean("fireball.can_break_blocks", true);
                int explosionStrength = config.getInt("fireball.explosion_strength", 4);

                // Create the explosion at the fireball's location
                World world = fireball.getWorld();
                world.createExplosion(fireball.getLocation(), explosionStrength, canBreakBlocks);

                // Apply damage to nearby entities if configured
                if (config.getInt("fireball.damage", 10) > 0) {
                    event.getEntity().getNearbyEntities(explosionRadius, explosionRadius, explosionRadius).forEach(entity -> {
                        if (entity instanceof Player) {
                            Player target = (Player) entity;
                            target.damage(config.getInt("fireball.damage", 10));
                        }
                    });
                }
            }

            // Cancel the lifespan timer because the fireball hit something
            if (fireballTimers.containsKey(fireball)) {
                fireballTimers.get(fireball).cancel();
                fireballTimers.remove(fireball);
            }
        }
    }

    // TabCompleter for the /fireballconfig command
    public class FireballConfigTabCompleter implements TabCompleter {

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                // First argument is the setting (e.g., speed, radius, etc.)
                List<String> settings = Arrays.asList("speed", "radius", "damage", "breakblocks", "cooldown", "lifespan");
                return filterSettings(settings, args[0]);
            } else if (args.length == 2) {
                // Second argument is the value (numeric input for speed, lifespan, etc.)
                List<String> possibleValues = new ArrayList<>();
                switch (args[0].toLowerCase()) {
                    case "speed":
                        possibleValues.add("<number-value>");  // Represent any decimal value
                        break;
                    case "lifespan":
                        possibleValues.add("<integer-value>");  // Only allow integers
                        break;
                    case "damage":
                    case "radius":
                        possibleValues.add("<integer-value>");
                        break;
                    case "cooldown":
                        possibleValues.add("<number-value>");
                        break;
                    case "breakblocks":
                        possibleValues.add("<true/false>");
                        break;
                }
                return possibleValues;
            }
            return Collections.emptyList();
        }

        private List<String> filterSettings(List<String> list, String arg) {
            List<String> result = new ArrayList<>();
            for (String setting : list) {
                if (setting.startsWith(arg.toLowerCase())) {
                    result.add(setting);
                }
            }
            return result;
        }
    }
}
