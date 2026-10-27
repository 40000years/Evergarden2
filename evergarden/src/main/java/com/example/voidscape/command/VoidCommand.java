package com.example.voidscape.command;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.item.RelicService.Relic;
import com.example.voidscape.world.DungeonLayout;
import com.example.voidscape.world.BiomeTerrain;
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
        if(sub.equals("pets")) {
            if(args.length>=2&&args[1].equalsIgnoreCase("wild"))return plugin.wildPets().command(sender,args);
            if(args.length>=2&&Set.of("perf","give","egg","rush").contains(args[1].toLowerCase(Locale.ROOT))&&plugin.pets()!=null)return plugin.pets().command(sender,args);
            return plugin.petPreviews().command(sender,args);
        }
        if((sub.equals("seraph")||sub.equals("shop"))&&plugin.seraphTrader()!=null)return plugin.seraphTrader().command(sender,args);
        if(Set.of("give","pregen","reload","status","pack","test","dev","kit","menu","enter","leave","tp","wands","wand","magic","cores","core","relics","relic","items","item","weapons","weapon","armoury").contains(sub)&&!isAdmin(sender)){
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
            case "biome" -> {
                var terrain=plugin.biomeTerrain();
                if(args.length>2&&args[1].equalsIgnoreCase("locate")){
                    if(!isAdmin(sender)){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
                    var kind=BiomeTerrain.Kind.fromId(args[2].toLowerCase(Locale.ROOT));
                    if(kind==null){plugin.message(sender,"/evergarden biome locate gardens|glacier|ancient-forest|lunar-city");return true;}
                    int x=p!=null&&p.getWorld()==plugin.world()?p.getLocation().getBlockX():0;
                    int z=p!=null&&p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                    var site=terrain.locate(kind,x,z);
                    plugin.message(sender,site==null?"ไม่พบไบโอมใหม่ในระยะค้นหา · พื้นที่เดิมอาจถูกสำรวจแล้ว":
                        kind.title+" · X="+site.x()+" Z="+site.z()+" · /evergarden tp "+kind.id);
                }else if(p!=null&&p.getWorld()==plugin.world()){
                    int x=p.getLocation().getBlockX(),z=p.getLocation().getBlockZ();
                    plugin.message(p,terrain.newWeight(x,z)<.5?"สวน Evergarden เดิม · พื้นที่ก่อนอัปเกรด":terrain.kind(x,z).title);
                    if(isAdmin(sender))plugin.message(p,"/evergarden biome locate gardens|glacier|ancient-forest|lunar-city");
                }else plugin.message(sender,"/evergarden biome locate gardens|glacier|ancient-forest|lunar-city");
            }
            case "protection" -> {
                if(p==null){plugin.message(sender,"ใช้คำสั่งนี้ในเกม แล้วมองไปที่บล็อกที่ต้องการตรวจสอบ");return true;}
                var block=p.getTargetBlockExact(8);
                if(block==null){plugin.message(p,"มองไปที่บล็อกในระยะ 8 บล็อกก่อนตรวจสอบ");return true;}
                boolean protectedBlock=plugin.structureProtection().protects(block)
                    ||plugin.dungeons().isSpawnIsland(block)
                    ||block.getWorld()==plugin.world()&&block.getY()>=94&&block.getY()<=140&&plugin.layout().at(block.getX(),block.getZ(),0)!=null;
                plugin.message(p,"Evergarden "+plugin.getPluginMeta().getVersion()+" · "+block.getWorld().getName()+" "+block.getX()+","+block.getY()+","+block.getZ());
                plugin.message(p,(protectedBlock?"พื้นที่ได้รับการป้องกัน":"ไม่พบเขตป้องกัน")+" · "+p.getGameMode()+" · ข้ามการป้องกัน: "+plugin.dungeons().canBypassProtection(p));
            }
            case "boss" -> {
                if(args.length>1&&args[1].equalsIgnoreCase("pack")){
                    if(args.length>2&&args[2].equalsIgnoreCase("resend")&&p!=null)plugin.judgePack().resend(p);
                    plugin.judgePack().describe(sender);return true;
                }
                if(p==null){plugin.message(sender,"ใช้คำสั่งนี้ในเกมภายในวิหารใหญ่");return true;}
                String action=args.length>1?args[1].toLowerCase(Locale.ROOT):"status";
                if(action.equals("status"))plugin.message(p,plugin.worldBoss().status(p));
                else if(action.equals("claim"))plugin.worldBoss().claimReward(p);
                else if(!isAdmin(sender))plugin.message(p,"อัญเชิญด้วยมือเปล่าคลิกขวา Amethyst กลางลาน · คำสั่งควบคุมสำหรับแอดมิน");
                else if(action.equals("start"))plugin.worldBoss().start(p,true);
                else if(action.equals("stop"))plugin.worldBoss().stop(p);
                else if(action.equals("model")){
                    // Main-boss showcase rigs (Arkhaia / Seraphon): spawn, pose program, clear.
                    String what=args.length>2?args[2].toLowerCase(Locale.ROOT):"menu";
                    switch(what){
                        case "arkhaia"->plugin.worldBoss().spawnArkhaiaPreview(p);
                        case "seraphon"->plugin.worldBoss().spawnSeraphonPreview(p);
                        case "both"->plugin.worldBoss().spawnBossShowcase(p);
                        case "clear"->plugin.worldBoss().removeFactionPreviews(p);
                        case "pose"->{
                            if(args.length>3)plugin.worldBoss().bossShowcaseMode(p,args[3].toLowerCase(Locale.ROOT));
                            else plugin.message(p,"/evergarden boss model pose "+String.join("|",com.example.voidscape.boss.WorldBossManager.bossShowcaseModes()));
                        }
                        default->plugin.testGui().openMainBosses(p);
                    }
                }
                else plugin.message(p,"/evergarden boss status|claim|start|stop|model");
            }
            case "upgrade", "up", "enchant", "forge" -> {
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                com.example.voidscape.gui.UpgradeMenuService.open(plugin, p);
            }
            case "test", "admin", "menu" -> {
                if(!isAdmin(sender)){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                plugin.testGui().open(p);
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
            case "weapons", "weapon", "armoury" -> {
                if(!isAdmin(sender)){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                if(plugin.adventurerWeapons() != null) plugin.adventurerWeapons().openGui(p);
            }
            case "pack" -> {
                if(args.length>1&&args[1].equalsIgnoreCase("resend")) {
                    Player target=args.length>2?Bukkit.getPlayerExact(args[2]):p;
                    if(target==null) {sender.sendMessage("Usage: /evergarden pack resend <online-player>");return true;}
                    plugin.packs().resend(target);
                    plugin.judgePack().resend(target);
                }
                plugin.packs().describe(sender);
                plugin.judgePack().describe(sender);
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
                    var biome=BiomeTerrain.Kind.fromId(dest);
                    if(biome!=null){teleportBiome(p,biome);return true;}
                    var small=com.example.voidscape.world.ExplorationLayout.Kind.fromId(dest);
                    if(small!=null){
                        int x=p.getWorld()==plugin.world()?p.getLocation().getBlockX():0;
                        int z=p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                        var site=plugin.exploration().nearest(small,x,z,48,true);
                        if(site==null&&small.medium())site=plugin.exploration().nearest(small,x,z,96,true);
                        if(site==null){plugin.message(p,"ไม่พบสถานที่สำรวจในพื้นที่ใหม่ · ตรวจ structures.exploration.enabled");return true;}
                        int[] target=site.transform(0,site.entryZ());int y=site.floorY()+1;
                        var arrival=new Location(plugin.world(),target[0]+.5,y,target[1]+.5);
                        if(!plugin.world().getWorldBorder().isInside(arrival)){plugin.message(p,"สถานที่นี้อยู่นอกขอบโลก");return true;}
                        plugin.world().getChunkAtAsync(Math.floorDiv(target[0],16),Math.floorDiv(target[1],16),true).whenComplete((chunk,error)->
                            Bukkit.getScheduler().runTask(plugin,()->{
                                if(!p.isOnline())return;
                                if(error!=null||!plugin.world().getBlockAt(target[0],y-1,target[1]).getType().isSolid()
                                    ||!plugin.world().getBlockAt(target[0],y,target[1]).isPassable()
                                    ||!plugin.world().getBlockAt(target[0],y+1,target[1]).isPassable()){
                                    plugin.message(p,"ทางเข้าถูกเปลี่ยนหรือโหลดไม่สำเร็จ");return;
                                }
                                p.teleport(arrival);plugin.message(p,small.title+" · X="+arrival.getBlockX()+" Z="+arrival.getBlockZ());
                            }));return true;
                    }
                    var bossKind=com.example.voidscape.world.BossStructureLayout.Kind.fromId(dest);
                    if(bossKind!=null){
                        if(!plugin.bossStructures().enabled(bossKind)||!plugin.getConfig().getBoolean("structures.boss-structures."+bossKind.id+".enabled",true)){
                            plugin.message(p,"Structure นี้ถูกปิดใน config");return true;
                        }
                        int x=p.getWorld()==plugin.world()?p.getLocation().getBlockX():0;
                        int z=p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                        var site=plugin.bossStructures().nearest(bossKind,x,z,32,true);
                        if(site==null){plugin.message(p,"ไม่พบ structure ในระยะค้นหา · ลองสำรวจพื้นที่ใหม่");return true;}
                        int tx=site.x(),tz=site.z()+bossKind.arrivalZ,y=bossKind.floorY+1;
                        var expected=site.blueprint().at(0,y-1,bossKind.arrivalZ);
                        if(plugin.world().getBlockAt(tx,y-1,tz).getType()!=expected
                                ||!plugin.world().getBlockAt(tx,y,tz).getType().isAir()
                                ||!plugin.world().getBlockAt(tx,y+1,tz).getType().isAir()){
                            plugin.message(p,"จุดนี้ยังไม่มีทางเข้าที่ปลอดภัย · กรุณาหา structure ในพื้นที่ใหม่");return true;
                        }
                        p.teleport(new Location(plugin.world(),tx+.5,y,tz+.5,180,0));
                        plugin.message(p,bossKind.title+" · X="+site.x()+" Z="+site.z());return true;
                    }
                    if(dest.equals("spawn")) {
                        p.teleport(new Location(plugin.world(),0.5,97.0,0.5));
                        plugin.message(p,"วาร์ปมายังจุดเกิดเกาะกลางมิติ (Y=97)");
                        return true;
                    }
                    if(dest.equals("observatory")||dest.equals("garden")||dest.equals("hanging-garden")) {
                        var kind=dest.equals("observatory")?com.example.voidscape.world.SkyLandmarkLayout.Kind.OBSERVATORY:
                                com.example.voidscape.world.SkyLandmarkLayout.Kind.HANGING_GARDEN;
                        if(!plugin.getConfig().getBoolean("structures."+kind.id+".enabled",true)){
                            plugin.message(p,"This landmark is disabled in config.");return true;
                        }
                        int x=p.getWorld()==plugin.world()?p.getLocation().getBlockX():0;
                        int z=p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                        var site=plugin.skyLandmarks().nearest(kind,x,z,12);
                        if(site==null){plugin.message(p,"No landmark found in the search radius.");return true;}
                        int tx=site.x()+kind.arrivalX,tz=site.z()+kind.arrivalZ;
                        Material expectedFloor=kind.blueprint().at(kind.arrivalX,kind.arrivalY-1,kind.arrivalZ);
                        if(plugin.world().getBlockAt(tx,kind.arrivalY-1,tz).getType()!=expectedFloor
                                ||!plugin.world().getBlockAt(tx,kind.arrivalY,tz).getType().isAir()
                                ||!plugin.world().getBlockAt(tx,kind.arrivalY+1,tz).getType().isAir()){
                            plugin.message(p,"This site is in previously generated terrain. Explore fresh chunks to find the new landmark.");return true;
                        }
                        p.teleport(new Location(plugin.world(),tx+.5,kind.arrivalY,tz+.5));
                        plugin.message(p,kind.id+" X="+site.x()+" Z="+site.z());return true;
                    }
                    if(dest.equals("boss-temple")||dest.equals("world-boss-temple")){
                        if(!plugin.getConfig().getBoolean("structures.world-boss-temple.enabled",true)){
                            plugin.message(p,"World boss temples are disabled in config.");return true;
                        }
                        int x=p.getWorld()==plugin.world()?p.getLocation().getBlockX():0;
                        int z=p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                        var site=plugin.bossTemples().nearest(x,z,64);
                        if(site==null){plugin.message(p,"No world boss temple found in the search radius.");return true;}
                        int y=com.example.voidscape.world.WorldBossTemple.ARRIVAL_Y;
                        int tx=site.x(),tz=site.z()+com.example.voidscape.world.WorldBossTemple.ARRIVAL_Z;
                        if(plugin.world().getBlockAt(tx,y-1,tz).getType()!=Material.POLISHED_ANDESITE
                            ||!plugin.world().getBlockAt(tx,y,tz).getType().isAir()||!plugin.world().getBlockAt(tx,y+1,tz).getType().isAir()){
                            plugin.message(p,"This temple has no safe arrival; explore fresh chunks or restore the entrance.");return true;
                        }
                        p.teleport(new Location(plugin.world(),tx+.5,y,tz+.5,180,0));
                        plugin.message(p,"วิหารโบราณ World Boss · X="+site.x()+" Z="+site.z()+" · ใช้มือเปล่าคลิกขวา Amethyst กลางลานเพื่ออัญเชิญ");return true;
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
                    int targetTier=0;
                    if(args.length>2){try{targetTier=Math.clamp(Integer.parseInt(args[2]),1,5);}catch(NumberFormatException ignored){}}
                    int x=p.getWorld()==plugin.world()?p.getLocation().getBlockX():0,z=p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                    var s=plugin.layout().locate(x,z,kind,targetTier,targetTier>0?32:12);
                    if(s==null){plugin.message(sender,"ไม่พบสิ่งก่อสร้างในระยะค้นหา");return true;}
                    p.teleport(new Location(plugin.world(),s.x()+0.5,97,s.z()+8.5));
                    plugin.message(p,"วาร์ปไปยัง "+s.kind().displayName+" "+s.tierColorCode()+"[Tier "+s.romanTier()+"] §fพิกัด X="+s.x()+" Y=97 Z="+(s.z()+8));
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

                if ((itemQuery.equalsIgnoreCase("all_weapons") || itemQuery.equalsIgnoreCase("adventurer_weapons")) && plugin.adventurerWeapons() != null) {
                    List<Player> recipients = new ArrayList<>();
                    if(allPlayers) recipients.addAll(Bukkit.getOnlinePlayers());
                    else if(singleTarget != null) recipients.add(singleTarget);
                    else if(p != null) recipients.add(p);
                    if(recipients.isEmpty()) {
                        plugin.message(sender, "ไม่พบผู้เล่นออนไลน์ที่จะมอบไอเทมให้");
                        return true;
                    }
                    for (Player recipient : recipients) {
                        for (var w : com.example.voidscape.item.AdventurerWeaponService.AdventurerWeapon.values()) {
                            var leftover = recipient.getInventory().addItem(plugin.adventurerWeapons().create(w));
                            if (!leftover.isEmpty()) {
                                leftover.values().forEach(drop -> recipient.getWorld().dropItemNaturally(recipient.getLocation(), drop));
                            }
                        }
                        recipient.updateInventory();
                        recipient.playSound(recipient.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    }
                    plugin.message(sender, "มอบอาวุธนักผจญภัยครบทั้ง 20 ชิ้นแล้ว");
                    return true;
                }

                ItemStack item = resolveItem(itemQuery, count);
                if(item == null) {
                    item = resolveItem(itemQuery.replace(" ", "_"), count);
                }
                if(item == null) {
                    plugin.message(sender, "ไม่พบไอเทม: " + itemQuery + " (ลองพิมพ์ชื่อตรงๆ เช่น ricochet, sharpness, eternity, shulker_levitation)");
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
            case "reload" -> {plugin.reloadConfig();plugin.petPreviews().reload();plugin.pets().reload();if(plugin.seraphTrader()!=null)plugin.seraphTrader().reload();plugin.message(sender,"โหลดการตั้งค่าแล้ว · ตำแหน่งวิหารคงเดิมตาม world-layout.yml");}
            case "status" -> plugin.message(sender,"Evergarden 3.0 · "+plugin.world().getName()+" · การต่อสู้ "+plugin.dungeons().activeCount()+" · มอน "+plugin.dungeons().mobCount()+" · pregen "+generated+"/"+total);
            default -> {
                plugin.message(sender,"Evergarden 3.0 · พิมพ์ /evergarden guide เพื่อดูคู่มือมิติ");
                plugin.message(sender,"สร้างกรอบประตู Block of Quartz (ขนาด 4x5) แล้วโยนดอกไม้เข้าไปในช่องว่างเพื่อเปิดประตู");
                plugin.message(sender,"/evergarden biome · ดูชื่อดินแดนที่กำลังสำรวจ");
                if(isAdmin(sender))plugin.message(sender,"แอดมิน: enter · leave · tp [dark|astral|time|whale|observatory|garden|boss-temple|arkhaia|valkyrie|seraphon|spawn] · menu · test · pets · wands · relics · give · status · pregen · reload");
                if(isAdmin(sender))plugin.message(sender,"ไบโอมใหม่: /evergarden tp gardens|glacier|ancient-forest|lunar-city · /evergarden biome locate <ชื่อไบโอม>");
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

        if(clean.startsWith("staff_")){
            var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
            var element=com.example.advancemagic.item.ElementalStaffService.Element.parse(clean.substring(6));
            if(element!=null&&addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic&&magic.isEnabled())return magic.staffs().create(element);
            return null;
        }
        // 0. Guide books
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
            case "key_t1", "void_key_1", "key1" -> { return plugin.relics().createVoidKey(1); }
            case "key_t2", "void_key_2", "key2" -> { return plugin.relics().createVoidKey(2); }
            case "key_t3", "void_key_3", "key3" -> { return plugin.relics().createVoidKey(3); }
            case "key_t4", "void_key_4", "key4" -> { return plugin.relics().createVoidKey(4); }
            case "key_t5", "void_key_5", "key5" -> { return plugin.relics().createVoidKey(5); }
            case "pickaxe", "rift_pickaxe", "riftpickaxe" -> { return plugin.relics().create(Relic.RIFT_PICKAXE, 1); }
            case "smelter", "smelter_pickaxe", "smelterpickaxe" -> { return plugin.relics().create(Relic.SMELTER_PICKAXE, 1); }
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
                return plugin.relics().create(r, 1);
            }
        }

        // 9. Adventurer Weapons (20 Sanctum Weapons)
        if (plugin.adventurerWeapons() != null) {
            var weapon = com.example.voidscape.item.AdventurerWeaponService.AdventurerWeapon.parse(clean);
            if (weapon != null) {
                return plugin.adventurerWeapons().create(weapon);
            }
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

    private void teleportBiome(Player player,BiomeTerrain.Kind kind){
        int x=player.getWorld()==plugin.world()?player.getLocation().getBlockX():0;
        int z=player.getWorld()==plugin.world()?player.getLocation().getBlockZ():0;
        var site=plugin.biomeTerrain().locate(kind,x,z);
        if(site==null){plugin.message(player,"ไม่พบไบโอมใหม่ในระยะค้นหา · ลองค้นหาจากพิกัดที่ไกลออกไป");return;}
        plugin.message(player,"กำลังเตรียมจุดสำรวจ · "+kind.title);
        plugin.world().getChunkAtAsync(Math.floorDiv(site.x(),16),Math.floorDiv(site.z(),16),true).whenComplete((chunk,error)->
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!player.isOnline())return;
                if(error!=null){plugin.message(player,"โหลดพื้นที่ไม่สำเร็จ · ลองอีกครั้ง");return;}
                for(int radius=0;radius<=7;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                    int px=site.x()+dx,pz=site.z()+dz;
                    if(Math.floorDiv(px,16)!=chunk.getX()||Math.floorDiv(pz,16)!=chunk.getZ())continue;
                    int y=plugin.world().getHighestBlockYAt(px,pz);
                    var floor=plugin.world().getBlockAt(px,y,pz);
                    if(y>=plugin.world().getMaxHeight()-2||!floor.getType().isSolid()||floor.isLiquid())continue;
                    if(!plugin.world().getBlockAt(px,y+1,pz).isPassable()||!plugin.world().getBlockAt(px,y+2,pz).isPassable())continue;
                    player.teleport(new Location(plugin.world(),px+.5,y+1,pz+.5));
                    plugin.message(player,kind.title+" · X="+px+" Z="+pz);return;
                }
                plugin.message(player,"ไม่พบจุดยืนที่ปลอดภัย · /evergarden biome locate "+kind.id);
            }));
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> c=new ArrayList<>();
        if(args.length==1){
            c.addAll(List.of("help","guide","upgrade","boss","protection","biome","shop"));
            if(isAdmin(sender))c.addAll(List.of("enter","leave","tp","test","menu","wands","magic","relics","items","weapons","give","status","reload","pregen","pack","pets","seraph"));
        }
        if(args.length==2&&args[0].equalsIgnoreCase("seraph")&&isAdmin(sender))c.addAll(List.of("status","here","reset","respawn"));
        if(args.length>=2&&args[0].equalsIgnoreCase("pets")) {
            if(args[1].equalsIgnoreCase("wild"))return plugin.wildPets().complete(sender,args);
            if(isAdmin(sender)&&Set.of("give","egg","rush").contains(args[1].toLowerCase(Locale.ROOT))&&plugin.pets()!=null)return plugin.pets().complete(args);
            var pets=new ArrayList<>(plugin.petPreviews().complete(sender,args));
            if(args.length==2&&isAdmin(sender))for(String extra:List.of("wild","perf","give","egg","rush"))if(extra.startsWith(args[1].toLowerCase(Locale.ROOT)))pets.add(extra);
            return pets;
        }
        if(args.length==2&&args[0].equalsIgnoreCase("biome")&&isAdmin(sender))c.add("locate");
        if(isAdmin(sender)&&(args.length==2&&args[0].equalsIgnoreCase("tp")
                ||args.length==3&&args[0].equalsIgnoreCase("biome")&&args[1].equalsIgnoreCase("locate")))
            for(var kind:BiomeTerrain.Kind.values())c.add(kind.id);
        if(args.length==2&&args[0].equalsIgnoreCase("guide")) {
            c.addAll(List.of("2","3","relics","magic"));
        }
        if(args.length==2&&args[0].equalsIgnoreCase("boss")){c.addAll(List.of("status","claim","pack"));if(isAdmin(sender))c.addAll(List.of("start","stop","model"));}
        if(args.length==3&&args[0].equalsIgnoreCase("boss")&&args[1].equalsIgnoreCase("model")&&isAdmin(sender))c.addAll(List.of("arkhaia","seraphon","both","clear","pose"));
        if(args.length==4&&args[0].equalsIgnoreCase("boss")&&args[1].equalsIgnoreCase("model")&&args[2].equalsIgnoreCase("pose")&&isAdmin(sender))
            c.addAll(com.example.voidscape.boss.WorldBossManager.bossShowcaseModes());
        if(args.length==3&&args[0].equalsIgnoreCase("boss")&&args[1].equalsIgnoreCase("pack"))c.add("resend");
        if(args.length==2&&args[0].equalsIgnoreCase("tp")&&isAdmin(sender))c.addAll(List.of("dark","astral","time","whale","observatory","garden","boss-temple","arkhaia","valkyrie","seraphon","spawn"));
        if(args.length==3&&args[0].equalsIgnoreCase("tp")&&isAdmin(sender)&&Set.of("dark","astral","time").contains(args[1].toLowerCase(Locale.ROOT)))c.addAll(List.of("1","2","3","4","5"));
        if(args.length==2&&args[0].equalsIgnoreCase("tp")&&isAdmin(sender))
            for(var kind:com.example.voidscape.world.ExplorationLayout.Kind.values())c.add(kind.id);
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
            for(var element:com.example.advancemagic.item.ElementalStaffService.Element.values())c.add("staff_"+element.id());
            for(var w:com.example.voidscape.item.AdventurerWeaponService.AdventurerWeapon.values())c.add(w.id());
            c.addAll(List.of("all_weapons","smelter","pickaxe","eternity","key","guide","guide_relics","guide_magic"));
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
