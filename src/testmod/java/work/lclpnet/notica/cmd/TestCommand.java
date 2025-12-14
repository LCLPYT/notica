package work.lclpnet.notica.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.CheckedSong;
import work.lclpnet.notica.util.ServerSongLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static net.minecraft.ChatFormatting.GREEN;
import static net.minecraft.ChatFormatting.RED;
import static net.minecraft.commands.Commands.literal;
import static work.lclpnet.notica.NoticaInit.identifier;

public class TestCommand {

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(identifier("test").toString())
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("play_with_offset")
                        .executes(this::playWithOffset)));
    }

    private int playWithOffset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        String name = "/songs/Driftveil City.nbs";
        InputStream in = getClass().getResourceAsStream(name);

        if (in == null) {
            player.sendSystemMessage(Component.literal("Could not find song \"%s\"".formatted(name)).withStyle(RED));
            return 0;
        }

        Identifier playWithOffset = identifier("play_with_offset");
        CheckedSong song;

        try (in) {
            song = ServerSongLoader.load(in, playWithOffset);
        } catch (IOException e) {
            player.sendSystemMessage(Component.literal("Failed to read song \"%s\"".formatted(name)).withStyle(RED));
            return 0;
        }

        Notica notica = Notica.getInstance(player.level().getServer());
        notica.playSong(song, 1.f, 200, Set.of(player));

        player.sendSystemMessage(Component.literal("Playing song \"%s\"".formatted(name)).withStyle(GREEN));

        return 1;
    }
}
