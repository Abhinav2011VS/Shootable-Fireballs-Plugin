package net.abhinav.sf;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShootableFireballs extends JavaPlugin implements Listener {

    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Register the event listener
        getServer().getPluginManager().registerEvents(this, this);

        // Set up default config values
        this.saveDefaultConfig();
        config = this.getConfig();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check if the player is right-clicking with a fire charge in their main hand
        if (event.getHand() == EquipmentSlot.HAND && event.getPlayer().getInventory().getItemInMainHand().getType() == Material.FIRE_CHARGE) {
            Player player = event.getPlayer();
            ItemStack fireCharge = player.getInventory().getItemInMainHand();

            // Reduce the fire charge count by 1
            fireCharge.setAmount(fireCharge.getAmount() - 1);
            player.getInventory().setItemInMainHand(fireCharge);

            Location eyeLocation = player.getEyeLocation();

            // Spawn a fireball at the player's location and set its direction
            Fireball fireball = (Fireball) player.getWorld().spawnEntity(eyeLocation.add(eyeLocation.getDirection().multiply(2)), EntityType.FIREBALL);
            fireball.setShooter(player);

            // Set explosion power from config
            int explosionPower = config.getInt("explosion-power", 1);  // Default explosion power is 1
            fireball.setYield(explosionPower);

            // Set whether the explosion breaks blocks
            boolean breakBlocks = config.getBoolean("explosion-break-blocks", false);  // Default is not to break blocks
            fireball.setIsIncendiary(breakBlocks);
        }
    }
}
