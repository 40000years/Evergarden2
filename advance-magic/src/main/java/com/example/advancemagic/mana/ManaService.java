package com.example.advancemagic.mana;

import com.example.advancemagic.spell.Spell;
import com.example.advancemagic.spell.BasicSpellProfile;
import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.*;

public final class ManaService {
    private final Plugin plugin;
    private final Map<UUID,CastAccount> accounts=new HashMap<>();
    private final Set<UUID> refreshingStats=new HashSet<>();
    private final Map<UUID,Long> combatUntil=new HashMap<>();
    private record Reservation(long oldGcd,long oldUltimate,long oldDelay,ElementalSpell spell){}
    private final Map<UUID,Reservation> reservations=new HashMap<>();
    public ManaService(Plugin plugin) { this.plugin=plugin; }
    public NamespacedKey key(String id) { return new NamespacedKey(plugin,id); }

    public CastAccount account(Player p) {
        boolean fresh=!accounts.containsKey(p.getUniqueId());
        CastAccount result=accounts.computeIfAbsent(p.getUniqueId(),id->{
            var data=p.getPersistentDataContainer();
            double maxMana=100.0;
            if (data.has(key("max_mana"),PersistentDataType.DOUBLE)) {
                maxMana=data.get(key("max_mana"),PersistentDataType.DOUBLE);
            } else if (data.has(key("max_mana"),PersistentDataType.INTEGER)) {
                maxMana=data.get(key("max_mana"),PersistentDataType.INTEGER);
            }
            double regen=data.getOrDefault(key("mana_regen"),PersistentDataType.DOUBLE,2.0);
            double mana=maxMana;
            if (data.has(key("mana"),PersistentDataType.DOUBLE)) {
                mana=data.get(key("mana"),PersistentDataType.DOUBLE);
            } else if (data.has(key("mana"),PersistentDataType.INTEGER)) {
                mana=data.get(key("mana"),PersistentDataType.INTEGER);
            }
            CastAccount a=new CastAccount(mana,maxMana,regen);
            for (Spell s:Spell.values()) a.restore(s.id(),data.getOrDefault(key("cd_"+s.id()),PersistentDataType.LONG,0L));
            a.restore(BasicSpellProfile.COOLDOWN_KEY,data.getOrDefault(key("cd_"+BasicSpellProfile.COOLDOWN_KEY),PersistentDataType.LONG,0L));
            for(ElementalSpell spell:ElementalSpell.values())a.restore("elemental_"+spell.id(),data.getOrDefault(key("cd_elemental_"+spell.id()),PersistentDataType.LONG,0L));
            return a;
        });
        if(plugin instanceof AdvanceMagicPlugin magic&&magic.elementalStats()!=null&&refreshingStats.add(p.getUniqueId())){
            try{
                result.setBonusMaxMana(magic.elementalStats().snapshot(p).maxMana()-result.baseMaxMana());
                if(fresh){var data=p.getPersistentDataContainer();Double stored=data.get(key("mana"),PersistentDataType.DOUBLE);if(stored!=null)result.setMana(stored);}
            }finally{refreshingStats.remove(p.getUniqueId());}
        }
        return result;
    }

    public void save(Player p) {
        CastAccount a=accounts.get(p.getUniqueId()); if(a==null)return;
        PersistentDataContainer data=((PersistentDataHolder)p).getPersistentDataContainer();
        data.set(key("mana"),PersistentDataType.DOUBLE,a.manaExact());
        data.set(key("max_mana"),PersistentDataType.DOUBLE,a.baseMaxMana());
        data.set(key("mana_regen"),PersistentDataType.DOUBLE,a.regenRate());
        for(Spell s:Spell.values()) data.set(key("cd_"+s.id()),PersistentDataType.LONG,a.end(s.id()));
        data.set(key("cd_"+BasicSpellProfile.COOLDOWN_KEY),PersistentDataType.LONG,a.end(BasicSpellProfile.COOLDOWN_KEY));
        for(ElementalSpell spell:ElementalSpell.values())data.set(key("cd_elemental_"+spell.id()),PersistentDataType.LONG,a.end("elemental_"+spell.id()));
    }

