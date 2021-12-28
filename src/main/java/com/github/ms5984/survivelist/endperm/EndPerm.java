/*
 * MIT License
 *
 * Copyright (c) 2021 Matt (ms5984) <https://github.com/ms5984>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.ms5984.survivelist.endperm;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public final class EndPerm extends JavaPlugin implements Listener {
    private final List<Player> playersInEndPortals = new LinkedList<>();
    private String perm;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        // Plugin startup logic
        initialize();

        // Save config to disk
        saveDefaultConfig();

        // Register EventHandlers
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalEvent(PlayerPortalEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;
        final Player player = e.getPlayer();
        if (!playersInEndPortals.contains(player)) {
            if (perm == null || player.hasPermission(perm)) return;
            // Player does not have permission
            playersInEndPortals.add(player);
            // Send message once
            player.sendMessage(ChatColor.RED + "You don't have permission to enter the End!");
        }
        if (playersInEndPortals.contains(player)) {
            // Keep the event cancelled
            e.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.testPermission(sender)) return false;
        if (args.length < 1) return false;
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            if (cleanupTask != null) {
                cleanupTask.cancel();
                cleanupTask = null;
            }
            initialize();
            sender.sendMessage(ChatColor.GRAY + "[EndPerm] " + ChatColor.YELLOW + "Configuration successfully reloaded.");
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length <= 1) return Collections.singletonList("reload");
        return Collections.emptyList();
    }

    private void initialize() {
        // Set perm from config
        this.perm = getConfig().getString("end-perm");
        // Get cooldown from config
        final long cooldown = getConfig().getInt("cooldown", 50);

        // Cleanup players list after players exit portals
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                new LinkedList<>(playersInEndPortals).stream()
                        .filter(p -> {
                            final Block block = p.getLocation().getBlock();
                            // If they are online and are standing in or just under an end portal, keep them in the list
                            return !(p.isOnline() && (block.getType() == Material.END_PORTAL || block.getRelative(BlockFace.UP).getType() == Material.END_PORTAL));
                        })
                        .forEach(playersInEndPortals::remove);
            }
        }.runTaskTimer(this, 0L, cooldown);
    }
}
