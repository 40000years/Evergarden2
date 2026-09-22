package com.example.voidscape.command;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.item.RelicService.Relic;
import com.example.voidscape.world.DungeonLayout;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class VoidCommand implements CommandExecutor,TabCompleter {
    private final VoidscapePlugin plugin;
    private int generated,total,cursor,radius;
    private boolean generating,inFlight;
    private boolean isAdmin(CommandSender s){return s.isOp()||s.hasPermission("evergarden.admin")||s.hasPermission("voidscape.admin");}
    private boolean canEnter(CommandSender s){return isAdmin(s);}
    public VoidCommand(VoidscapePlugin plugin){this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        Player p=sender instanceof Player player?player:null;
        if(label.equalsIgnoreCase("upgrade") || label.equalsIgnoreCase("up")) {
            if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
            com.example.voidscape.gui.UpgradeMenuService.open(plugin, p);
            return true;
        }
        String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        if(Set.of("give","pregen","reload","status","pack","test","dev","kit","menu","enter","leave","tp","wands","wand","magic","cores","core","relics","relic","items","item").contains(sub)&&!isAdmin(sender)){
            if(sub.equals("enter")) {
                plugin.message(sender,"คำสั่งนี้สำหรับแอดมินเท่านั้น · กรุณาสร้างประตูควอตซ์ (Block of Quartz 4x5) แล้วโยนดอกไม้เพื่อเดินทางเข้าสู่ Evergarden");
                return true;
            }
            if(sub.equals("leave")) {
                plugin.message(sender,"คำสั่งนี้สำหรับแอดมินเท่านั้น · กรุณาใช้ประตูมิติกลับที่เกาะกลาง (Spawn Island) เพื่อเดินทางกลับ");
                return true;
            }
            plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");
            return true;
        }
        switch(sub) {
            case "upgrade", "up", "enchant", "forge" -> {
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                com.example.voidscape.gui.UpgradeMenuService.open(plugin, p);
            }
            case "test", "admin", "menu" -> {
                if(!isAdmin(sender)){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                plugin.testGui().open(p);
            }
            case "crops", "farm", "crop", "seeds" -> {
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                plugin.cropGui().open(p);
            }
            case "wands", "wand", "magic", "cores", "core" -> {
                if(!isAdmin(sender)){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                if(plugin.wandGui() != null) plugin.wandGui().open(p);
            }
            case "relics", "relic", "items", "item" -> {
                if(!isAdmin(sender)){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                if(plugin.relicGui() != null) plugin.relicGui().open(p);
            }
            case "pack" -> {
                if(args.length>1&&args[1].equalsIgnoreCase("resend")) {
                    Player target=args.length>2?Bukkit.getPlayerExact(args[2]):p;
                    if(target==null) {sender.sendMessage("Usage: /evergarden pack resend <online-player>");return true;}
                    plugin.packs().offer(target);
                }
                plugin.packs().describe(sender);
            }
            case "guide" -> {
                if(p!=null) {
                    if (args.length > 1) {
                        var type = com.example.voidscape.guide.GuideBookType.fromId(args[1]);
                        if (type != null) {
                            com.example.voidscape.guide.BedrockGuideService.openGuide(plugin, p, type, 0);
                            return true;
                        }
                    }
                    com.example.voidscape.guide.BedrockGuideService.openMenu(plugin, p);
                }
            }
            case "enter" -> {
                if(!isAdmin(sender)){
                    plugin.message(sender,"คำสั่งนี้สำหรับแอดมินเท่านั้น · กรุณาสร้างประตูควอตซ์ (Block of Quartz 4x5) แล้วโยนดอกไม้เพื่อเดินทางเข้าสู่ Evergarden");
                    return true;
                }
                if(p!=null){if(p.getWorld()==plugin.world())plugin.travel().leave(p,false);else plugin.travel().enter(p);}
            }
            case "tp" -> {
                if(!isAdmin(sender)){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                if(args.length>1) {
                    String dest=args[1].toLowerCase(Locale.ROOT);
                    if(dest.equals("spawn")) {
                        p.teleport(new Location(plugin.world(),0.5,97.0,0.5));
                        plugin.message(p,"วาร์ปมายังจุดเกิดเกาะกลางมิติ (Y=97)");
                        return true;
                    }
                    if(dest.equals("whale")||dest.equals("skywhale")) {
                        if(!plugin.getConfig().getBoolean("structures.sky-whale.enabled",true)) {
                            plugin.message(p,"ปิดการสร้างซากวาฬไว้ใน config");return true;
                        }
                        int fromX=p.getWorld()==plugin.world()?p.getLocation().getBlockX():0;
                        int fromZ=p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                        var whale=plugin.skyWhales().nearest(fromX,fromZ,12);
                        if(whale==null){plugin.message(p,"ไม่พบซากวาฬในระยะค้นหา");return true;}
                        p.teleport(new Location(plugin.world(),whale.x()-99.5,104,whale.z()+0.5));
                        plugin.message(p,"ซากวาฬใกล้ที่สุดอยู่ที่ X="+whale.x()+" Z="+whale.z());
                        return true;
                    }
                    var kind=dest.contains("astral")?DungeonLayout.Kind.SANCTUM_ASTRAL:
                             dest.contains("time")?DungeonLayout.Kind.SANCTUM_TIME:
                             DungeonLayout.Kind.SANCTUM_DARK;
                    int x=p.getWorld()==plugin.world()?p.getLocation().getBlockX():0,z=p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                    var s=plugin.layout().locate(x,z,kind,12);
                    if(s==null){plugin.message(sender,"ไม่พบสิ่งก่อสร้างในระยะค้นหา");return true;}
                    p.teleport(new Location(plugin.world(),s.x()+0.5,97,s.z()+8.5));
                    plugin.message(p,"วาร์ปไปยัง "+s.kind().displayName+" พิกัด X="+s.x()+" Y=97 Z="+(s.z()+8));
                    return true;
                }
                if(p.getWorld()==plugin.world())plugin.travel().leave(p,false);
                else plugin.travel().enter(p);
            }
            case "leave" -> {
                if(!isAdmin(sender)){
                    plugin.message(sender,"คำสั่งนี้สำหรับแอดมินเท่านั้น · กรุณาใช้ประตูมิติกลับที่เกาะกลาง (Spawn Island) เพื่อเดินทางกลับ");
                    return true;
                }
                if(p!=null&&p.getWorld()==plugin.world())plugin.travel().leave(p,false);
            }
            case "give" -> {
                if(args.length < 2) {
                    plugin.message(sender, "วิธีใช้: /evergarden give <ชื่อไอเทม> [จำนวน] [ผู้เล่น/@a/@p/@s]");
                    plugin.message(sender, "ตัวอย่าง: /evergarden give ricochet, /evergarden give shulker_levitation @a, /evergarden give key 4");
                    return true;
                }

                List<String> tokens = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
                boolean allPlayers = false;
                Player singleTarget = null;
                int count = 1;

                // 1. Check for @a
                for(Iterator<String> it = tokens.iterator(); it.hasNext();) {
                    String tok = it.next();
                    if(tok.equalsIgnoreCase("@a")) {
                        allPlayers = true;
                        it.remove();
                        break;
                    }
                }

                // 2. Check for @s, @p
                if(!allPlayers) {
                    for(Iterator<String> it = tokens.iterator(); it.hasNext();) {
                        String tok = it.next();
                        if(tok.equalsIgnoreCase("@s") || tok.equalsIgnoreCase("@p")) {
                            singleTarget = p != null ? p : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                            it.remove();
                            break;
                        }
                    }
                }

                // 3. Check for online player name
                if(!allPlayers && singleTarget == null) {
                    for(Iterator<String> it = tokens.iterator(); it.hasNext();) {
                        String tok = it.next();
                        Player found = findOnlinePlayer(tok);
                        if(found != null) {
                            singleTarget = found;
                            it.remove();
                            break;
                        }
                    }
                }

                // 4. Check for count (positive integer)
                for(Iterator<String> it = tokens.iterator(); it.hasNext();) {
                    String tok = it.next();
                    try {
                        int val = Integer.parseInt(tok);
                        if(val > 0) {
                            count = val;
                            it.remove();
                            break;
                        }
                    } catch(NumberFormatException ignored) {}
                }

                // 5. Remaining tokens form the item query
                String itemQuery = String.join("_", tokens).trim();
                if(itemQuery.isEmpty()) {
                    plugin.message(sender, "กรุณาระบุชื่อไอเทม");
                    return true;
                }

                ItemStack item = resolveItem(itemQuery, count);
                if(item == null) {
                    item = resolveItem(itemQuery.replace(" ", "_"), count);
                }
                if(item == null) {
                    plugin.message(sender, "ไม่พบไอเทม: " + itemQuery + " (ลองพิมพ์ชื่อตรงๆ เช่น ricochet, sharpness, storm_bow, eternity, shulker_levitation)");
                    return true;
                }

                List<Player> recipients = new ArrayList<>();
                if(allPlayers) {
                    recipients.addAll(Bukkit.getOnlinePlayers());
                } else {
                    if(singleTarget != null) {
                        recipients.add(singleTarget);
                    } else if(p != null) {
                        recipients.add(p);
                    } else {
                        plugin.message(sender, "ระบุผู้เล่นออนไลน์ด้วย หรือรันคำสั่งในฐานะผู้เล่น");
                        return true;
                    }
                }

                if(recipients.isEmpty()) {
                    plugin.message(sender, "ไม่พบผู้เล่นออนไลน์ที่จะมอบไอเทมให้");
                    return true;
                }

                String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()) : item.getType().name();

                for(Player recipient : recipients) {
                    ItemStack toGive = item.clone();
                    var leftover = recipient.getInventory().addItem(toGive);
                    if(!leftover.isEmpty()) {
                        leftover.values().forEach(drop -> recipient.getWorld().dropItemNaturally(recipient.getLocation(), drop));
                    }
                    recipient.updateInventory();
                    recipient.playSound(recipient.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                }

                if(allPlayers) {
                    plugin.message(sender, "มอบ " + itemName + " x" + count + " ให้ผู้เล่นทุกคน (" + recipients.size() + " คน) แล้ว");
                } else {
                    plugin.message(sender, "มอบ " + itemName + " x" + count + " ให้ " + recipients.get(0).getName() + " แล้ว");
                }
                return true;
            }
            case "pregen" -> pregen(sender,args);
            case "reload" -> {plugin.reloadConfig();plugin.message(sender,"โหลดการตั้งค่าแล้ว · ตำแหน่งวิหารคงเดิมตาม world-layout.yml");}
            case "status" -> plugin.message(sender,"Evergarden 3.0 · "+plugin.world().getName()+" · การต่อสู้ "+plugin.dungeons().activeCount()+" · มอน "+plugin.dungeons().mobCount()+" · pregen "+generated+"/"+total);
            default -> {
                plugin.message(sender,"Evergarden 3.0 · พิมพ์ /evergarden guide เพื่อดูคู่มือมิติ");
                plugin.message(sender,"สร้างกรอบประตู Block of Quartz (ขนาด 4x5) แล้วโยนดอกไม้เข้าไปในช่องว่างเพื่อเปิดประตู");
                if(isAdmin(sender))plugin.message(sender,"แอดมิน: enter · leave · tp [dark|astral|time|spawn] · menu · test · crops · wands · relics · give · status · pregen · reload");
            }
        }
        return true;
    }
    private void pregen(CommandSender sender,String[] args) {
        if(args.length>1&&args[1].equalsIgnoreCase("stop")){generating=false;plugin.message(sender,"หยุดสร้างล่วงหน้าแล้ว");return;}
        if(generating){plugin.message(sender,"กำลังสร้าง "+generated+"/"+total);return;}
        try{radius=Math.max(1,Math.min(96,Integer.parseInt(args[1])));}catch(RuntimeException e){plugin.message(sender,"/evergarden pregen <รัศมี chunks 1–96> หรือ stop");return;}
        total=(radius*2+1)*(radius*2+1);generated=0;cursor=0;generating=true;
        plugin.message(sender,"สร้างล่วงหน้า "+total+" chunks ทีละ chunk · /evergarden status");
        new org.bukkit.scheduler.BukkitRunnable(){public void run(){
            if(!generating){cancel();return;}if(inFlight)return;
            if(cursor>=total){generating=false;plugin.message(sender,"สร้างเสร็จ "+generated+" chunks");cancel();return;}
            int n=cursor++,side=radius*2+1,x=n/side-radius,z=n%side-radius;inFlight=true;
            plugin.world().getChunkAtAsync(x,z,true).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(plugin,()->{
                inFlight=false;if(error!=null){generating=false;plugin.getLogger().warning("Pregeneration stopped: "+error.getMessage());return;}
                generated++;plugin.world().unloadChunkRequest(x,z);
            }));
        }}.runTaskTimer(plugin,1,2);
    }
    private Player findOnlinePlayer(String name) {
        if(name == null || name.isBlank()) return null;
        Player pl = Bukkit.getPlayerExact(name);
        if(pl != null) return pl;
        pl = Bukkit.getPlayer(name);
        if(pl != null) return pl;
        String clean = name.toLowerCase(Locale.ROOT).replace(".", "").trim();
        for(Player online : Bukkit.getOnlinePlayers()) {
            String onClean = online.getName().toLowerCase(Locale.ROOT).replace(".", "").trim();
            if(onClean.equals(clean) || online.getName().equalsIgnoreCase("." + name) || online.getName().equalsIgnoreCase(name)) {
                return online;
            }
        }
        return null;
    }

    private ItemStack resolveItem(String raw, int count) {
        if(raw == null || raw.isBlank()) return null;
        String clean = raw.toLowerCase(Locale.ROOT).trim().replace("-", "_");

        // 0. Shulker / Levitation / Mythic Core shortcuts
        if(clean.equals("shulker") || clean.equals("levitation") || clean.equals("shulker_levitation") ||
           clean.equals("shulker_core") || clean.equals("levitation_core") || clean.equals("mythic_core") ||
           clean.equals("mythic") || clean.equals("core_shulker_levitation") || clean.equals("wand_shulker_levitation")) {
            ItemStack is = plugin.relics().createMagicCore("shulker_levitation");
            if(count > 1) is.setAmount(Math.min(count, 64));
            return is;
        }

        // 0. Guide books
        if (clean.equals("guide_crops") || clean.equals("crop_guide") || clean.equals("guide_crop") || clean.equals("book_crops")) {
            return plugin.relics().createCropGuideBook();
        }
        if (clean.equals("guide_relics") || clean.equals("relic_guide") || clean.equals("guide_relic") || clean.equals("book_relics")) {
            return plugin.relics().createRelicGuideBook();
        }
        if (clean.equals("guide_magic") || clean.equals("magic_guide") || clean.equals("book_magic")) {
            return plugin.relics().createMagicGuideBook();
        }
        if (clean.equals("guide") || clean.equals("guidebook") || clean.equals("guide_book")) {
            return plugin.relics().createGuideBook();
        }

        // 1. Scroll of Eternity (Unbreakable)
        if(clean.equals("scroll_eternity") || clean.equals("eternity") || clean.equals("unbreakable") || clean.equals("scroll_of_eternity")) {
            ItemStack is = plugin.relics().createScrollEternity();
            if(count > 1) is.setAmount(Math.min(count, 64));
            return is;
        }

        // 2. Magic Cores with prefix (core_ or wand_)
        if(clean.startsWith("core_") || clean.startsWith("core") || clean.startsWith("wand_")) {
            String coreId = clean;
            if(coreId.startsWith("core_")) coreId = coreId.substring(5);
            else if(coreId.startsWith("wand_")) coreId = coreId.substring(5);
            else if(coreId.startsWith("core")) coreId = coreId.substring(4);
            if(coreId.startsWith("_")) coreId = coreId.substring(1);
            if(!coreId.isEmpty()) {
                ItemStack is = plugin.relics().createMagicCore(coreId);
                if(count > 1) is.setAmount(Math.min(count, 64));
                return is;
            }
        }

        // 3. Limit Break with prefix (lb_ or limit_break_)
        if(clean.startsWith("lb_") || clean.startsWith("limit_break_")) {
            String lbName = clean.replace("limit_break_", "").replace("lb_", "").replace("_", "");
            for(var lb : LimitBreakType.values()) {
                if(lb.name().replace("_", "").equalsIgnoreCase(lbName)) {
                    ItemStack is = plugin.relics().createScrollLimitBreak(lb);
                    if(count > 1) is.setAmount(Math.min(count, 64));
                    return is;
                }
            }
        }

        // 4. Unique Enchant with prefix (ue_ or unique_)
        if(clean.startsWith("ue_") || clean.startsWith("unique_")) {
            String ueName = clean.replace("unique_", "").replace("ue_", "").replace("_", "");
            for(var ue : UniqueEnchant.values()) {
                if(ue.name().replace("_", "").equalsIgnoreCase(ueName) || ue.id().replace("_", "").equalsIgnoreCase(ueName)) {
                    ItemStack is = plugin.relics().createScrollUnique(ue);
                    if(count > 1) is.setAmount(Math.min(count, 64));
                    return is;
                }
            }
        }

        // 5. Unique Enchants DIRECT name (e.g. "ricochet", "colossus_slayer", "absolute_zero", etc.)
        for(var ue : UniqueEnchant.values()) {
            if(ue.name().equalsIgnoreCase(clean) || ue.id().equalsIgnoreCase(clean) || ue.name().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                ItemStack is = plugin.relics().createScrollUnique(ue);
                if(count > 1) is.setAmount(Math.min(count, 64));
                return is;
            }
        }

        // 6. Limit Breaks DIRECT name (e.g. "sharpness", "protection", "power", "efficiency", "fortune", "looting")
        for(var lb : LimitBreakType.values()) {
            if(lb.name().equalsIgnoreCase(clean) || lb.name().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                ItemStack is = plugin.relics().createScrollLimitBreak(lb);
                if(count > 1) is.setAmount(Math.min(count, 64));
                return is;
            }
        }

        // 7. Common aliases & shortcuts
        switch(clean) {
            case "key", "void_key", "voidkey" -> { return plugin.relics().createVoidKey(); }
            case "shard", "key_shard", "keyshard" -> { return plugin.relics().createKeyShard(Math.max(1, count)); }
            case "dust", "astral_dust", "astraldust" -> { return plugin.relics().createAstralDust(Math.max(1, count)); }
            case "repair", "repair_stone", "repairstone" -> { return plugin.relics().createRepairStone(Math.max(1, count)); }
            case "elixir", "void_elixir", "voidelixir" -> { return plugin.relics().createVoidElixir(Math.max(1, count)); }
            case "pickaxe", "rift_pickaxe", "riftpickaxe" -> { return plugin.relics().create(Relic.RIFT_PICKAXE, 1); }
            case "smelter", "smelter_pickaxe", "smelterpickaxe" -> { return plugin.relics().create(Relic.SMELTER_PICKAXE, 1); }
            case "blade", "sword", "rift_blade", "riftblade", "rift_sword" -> { return plugin.relics().create(Relic.RIFT_BLADE, 1); }
            case "aegis", "shield", "eternal_aegis", "eternalaegis" -> { return plugin.relics().create(Relic.ETERNAL_AEGIS, 1); }
            case "storm", "storm_bow", "stormbow" -> { return plugin.relics().create(Relic.STORM_BOW, 1); }
            case "nova", "nova_bow", "novabow" -> { return plugin.relics().create(Relic.NOVA_BOW, 1); }
            case "mythic_core", "shulker_core", "levitation_core" -> { return plugin.relics().createMagicCore("shulker_levitation"); }
        }

        // 8. Relic enum match
        for(Relic r : Relic.values()) {
            if(r.name().equalsIgnoreCase(clean) || r.id().equalsIgnoreCase(clean) || r.name().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                if(r == Relic.SCROLL_ETERNITY) {
                    ItemStack is = plugin.relics().createScrollEternity();
                    if(count > 1) is.setAmount(Math.min(count, 64));
                    return is;
                }
                if(r == Relic.SCROLL_LIMIT_BREAK) return plugin.relics().createScrollLimitBreak(LimitBreakType.SHARPNESS);
                if(r == Relic.SCROLL_UNIQUE) return plugin.relics().createScrollUnique(UniqueEnchant.COLOSSUS_SLAYER);
                if(r == Relic.KEY_SHARD) return plugin.relics().createKeyShard(Math.max(1, count));
                if(r == Relic.ASTRAL_DUST) return plugin.relics().createAstralDust(Math.max(1, count));
                if(r == Relic.REPAIR_STONE) return plugin.relics().createRepairStone(Math.max(1, count));
                if(r == Relic.VOID_ELIXIR) return plugin.relics().createVoidElixir(Math.max(1, count));
                return plugin.relics().create(r, 1);
            }
        }

        // 9. Magic Cores DIRECT match (e.g. "lightning_strike", "frost_nova")
        for(var core : RelicService.MAGIC_CORES) {
            if(core.id().equalsIgnoreCase(clean) || core.id().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                ItemStack is = plugin.relics().createMagicCore(core);
                if(count > 1) is.setAmount(Math.min(count, 64));
                return is;
            }
        }

        // 10. Magic Wands (e.g. "lightning_strike_wand", "lightning_wand", "wand_lightning_strike")
        if (clean.contains("wand")) {
            String spellName = clean.replace("wand_", "").replace("_wand", "").replace("wand", "");
            for (var core : RelicService.MAGIC_CORES) {
                if (core.id().equalsIgnoreCase(spellName)
                        || core.id().replace("_", "").equalsIgnoreCase(spellName)
                        || core.wandTitle().replace(" ", "").equalsIgnoreCase(spellName)
                        || core.title().replace(" ", "").equalsIgnoreCase(spellName)) {
                    ItemStack wand = createWandViaAdvanceMagic(core.id());
                    if (wand != null) {
                        if (count > 1) wand.setAmount(Math.min(count, 64));
                        return wand;
                    }
                }
            }
        }

        // 11. Crops and Seeds
        if (clean.startsWith("seed_") || clean.startsWith("seeds_")) {
            String cName = clean.replace("seeds_", "").replace("seed_", "");
            var type = com.example.voidscape.crop.CropType.fromId(cName);
            if (type != null) {
                return plugin.crops().factory().createSeed(type, count);
            }
        }
        if (clean.startsWith("crop_") || clean.startsWith("crops_")) {
            String cName = clean.replace("crops_", "").replace("crop_", "");
            var type = com.example.voidscape.crop.CropType.fromId(cName);
            if (type != null) {
                return plugin.crops().factory().createFood(type, count);
            }
        }
        var cropType = com.example.voidscape.crop.CropType.fromId(clean);
        if (cropType != null) {
            return plugin.crops().factory().createFood(cropType, count);
        }

        return null;
    }

    public static ItemStack createWandViaAdvanceMagic(String spellId) {
        try {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("advance-magic");
            if (p != null) {
                Object wands = p.getClass().getMethod("wands").invoke(p);
                Class<?> spellEnum = Class.forName("com.example.advancemagic.spell.Spell");
                Object spell = spellEnum.getMethod("parse", String.class).invoke(null, spellId);
                if (spell != null) {
                    return (ItemStack) wands.getClass().getMethod("create", spellEnum).invoke(wands, spell);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> c=new ArrayList<>();
        if(args.length==1){
            c.addAll(List.of("help","guide","upgrade"));
            if(isAdmin(sender))c.addAll(List.of("enter","leave","tp","test","menu","crops","wands","magic","relics","items","give","status","reload","pregen","pack"));
        }
        if(args.length==2&&args[0].equalsIgnoreCase("guide")) {
            c.addAll(List.of("1","2","3","crops","relics","magic"));
        }
        if(args.length==2&&args[0].equalsIgnoreCase("tp")&&isAdmin(sender))c.addAll(List.of("dark","astral","time","whale","spawn"));
        if(args.length==2&&args[0].equalsIgnoreCase("give")&&isAdmin(sender)) {
            // Relics & Equipment
            for(Relic r:Relic.values()) c.add(r.id());
            // Unique Enchants (both direct and prefixed)
            for(var ue : UniqueEnchant.values()) {
                c.add(ue.id());
                c.add("ue_"+ue.id());
            }
            // Limit Breaks (both direct and prefixed)
            for(var lb : LimitBreakType.values()) {
                c.add(lb.name().toLowerCase(Locale.ROOT));
                c.add("lb_"+lb.name().toLowerCase(Locale.ROOT));
            }
            // Magic Cores (both direct and prefixed)
            for(var core : RelicService.MAGIC_CORES) {
                c.add(core.id());
                c.add("core_"+core.id());
                c.add(core.id()+"_wand");
                c.add("wand_"+core.id());
            }
            // Crops & Seeds
            for(var crop : com.example.voidscape.crop.CropType.values()) {
                c.add(crop.id);
                c.add("seed_"+crop.id);
                c.add("crop_"+crop.id);
            }
            // Shortcuts
            c.addAll(List.of("smelter","pickaxe","eternity","key","shard","dust","repair","elixir","storm","nova","blade","aegis","shulker_levitation","shulker","wand","crops","seeds","guide","guide_crops","guide_relics","guide_magic"));
        }
        if(args.length==3&&args[0].equalsIgnoreCase("give")&&isAdmin(sender)) {
            c.addAll(List.of("@a","@p","@s","1","2","4","8","16","32","64"));
            for(Player pl : Bukkit.getOnlinePlayers()) {
                c.add(pl.getName());
                if(pl.getName().startsWith(".")) c.add(pl.getName().substring(1));
            }
        }
        if(args.length>=4&&args[0].equalsIgnoreCase("give")&&isAdmin(sender)) {
            c.addAll(List.of("@a","@p","@s"));
            for(Player pl : Bukkit.getOnlinePlayers()) {
                c.add(pl.getName());
                if(pl.getName().startsWith(".")) c.add(pl.getName().substring(1));
            }
        }
        return c.stream().filter(s->s.startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
    }
}
