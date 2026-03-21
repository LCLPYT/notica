package work.lclpnet.notica.impl;

import net.minecraft.core.Position;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.notica.api.Speaker;

import static java.lang.Math.*;

public interface SoundPositionProvider {

    Vec3 getPosition(ServerPlayer player, float panning);

    static SoundPositionProvider playerRelative() {
        return (player, panning) -> {
            double x = player.getX();
            double y = player.getY();  // eyeY sounds awfully, as sound positions are only sent as integers
            double z = player.getZ();

            if (Math.abs(panning) >= 1e-3) {
                double yaw = toRadians(player.getYRot() - 90f);  // rotate 90 degrees ccw

                x += sin(yaw) * panning * 2;
                z -= cos(yaw) * panning * 2;
            }

            return new Vec3(x, y, z);
        };
    }

    static SoundPositionProvider ofSpeaker(Speaker speaker) {
        return (player, panning) -> {
            Position sourcePos = speaker.resolvePosition(player.level());

            // construct right vector from relative position of the player toward the speaker
            double dx = player.getX() - sourcePos.x();
            double dz = player.getZ() - sourcePos.z();

            double length = sqrt(dx * dx + dz * dz);

            double rightX = 0;
            double rightZ = 0;

            if (length > 0) {
                rightX = dz / length;
                rightZ = -dx / length;
            }

            double radius = speaker.radius();

            double finalX = sourcePos.x() + (rightX * panning * radius);
            double finalY = sourcePos.y();  // eyeY sounds awfully, as sound positions are only sent as integers
            double finalZ = sourcePos.z() + (rightZ * panning * radius);

            return new Vec3(finalX, finalY, finalZ);
        };
    }
}
