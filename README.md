# Notica
A Fabric mod for playing note block songs in the .nbs file format to your players.
This mod is vanilla compatible on the server-side, which means that vanilla players can join without this mod installed as well.
Players with the mod installed benefit from reduced network-usage, as well as better playback quality.

## Features
### NBS file playback
Play any song created with [Open Note Block Studio](https://opennbs.org/) on a server or in single player.
Place the .nbs files in the `config/notica/songs` directory.
Use the `/music play <song> for [players]` command to play a song.

![Play a song with Notica](https://i.imgur.com/ihCc1gY.gif)

You can find many NBS songs at [Noteblock World](https://noteblock.world/).

### Clientside song playback
On a vanilla client connection, each note is sent to the player with a separate packet.
This creates huge amounts of packets being sent, which causes lag and reduced playback quality.

However, if a player has the mod installed, the song notes are packed into chunks and sent in larger, but way less packets.
This process is similar to video buffering.

![Notica Server Client Architecture](https://i.imgur.com/rZMliF9.png)

### Improved playback quality
Vanilla Minecraft has some limitations that impact the playback quality of songs.
For one, notes can only be played in a certain octave range (F#3 to F#5).
For another, sounds must have an integer position in the world, which causes audio artifacts when the player is moving during playback.
Additionally, stereo panning of notes is significantly worse because note positions are clamped to the nearest integer values.

Clients with Notica installed do not have these limitations, as notes are played directly via OpenAL, the audio engine used by Minecraft: Java Edition.
Song playback will thereby sound similar to the playback within Open Note Block Studio.

### Custom instruments
Any Minecraft sound can be used as a custom instrument in .nbs files.
Notica supports all custom instrument related features of the [nbs file format specification](https://opennbs.org/nbs) (up to version 5).

### Arbitrary song tempo
Much like other server-sided nbs players, Notica is not limited to specific song tempos.
This mod makes sure that the song playback timings are accurate, by running song playbacks asynchronously.
Packets are sent without server-thread synchronization, which would limit the tempo to 20 ticks per second again.

### Song Looping
NBS format version 4 introduces loop-options like on/off, max loop count and loop start tick.
This mod supports these options and will automatically loop accordingly.

Hint: If a song loops indefinitely, you can stop it using `/music stop` in-game.

### Extended octave range
As mentioned earlier, Minecraft imposes a limit to the note pitch, limiting the octave range to F#3 until F#5.
Modded players do not have this limitation, but vanilla players are still impacted.
Notes outside the vanilla range will be transposed for vanilla players by default, so that can be played.
Vanilla clients are still able to play notes outside the vanilla range, by installing the extended notes resource pack.
The extra notes resource pack can be installed by using `/music set extended_range true` to enable the extended range playback (**vanilla players only**).

### Positional music playing
Songs can be played through "speakers" at positions in the world.
A speaker may either have a fixed position or may be attached to an entity.
If the entity of a speaker moves, the song moves as well.

#### Doppler effect
Songs attached to entities may enable the doppler effect which distorts the audio pitch depending on the entity motion relative to the player.
In the mod settings this may be extended to use the player's motion as well.
This will enable a complete doppler effect simulation using OpenAL.

## Commands

All `/music` commands require OP or the corresponding permission listed in the table above.

### Playing

Play a song for yourself:
```
/music play <song>
```

Play a song for specific players or all online players:
```
/music play MySong.nbs for @a
/music play MySong.nbs global
```

The `<song>` argument is the path to a `.nbs` file relative to the `config/notica/songs` directory. Tab-completion will suggest available files.

Songs started with `global` are also automatically played for players who join the server while the song is running.

#### Volume and playback options

Specify a volume between `0.0` and `1.0` after the listener argument:
```
/music play MySong.nbs for @a 0.7
```

Two playback variants are available: `individual` and `streamed`. `streamed` is used by default.

With `individual` playback, each note is played directly through Minecraft's sound system, which has a limit of 256 simultaneously playing sounds. This pool is shared with all other game sounds. Songs with a high simultaneous note count may have notes dropped when the pool is full, and other game sounds may also be cut off in that case.

With `streamed` playback, a custom audio mixer produces a single audio stream from all notes. This handles songs with any simultaneous note count while maintaining high sound quality, and does not interfere with other game sounds. Songs with a lot of stereo panning may sound slightly different under `individual` playback compared to `streamed`.
```
/music play MySong.nbs for @a 0.8 individual
/music play MySong.nbs for @a 0.8 streamed
```

Streamed playback also supports two stereo mixing modes, `spatial` (default) and `equal_power`:
```
/music play MySong.nbs for @a 0.8 streamed equal_power
```

#### Song IDs

Every active song has an ID. When no explicit ID is given, the ID is derived from the filename and the name of the player who ran the command. Starting a song this way automatically stops any songs already playing for the affected listeners.

To play multiple songs at the same time, provide an explicit ID using the `id` keyword. This also skips the auto-stop:
```
/music play Ambient.nbs for @a 0.5 individual id myserver:ambient
/music play Theme.nbs for @a 0.8 id myserver:theme
```

The ID is also used with `/music stop` and `/music seek` to target a specific song.

### Positional playback

Songs can be played through a speaker at a fixed position in the world or attached to a moving entity:
```
/music play MySong.nbs at position 100 64 200
/music play MySong.nbs at position 100 64 200 for @a
/music play MySong.nbs at entity @e[type=armor_stand,limit=1]
/music play MySong.nbs at entity @e[type=armor_stand,limit=1] for @a
```

After the volume you can configure the speaker range and radius. `range` (16 to 128) is the maximum distance in blocks at which the song can be heard. `radius` (0 to 15) applies to stereo songs and determines how far apart the left and right speaker channels are placed in the world:
```
/music play MySong.nbs at position 0 64 0 for @a 0.8 64 8
```

Using mono channel mode down-mixes the song to a single point-like source. This makes the speaker behave like a single point in space rather than a spread stereo pair. Note that down-mixing will make the sound a bit quieter. Mono is required to enable the doppler effect on entity speakers:
```
/music play MySong.nbs at position 0 64 0 for @a 0.8 streamed mono
```

#### Doppler effect

Entity speakers with mono streamed playback support the doppler effect, which shifts the pitch based on the entity's velocity relative to each listener. This requires Notica to be installed on the client:
```
/music play MySong.nbs at entity @e[type=minecart,limit=1] for @a 0.8 streamed mono doppler
```

### Stopping

Stop all songs currently playing for yourself:
```
/music stop
```

Stop songs for specific players, all songs on the server, or a specific song by ID:
```
/music stop for <players>
/music stop all
/music stop id <id>
/music stop for <players> <id>
```

Songs started with `global` can only be stopped with `/music stop id` or `/music stop all`.

### Adding listeners

Add players to a song that is already playing without restarting it:
```
/music add <id> <players>
```

### Seeking

Jump to a specific position in a song using `s`/`sec` for seconds, `m`/`min` for minutes, or `t`/`ticks` for ticks. A plain number seeks to an absolute position; a `+` or `-` prefix seeks relative to the current position:
```
/music seek 1m30s
/music seek +10s
/music seek -30t
/music seek 50sec+3ticks
```

To seek for specific players or a specific song:
```
/music seek +10s <players>
/music seek +10s <players> <id>
```

### Client settings

These commands are available to all players and do not require any permissions.

Set your personal playback volume (0 to 100 percent):
```
/music set volume 75
```

Enable the extended octave range resource pack (vanilla players only, see [Extended octave range](#extended-octave-range)):
```
/music set extended_range true
```

## Permissions
In order to play or stop music, you need to be a server operator (have OP) or you need to have the corresponding permissions:

| Action                | Required permission             |
|-----------------------|---------------------------------|
| Play music            | notica.command.music.play       |
| Play positional songs | notica.command.music.play.positional   |
| Play music to others  | notica.command.music.play.other |
| Stop music            | notica.command.music.stop       |
| Stop music for others | notica.command.music.stop.other |
| Seek music            | notica.command.music.seek |
| Seek music for others | notica.command.music.seek.other |

Please note that setting the music volume and toggling extended octave range support is always possible.

## Similar projects
- [Nota](https://github.com/PinkGoosik/nota) by PinkGoosik (Fabric, Quilt)
- [NoteBlockAPI](https://github.com/koca2000/NoteBlockAPI) by koca2000 (Bukkit and derivates)

## Developer API
### Gradle dependency
First, add Notica as gradle dependency:
```groovy
repositories {
    maven {
        url "https://repo.lclpnet.work/repository/internal"
    }
}

dependencies {
    modImplementation 'work.lclpnet.mods:notica:1.1.3+1.20.6'  // replace with your version
}
```

### Load a song
You can load a song from any `InputStream`.
If you want to load a song from a file specified by a `Path`, you can use:

```java
Path path = Path.of("path", "to", "song.nbs");
Identifier id = Identifier.of("myMod", "foo");
CheckedSong song;

try (var in = Files.newInputStream(path)) {
    song = ServerSongLoader.load(in, id);
} catch (IOException e) {
    // handle the error
    return;
}
```

The song will be loaded from the file `path/to/song.nbs` with a given identifier, that can be used to control song playback via commands.
A `CheckedSong` is a song, associated with an id and a checksum, which can be arbitrary.

### Playing a song
Song playback can be controlled via the Notica interface, which can be acquired for a given `MinecraftServer` instance:
```java
Notica api = Notica.getInstance(server);
```

You can play a song to a given set of players using:
```java
float volume = 1f;
Set<ServerPlayerEntity> listeners = Set.of(playerOne, playerTwo);

SongHandle handle = api.playSong(song, volume, listeners);
```

You can use the song handle to remove certain listeners or stop the playback altogether.

Make sure you do not hold on to SongHandles for to long, as they cannot be garbage collected if they are still referenced somewhere.
Implement some kind of cleanup, or use `SongHandle::onDestroy` to clean up your references to the instance.

### Getting song handles involving a certain player
You can get all SongHandles that the player is a listener of using:
```java
Set<SongHandle> handles = api.getPlayingSongs(player);
```

### Getting a song handle by id of a player
You can get the SongHandle with a song id that the player is listening to using:
```java
Optional<SongHandle> handle = api.getPlayingSong(player, id);
```

### Getting a song handle by id
```java
Optional<SongHandle> handle = api.getPlayingSong(id);
```

### Getting all song handle instances
You can get all SongHandles currently playing using:
```java
Set<SongHandle> handles = api.getPlayingSongs();
```
