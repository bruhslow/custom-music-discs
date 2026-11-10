package me.giridharan.firstplugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FirstPlugin extends JavaPlugin {

    private MusicManager musicManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize music manager
        musicManager = new MusicManager(this);

        // Register event listener
        getServer().getPluginManager().registerEvents(new MusicListener(musicManager), this);

        // Register command and tab completer
        PluginCommand cmd = getCommand("music");
        if (cmd != null) {
            MusicCommand musicCommand = new MusicCommand(this, musicManager);
            cmd.setExecutor(musicCommand);
            cmd.setTabCompleter(musicCommand);
        }

        getLogger().info("Custom Music Discs plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (musicManager != null) {
            musicManager.stopAll();
        }
        getLogger().info("Custom Music Discs plugin disabled!");
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }
}
