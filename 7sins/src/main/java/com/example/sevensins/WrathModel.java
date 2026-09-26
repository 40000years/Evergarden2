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
    private final float scale;
    private final List<Bone> bones = new ArrayList<>();
    private ArmorStand fallback;
    private boolean unbound;
    private float heading;
    private Location lastAnchor;
    private double stride;
    private float walkingWeight;
    private final Map<String, WrathAnimation.Pose> rendered = new HashMap<>();

    WrathModel(SevenSinsPlugin plugin, Location at, double scale) {
        this.plugin = plugin;
        this.scale = (float)scale;
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
            e.setMarker(true); e.setVisible(false); e.setInvulnerable(true); e.setSilent(true); e.setArms(true);
            e.getAttribute(Attribute.SCALE).setBaseValue(1.5*scale);
            e.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            e.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            e.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            e.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            e.getEquipment().setItemInMainHand(new ItemStack(Material.MACE));
            e.getPersistentDataContainer().set(plugin.entityKey(), org.bukkit.persistence.PersistentDataType.STRING, "visual");
        });
        animate(at, 0, WrathBoss.State.ARRIVAL, WrathBoss.Attack.SWEEP, 0, false, false);
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
            e.setRotation(0, 0); e.setDisplayWidth(8*scale); e.setDisplayHeight(8*scale);
            e.getPersistentDataContainer().set(plugin.entityKey(), org.bukkit.persistence.PersistentDataType.STRING, "visual");
            if (id.equals("core")) e.setBrightness(new Display.Brightness(15, 15));
        });
        if (!display.isValid()) throw new IllegalStateException("Model bone spawn was rejected: " + id);
        bones.add(new Bone(display, id, x, y, z));
    }

    void refresh(Player player) {
        boolean custom = plugin.packs().loaded(player);
        if (custom) player.hideEntity(plugin, fallback);
        for (Bone bone : bones) {
            if (custom) player.showEntity(plugin, bone.entity); else player.hideEntity(plugin, bone.entity);
        }
        if (!custom) player.showEntity(plugin, fallback);
    }

    void animate(Location at, int ticks, WrathBoss.State state, WrathBoss.Attack attack, double progress, boolean enraged, boolean walking) {
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
        double distance=lastAnchor==null?0:Math.hypot(at.getX()-lastAnchor.getX(),at.getZ()-lastAnchor.getZ());
        float targetWeight=walking?(float)Math.min(1,distance/0.17):0;
        walkingWeight+=(targetWeight-walkingWeight)*0.28f;
        if(distance<2*scale) stride+=distance*3.8/scale;
        Map<String, WrathAnimation.Pose> sampled=WrathAnimation.sample(state,attack,progress,stride,walkingWeight,ticks,enraged);
        for (Bone bone : bones) {
            WrathAnimation.Pose pose=sampled.get(bone.id);
            WrathAnimation.Pose old=rendered.get(bone.id);
            // Exact strike/contact poses preserve timing; blend locomotion and interrupted states.
            boolean authored=state==WrathBoss.State.WINDUP||state==WrathBoss.State.STRIKE||state==WrathBoss.State.RECOVERY;
            if(old!=null&&!authored) pose=new WrathAnimation.Pose(
                    new Vector3f(old.position()).lerp(pose.position(),0.32f),
                    new Quaternionf(old.rotation()).slerp(pose.rotation(),0.32f));
            rendered.put(bone.id,pose);
            if (moved) bone.entity.teleport(renderAnchor);
            Quaternionf rotation = new Quaternionf(root).mul(pose.rotation());
            Transformation previous = bone.entity.getTransformation();
            if (rotation.dot(previous.getLeftRotation()) < 0) rotation.set(-rotation.x, -rotation.y, -rotation.z, -rotation.w);
            Transformation transform = new Transformation(root.transform(new Vector3f(pose.position()).mul(scale)),
                    rotation, new Vector3f(scale), new Quaternionf());
            if (!transform.equals(previous)) {
                bone.entity.setTransformation(transform);
                bone.entity.setInterpolationDelay(0);
            }
            bone.entity.setGlowing(enraged && bone.id.equals("core"));
        }
        lastAnchor = renderAnchor;
        Location fallbackAt = at.clone(); fallbackAt.setYaw(heading); fallbackAt.setPitch(0);
        if (moved || Math.abs(fallback.getLocation().getYaw() - heading) > 0.1) fallback.teleport(fallbackAt);
        fallback.setRightArmPose(armPose(sampled.get("right_arm").rotation()));
        fallback.setLeftArmPose(armPose(sampled.get("left_arm").rotation()));
        fallback.setLeftLegPose(armPose(sampled.get("left_leg").rotation()));
        fallback.setRightLegPose(armPose(sampled.get("right_leg").rotation()));
        fallback.setHeadPose(armPose(sampled.get("head").rotation()));
    }

    private EulerAngle armPose(Quaternionf rotation) {
        Vector3f angles=rotation.getEulerAnglesXYZ(new Vector3f());
        return new EulerAngle(-angles.x,angles.y,angles.z);
    }

    void remove() { bones.forEach(b -> b.entity.remove()); fallback.remove(); }
    List<Entity> entities() {
        List<Entity> list = new ArrayList<>(); bones.forEach(b -> list.add(b.entity)); list.add(fallback); return list;
    }
}
