package work.lclpnet.notica.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.CheckedSong;
import work.lclpnet.notica.util.ServerSongLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.util.Formatting.GREEN;
import static net.minecraft.util.Formatting.RED;
import static work.lclpnet.notica.NoticaInit.identifier;

public class TestCommand {

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal(identifier("test").toString())
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("play_with_offset")
                        .executes(this::playWithOffset)));
    }

    private int playWithOffset(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        String name = "/songs/Driftveil City.nbs";
        InputStream in = getClass().getResourceAsStream(name);

        if (in == null) {
            player.sendMessage(Text.literal("Could not find song \"%s\"".formatted(name)).formatted(RED));
            return 0;
        }

        Identifier playWithOffset = identifier("play_with_offset");
        CheckedSong song;

        try (in) {
            song = ServerSongLoader.load(in, playWithOffset);
        } catch (IOException e) {
            player.sendMessage(Text.literal("Failed to read song \"%s\"".formatted(name)).formatted(RED));
            return 0;
        }

        Notica notica = Notica.getInstance(player.getServer());
        notica.playSong(song, 1.f, 200, Set.of(player));

        player.sendMessage(Text.literal("Playing song \"%s\"".formatted(name)).formatted(GREEN));

        return 1;
    }
}
