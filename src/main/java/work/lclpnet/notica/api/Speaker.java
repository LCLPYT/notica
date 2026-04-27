package work.lclpnet.notica.api;

import net.minecraft.core.Position;
import net.minecraft.resources.ResourceKey;
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
 * @param dimension The speaker dimension. Will be ignored if there is a source entity is present.
 * @param radius The spacing between the speaker position and the maximum panning to a direction.
 * @param sourceEntityUuid The source entity uuid. If non-null, the speaker will be at the entity.
 * @param dopplerEffect Whether to enable the doppler effect for moving speakers.
 *                      Also requires a source entity as the entity velocity is used as sound velocity.
 *                      Only supported on clients with notica installed.
 */
public record Speaker(
        Vec3 position,
        ResourceKey<Level> dimension,
        double radius,
        float range,
        @NotNull Optional<UUID> sourceEntityUuid,
        boolean dopplerEffect
) {
    public Speaker {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius might not be negative");
        }

        if (radius <= 0) {
            throw new IllegalArgumentException("Range must be positive");
        }
    }

    public boolean isWithinRange(Position pos, double range) {
        double dx = pos.x() - position.x();
        double dy = pos.y() - position.y();
        double dz = pos.z() - position.z();

        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    public boolean isMono() {
        return abs(radius) < 1e-3d;
    }

    public Speaker asMonoSpeaker() {
        return withRadius(0);
    }

    public Speaker withRadius(double radius) {
        return new Speaker(position, dimension, radius, range, sourceEntityUuid, dopplerEffect);
    }

    public Speaker withDopplerEffect(boolean dopplerEffect) {
        return new Speaker(position, dimension, radius, range, sourceEntityUuid, dopplerEffect);
    }

    public static Speaker fixed(Vec3 position, Level level) {
        return fixed(position, level, 1d);
    }

    public static Speaker fixed(Vec3 position, Level level, double radius) {
        return fixed(position, level, radius, 16f);
    }

    public static Speaker fixed(Vec3 position, Level level, double radius, float range) {
        return new Speaker(position, level.dimension(), radius, range, Optional.empty(), false);
    }

    public static Speaker ofEntity(Entity entity) {
        return ofEntity(entity, 1d);
    }

    public static Speaker ofEntity(Entity entity, double radius) {
        return ofEntity(entity, radius, false);
    }

    public static Speaker ofEntity(Entity entity, double radius, boolean dopplerEffect) {
        return ofEntity(entity, radius, 16f, dopplerEffect);
    }

    public static Speaker ofEntity(Entity entity, double radius, float range, boolean dopplerEffect) {
        return new Speaker(entity.position(), entity.level().dimension(), radius, range, Optional.of(entity.getUUID()), dopplerEffect);
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
