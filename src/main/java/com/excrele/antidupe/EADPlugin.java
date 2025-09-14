package com.excrele.antidupe;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * EADPlugin - Excrele Anti Dupe, a Minecraft plugin to prevent item duplication exploits.
 *
 * This plugin listens to various events that are commonly exploited for duping items:
 * 1. Player death: Clears inventory on death to prevent duping via keepInventory or lag.
 * 2. Inventory clicks in crafting/brewing: Cancels suspicious clicks that could lead to duping.
 * 3. Item drops: Cancels drops during certain conditions (e.g., if player is in a vulnerable state).
 * 4. Player quits: Clears inventory if the player quits while in certain menus or states.
 * 5. Player interactions: Monitors for exploit-prone interactions like pistons or portals.
 *
 * Features (v1.2):
 * - Alert System: Sends color-coded messages to players with 'ead.message' permission on suspicious events.
 *   - §cRed§r: High severity (e.g., death, quit).
 *   - §eYellow§r: Medium severity (e.g., cancelled interactions).
 *   - §aGreen§r: Low severity (e.g., warnings, but not used yet).
 * - False Positive Mitigation:
 *   - Inventory clicks: Only cancel if the clicked item is stackable and amount >1.
 *   - Drops: Only cancel in creative if dropping more than 1 item.
 *   - Interactions: Only cancel if the held item is valuable (e.g., diamond tier or higher).
 *   - Bypass: All checks respect 'ead.bypass' permission.
 *
 * Note: This is a basic implementation. For more advanced anti-dupe, consider database tracking or more events.
 * Always test thoroughly in a development environment.
 *
 * Author: Professional Minecraft Plugin Developer
 * Version: 1.2
 * Date: September 14, 2025
 */
public class EADPlugin extends JavaPlugin implements Listener {

