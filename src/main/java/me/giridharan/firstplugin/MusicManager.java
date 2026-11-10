package me.giridharan.firstplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MusicManager {

    private final FirstPlugin plugin;
    private final File musicFolder;
    private final File packFolder;
    private final File bedrockPackFolder;

    private final NamespacedKey discKey;
    private final NamespacedKey songKey;
    private final NamespacedKey soundEventKey;

    private final Map<String, File> availableSongs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<Location, ActiveTrack> activeJukeboxes = new HashMap<>();
    private final Map<UUID, String> activePortable = new HashMap<>();

    public record ActiveTrack(String songId, String soundKey, ItemStack discItem, BukkitTask task) {}

    public MusicManager(FirstPlugin plugin) {
        this.plugin = plugin;
        this.discKey = new NamespacedKey(plugin, "custom_disc");
        this.songKey = new NamespacedKey(plugin, "song_id");
        this.soundEventKey = new NamespacedKey(plugin, "sound_key");

        this.musicFolder = new File(plugin.getDataFolder(), "music");
        this.packFolder = new File(plugin.getDataFolder(), "resourcepack");
        this.bedrockPackFolder = new File(plugin.getDataFolder(), "bedrock_pack");

        setupDirectories();
        scanSongs();
    }

    private void setupDirectories() {
        if (!musicFolder.exists()) {
            musicFolder.mkdirs();
            createSampleReadme();
        }
        if (!packFolder.exists()) {
            packFolder.mkdirs();
        }
        if (!bedrockPackFolder.exists()) {
            bedrockPackFolder.mkdirs();
        }
    }

    private void createSampleReadme() {
        File readme = new File(musicFolder, "README_SONGS_HERE.txt");
        if (!readme.exists()) {
            try (FileWriter writer = new FileWriter(readme, StandardCharsets.UTF_8)) {
                writer.write("====================================================\n");
                writer.write("HOW TO ADD CUSTOM SONGS:\n");
                writer.write("====================================================\n\n");
                writer.write("1. Place your .ogg files directly into this 'music' folder.\n");
                writer.write("   Example: 1.ogg, 3.ogg, 5.ogg\n\n");
                writer.write("IMPORTANT NOTE ON MP3:\n");
                writer.write("Minecraft's audio engine ONLY plays .ogg (Vorbis) audio.\n");
                writer.write("If you have an .mp3, convert it to .ogg first (using Audacity or convertio.co).\n\n");
                writer.write("2. In-game, run /music reload\n");
                writer.write("3. Take 'plugins/CustomMusicDiscs/CustomMusicPack.zip' and put it in your Minecraft resourcepacks folder!\n");
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create README: " + e.getMessage());
            }
        }
    }

    public synchronized void scanSongs() {
        availableSongs.clear();

        File[] files = musicFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile()) continue;
                String name = file.getName().toLowerCase();
                if (name.endsWith(".ogg") || name.endsWith(".mp3") || name.endsWith(".wav")) {
                    String baseName = file.getName();
                    int lastDot = baseName.lastIndexOf('.');
                    if (lastDot > 0) {
                        baseName = baseName.substring(0, lastDot);
                    }
                    String cleanName = baseName.trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
                    availableSongs.put(cleanName, file);
                    if (!cleanName.equals(baseName)) {
                        availableSongs.put(baseName, file);
                    }

                    if (name.endsWith(".mp3")) {
                        plugin.getLogger().warning("[CustomMusic] '" + file.getName() + "' is an MP3 file! Minecraft CANNOT decode MP3s. Please convert '" + file.getName() + "' to .ogg format!");
                    }
                }
            }
        }

        plugin.getLogger().info("Scanned " + availableSongs.size() + " songs in music folder: " + availableSongs.keySet());
        generateResourcePackFiles();
    }

    private void generateResourcePackFiles() {
        String namespace = plugin.getConfig().getString("sound-namespace", "custom_music");

        // 1. Generate pack.mcmeta with broad Minecraft version support (1.20 - 1.21+)
        File mcmeta = new File(packFolder, "pack.mcmeta");
        try (FileWriter writer = new FileWriter(mcmeta, StandardCharsets.UTF_8)) {
            writer.write("{\n");
            writer.write("  \"pack\": {\n");
            writer.write("    \"pack_format\": 34,\n");
            writer.write("    \"supported_formats\": [15, 48],\n");
            writer.write("    \"description\": \"Custom Music Discs Server Resource Pack\"\n");
            writer.write("  }\n");
            writer.write("}\n");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write pack.mcmeta: " + e.getMessage());
        }

        // 2. Prepare assets/<namespace>/sounds/ directory & copy .ogg files
        File soundsDir = new File(packFolder, "assets/" + namespace + "/sounds");
        soundsDir.mkdirs();

        Set<String> uniqueCleanIds = new HashSet<>();

        for (Map.Entry<String, File> entry : availableSongs.entrySet()) {
            File source = entry.getValue();
            String cleanId = entry.getKey().trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
            uniqueCleanIds.add(cleanId);

            if (source.getName().toLowerCase().endsWith(".ogg")) {
                File dest = new File(soundsDir, cleanId + ".ogg");
                try {
                    Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to copy sound file: " + e.getMessage());
                }
            }
        }

        // 3. Generate sounds.json inside assets/<namespace>/
        File soundDir = new File(packFolder, "assets/" + namespace);
        File soundsJson = new File(soundDir, "sounds.json");
        try (FileWriter writer = new FileWriter(soundsJson, StandardCharsets.UTF_8)) {
            writer.write("{\n");
            int count = 0;
            int total = uniqueCleanIds.size();

            for (String songId : uniqueCleanIds) {
                count++;
                writer.write("  \"" + songId + "\": {\n");
                writer.write("    \"category\": \"record\",\n");
                writer.write("    \"sounds\": [\n");
                writer.write("      {\n");
                writer.write("        \"name\": \"" + namespace + ":" + songId + "\",\n");
                writer.write("        \"stream\": true,\n");
                writer.write("        \"attenuation_distance\": 24\n");
                writer.write("      }\n");
                writer.write("    ]\n");
                writer.write("  }" + (count < total ? "," : "") + "\n");
            }

            writer.write("}\n");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write sounds.json: " + e.getMessage());
        }

        // 4. Also generate fallback assets/minecraft/sounds.json
        File mcAssetsDir = new File(packFolder, "assets/minecraft");
        mcAssetsDir.mkdirs();
        File mcSoundsJson = new File(mcAssetsDir, "sounds.json");
        try (FileWriter writer = new FileWriter(mcSoundsJson, StandardCharsets.UTF_8)) {
            writer.write("{\n");
            int count = 0;
            int total = uniqueCleanIds.size();

            for (String songId : uniqueCleanIds) {
                count++;
                writer.write("  \"" + namespace + "." + songId + "\": {\n");
                writer.write("    \"category\": \"record\",\n");
                writer.write("    \"sounds\": [\n");
                writer.write("      {\n");
                writer.write("        \"name\": \"" + namespace + ":" + songId + "\",\n");
                writer.write("        \"stream\": true,\n");
                writer.write("        \"attenuation_distance\": 24\n");
                writer.write("      }\n");
                writer.write("    ]\n");
                writer.write("  }" + (count < total ? "," : "") + "\n");
            }

            writer.write("}\n");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write minecraft sounds.json: " + e.getMessage());
        }

        // 5. Automatically build CustomMusicPack.zip
        buildZipPack();

        // 6. Build Bedrock Resource Pack (.mcpack) for Geyser players
        if (plugin.getConfig().getBoolean("enable-bedrock-pack", true)) {
            generateBedrockPackFiles(uniqueCleanIds, namespace);
        }
    }

    public void buildZipPack() {
        File zipFile = new File(plugin.getDataFolder(), "CustomMusicPack.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipDirectory(packFolder, packFolder, zos);
            plugin.getLogger().info("Generated 'CustomMusicPack.zip' in " + plugin.getDataFolder().getName());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create CustomMusicPack.zip: " + e.getMessage());
        }
    }

    private void generateBedrockPackFiles(Set<String> uniqueCleanIds, String namespace) {
        if (!bedrockPackFolder.exists()) {
            bedrockPackFolder.mkdirs();
        }

        // 1. Generate Bedrock manifest.json with deterministic UUIDs and incrementing version
        File manifest = new File(bedrockPackFolder, "manifest.json");
        UUID headerUuid = UUID.nameUUIDFromBytes(("custom-music-bedrock-header-" + namespace).getBytes(StandardCharsets.UTF_8));
        UUID moduleUuid = UUID.nameUUIDFromBytes(("custom-music-bedrock-module-" + namespace).getBytes(StandardCharsets.UTF_8));
        int packVersion = getNextPackVersion();

        try (FileWriter writer = new FileWriter(manifest, StandardCharsets.UTF_8)) {
            writer.write("{\n");
            writer.write("  \"format_version\": 2,\n");
            writer.write("  \"header\": {\n");
            writer.write("    \"description\": \"Custom Music Discs Bedrock Resource Pack\",\n");
            writer.write("    \"name\": \"Custom Music Discs (Bedrock)\",\n");
            writer.write("    \"uuid\": \"" + headerUuid + "\",\n");
            writer.write("    \"version\": [1, 0, " + packVersion + "],\n");
            writer.write("    \"min_engine_version\": [1, 20, 0]\n");
            writer.write("  },\n");
            writer.write("  \"modules\": [\n");
            writer.write("    {\n");
            writer.write("      \"description\": \"Custom Music Discs Audio\",\n");
            writer.write("      \"type\": \"resources\",\n");
            writer.write("      \"uuid\": \"" + moduleUuid + "\",\n");
            writer.write("      \"version\": [1, 0, " + packVersion + "]\n");
            writer.write("    }\n");
            writer.write("  ]\n");
            writer.write("}\n");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write Bedrock manifest.json: " + e.getMessage());
        }

        // 2. Prepare sounds/<namespace>/ directory in bedrock pack & copy .ogg files
        File soundsDir = new File(bedrockPackFolder, "sounds/" + namespace);
        soundsDir.mkdirs();

        for (Map.Entry<String, File> entry : availableSongs.entrySet()) {
            File source = entry.getValue();
            String cleanId = entry.getKey().trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");

            if (source.getName().toLowerCase().endsWith(".ogg")) {
                File dest = new File(soundsDir, cleanId + ".ogg");
                try {
                    Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to copy sound file to Bedrock pack: " + e.getMessage());
                }
            }
        }

        // 3. Generate sounds/sound_definitions.json in bedrock pack
        File soundsRoot = new File(bedrockPackFolder, "sounds");
        soundsRoot.mkdirs();
        File soundDefinitions = new File(soundsRoot, "sound_definitions.json");
        try (FileWriter writer = new FileWriter(soundDefinitions, StandardCharsets.UTF_8)) {
            writer.write("{\n");
            writer.write("  \"format_version\": \"1.14.0\",\n");
            writer.write("  \"sound_definitions\": {\n");

            List<String> songList = new ArrayList<>(uniqueCleanIds);
            for (int i = 0; i < songList.size(); i++) {
                String songId = songList.get(i);
                boolean isLastSong = (i == songList.size() - 1);

                // Entry 1: namespace.songId (Geyser dot notation)
                writer.write("    \"" + namespace + "." + songId + "\": {\n");
                writer.write("      \"category\": \"record\",\n");
                writer.write("      \"sounds\": [\n");
                writer.write("        {\n");
                writer.write("          \"name\": \"sounds/" + namespace + "/" + songId + "\",\n");
                writer.write("          \"stream\": true,\n");
                writer.write("          \"load_on_low_memory\": true\n");
                writer.write("        }\n");
                writer.write("      ]\n");
                writer.write("    },\n");

                // Entry 2: namespace:songId (Geyser colon notation)
                writer.write("    \"" + namespace + ":" + songId + "\": {\n");
                writer.write("      \"category\": \"record\",\n");
                writer.write("      \"sounds\": [\n");
                writer.write("        {\n");
                writer.write("          \"name\": \"sounds/" + namespace + "/" + songId + "\",\n");
                writer.write("          \"stream\": true,\n");
                writer.write("          \"load_on_low_memory\": true\n");
                writer.write("        }\n");
                writer.write("      ]\n");
                writer.write("    }" + (isLastSong ? "" : ",") + "\n");
            }

            writer.write("  }\n");
            writer.write("}\n");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write Bedrock sound_definitions.json: " + e.getMessage());
        }

        // 4. Zip Bedrock pack into CustomMusicPack-Bedrock.mcpack and CustomMusicPack-Bedrock.zip
        File mcpackFile = new File(plugin.getDataFolder(), "CustomMusicPack-Bedrock.mcpack");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(mcpackFile))) {
            zipDirectory(bedrockPackFolder, bedrockPackFolder, zos);
            plugin.getLogger().info("Generated 'CustomMusicPack-Bedrock.mcpack' in " + plugin.getDataFolder().getName());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create CustomMusicPack-Bedrock.mcpack: " + e.getMessage());
        }

        File bedrockZip = new File(plugin.getDataFolder(), "CustomMusicPack-Bedrock.zip");
        try {
            Files.copy(mcpackFile.toPath(), bedrockZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}

        // 5. Automatically install into Geyser's packs/ directory if present
        if (plugin.getConfig().getBoolean("auto-install-to-geyser", true)) {
            autoInstallToGeyser(mcpackFile);
        }
    }

    private void autoInstallToGeyser(File mcpackFile) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null || !pluginsDir.exists()) return;

        File[] geyserCandidates = new File[] {
            new File(pluginsDir, "Geyser-Spigot/packs"),
            new File(pluginsDir, "Geyser/packs"),
            new File(pluginsDir, "geyser/packs"),
            new File(pluginsDir, "Geyser-Paper/packs")
        };

        boolean installed = false;
        for (File packsDir : geyserCandidates) {
            if (packsDir.exists() || packsDir.getParentFile().exists()) {
                if (!packsDir.exists()) {
                    packsDir.mkdirs();
                }
                File target = new File(packsDir, "CustomMusicPack-Bedrock.mcpack");
                try {
                    Files.copy(mcpackFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info(">> [Geyser] Automatically installed Bedrock resource pack into Geyser: " + target.getPath());
                    installed = true;
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to auto-copy Bedrock pack to Geyser: " + e.getMessage());
                }
                break;
            }
        }

        if (!installed) {
            plugin.getLogger().info("[Geyser] If running Geyser on a proxy or external folder, copy 'CustomMusicPack-Bedrock.mcpack' into your Geyser packs/ directory.");
        }
    }

    private int getNextPackVersion() {
        File verFile = new File(plugin.getDataFolder(), "bedrock_version.txt");
        int current = 1;
        if (verFile.exists()) {
            try {
                String content = Files.readString(verFile.toPath()).trim();
                current = Integer.parseInt(content) + 1;
            } catch (Exception ignored) {}
        }
        try {
            Files.writeString(verFile.toPath(), String.valueOf(current));
        } catch (IOException ignored) {}
        return current;
    }

    private void zipDirectory(File root, File current, ZipOutputStream zos) throws IOException {
        File[] files = current.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[8192];
        for (File f : files) {
            if (f.isDirectory()) {
                zipDirectory(root, f, zos);
            } else {
                String relativePath = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(relativePath);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(f)) {
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    public boolean songExists(String songId) {
        if (songId.equalsIgnoreCase("test")) return true;
        return availableSongs.containsKey(songId);
    }

    public Set<String> getSongIds() {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(availableSongs.keySet());
        set.add("test");
        return Collections.unmodifiableSet(set);
    }

    public boolean isMp3Only(String songId) {
        File file = availableSongs.get(songId);
        if (file == null) return false;
        return file.getName().toLowerCase().endsWith(".mp3");
    }

    public ItemStack createDisc(String songId) {
        String materialName = plugin.getConfig().getString("default-disc-material", "MUSIC_DISC_CAT");
        Material mat = Material.matchMaterial(materialName);
        if (mat == null || !mat.isItem()) {
            mat = Material.MUSIC_DISC_CAT;
        }

        ItemStack disc = new ItemStack(mat);
        ItemMeta meta = disc.getItemMeta();
        if (meta == null) return disc;

        String soundKey;
        if (songId.equalsIgnoreCase("test")) {
            soundKey = "minecraft:music_disc.pigstep";
        } else {
            String namespace = plugin.getConfig().getString("sound-namespace", "custom_music");
            soundKey = namespace + ":" + songId.toLowerCase();
        }

        meta.displayName(Component.text("♫ Music Disc - " + songId)
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Track: ", NamedTextColor.GRAY)
                .append(Component.text(songId, NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Custom Audio Disc", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Right-click a Jukebox to play!", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(discKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(songKey, PersistentDataType.STRING, songId);
        pdc.set(soundEventKey, PersistentDataType.STRING, soundKey);

        disc.setItemMeta(meta);
        return disc;
    }

    public boolean isCustomDisc(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(discKey, PersistentDataType.BYTE);
    }

    public String getSongId(ItemStack item) {
        if (!isCustomDisc(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(songKey, PersistentDataType.STRING);
    }

    public String getSoundKey(ItemStack item) {
        if (!isCustomDisc(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(soundEventKey, PersistentDataType.STRING);
    }

    public boolean isJukeboxPlaying(Location loc) {
        return activeJukeboxes.containsKey(loc);
    }

    public void playJukebox(Player player, Block block, ItemStack disc) {
        Location loc = block.getLocation();

        // If already playing, stop first and eject
        if (activeJukeboxes.containsKey(loc)) {
            stopJukebox(block, true);
            return;
        }

        String songId = getSongId(disc);
        String soundKey = getSoundKey(disc);
        if (songId == null || soundKey == null) return;

        // Take 1 disc from player
        ItemStack singleDisc = disc.clone();
        singleDisc.setAmount(1);
        disc.subtract(1);

        float volume = (float) plugin.getConfig().getDouble("jukebox-volume", 2.0);
        Location center = loc.clone().add(0.5, 0.5, 0.5);

        // Play the sound natively at the Jukebox with spatial distance attenuation
        loc.getWorld().playSound(center, soundKey, SoundCategory.RECORDS, volume, 1.0f);

        // Notify nearby players on action bar
        broadcastActionBar(center, volume * 16.0, Component.text("♫ Now Playing: " + songId + " ♫", NamedTextColor.GOLD));

        if (isMp3Only(songId)) {
            player.sendMessage(Component.text("⚠ Notice: Song '" + songId + "' is saved as an .mp3. Minecraft clients CANNOT hear MP3s! Convert it to .ogg to hear sound.", NamedTextColor.RED));
        }

        // Start repeating particle effect while music is playing
        boolean enableParticles = plugin.getConfig().getBoolean("enable-note-particles", true);
        BukkitTask task = null;
        if (enableParticles) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                center.getWorld().spawnParticle(
                        Particle.NOTE,
                        center.getX(),
                        center.getY() + 0.7,
                        center.getZ(),
                        2, 0.25, 0.2, 0.25, 0.5);
            }, 5L, 12L);
        }

        activeJukeboxes.put(loc, new ActiveTrack(songId, soundKey, singleDisc, task));
    }

    public void stopJukebox(Block block, boolean dropDisc) {
        Location loc = block.getLocation();
        ActiveTrack track = activeJukeboxes.remove(loc);
        if (track == null) return;

        if (track.task != null) {
            track.task.cancel();
        }

        float volume = (float) plugin.getConfig().getDouble("jukebox-volume", 2.0);
        double range = volume * 16.0;
        double rangeSq = range * range;

        // Stop sound for players within hearing radius when disc is ejected
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= rangeSq) {
                p.stopSound(track.soundKey, SoundCategory.RECORDS);
            }
        }

        broadcastActionBar(center, range, Component.text("⏹ Music Stopped", NamedTextColor.RED));

        if (dropDisc && track.discItem != null) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1.0, 0.5), track.discItem);
        }
    }

    public void playPortable(Player player, ItemStack disc) {
        if (!plugin.getConfig().getBoolean("allow-portable-playback", true)) return;

        UUID id = player.getUniqueId();
        if (activePortable.containsKey(id)) {
            stopPortable(player);
            return;
        }

        String songId = getSongId(disc);
        String soundKey = getSoundKey(disc);
        if (songId == null || soundKey == null) return;

        player.playSound(player.getLocation(), soundKey, SoundCategory.RECORDS, 1.0f, 1.0f);
        activePortable.put(id, soundKey);

        player.sendActionBar(Component.text("▶ Playing: " + songId + " (Right-click again to stop)", NamedTextColor.GREEN));

        if (isMp3Only(songId)) {
            player.sendMessage(Component.text("⚠ Notice: Song '" + songId + "' is saved as an .mp3. Minecraft CANNOT hear MP3s! Convert to .ogg to hear sound.", NamedTextColor.RED));
        }
    }

    public void stopPortable(Player player) {
        String soundKey = activePortable.remove(player.getUniqueId());
        if (soundKey != null) {
            player.stopSound(soundKey, SoundCategory.RECORDS);
            player.sendActionBar(Component.text("⏹ Stopped music", NamedTextColor.RED));
        }
    }

    public void stopAll() {
        for (Location loc : new HashSet<>(activeJukeboxes.keySet())) {
            stopJukebox(loc.getBlock(), true);
        }
        for (UUID id : new HashSet<>(activePortable.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                stopPortable(p);
            }
        }
        activePortable.clear();
        activeJukeboxes.clear();
    }

    private void broadcastActionBar(Location center, double radius, Component message) {
        double rSq = radius * radius;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= rSq) {
                p.sendActionBar(message);
            }
        }
    }

    public File getMusicFolder() {
        return musicFolder;
    }

    public File getPackFolder() {
        return packFolder;
    }

    public File getBedrockPackFolder() {
        return bedrockPackFolder;
    }
}
