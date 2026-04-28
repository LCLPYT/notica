package work.lclpnet.notica.util;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;
import work.lclpnet.notica.NoticaInit;

import java.util.function.Predicate;

public enum NoticaPermissions {

    COMMAND_MUSIC_PLAY("command.music.play"),
    COMMAND_MUSIC_PLAY_POSITIONAL("command.music.play.positional"),
    COMMAND_MUSIC_PLAY_OTHER("command.music.play.other"),
    COMMAND_MUSIC_STOP("command.music.stop"),
    COMMAND_MUSIC_STOP_OTHER("command.music.stop.other"),
    COMMAND_MUSIC_SEEK("command.music.seek"),
    COMMAND_MUSIC_SEEK_OTHER("command.music.seek.other"),
    ;

    private final String id;

    NoticaPermissions(String id) {
        this.id = NoticaInit.MOD_ID + "." + id;
    }

    public String id() {
        return id;
    }

    public Predicate<CommandSourceStack> ofAtLeast(PermissionLevel level) {
        return Permissions.require(id, level);
    }

    public boolean checkAtLeast(CommandSourceStack source, PermissionLevel level) {
        return Permissions.check(source, id, level);
    }
}
