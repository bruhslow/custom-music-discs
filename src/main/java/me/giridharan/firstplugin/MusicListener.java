package me.giridharan.firstplugin;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MusicListener implements Listener {

    private final MusicManager musicManager;

    public MusicListener(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 1. Jukebox Interaction
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.JUKEBOX) {

                // Player is inserting a custom disc
                if (item != null && musicManager.isCustomDisc(item)) {
                    event.setCancelled(true);
                    musicManager.playJukebox(player, block, item);
                    return;
                }

                // Player is clicking an already playing custom Jukebox to stop it
                if (musicManager.isJukeboxPlaying(block.getLocation())) {
                    event.setCancelled(true);
                    musicManager.stopJukebox(block, true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.JUKEBOX) {
            if (musicManager.isJukeboxPlaying(block.getLocation())) {
                musicManager.stopJukebox(block, true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        musicManager.stopPortable(event.getPlayer());
    }
}
