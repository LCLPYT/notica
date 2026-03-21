package work.lclpnet.notica.api;

import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents a speaker with a position.
 * The speaker can be bound to an entity to automatically sync the speaker position with the entity position.
 * @param position The speaker position. If a source entity is given, this is relative to the entity position.
 * @param radius The spacing between the speaker position and the maximum panning to a direction.
 * @param sourceEntityUuid The source entity uuid. If non-null, the speaker will be at the entity.
 */
public record Speaker(
        Position position,
        double radius,
        @Nullable UUID sourceEntityUuid
) {
    public boolean isWithinListeningRange(Position pos) {
        double dx = pos.x() - position.x();
        double dy = pos.y() - position.y();
        double dz = pos.z() - position.z();

        // default sound listening range is 16
        return dx * dx + dy * dy + dz * dz <= 16.0 * 16.0;
    }

    public static Speaker fixed(Position position) {
        return fixed(position, 1d);
    }

    public static Speaker fixed(Position position, double radius) {
        return new Speaker(position, radius, null);
    }

    public static Speaker ofEntity(Entity entity) {
        return ofEntity(entity, 1d);
    }

    public static Speaker ofEntity(Entity entity, double radius) {
        return new Speaker(entity.position(), radius, entity.getUUID());
    }

    public Position resolvePosition(Level level) {
        if (sourceEntityUuid == null) return position;

        Entity entity = level.getEntity(sourceEntityUuid);

        if (entity == null) return position;

        return entity.position();
    }
}
