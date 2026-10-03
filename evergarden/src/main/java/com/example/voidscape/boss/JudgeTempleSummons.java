package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.compat.LevelledMobsCompat;
import com.example.voidscape.world.DungeonLayout;
import com.example.voidscape.dungeon.GuardianAppearance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Temple boss bodies owned by this fight, without creating a second dungeon encounter. */
final class JudgeTempleSummons {
    static Mob spawn(VoidscapePlugin plugin,Location at,int index){
        DungeonLayout.Kind kind=DungeonLayout.Kind.values()[index%3];
        Class<? extends Mob> type=switch(kind){case SANCTUM_DARK->WitherSkeleton.class;case SANCTUM_ASTRAL->Stray.class;case SANCTUM_TIME->PiglinBrute.class;};
        String name=switch(kind){case SANCTUM_DARK->"จอมมารแห่งความมืด";case SANCTUM_ASTRAL->"อัครเทวทูตดวงดาว";case SANCTUM_TIME->"ผู้พิทักษ์กาลเวลา";};
        Mob mob=at.getWorld().spawn(at,type,m->{
            // Set before CreatureSpawnEvent so Evergarden's dimension filter accepts the summon.
            m.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
            m.getPersistentDataContainer().set(new NamespacedKey("7sins","control_resistant"),PersistentDataType.BYTE,(byte)1);
            LevelledMobsCompat.tagMob(m,plugin,true);
            m.setPersistent(false);m.setRemoveWhenFarAway(false);m.setMaximumNoDamageTicks(0);
            double hp=Math.clamp(plugin.getConfig().getDouble("combat.boss-health",1024),100,750)+150;
            attribute(m,Attribute.MAX_HEALTH,hp);m.setHealth(hp);
            attribute(m,Attribute.ATTACK_DAMAGE,Math.clamp(plugin.getConfig().getDouble("combat.boss-attack",27),10,100));
            attribute(m,Attribute.ARMOR,24);attribute(m,Attribute.ARMOR_TOUGHNESS,16);
            attribute(m,Attribute.KNOCKBACK_RESISTANCE,1);attribute(m,Attribute.SCALE,1.8);
            if(m instanceof PiglinAbstract piglin)piglin.setImmuneToZombification(true);
            m.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));m.getEquipment().setChestplateDropChance(0);
            m.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));m.getEquipment().setItemInMainHandDropChance(0);
            GuardianAppearance.apply(m,kind,true);
            m.customName(Component.text(name+" · อัญเชิญ",NamedTextColor.GOLD));m.setCustomNameVisible(true);
        });
        if(!mob.isValid())throw new IllegalStateException("Temple boss summon was cancelled");
        return mob;
    }
    private static void attribute(Mob mob,Attribute attr,double value){var a=mob.getAttribute(attr);if(a!=null)a.setBaseValue(value);}
}