    public void quit(Player p) { save(p); accounts.remove(p.getUniqueId());combatUntil.remove(p.getUniqueId());reservations.remove(p.getUniqueId()); }
    public void regenerate(Player p) { regenerate(p,System.currentTimeMillis()); }
    public void regenerate(Player p,long now) {
        var a=account(p);
        if(plugin instanceof AdvanceMagicPlugin magic&&magic.elementalStats()!=null){
            var stats=magic.elementalStats().snapshot(p);a.setBonusMaxMana(stats.maxMana()-a.baseMaxMana());
            if(now<p.getPersistentDataContainer().getOrDefault(key("elemental_regen_after"),PersistentDataType.LONG,0L))return;
            double base=plugin.getConfig().getDouble(isInCombat(p,now)?"elemental-system.mana.combat-regen":"elemental-system.mana.out-of-combat-regen",isInCombat(p,now)?3:7);
            if(a.regenerate(Math.max(0,base)*(1+stats.manaRegenBonus())))save(p);
        }else if(a.regenerate())save(p);
    }
    public void markCombat(Player p){long until=System.currentTimeMillis()+Math.max(1000,plugin.getConfig().getLong("elemental-system.mana.combat-duration-seconds",10)*1000);combatUntil.put(p.getUniqueId(),until);p.getPersistentDataContainer().set(key("elemental_combat_until"),PersistentDataType.LONG,until);}
    public boolean isInCombat(Player p,long now){return now<Math.max(combatUntil.getOrDefault(p.getUniqueId(),0L),p.getPersistentDataContainer().getOrDefault(key("elemental_combat_until"),PersistentDataType.LONG,0L));}
    public long elementalRemaining(Player p,ElementalSpell spell,long now){return account(p).remaining("elemental_"+spell.id(),now);}
    public long gcdRemaining(Player p,long now){return Math.max(0,p.getPersistentDataContainer().getOrDefault(key("elemental_gcd"),PersistentDataType.LONG,0L)-now);}
    public long ultimateRemaining(Player p,long now){return Math.max(0,p.getPersistentDataContainer().getOrDefault(key("elemental_ultimate"),PersistentDataType.LONG,0L)-now);}
    public boolean canReserveElemental(Player p,ElementalSpell spell,int cost,long now){
        return cost>0&&account(p).manaExact()>=cost&&elementalRemaining(p,spell,now)==0&&gcdRemaining(p,now)==0&&(!spell.ultimate()||ultimateRemaining(p,now)==0);
    }
    public boolean reserveElemental(Player p,ElementalSpell spell,int cost,long now){
        return reserve(p,spell,cost,now,false,1,1);
    }
    /** cooldownScale comes from the held staff's Cooldown Core level (1.0 = unchanged). */
    public boolean reserveElemental(Player p,ElementalSpell spell,int cost,long now,double cooldownScale){
        return reserve(p,spell,cost,now,false,cooldownScale,1);
    }
    /** levelFactor is the spell level's cooldown factor; unlike the wand's scale it is not clamped. */
    public boolean reserveElemental(Player p,ElementalSpell spell,int cost,long now,double cooldownScale,double levelFactor){
        return reserve(p,spell,cost,now,false,cooldownScale,levelFactor);
    }
    public boolean reserveElementalPassive(Player p,ElementalSpell spell,int cost,long now){
        return reserve(p,spell,cost,now,true,1,1);
    }
    public boolean reserveElementalPassive(Player p,ElementalSpell spell,int cost,long now,double cooldownScale){
        return reserve(p,spell,cost,now,true,cooldownScale,1);
    }
    private boolean reserve(Player p,ElementalSpell spell,int cost,long now,boolean passive,double cooldownScale,double levelFactor){
        if(plugin instanceof AdvanceMagicPlugin magic&&magic.elementalStats()!=null)account(p).setBonusMaxMana(magic.elementalStats().snapshot(p).maxMana()-account(p).baseMaxMana());
        if(passive){if(cost<=0||account(p).manaExact()<cost||elementalRemaining(p,spell,now)>0||(spell.ultimate()&&ultimateRemaining(p,now)>0))return false;}
        else
        if(!canReserveElemental(p,spell,cost,now))return false;
        var data=p.getPersistentDataContainer();
        var reservation=new Reservation(data.getOrDefault(key("elemental_gcd"),PersistentDataType.LONG,0L),data.getOrDefault(key("elemental_ultimate"),PersistentDataType.LONG,0L),data.getOrDefault(key("elemental_regen_after"),PersistentDataType.LONG,0L),spell);
        double scale=(Double.isFinite(cooldownScale)?Math.clamp(cooldownScale,.5,1):1)*(Double.isFinite(levelFactor)?Math.clamp(levelFactor,.25,4):1);
        if(!account(p).reserve("elemental_"+spell.id(),cost,spell.cooldown()*scale,now))return false;
        reservations.put(p.getUniqueId(),reservation);
        double gcd=plugin.getConfig().getDouble(spell.ultimate()?"elemental-system.mana.ultimate-gcd":"elemental-system.mana.global-cooldown",spell.ultimate()?1.2:.65);
        data.set(key("elemental_gcd"),PersistentDataType.LONG,now+(long)(Math.max(.1,gcd)*1000));
        if(spell.ultimate())data.set(key("elemental_ultimate"),PersistentDataType.LONG,now+Math.max(0,plugin.getConfig().getLong("elemental-system.mana.ultimate-category-seconds",15))*1000);
        data.set(key("elemental_regen_after"),PersistentDataType.LONG,now+Math.max(0,plugin.getConfig().getLong("elemental-system.mana.regen-delay-seconds",2))*1000);
        save(p);return true;
    }
    public void rollbackElemental(Player p,ElementalSpell spell,int cost){
        var r=reservations.remove(p.getUniqueId());if(r==null||r.spell()!=spell)return;
        account(p).refund("elemental_"+spell.id(),cost);var data=p.getPersistentDataContainer();
        data.set(key("elemental_gcd"),PersistentDataType.LONG,r.oldGcd());data.set(key("elemental_ultimate"),PersistentDataType.LONG,r.oldUltimate());data.set(key("elemental_regen_after"),PersistentDataType.LONG,r.oldDelay());save(p);
    }

    public boolean drinkDragonBreath(Player p, ItemStack item, EquipmentSlot hand) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(ChatColor.YELLOW+"Dragon's Breath ไม่สามารถใช้ฟื้นฟูมานาได้"));
        p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,1.0f,1.0f);
        return false;
    }
}
