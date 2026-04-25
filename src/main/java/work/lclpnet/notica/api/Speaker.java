package work.lclpnet.notica.api;

import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

import static java.lang.Math.abs;

/**
 * Represents a speaker with a position.
 * The speaker can be bound to an entity to automatically sync the speaker position with the entity position.
 * @param position The speaker position. If a source entity is given, this is relative to the entity position.
 * @param radius The spacing between the speaker position and the maximum panning to a direction.
 * @param sourceEntityUuid The source entity uuid. If non-null, the speaker will be at the entity.
 * @param dopplerEffect Whether to enable the doppler effect for moving speakers.
 *                      Also requires a source entity as the entity velocity is used as sound velocity.
 *                      Only supported on clients with notica installed.
 */
public record Speaker(
        Vec3 position,
        double radius,
        @NotNull Optional<UUID> sourceEntityUuid,
        boolean dopplerEffect
) {
    public Speaker {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius might not be negative");
        }
    }

    public boolean isWithinListeningRange(Position pos) {
        double dx = pos.x() - position.x();
        double dy = pos.y() - position.y();
        double dz = pos.z() - position.z();

        // default sound listening range is 16
        return dx * dx + dy * dy + dz * dz <= 16.0 * 16.0;
    }

    public boolean isMono() {
        return abs(radius) < 1e-3d;
    }

    public Speaker asMonoSpeaker() {
        return withRadius(0);
    }

    public Speaker withRadius(double radius) {
        return new Speaker(position, radius, sourceEntityUuid, dopplerEffect);
    }

    public Speaker withDopplerEffect(boolean dopplerEffect) {
        return new Speaker(position, radius, sourceEntityUuid, dopplerEffect);
    }

    public static Speaker fixed(Vec3 position) {
        return fixed(position, 1d);
    }

    public static Speaker fixed(Vec3 position, double radius) {
        return new Speaker(position, radius, Optional.empty(), false);
    }

    public static Speaker ofEntity(Entity entity) {
        return ofEntity(entity, 1d);
    }

    public static Speaker ofEntity(Entity entity, double radius) {
        return ofEntity(entity, radius, false);
    }

    public static Speaker ofEntity(Entity entity, double radius, boolean dopplerEffect) {
        return new Speaker(entity.position(), radius, Optional.of(entity.getUUID()), dopplerEffect);
    }

    public Vec3 resolvePosition(Level level) {
        return resolveEntity(level)
                .map(Entity::position)
                .orElse(position);
    }

    public Optional<Entity> resolveEntity(Level level) {
        return sourceEntityUuid.map(level::getEntity);
    }
}
