package com.example.advancemagic;

import com.example.advancemagic.item.ElementalStaffService.Element;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class MagicCommand implements TabExecutor {
    private final AdvanceMagicPlugin plugin;
    private final com.example.advancemagic.item.ArmorSetService armor;
    public MagicCommand(AdvanceMagicPlugin plugin){this.plugin=plugin;armor=new com.example.advancemagic.item.ArmorSetService(plugin);}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String action=args.length==0?"list":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("weapon")||action.equals("weapons"))return plugin.legendaryWeapons().command(sender,args);
        if(action.equals("armor"))return armor.command(sender,args,this::resolvePlayer);
        if(action.equals("list")){
            sender.sendMessage(ChatColor.LIGHT_PURPLE+"Elemental Staffs · คทาธาตุ 8 แบบ + คทาไม้เริ่มต้น");
            sender.sendMessage(ChatColor.AQUA+"Fire → Wind → Earth → Ice → Water → Fire");
            sender.sendMessage(ChatColor.GOLD+"Lightning → Light → Dark → Lightning");
            sender.sendMessage(ChatColor.GRAY+"จัดเวท: /spell · พัฒนาตัวละคร: /skilltree · ดูคทา: /magic staffs");return true;
        }
        if(action.equals("staffs")||action.equals("items")){
            if(action.equals("items")&&!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            if(sender instanceof Player player)plugin.itemMenu().open(player,sender.hasPermission("advance-magic.admin"));
            else sender.sendMessage("Use this menu in-game.");return true;
        }
        if(action.equals("staff")){
            if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            if(args.length<3||args.length>4||!args[1].equalsIgnoreCase("give")){sender.sendMessage("/magic staff give <element|wood|all> [player]");return true;}
            Player target=args.length==4?resolvePlayer(sender,args[3]):sender instanceof Player p?p:null;
            Element element=Element.parse(args[2]);boolean all=args[2].equalsIgnoreCase("all");
            boolean wood=args[2].equalsIgnoreCase("wood")||args[2].equalsIgnoreCase("wooden");
            if(target==null||(!all&&!wood&&element==null)){sender.sendMessage(ChatColor.RED+"Unknown staff or online player.");return true;}
            if(wood){giveOrDrop(target,plugin.staffSpells().createWoodenStaff());sender.sendMessage(ChatColor.GREEN+"Gave Amethystwood Staff to "+target.getName());return true;}
            for(Element e:all?Element.values():new Element[]{element})giveOrDrop(target,plugin.staffSpells().createStaff(e,com.example.advancemagic.elemental.StaffSpellService.Tier.APPRENTICE));
            sender.sendMessage(ChatColor.GREEN+"Gave "+(all?"all eight elemental staffs":element.title+" Staff")+" to "+target.getName());return true;
        }
        if(action.equals("craft")||action.equals("give")||action.equals("givecore")){
            sender.sendMessage(ChatColor.YELLOW+"คทาและสูตรคราฟชุดเดิมถูกยกเลิกแล้ว · /magic staffs");
            if(sender.hasPermission("advance-magic.admin"))sender.sendMessage(ChatColor.GRAY+"/magic staff give <element|all> [player]");return true;
        }
        if(action.equals("mana")&&sender instanceof Player p){
            plugin.casts().actionbar(p,"");var a=plugin.mana().account(p);
            p.sendMessage(ChatColor.AQUA+"Mana: "+String.format(Locale.ROOT,"%.1f/%.1f",a.manaExact(),a.maxMana())+" | Regen: "+String.format(Locale.ROOT,"%.1f/s",a.regenRate()));return true;
        }
        if(action.equals("pack")){
            if(args.length>1&&args[1].equalsIgnoreCase("resend")){if(sender instanceof Player p)plugin.packs().resend(p);return true;}
            if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            plugin.packs().describe(sender);return true;
        }
        if(action.equals("turbo")){
            if(!(sender instanceof Player p)){sender.sendMessage("Use this command in-game.");return true;}
            if(!plugin.flyingStaff().isRiding(p)){sender.sendMessage(ChatColor.YELLOW+"ขึ้นขี่ไม้เท้าบินก่อนใช้ Turbo");return true;}
            sender.sendMessage(ChatColor.AQUA+(plugin.flyingStaff().toggleTurbo(p)?"เปิด Turbo":"ปิด Turbo"));return true;
        }
        if(action.equals("givestaff")){
            if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            Player p=args.length>=2?resolvePlayer(sender,args[1]):sender instanceof Player self?self:null;
            if(p==null){sender.sendMessage("/magic givestaff [player]");return true;}
            giveOrDrop(p,plugin.flyingStaff().create());sender.sendMessage(ChatColor.GREEN+"Gave Flying Staff to "+p.getName());return true;
        }
        sender.sendMessage("/magic [list|staffs|staff give <element|all> [player]|mana|items|weapons|armor give <seraph|revenant> [piece|all] [player]|pack [resend]|givestaff [player]|turbo]");return true;
    }
    private Player resolvePlayer(CommandSender sender, String targetName) {
        if(targetName==null||targetName.isEmpty()) return sender instanceof Player p ? p : null;
        if(targetName.equalsIgnoreCase("@s")||targetName.equalsIgnoreCase("@p")) {
            if(sender instanceof Player p) return p;
            return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        }
        if(targetName.equalsIgnoreCase("@a")) {
            if(sender instanceof Player p) return p;
            return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        }
        Player p=Bukkit.getPlayerExact(targetName);
        if(p!=null) return p;
        for(Player pl : Bukkit.getOnlinePlayers()) {
            if(pl.getName().equalsIgnoreCase(targetName)) return pl;
        }
        String dotTarget=targetName.startsWith(".") ? targetName : "." + targetName;
        for(Player pl : Bukkit.getOnlinePlayers()) {
            if(pl.getName().equalsIgnoreCase(dotTarget)) return pl;
            if(pl.getName().startsWith(".") && pl.getName().substring(1).equalsIgnoreCase(targetName)) return pl;
        }
        return Bukkit.getPlayer(targetName);
    }

    private void giveOrDrop(Player p, ItemStack item) {
        var leftover=p.getInventory().addItem(item);
        if(!leftover.isEmpty()) {
            leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
        }
        p.updateInventory();
    }

    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length>1&&(args[0].equalsIgnoreCase("weapon")||args[0].equalsIgnoreCase("weapons")))return plugin.legendaryWeapons().complete(sender,args);
        if(args.length>1&&args[0].equalsIgnoreCase("armor"))return armor.complete(sender,args);
        List<String> values=new ArrayList<>();
        if(args.length==1){values.addAll(List.of("list","staffs","mana","turbo","pack"));if(sender.hasPermission("advance-magic.admin"))values.addAll(List.of("staff","givestaff","items","weapons","weapon","armor"));}
        if(args.length==2&&args[0].equalsIgnoreCase("pack"))values.add("resend");
        if(sender.hasPermission("advance-magic.admin")){
            if(args.length==2&&args[0].equalsIgnoreCase("staff"))values.add("give");
            if(args.length==3&&args[0].equalsIgnoreCase("staff")&&args[1].equalsIgnoreCase("give")){for(Element e:Element.values())values.add(e.id());values.addAll(List.of("wood","all"));}
            if((args.length==2&&args[0].equalsIgnoreCase("givestaff"))||(args.length==4&&args[0].equalsIgnoreCase("staff"))){
                values.addAll(List.of("@s","@p"));for(Player p:Bukkit.getOnlinePlayers())values.add(p.getName());
            }
        }
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