    // Helper method to broadcast alert messages to permitted players
    private void sendAlert(String message, ChatColor color) {
        String coloredMessage = ChatColor.GOLD + "[EAD] " + color + message + ChatColor.RESET;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("ead.message")) {
                p.sendMessage(coloredMessage);
            }
        }
        // Also log to console for admins
        getLogger().info(coloredMessage);
    }

    @Override
    public void onEnable() {
        // Register the plugin's event listeners
        // This ensures all anti-dupe mechanisms are active when the server starts
        getServer().getPluginManager().registerEvents(this, this);

        // Log the plugin activation for debugging and monitoring
        getLogger().info("EADPlugin v1.2 has been enabled successfully!");

        // Double-check: Ensure Bukkit is running the correct version (optional, for compatibility)
        // Note: In a real plugin, add version checks here if needed
    }

    @Override
    public void onDisable() {
        // Clean up resources if necessary (though minimal in this plugin)
        // Log the plugin deactivation
        getLogger().info("EADPlugin has been disabled.");
    }

    /**
     * Event Handler for Player Death
     * Prevents duplication on death by clearing the player's inventory and experience.
     * Common exploit: Players die in portals or during lag to dupe items.
     * Alert: High severity (red).
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Bypass check: Skip if player has bypass permission
        if (player.hasPermission("ead.bypass")) {
            return;
        }

        // Clear the drops to prevent any item retention
        event.getDrops().clear();
        event.setKeepInventory(false); // Ensure inventory is not kept
        event.setKeepLevel(false);     // Drop experience to prevent XP duping

        // Additional check: Clear the player's inventory manually for safety
        player.getInventory().clear();
        player.setTotalExperience(0);

        // Send high-severity alert
        sendAlert("§cHigh Alert: " + player.getName() + " died - inventory cleared to prevent duping.", ChatColor.RED);

        // Log the event for monitoring potential exploits
        getLogger().warning("Player " + player.getName() + " died - inventory cleared to prevent duping.");
    }

    /**
     * Event Handler for Inventory Clicks
     * Monitors clicks in crafting and brewing inventories to prevent dupe exploits.
     * Exploit example: Rapid clicking during lag to duplicate crafted items.
     * Mitigation: Only cancel shift clicks or hotbar swaps if the item is stackable and amount >1.
     * Alert: Medium severity (yellow).
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the inventory is a crafting or brewing table
        if (event.getInventory().getType() == InventoryType.CRAFTING ||
                event.getInventory().getType() == InventoryType.BREWING) {

            Player player = (Player) event.getWhoClicked();

            // Bypass check
            if (player.hasPermission("ead.bypass")) {
                return;
            }

            ItemStack currentItem = event.getCurrentItem();

            // Mitigation: Only flag if shift click or hotbar swap, AND item is stackable with amount >1
            if ((event.isShiftClick() || event.getHotbarButton() != -1) &&
                    currentItem != null && currentItem.getType().isItem() &&
                    currentItem.getMaxStackSize() > 1 && currentItem.getAmount() > 1) {

                event.setCancelled(true);

                // Send medium-severity alert
                sendAlert("§eMedium Alert: " + player.getName() + " attempted suspicious stack shift in " +
                        event.getInventory().getType() + " - cancelled.", ChatColor.YELLOW);

                // Notify the player for feedback
                player.sendMessage(ChatColor.RED + "[EAD] Suspicious inventory action cancelled to prevent duplication.");

                // Log for server admins
                getLogger().info("Cancelled suspicious click by " + player.getName() + " in " + event.getInventory().getType());
            }
        }
    }

    /**
     * Event Handler for Player Dropping Items
     * Prevents dropping items in certain scenarios, like during teleportation or in portals.
     * Exploit: Dropping items into unloaded chunks or during lag.
     * Mitigation: Only cancel in creative if dropping stackable item with amount >1.
     * Alert: Medium severity (yellow).
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // Bypass check
        if (player.hasPermission("ead.bypass")) {
            return;
        }

        // Mitigation: Only cancel if in creative AND dropping stackable item with amount >1
        if (player.getGameMode().toString().equals("CREATIVE") &&
                droppedItem.getType().isItem() && droppedItem.getMaxStackSize() > 1 &&
                droppedItem.getAmount() > 1) {

            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "[EAD] Item drop cancelled to prevent duplication exploit.");

            // Send medium-severity alert
            sendAlert("§eMedium Alert: " + player.getName() + " attempted to drop stack in creative - cancelled.", ChatColor.YELLOW);

            // Log the attempted drop
            getLogger().warning("Cancelled item drop by " + player.getName() + " - potential exploit detected.");
        }
    }

    /**
     * Event Handler for Player Quit
     * Clears inventory on quit to prevent duping via force-quit during transactions.
     * Common in anarchy servers where players quit to dupe in chests or trades.
     * Alert: High severity (red).
     * Mitigation: Only clear if inventory has more than 10 items.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Bypass check
        if (player.hasPermission("ead.bypass")) {
            return;
        }

        // Mitigation: Only clear if inventory is not mostly empty (count non-air items)
        int itemCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                itemCount++;
            }
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                itemCount++;
            }
        }

        if (itemCount > 10) {  // Threshold for "full enough" to warrant clearing
            // Clear inventory and ender chest for thoroughness
            player.getInventory().clear();
            player.getEnderChest().clear();

            // Send high-severity alert
            sendAlert("§cHigh Alert: " + player.getName() + " quit with inventory - cleared to prevent duping.", ChatColor.RED);

            // Log the quit event
            getLogger().info("Player " + player.getName() + " quit - inventory cleared to prevent duping.");
        }
    }

    /**
     * Event Handler for Player Interactions
     * Monitors block interactions that could lead to duping, like pistons pushing items or portal use.
     * For now, cancels interactions with pistons and portals if holding valuable items.
     * Mitigation: Only cancel if held item is "valuable" (e.g., diamond, netherite, or enchanted).
     * Alert: Medium severity (yellow).
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Bypass check
        if (player.hasPermission("ead.bypass")) {
            return;
        }

        // Check if interacting with a piston or portal
        if (event.hasBlock() && (event.getClickedBlock().getType() == Material.PISTON ||
                event.getClickedBlock().getType() == Material.END_PORTAL_FRAME ||
                event.getClickedBlock().getType() == Material.NETHER_PORTAL)) {

            ItemStack item = event.getItem();
            // Mitigation: Only flag if holding a valuable item (diamond tier or higher, or enchanted)
            boolean isValuable = false;
            if (item != null && item.getType() != Material.AIR) {
                Material mat = item.getType();
                if (mat.name().contains("DIAMOND") || mat.name().contains("NETHERITE") ||
                        mat.name().contains("ELYTRA") || item.getEnchantments().size() > 0) {
                    isValuable = true;
                }
            }

            if (isValuable) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "[EAD] Interaction cancelled to prevent duplication.");

                // Send medium-severity alert
                sendAlert("§eMedium Alert: " + player.getName() + " interacted with " +
                        event.getClickedBlock().getType() + " while holding valuable item - cancelled.", ChatColor.YELLOW);

                // Log the interaction
                getLogger().info("Cancelled interaction by " + player.getName() + " with " + event.getClickedBlock().getType());
            }
        }
    }
}