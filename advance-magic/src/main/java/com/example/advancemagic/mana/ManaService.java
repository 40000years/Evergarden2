package com.example.advancemagic.mana;

import com.example.advancemagic.spell.Spell;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.*;

public final class ManaService {
    private final Plugin plugin;
    private final Map<UUID,CastAccount> accounts=new HashMap<>();
    public ManaService(Plugin plugin) { this.plugin=plugin; }
    public NamespacedKey key(String id) { return new NamespacedKey(plugin,id); }

    public CastAccount account(Player p) {
        return accounts.computeIfAbsent(p.getUniqueId(),id->{
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
            return a;
        });
    }

    public void save(Player p) {
        CastAccount a=accounts.get(p.getUniqueId()); if(a==null)return;
        var data=p.getPersistentDataContainer();
        data.set(key("mana"),PersistentDataType.DOUBLE,a.manaExact());
        data.set(key("max_mana"),PersistentDataType.DOUBLE,a.maxMana());
        data.set(key("mana_regen"),PersistentDataType.DOUBLE,a.regenRate());
        for(Spell s:Spell.values()) data.set(key("cd_"+s.id()),PersistentDataType.LONG,a.end(s.id()));
    }

    public void quit(Player p) { save(p); accounts.remove(p.getUniqueId()); }
    public void regenerate(Player p) { if(account(p).regenerate()) save(p); }

    public boolean drinkDragonBreath(Player p, ItemStack item, EquipmentSlot hand) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(ChatColor.YELLOW+"ระบบ Dragon's Breath ถูกแทนที่ด้วยพืชผัก Evergarden (Ancient Astral Root & Yggdrasil Sprout) แล้ว"));
        p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,1.0f,1.0f);
        return false;
    }
}
