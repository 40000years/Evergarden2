package com.example.voidscape.crop;

import org.bukkit.Location;
import java.util.UUID;

public final class PlantedCrop {
    private final Location location;
    private final CropType type;
    private int stage;
    private long plantedAt;
    private UUID standUuid;

    public PlantedCrop(Location location, CropType type, int stage, long plantedAt, UUID standUuid) {
        this.location = location;
        this.type = type;
        this.stage = stage;
        this.plantedAt = plantedAt;
        this.standUuid = standUuid;
    }

    public PlantedCrop(Location location, CropType type, int stage, long plantedAt, UUID itemDisplayUuid, UUID interactionUuid) {
        this(location, type, stage, plantedAt, itemDisplayUuid);
    }

    public Location getLocation() { return location.clone(); }
    public CropType getType() { return type; }
    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = Math.max(0, Math.min(2, stage)); }
    public long getPlantedAt() { return plantedAt; }
    public void setPlantedAt(long plantedAt) { this.plantedAt = plantedAt; }
    public UUID getStandUuid() { return standUuid; }
    public void setStandUuid(UUID uuid) { this.standUuid = uuid; }
    public UUID getItemDisplayUuid() { return standUuid; }
    public void setItemDisplayUuid(UUID uuid) { this.standUuid = uuid; }
    public UUID getInteractionUuid() { return null; }
    public void setInteractionUuid(UUID uuid) { /* no-op for single-entity architecture */ }

    public boolean isMature() {
        return stage >= 2;
    }

    public long elapsedSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - plantedAt) / 1000L);
    }

    public double growthProgress() {
        return Math.min(1.0, (double) elapsedSeconds() / type.tier.growthSeconds);
    }

    public int secondsRemaining() {
        return Math.max(0, type.tier.growthSeconds - (int) elapsedSeconds());
    }

    public void accelerate(int seconds) {
        this.plantedAt -= seconds * 1000L;
    }

    public int calculateTargetStage() {
        long elapsed = elapsedSeconds();
        long total = type.tier.growthSeconds;
        if (elapsed >= total) return 2;
        if (elapsed >= total / 3) return 1;
        return 0;
    }

    public long getNextStageTimestamp() {
        if (isMature()) return Long.MAX_VALUE;
        long totalMs = type.tier.growthSeconds * 1000L;
        if (stage == 0) {
            return plantedAt + (totalMs / 3);
        } else if (stage == 1) {
            return plantedAt + totalMs;
        }
        return Long.MAX_VALUE;
    }
}
