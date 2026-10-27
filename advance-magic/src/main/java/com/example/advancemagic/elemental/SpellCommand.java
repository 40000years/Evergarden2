package com.example.advancemagic.elemental;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ElementalStaffService.Element;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Input paths shared by Java clients, Geyser clients and console administrators. */
public final class SpellCommand implements CommandExecutor,TabCompleter {
    private final AdvanceMagicPlugin plugin;
    private final SpellMenu menu;
    public SpellCommand(AdvanceMagicPlugin plugin,SpellMenu menu){this.plugin=plugin;this.menu=menu;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{
            String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            if(action.equals("give"))return give(sender,args);
            if(action.equals("adminupgrade"))return adminUpgrade(sender,args);
            if(action.equals("help")){help(sender);return true;}
            if(action.equals("recipe")||action.equals("recipes")){
                Element element=args.length>1?Element.parse(args[1]):null;
                if(args.length>1&&element==null&&!wooden(args[1]))sender.sendMessage(ChatColor.RED+"ใช้ /staff recipe wood เพื่อดูสูตรคทาไม้");else recipes(sender,element);return true;
            }
            if(!(sender instanceof Player player)){sender.sendMessage("Player required. /spell give scroll|staff ...");return true;}
            switch(action){
                case "menu","manage"->menu.open(player);
                case "codex","collection"->{
                    Element element=args.length>1?Element.parse(args[1]):Element.FIRE;
                    if(element==null)sender.sendMessage(ChatColor.RED+"ธาตุไม่ถูกต้อง");else menu.codex(player,element);
                }
                case "imprint"->menu.imprint(player);
                case "fuse","fusion"->menu.fusion(player);
                case "extract","remove"->{
                    if(args.length>2)sender.sendMessage(ChatColor.YELLOW+"/spell extract [ช่อง]");
                    else if(args.length==2){int slot=Integer.parseInt(args[1]);if(slot<1||slot>5)sender.sendMessage(ChatColor.RED+"กรุณาระบุเลขช่อง 1-5");else menu.extract(player,slot);}
                    else menu.extract(player);
                }
                case "select"->{
                    if(args.length!=2||!plugin.staffSpells().select(player,Integer.parseInt(args[1])))sender.sendMessage(ChatColor.RED+"เลือกช่องที่มีเวท: /spell select 1-5");
                }
                case "next","cycle"->{if(!plugin.staffSpells().cycle(player))sender.sendMessage(ChatColor.YELLOW+"ถือคทาที่ประทับเวทแล้วในมือหลัก");}
                case "reorder","swap"->{
                    if(args.length!=3)sender.sendMessage(ChatColor.YELLOW+"/spell reorder <ช่องแรก> <ช่องที่สอง>");
                    else result(sender,plugin.staffSpells().reorder(player,Integer.parseInt(args[1]),Integer.parseInt(args[2])));
                }
                case "upgrade"->result(sender,plugin.staffSpells().upgrade(player));
                case "protect"->result(sender,plugin.staffSpells().protect(player));
                case "cast"->{
                    if(!player.hasPermission("advance-magic.cast"))sender.sendMessage(ChatColor.RED+"ไม่มีสิทธิ์ร่ายเวท");
                    else plugin.elemental().cast(player);
                }
                default->help(sender);
            }
        }catch(NumberFormatException exception){sender.sendMessage(ChatColor.RED+"กรุณาระบุเลขช่อง 1-5 หรือจำนวนคัมภีร์ 1-64");}
        return true;
    }
    private boolean give(CommandSender sender,String[] args){
        if(!admin(sender))return true;
        if(args.length<3){giveHelp(sender);return true;}
        ItemStack item;Player target;String description;
        if(args[1].equalsIgnoreCase("scroll")){
            ElementalSpell spell=ElementalSpell.parse(args[2]);
            if(spell==null){sender.sendMessage(ChatColor.RED+"Spell ID ไม่ถูกต้อง ใช้ Tab เพื่อดูรายการ");return true;}
            target=target(sender,args.length>3?args[3]:null);if(target==null)return true;
            int amount=args.length>4?Integer.parseInt(args[4]):1;
            if(amount<1||amount>64){sender.sendMessage(ChatColor.RED+"จำนวนต้องอยู่ระหว่าง 1-64");return true;}
            int level=args.length>5?Integer.parseInt(args[5]):SpellLevels.BASE;
            if(level<SpellLevels.MIN||level>SpellLevels.MAX){sender.sendMessage(ChatColor.RED+"ระดับต้องอยู่ระหว่าง 1-7");return true;}
            level=SpellLevels.clamp(spell,level);
            item=plugin.staffSpells().createScroll(spell,level);item.setAmount(amount);description=SpellLevels.label(spell,level)+" Scroll x"+amount;
        }else if(args[1].equalsIgnoreCase("staff")){
            boolean wood=wooden(args[2]);Element element=Element.parse(args[2]);
            StaffSpellService.Tier tier=args.length>3?StaffSpellService.Tier.parse(args[3]):wood?StaffSpellService.Tier.WOODEN:StaffSpellService.Tier.APPRENTICE;
            if((element==null&&!wood)||tier==null||wood!=tier.wooden()){giveHelp(sender);return true;}
            target=target(sender,args.length>4?args[4]:null);if(target==null)return true;
            item=wood?plugin.staffSpells().createWoodenStaff(tier):plugin.staffSpells().createStaff(element,tier);
            description=tier+" "+(wood?"Wooden":element.title)+" Staff";
        }else{giveHelp(sender);return true;}
        if(!canFit(target,item)){sender.sendMessage(ChatColor.RED+"กระเป๋าของ "+target.getName()+" เต็ม กรุณาเว้นช่องก่อน");return true;}
        target.getInventory().addItem(item);plugin.staffSpells().discover(target);
        plugin.getLogger().info("SPELL_ADMIN_GIVE actor="+sender.getName()+" target="+target.getUniqueId()+" item="+description);
        sender.sendMessage(ChatColor.GREEN+"มอบ "+description+" ให้ "+target.getName()+" แล้ว");return true;
    }
    private boolean adminUpgrade(CommandSender sender,String[] args){
        if(!admin(sender))return true;
        StaffSpellService.Tier tier=args.length>1?StaffSpellService.Tier.parse(args[1]):null;
        if(tier==null){sender.sendMessage(ChatColor.YELLOW+"/spell adminupgrade <reinforced_wooden|arcane|mythic> [ผู้เล่น]");return true;}
        Player target=target(sender,args.length>2?args[2]:null);if(target==null)return true;
        result(sender,plugin.staffSpells().upgrade(target,tier));return true;
    }
    private static boolean admin(CommandSender sender){
        if(sender.hasPermission("advance-magic.admin"))return true;sender.sendMessage(ChatColor.RED+"ต้องมีสิทธิ์ advance-magic.admin");return false;
    }
    private static Player target(CommandSender sender,String name){
        Player player=name!=null?Bukkit.getPlayerExact(name):sender instanceof Player self?self:null;
        if(player==null)sender.sendMessage(ChatColor.RED+"ระบุชื่อผู้เล่นที่ออนไลน์");return player;
    }
    private static boolean canFit(Player player,ItemStack item){
        int left=item.getAmount();for(ItemStack slot:player.getInventory().getStorageContents()){
            if(slot==null||slot.getType().isAir())left-=item.getMaxStackSize();
            else if(slot.isSimilar(item))left-=Math.max(0,item.getMaxStackSize()-slot.getAmount());
            if(left<=0)return true;
        }return false;
    }
    private static boolean wooden(String name){return name.equalsIgnoreCase("wood")||name.equalsIgnoreCase("wooden");}
    private static void result(CommandSender sender,StaffSpellService.Result result){sender.sendMessage((result.success()?ChatColor.GREEN:ChatColor.RED)+result.message());}
    private static void help(CommandSender sender){
        sender.sendMessage(ChatColor.GOLD+"/spell หรือ /staff — จัดการคทาในมือหลัก");
        sender.sendMessage(ChatColor.YELLOW+"/spell imprint · /spell select 1-5 · /spell next");
        sender.sendMessage(ChatColor.YELLOW+"/spell extract [ช่อง] — ถอดเวทและรับคัมภีร์คืน");
        sender.sendMessage(ChatColor.LIGHT_PURPLE+"/spell fuse — แท่นหลอม: คัมภีร์ 2 แผ่นระดับเดียวกัน → ระดับถัดไป (I–VII)");
        sender.sendMessage(ChatColor.YELLOW+"/spell reorder <ช่อง> <ช่อง> · /spell codex [ธาตุ]");
        sender.sendMessage(ChatColor.YELLOW+"/staff upgrade · /staff protect");
        sender.sendMessage(ChatColor.YELLOW+"/staff recipe wood · /skilltree");
        sender.sendMessage(ChatColor.GRAY+"คัมภีร์ทุกธาตุ: ธาตุตรงดาเมจ +50% · ธาตุอื่นดาเมจ -50%");
        if(sender.hasPermission("advance-magic.admin"))giveHelp(sender);
    }
    public static void recipes(CommandSender sender,Element element){
        sender.sendMessage(ChatColor.GOLD+"สูตรคทาไม้เริ่มต้น: โต๊ะคราฟต์ 3×3");
        sender.sendMessage(ChatColor.YELLOW+"_ A _ / _ B _ / _ B _");
        sender.sendMessage(ChatColor.GRAY+"A = Amethyst Shard 1 ชิ้น · B = Blaze Rod รวม 2 ชิ้น");
        sender.sendMessage(ChatColor.GRAY+"เริ่ม 1 ช่อง · /staff upgrade ใช้ 4 Amethyst Shard + 2 Blaze Rod เพื่อเพิ่มเป็น 2 ช่อง");
        sender.sendMessage(ChatColor.GRAY+"รองรับสูงสุด Rare · คทาไร้ธาตุทำดาเมจ 50% กับทุกเวท");
        sender.sendMessage(ChatColor.GRAY+(element==null?"คทาธาตุทั้ง 8 ชนิด":element.title+" Staff")+" หาได้จากลูต ไม่มีสูตรคราฟต์");
        sender.sendMessage(ChatColor.AQUA+"หาคัมภีร์จากหีบโครงสร้าง Evergarden แล้วถือคทาใช้ /spell imprint");
    }
    private static void giveHelp(CommandSender sender){
        sender.sendMessage(ChatColor.YELLOW+"/spell give scroll <spell_id> [player] [amount] [level 1-7]");
        sender.sendMessage(ChatColor.YELLOW+"/spell give staff <element> [apprentice|arcane|mythic] [player]");
        sender.sendMessage(ChatColor.YELLOW+"/spell give staff wood [wooden|reinforced_wooden] [player]");
        sender.sendMessage(ChatColor.YELLOW+"/spell adminupgrade <reinforced_wooden|arcane|mythic> [player]");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        List<String> options=new ArrayList<>();
        if(args.length==1){options.addAll(List.of("menu","imprint","fuse","extract","select","next","reorder","codex","upgrade","protect","recipe","help"));if(sender.hasPermission("advance-magic.admin"))options.addAll(List.of("give","adminupgrade"));}
        else if(args[0].equalsIgnoreCase("select")&&args.length==2)options.addAll(List.of("1","2","3","4","5"));
        else if((args[0].equalsIgnoreCase("extract")||args[0].equalsIgnoreCase("remove"))&&args.length==2)options.addAll(List.of("1","2","3","4","5"));
        else if(args[0].equalsIgnoreCase("reorder")&&args.length<=3)options.addAll(List.of("1","2","3","4","5"));
        else if(args[0].equalsIgnoreCase("codex")&&args.length==2)for(Element element:Element.values())options.add(element.id());
        else if((args[0].equalsIgnoreCase("recipe")||args[0].equalsIgnoreCase("recipes"))&&args.length==2)options.add("wood");
        else if(sender.hasPermission("advance-magic.admin")&&args[0].equalsIgnoreCase("give")){
            if(args.length==2)options.addAll(List.of("scroll","staff"));
            else if(args.length==3&&args[1].equalsIgnoreCase("scroll"))for(ElementalSpell spell:ElementalSpell.values())options.add(spell.id());
            else if(args.length==3&&args[1].equalsIgnoreCase("staff")){options.add("wood");for(Element element:Element.values())options.add(element.id());}
            else if(args.length==4&&args[1].equalsIgnoreCase("staff"))options.addAll(wooden(args[2])?List.of("wooden","reinforced_wooden"):List.of("apprentice","arcane","mythic"));
            else if((args.length==4&&args[1].equalsIgnoreCase("scroll"))||(args.length==5&&args[1].equalsIgnoreCase("staff")))for(Player player:Bukkit.getOnlinePlayers())options.add(player.getName());
            else if(args.length==5&&args[1].equalsIgnoreCase("scroll"))options.addAll(List.of("1","8","16","64"));
            else if(args.length==6&&args[1].equalsIgnoreCase("scroll"))options.addAll(List.of("1","2","3","4","5","6","7"));
        }else if(sender.hasPermission("advance-magic.admin")&&args[0].equalsIgnoreCase("adminupgrade")){
            if(args.length==2)options.addAll(List.of("reinforced_wooden","arcane","mythic"));else if(args.length==3)for(Player player:Bukkit.getOnlinePlayers())options.add(player.getName());
        }
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(option->option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
