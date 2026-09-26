package com.example.sevensins;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Nine articulated item models; the armor stand is the no-pack/Bedrock fallback. */
final class WrathModel {
    private record Bone(ItemDisplay entity, String id, float x, float y, float z) {}
    private final SevenSinsPlugin plugin;
    private final List<Bone> bones = new ArrayList<>();
    private ArmorStand fallback;
    private boolean unbound;
    private float heading;
    private Location lastAnchor;

    WrathModel(SevenSinsPlugin plugin, Location at) {
        this.plugin = plugin;
        heading = at.getYaw();
        try {
        add(at, "body", 0, 1.65f, 0);
        add(at, "head", 0, 2.85f, 0);
        add(at, "left_arm", 0.8f, 2.3f, 0);
        add(at, "right_arm", -0.8f, 2.3f, 0);
        add(at, "left_leg", 0.35f, 0.85f, 0);
        add(at, "right_leg", -0.35f, 0.85f, 0);
        add(at, "cleaver", -0.9f, 1.4f, -0.35f);
        add(at, "core", 0, 2.0f, -0.42f);
        add(at, "crown", 0, 3.15f, 0);
        fallback = at.getWorld().spawn(at, ArmorStand.class, e -> {
            e.setVisibleByDefault(false); e.setPersistent(false); e.setGravity(false);
            e.setMarker(true); e.setInvulnerable(true); e.setSilent(true); e.setArms(true);
            e.getAttribute(Attribute.SCALE).setBaseValue(1.5);
            e.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            e.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            e.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            e.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            e.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
            e.getPersistentDataContainer().set(plugin.entityKey(), org.bukkit.persistence.PersistentDataType.STRING, "visual");
        });
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
        } catch (RuntimeException error) {
            bones.forEach(b -> b.entity.remove()); if (fallback != null) fallback.remove(); throw error;
        }
    }

    private void add(Location at, String id, float x, float y, float z) {
        ItemStack item = new ItemStack(Material.PAPER);
        var meta = item.getItemMeta(); meta.setItemModel(new NamespacedKey("sevensins", "wrath/" + id)); item.setItemMeta(meta);
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setVisibleByDefault(false); e.setPersistent(false); e.setInvulnerable(true);
            e.setItemStack(item); e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setInterpolationDuration(2); e.setTeleportDuration(2); e.setViewRange(1.2f);
            e.setRotation(0, 0); e.setDisplayWidth(8); e.setDisplayHeight(8);
            e.getPersistentDataContainer().set(plugin.entityKey(), org.bukkit.persistence.PersistentDataType.STRING, "visual");
            if (id.equals("core")) e.setBrightness(new Display.Brightness(15, 15));
        });
        if (!display.isValid()) throw new IllegalStateException("Model bone spawn was rejected: " + id);
        bones.add(new Bone(display, id, x, y, z));
    }

    void refresh(Player player) {
        boolean custom = plugin.packs().loaded(player);
        for (Bone bone : bones) {
            if (custom) player.showEntity(plugin, bone.entity); else player.hideEntity(plugin, bone.entity);
        }
        if (custom) player.hideEntity(plugin, fallback); else player.showEntity(plugin, fallback);
    }

    void animate(Location at, int ticks, WrathBoss.State state, double progress, boolean enraged, boolean walking) {
        heading = WrathPose.turn(heading, at.getYaw(), 18);
        // All bones share one heading. Entity teleport packets contain no yaw rotation.
        Quaternionf root = new Quaternionf().rotationY((float) Math.toRadians(180 - heading));
        Location renderAnchor = at.clone(); renderAnchor.setYaw(0); renderAnchor.setPitch(0);
        boolean moved = lastAnchor == null || !lastAnchor.getWorld().equals(at.getWorld())
                || lastAnchor.distanceSquared(renderAnchor) > 0.000001;
        if (unbound != enraged) {
            unbound = enraged;
            for (Bone bone : bones) if (Set.of("body", "head", "cleaver", "core").contains(bone.id)) {
                ItemStack item = bone.entity.getItemStack(); var meta = item.getItemMeta();
                meta.setItemModel(new NamespacedKey("sevensins", "wrath/" + bone.id + (enraged ? "_unbound" : "")));
                item.setItemMeta(meta); bone.entity.setItemStack(item);
            }
        }
        float gait = walking ? (float) Math.sin(ticks * 0.15) * 0.42f : 0;
        float breathe = (float) Math.sin(ticks * 0.075) * 0.025f;
        float raise = state == WrathBoss.State.WINDUP ? (float) progress : 0;
        boolean strike = state == WrathBoss.State.RECOVERY;
        boolean stagger = state == WrathBoss.State.STAGGER;
        for (Bone bone : bones) {
            float x = bone.x, y = bone.y, z = bone.z, rx = 0, ry = 0, rz = 0;
            switch (bone.id) {
                case "left_leg" -> rx = gait;
                case "right_leg" -> rx = -gait;
                case "left_arm" -> { rx = -gait * 0.6f - raise * 0.8f; rz = -0.12f; }
                case "right_arm" -> { rx = gait * 0.6f - raise * 2.1f; rz = 0.12f; if (strike) rx = 0.6f; }
                case "cleaver" -> { rx = -raise * 1.9f; y += raise * 1.1f; z += raise * 0.5f; if (strike) {rx = 1.1f; z -= 0.7f;} }
                case "body" -> { y += breathe; if (stagger) { rx = 0.5f; y -= 0.35f; } }
                case "head" -> { if (stagger) {rx = 0.45f; y -= 0.45f; z -= 0.25f;} }
                case "crown" -> { ry = (float) (ticks * (enraged ? 0.035 : 0.012)); y += breathe; }
                case "core" -> { y += breathe; if (stagger) {y -= 0.35f; z -= 0.25f;} }
                default -> {}
            }
            if (moved) bone.entity.teleport(renderAnchor);
            Quaternionf rotation = new Quaternionf(root).mul(new Quaternionf().rotationXYZ(rx, ry, rz));
            Transformation previous = bone.entity.getTransformation();
            // q and -q describe the same pose; keep a continuous quaternion representation.
            if (rotation.dot(previous.getLeftRotation()) < 0) rotation.set(-rotation.x, -rotation.y, -rotation.z, -rotation.w);
            Transformation pose = new Transformation(root.transform(new Vector3f(x, y, z)),
                    rotation, new Vector3f(1, 1, 1), new Quaternionf());
            if (!pose.equals(previous)) {
                bone.entity.setTransformation(pose);
                bone.entity.setInterpolationDelay(0);
            }
            bone.entity.setGlowing(enraged && bone.id.equals("core"));
        }
        lastAnchor = renderAnchor;
        Location fallbackAt = at.clone(); fallbackAt.setYaw(heading); fallbackAt.setPitch(0);
        if (moved || Math.abs(fallback.getLocation().getYaw() - heading) > 0.1) fallback.teleport(fallbackAt);
        fallback.setRightArmPose(new EulerAngle(-raise * 2.1 + (strike ? 0.6 : 0), 0, 0.15));
        fallback.setLeftLegPose(new EulerAngle(gait, 0, 0));
        fallback.setRightLegPose(new EulerAngle(-gait, 0, 0));
        fallback.setHeadPose(new EulerAngle(stagger ? 0.5 : 0, 0, 0));
    }

    void remove() { bones.forEach(b -> b.entity.remove()); fallback.remove(); }
    List<Entity> entities() {
        List<Entity> list = new ArrayList<>(); bones.forEach(b -> list.add(b.entity)); list.add(fallback); return list;
    }
}
