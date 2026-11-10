# Custom Music Discs

A versatile Paper and Spigot plugin that enables custom music discs with localized audio playback, custom lore, and jukebox integration.

---

### Direct Download

* **Latest Release**: [Download `CustomMusicDiscs.jar`](https://github.com/bruhslow/custom-music-discs/releases/latest)
* **Repository Build**: [`build/libs/CustomMusicDiscs.jar`](build/libs/CustomMusicDiscs.jar)

---

### Overview

| Detail | Specification |
| :--- | :--- |
| **Platform** | Paper / Purpur / Spigot |
| **Minecraft Versions** | 1.21+ / 26.x |
| **Java Version** | 21+ (Java 25 recommended for 26.x) |
| **License** | MIT |

---

### Key Features

* **Custom Music Disc Generation**: Create custom music discs with localized titles, custom artist credits, and lore.
* **Jukebox Integration**: Right-click jukeboxes with custom discs to play music localized to that jukebox position.
* **Audio Playback Controls**: Start, stop, and manage custom audio for individual players or across regions.
* **Staff Distribution Commands**: Convenient administrative command syntax to generate discs for yourself or dispatch them to online players.
* **Full Tab Completion**: Seamless autocomplete for disc identifiers, track titles, and online players.

---

### Commands & Permissions

| Command | Arguments | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/music disk generate` | `<name> [player]` | `custommusic.admin` | Creates a custom music disc for the specified track |
| `/music play` | `<name> [player]` | `custommusic.admin` | Plays track audio at the target player's location |
| `/music stop` | `[player]` | `custommusic.admin` | Halts any active custom audio for the target player |
| `/music reload` | None | `custommusic.admin` | Reloads configurations and registered sound tracks |

**Aliases**: `/mdisc`, `/custommusic`

---

### Quick Start

1. Download **`CustomMusicDiscs.jar`** and place it in your server's `plugins/` directory.
2. Restart or reload your server.
3. Configure your sound definitions in `config.yml`.
4. Run `/music disk generate <track>` in-game to receive your custom disc.

---

### Building from Source

Prerequisites: JDK 21+.

```bash
# Clone the repository
git clone https://github.com/bruhslow/custom-music-discs.git

# Build the plugin archive
./gradlew build
```

Compiled JARs will be generated in `build/libs/`.

---

### License

This project is licensed under the MIT License.
