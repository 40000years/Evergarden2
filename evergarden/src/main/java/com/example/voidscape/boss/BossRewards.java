package com.example.voidscape.boss;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.function.Predicate;

/** Sanctuary victory only: echoes, previews and Arkhaia's repeatable river summons never call this. */
final class BossRewards {
    private BossRewards(){}
    static String id(ArkhaiaWaves.Kind kind){return kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?"banshee":kind.name().toLowerCase(Locale.ROOT);}
    static int color(ArkhaiaWaves.Kind kind){return switch(kind){
        case ACHERON->0xef355b;case PHLEGETHON->0xff941f;case COCYTUS->0x72e4ff;
        case BANSHEE_WAIL->0x6fffc4;case MALEFEASANCE->0xb95aff;case STYX->0xffdb69;};}
    static ItemStack orb(VoidscapePlugin plugin,ArkhaiaWaves.Kind kind){
        ItemStack item=new ItemStack(Material.HEART_OF_THE_SEA);var meta=item.getItemMeta();String id=id(kind);
        meta.displayName(net.kyori.adventure.text.Component.text(ArkhaiaMinions.name(kind)+" Soul Orb",net.kyori.adventure.text.format.TextColor.color(color(kind))).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));
        meta.setLore(List.of(ChatColor.GRAY+"ลูกแก้ววิญญาณแห่ง "+ArkhaiaMinions.name(kind),ChatColor.DARK_GRAY+"รางวัลจากการพิชิตวิหาร"));
        meta.setItemModel(new NamespacedKey("voidscape","boss_orb_"+id));
        var model=meta.getCustomModelDataComponent();model.setStrings(List.of("voidscape:boss_orb_"+id));meta.setCustomModelDataComponent(model);
        meta.getPersistentDataContainer().set(plugin.key("boss_orb"),PersistentDataType.STRING,id);item.setItemMeta(meta);return item;
    }
    static void victory(VoidscapePlugin plugin,ArkhaiaMinions.Defeat defeat,Location drop,Predicate<Player> eligible){
        // One shared physical trophy; each present contributing player separately earns a point.
        Item item=drop.getWorld().dropItem(drop.clone().add(0,.6,0),orb(plugin,defeat.kind()));item.setGlowing(true);item.setInvulnerable(true);
        item.setVelocity(new org.bukkit.util.Vector(0,.18,0));
        var dust=new Particle.DustOptions(Color.fromRGB(color(defeat.kind())),1.7f);
        for(int n=0;n<48;n++){double a=n*Math.PI/24;drop.getWorld().spawnParticle(Particle.DUST,drop.clone().add(Math.cos(a)*2,.3,Math.sin(a)*2),1,0,0,0,0,dust,true);}
        drop.getWorld().spawnParticle(Particle.END_ROD,drop.clone().add(0,1,0),36,.6,.8,.6,.08);
        drop.getWorld().playSound(drop,Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1.2f);
        var trader=plugin.seraphTrader();
        if(trader!=null)for(UUID uuid:defeat.contributors()){Player p=Bukkit.getPlayer(uuid);
            if(p!=null&&eligible.test(p))trader.feathers().grant(p,plugin.integer("seraph-trader.feathers.sanctuary-boss",5,0,64),"พิชิต "+ArkhaiaMinions.name(defeat.kind()),true);}
        var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
        if(addon instanceof AdvanceMagicPlugin magic&&magic.isEnabled()&&magic.skillTree()!=null)
            for(UUID uuid:defeat.contributors()){Player p=Bukkit.getPlayer(uuid);if(p!=null&&eligible.test(p)){
                magic.skillTree().grantPoints(p,1,ArkhaiaMinions.name(defeat.kind()));
                if(magic.armorCombat()!=null)magic.armorCombat().sanctuaryVictory(p,id(defeat.kind()));
            }}
    }
}
