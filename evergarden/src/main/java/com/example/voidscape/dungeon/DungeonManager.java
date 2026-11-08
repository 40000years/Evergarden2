package com.example.voidscape.dungeon;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.BossDamage;
import com.example.voidscape.dungeon.DungeonBalance.Species;
import com.example.voidscape.world.DungeonLayout;
import com.example.voidscape.world.DungeonLayout.Site;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.ThreadLocalRandom;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class DungeonManager implements Listener {
    private static final class Encounter {
        final Site site; final Map<UUID,Species> mobs=new HashMap<>();
        final Map<UUID,Integer> presence=new HashMap<>();
        final Map<UUID,Long> casterCooldowns=new HashMap<>();
        final Map<UUID,Long> casterVexCooldowns=new HashMap<>();
        int wave=0; boolean bossStarted=false,bossEnraged=false,finished=false;
        long lastPresent=System.currentTimeMillis(),lastSkill=0,warningAt=0;
        long lastLaser=0; boolean laserActive=false;
        long lastSonicBoom=0; // cooldown tracker สำหรับ Boss Elemental Sonic Blast
        long lastHazard=0;    // cooldown tracker สำหรับ Tier 5 Elemental Arena Hazard
        boolean warnedAway;   // the "back to the arena or it resets" warning has been shown
        Location warning; final List<Location> extraWarnings=new ArrayList<>(); BossBar bar;
        final int totalWaves;
        // Tier VI-VIII: two bosses from VII, ground seals, VIII's sanctuary skills, unique (Forge elite) mobs.
        final List<UUID> bosses=new ArrayList<>();
        final Map<UUID,Long> nextSeal=new HashMap<>(),nextSpecial=new HashMap<>(),berserkUntil=new HashMap<>();
        final List<Seal> seals=new ArrayList<>();
        final Set<UUID> uniques=new HashSet<>();
        int sealSerial;
        Encounter(Site site,int totalWaves){this.site=site;this.totalWaves=totalWaves;}
    }
    /** A ground seal waiting to burst: 0 = the tier seal, 1 = Acheron's binding circle. */
    private record Seal(String key,String model,Location at,double radius,long born,long burstAt,UUID boss,int kind){}
    private record Root(long until,String site,Location at){}
    private final VoidscapePlugin plugin;
    private final Map<String,Encounter> active=new LinkedHashMap<>();
    private final com.example.voidscape.boss.SanctumSigils sigils;
    private final com.example.voidscape.world.SanctumExpansion expansion;
    private final Map<UUID,Root> roots=new HashMap<>();
    private final Map<UUID,Long> resistHint=new HashMap<>();
    /** Seal damage in flight: the exact amount, so difficulty scaling never changes "a quarter of maximum health". */
    private final Map<UUID,Double> exactSeal=new HashMap<>();
    private final NamespacedKey hpScaleKey,uniqueColorKey,volleyKey,rootKey;
    private int expansionTicks;
    private final Map<UUID,Encounter> owners=new HashMap<>();
    private final Map<UUID,Long> combatUntil=new HashMap<>();
    private final Map<UUID,Double> scriptedImpacts=new HashMap<>();
    private final Map<UUID,String> seen=new HashMap<>();
    private final Map<Block,Long> tempWebs=new HashMap<>();
    private final Map<UUID,Long> webCooldowns=new HashMap<>();
    private final Map<UUID,BossBar> trueDeathBars=new HashMap<>();
    private final NamespacedKey mobKey,runKey,trueDeathHitsKey,trueDeathLevelKey,mobTargetHpKey,mobTargetDmgKey,mobTargetNameKey;
    private final String runId=UUID.randomUUID().toString();
    private final YamlConfiguration ledger;
    private final File file;
    private boolean storageHealthy=true;

    public DungeonManager(VoidscapePlugin plugin)throws IOException {
        this.plugin=plugin;mobKey=plugin.key("dungeon_mob");runKey=plugin.key("runtime");
        trueDeathHitsKey=plugin.key("true_death_hits");trueDeathLevelKey=plugin.key("true_death_level");
        mobTargetHpKey=plugin.key("target_hp");mobTargetDmgKey=plugin.key("target_dmg");mobTargetNameKey=plugin.key("target_name");
        hpScaleKey=plugin.key("sanctum_hp_scale");uniqueColorKey=plugin.key("sanctum_unique_color");
        volleyKey=plugin.key("sanctum_volley");rootKey=plugin.key("sanctum_root");
        sigils=new com.example.voidscape.boss.SanctumSigils(plugin);
        expansion=new com.example.voidscape.world.SanctumExpansion(plugin,plugin.layout());
        plugin.layout().expand(expansion);
        Bukkit.getScheduler().runTaskTimer(plugin,this::sealTick,40,2);
        file=new File(plugin.getDataFolder(),"dungeons.yml");
        ledger=new YamlConfiguration();
        if(file.exists())try{ledger.load(file);}catch(Exception e){throw new IOException("Cannot safely read reward ledger",e);}
        for(Entity e:plugin.world().getEntities())if(e.getPersistentDataContainer().has(mobKey))e.remove();
    }

    private String path(Site s){return "sites."+s.id();}

    private boolean save() {
        try {
            Path dest=file.toPath(),tmp=dest.resolveSibling("dungeons.yml.tmp");
            if(dest.getParent()!=null)Files.createDirectories(dest.getParent());
            Files.writeString(tmp,ledger.saveToString());
            try{Files.move(tmp,dest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException e){storageHealthy=false;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Reward storage failed",e);return false;}
    }

    public boolean inCombat(Player p){return combatUntil.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis();}
    public int mobCount(){return owners.size();} public int activeCount(){return active.size();}
    private boolean playable(Player p){return p.isOnline()&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR;}

    private List<Player> players(Site site) {
        List<Player> result=new ArrayList<>();
        for(Player p:plugin.world().getPlayers())if(playable(p)&&p.getY()>60&&p.getY()<170&&site.contains(p.getX(),p.getZ(),12))result.add(p);
        return result;
    }

    private Location position(Site s,int x,int y,int z){return new Location(plugin.world(),s.x()+x+0.5,y,s.z()+z+0.5);}

    /**
     * Inside the combat ring (radius 21.5 around the floor at Y 97): anywhere from the floor up to 48 blocks,
     * so wind-charge and mace jumps stay in the fight, but not standing on pillars or arches above Y 102.5
     * and not under the floor. Only players inside may hurt the encounter or keep it running.
     */
    private boolean inArena(Site s,Player p){
        if(p.getWorld()!=plugin.world())return false;
        double r=Math.hypot(p.getX()-(s.x()+0.5),p.getZ()-(s.z()+0.5));
        if(r>21.5||p.getY()<95.5||p.getY()>97+48)return false;
        return !(p.getY()>102.5&&p.getLocation().subtract(0,.2,0).getBlock().getType().isSolid());
    }

    private NamedTextColor tierNamedColor(int tier) {
        return switch (Math.clamp(tier, 1, DungeonLayout.MAX_TIER)) {
            case 1 -> NamedTextColor.GREEN;
            case 2 -> NamedTextColor.AQUA;
            case 3 -> NamedTextColor.YELLOW;
            case 4 -> NamedTextColor.GOLD;
            case 5 -> NamedTextColor.RED;
            case 6 -> NamedTextColor.BLUE;
            case 7 -> NamedTextColor.DARK_PURPLE;
            default -> NamedTextColor.DARK_RED;
        };
    }

    private BarColor tierBarColor(int tier) {
        return switch (Math.clamp(tier, 1, DungeonLayout.MAX_TIER)) {
            case 1 -> BarColor.GREEN;
            case 2, 6 -> BarColor.BLUE;
            case 3 -> BarColor.YELLOW;
            case 4, 7 -> BarColor.PURPLE;
            default -> BarColor.RED;
        };
    }

    private void placeTemporaryWeb(Block block, long durationMs) {
        if(block==null||block.getWorld()!=plugin.world())return;
        if(block.getType()!=Material.AIR&&block.getType()!=Material.CAVE_AIR) {
            Block above=block.getRelative(0,1,0);
            if(above.getType()==Material.AIR||above.getType()!=Material.CAVE_AIR) {
                if(above.getType()==Material.AIR) block=above;
                else return;
            } else {
                return;
            }
        }
        block.setType(Material.COBWEB,false);
        tempWebs.put(block,System.currentTimeMillis()+durationMs);
        block.getWorld().playSound(block.getLocation().add(0.5,0.5,0.5),Sound.ENTITY_SPIDER_AMBIENT,0.8f,1.2f);
        block.getWorld().spawnParticle(Particle.CLOUD,block.getLocation().add(0.5,0.5,0.5),8,0.2,0.2,0.2,0.02);
    }

    private void maybePlaceWeb(Player player, long durationMs) {
        long now=System.currentTimeMillis();
        long cooldown=plugin.integer("combat.cobweb-cooldown-ms",8000,1000,60000);
        if(webCooldowns.getOrDefault(player.getUniqueId(),0L)>now)return;
        double chance=Math.max(0.0,Math.min(1.0,plugin.getConfig().getDouble("combat.cobweb-chance",0.05)));
        if(Math.random()>=chance)return;
        webCooldowns.put(player.getUniqueId(),now+cooldown);
        placeTemporaryWeb(player.getLocation().getBlock(),durationMs);
    }

    private void clearWebs(Site site) {
        Iterator<Map.Entry<Block,Long>> it=tempWebs.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<Block,Long> entry=it.next();
            Block b=entry.getKey();
            if(site==null||site.contains(b.getX(),b.getZ(),16)) {
                if(b.getType()==Material.COBWEB) {
                    b.setType(Material.AIR,false);
                }
                it.remove();
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void interact(PlayerInteractEvent e) {
        Player p=e.getPlayer();
        if(p.getWorld()!=plugin.world())return;
        if(p.getGameMode()==GameMode.SPECTATOR)return;
        if(e.getHand()!=null&&e.getHand()!=EquipmentSlot.HAND)return;

        Block block=e.getClickedBlock();
        if(block==null&&e.getAction().isRightClick()) {
            block=p.getTargetBlockExact(5);
        }
        if(block==null)return;

        // Vault interaction
        if(block.getType()==Material.VAULT) {
            Site site=plugin.layout().at(block.getX(),block.getZ(),12);
            if(site!=null) {
                e.setCancelled(true);
                openVault(p,site,block);
                return;
            }
        }

        // Altar Lodestone interaction at sanctum center
        if(block.getType()==Material.LODESTONE) {
            Site site=plugin.layout().at(block.getX(),block.getZ(),12);
            if(site!=null) {
                if(Math.abs(block.getX()-site.x())<=10 && Math.abs(block.getZ()-(site.z()+8))<=10) {
                    e.setCancelled(true);

                    if(!storageHealthy){plugin.message(p,"ระบบบันทึกไม่พร้อม · แจ้งแอดมิน");return;}
                    long next=ledger.getLong(path(site)+".next-open",0);
                    if(next>System.currentTimeMillis()) {
                        if(p.isOp()||p.hasPermission("voidscape.admin")||p.hasPermission("evergarden.admin")||p.getGameMode()==GameMode.CREATIVE) {
                            plugin.message(p,"§d[Admin Bypass] ข้ามคูลดาวน์วิหารสำหรับผู้ดูแลระบบ");
                        } else {
                            plugin.message(p,"วิหารกำลังฟื้นตัว · อีก "+Math.max(1,(next-System.currentTimeMillis())/60000)+" นาที");
                            return;
                        }
                    }

                    Encounter encounter=active.get(site.id());
                    if(encounter!=null) {
                        plugin.message(p,"การต่อสู้ในวิหารนี้กำลังดำเนินอยู่!");
                        return;
                    }
                    if(active.size()>=plugin.integer("performance.max-active-dungeons",6,1,16)){plugin.message(p,"มีการต่อสู้หลายแห่งในมิติ · ลองใหม่ภายหลัง");return;}
                    if(mobCount()+6>plugin.integer("performance.max-dungeon-mobs",64,4,128)){plugin.message(p,"พลังงานมิติยังไม่คงที่ · ลองใหม่ภายหลัง");return;}

                    p.getWorld().playSound(block.getLocation().add(0.5,0.5,0.5),Sound.BLOCK_BEACON_ACTIVATE,1.0f,1.2f);
                    p.getWorld().spawnParticle(Particle.PORTAL,block.getLocation().add(0.5,1.0,0.5),25,0.3,0.3,0.3,0.1);

                    encounter=new Encounter(site,waveCount(site));
                    active.put(site.id(),encounter);
                    startWave(encounter,1);
                    return;
                }
            }
        }
    }

    private void openVault(Player p,Site site,Block block) {
        if(!storageHealthy){plugin.message(p,"ระบบบันทึก Vault ไม่พร้อม · ยังไม่ใช้กุญแจ กรุณาแจ้งแอดมิน");return;}
        String openedPath=path(site)+".opened."+p.getUniqueId();
        // The production entitlement is once per UUID/site, including admins and Creative players.
        // Explicit admin test menus provide test rolls without reopening real Vaults.
        if(ledger.getBoolean(openedPath,false)) {
            plugin.message(p,"คุณเคยเปิดกล่องสมบัตินี้ไปแล้ว (เปิดได้คนละ 1 ครั้งต่อวิหาร)");
            p.playSound(p.getLocation(),Sound.BLOCK_CHEST_LOCKED,0.7f,1.0f);
            return;
        }

        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        ItemStack keyItem = null;

        if (plugin.relics().isVoidKey(main)) {
            keyItem = main;
        } else if (plugin.relics().isVoidKey(off)) {
            keyItem = off;
        }

        if (keyItem == null) {
            if (main.getType() == Material.TRIAL_KEY || main.getType() == Material.OMINOUS_TRIAL_KEY
                || off.getType() == Material.TRIAL_KEY || off.getType() == Material.OMINOUS_TRIAL_KEY) {
                plugin.message(p,"กุญแจ Trial จากโลกปกติไม่สามารถเปิด Evergarden Vault ได้! ต้องใช้ Evergarden Key จากวิหาร");
            } else {
                plugin.message(p,"ต้องใช้ Evergarden Key [Tier "+site.romanTier()+" ขึ้นไป] ในการเปิดกล่องสมบัตินี้ (เปิดได้คนละ 1 ครั้ง)");
            }
            p.playSound(p.getLocation(),Sound.BLOCK_CHEST_LOCKED,0.7f,1.0f);
            return;
        }

        int keyTier = plugin.relics().getVoidKeyTier(keyItem);
        if (keyTier < site.tier()) {
            plugin.message(p,"⚠ กุญแจของคุณเป็นระดับ [Tier "+roman(keyTier)+"] ไม่สามารถไข Vault ระดับ ["+site.tierColorCode()+"Tier "+site.romanTier()+"§f] ได้! (ต้องใช้กุญแจ Tier "+site.romanTier()+" ขึ้นไป)");
            p.playSound(p.getLocation(),Sound.BLOCK_CHEST_LOCKED,0.8f,0.8f);
            return;
        }

        // Commit the entitlement and selected reward(s) before consuming a key or delivering anything.
        // A failed disk write must never leave a reward that can be claimed again after a restart.
        List<ItemStack> rewards = plugin.relics().rollVaultRewards(site, p);
        ItemStack primary = rewards.getFirst();
        String receiptPath=path(site)+".vault-claims."+p.getUniqueId();
        Object previousOpened=ledger.get(openedPath),previousReceipt=ledger.get(receiptPath);
        List<String> rewardIds = new ArrayList<>(rewards.size());
        for (ItemStack rw : rewards) {
            String coreId=rw.getPersistentDataContainer().get(plugin.key("magic_core"),PersistentDataType.STRING);
            var relic=plugin.relics().type(rw);
            rewardIds.add(coreId!=null?"core:"+coreId:relic!=null?relic.id():rw.getType().getKey().toString());
        }
        String rewardId = String.join(",", rewardIds);
        ledger.set(openedPath,true);
        ledger.createSection(receiptPath,Map.of("player",p.getName(),"at",System.currentTimeMillis(),
                "reward",rewardId,"amount",primary.getAmount(),"tier",site.tier(),"key-tier",keyTier));
        if(!save()) {
            ledger.set(openedPath,previousOpened);
            ledger.set(receiptPath,previousReceipt);
            plugin.message(p,"บันทึกสิทธิ์ Vault ไม่สำเร็จ · ยังไม่ใช้กุญแจ กรุณาแจ้งแอดมิน");
            return;
        }
        keyItem.subtract(1);
        p.updateInventory();

        List<String> displayNames = new ArrayList<>(rewards.size());
        for (ItemStack reward : rewards) {
            var leftover=p.getInventory().addItem(reward.clone());
            if(!leftover.isEmpty()) {
                leftover.values().forEach(item->p.getWorld().dropItemNaturally(block.getLocation().add(0.5,1.2,0.5),item));
            }
            String rName=reward.getItemMeta()!=null&&reward.getItemMeta().hasDisplayName()?
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(reward.getItemMeta().displayName()):
                reward.getType().name();
            displayNames.add(rName + " ×" + reward.getAmount());
        }

        // Vault fanfare
        p.playSound(block.getLocation(),Sound.BLOCK_VAULT_OPEN_SHUTTER,1.0f,1.0f);
        p.playSound(block.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.8f,1.2f);
        p.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION,block.getLocation().add(0.5,1.0,0.5),30,0.4,0.4,0.4,0.05);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,block.getLocation().add(0.5,1.0,0.5),25,0.3,0.5,0.3,0.1);

        plugin.getLogger().info("[Vault-Claim] player="+p.getName()+" uuid="+p.getUniqueId()+" site="+site.id()
                +" tier="+site.tier()+" keyTier="+keyTier+" reward="+rewardId);
        plugin.message(p,"✦ ปลดล็อก Evergarden Vault ["+site.tierColorCode()+"Tier "+site.romanTier()+"§f] สำเร็จ! คุณได้รับ: "+String.join(" §7+ §f", displayNames));
    }

    private void startWave(Encounter enc,int waveNum) {
        enc.wave=waveNum;
        List<Player> team=players(enc.site);
        int tier=enc.site.tier();
        for(Player member:team) {
            enc.presence.putIfAbsent(member.getUniqueId(),0);
            plugin.message(member,"✦ "+enc.site.tierColorCode()+"[Tier "+enc.site.romanTier()+"] §fระลอกที่ "+waveNum+"/"+enc.totalWaves+": ผู้พิทักษ์แห่ง"+enc.site.kind().displayName+"ปรากฏตัว!");
            member.playSound(member.getLocation(),Sound.EVENT_RAID_HORN,0.7f,1.1f);
        }
        int baseMobs = tier <= 1 ? 2 : tier <= 3 ? 3 : 4;
        int count=Math.min(8, baseMobs + Math.max(0, team.size() - 1) + (waveNum - 1) / 2);
        if(tier>=6) {
            if(waveNum==1)for(Player member:team)plugin.message(member,tierRules(tier));
            // Seraph armour turns undead away: Tier VI-VIII send illagers, blazes and magma cubes instead.
            int ravagers=0;
            for(int i=0;i<count;i++) {
                double angle=(2.0*Math.PI*i)/Math.max(1,count);
                Location loc=position(enc.site,(int)Math.round(Math.cos(angle)*9.0),97,(int)Math.round(Math.sin(angle)*9.0));
                int pick=ThreadLocalRandom.current().nextInt(waveNum>=2&&ravagers==0&&i<count-1?4:3);
                switch(pick) {
                    case 0 -> spawn(enc,Species.MINION,loc,team.size(),Vindicator.class);
                    case 1 -> spawn(enc,Species.MINION,loc,team.size(),MagmaCube.class);
                    case 2 -> spawn(enc,Species.STALKER,loc,team.size(),Blaze.class);
                    default -> {
                        ravagers++;i++;
                        LivingEntity ravager=spawn(enc,Species.STALKER,loc,team.size(),Ravager.class);
                        LivingEntity rider=spawn(enc,Species.CASTER,loc,team.size(),Evoker.class);
                        if(ravager!=null&&rider!=null)ravager.addPassenger(rider);
                    }
                }
            }
            return;
        }
        for(int i=0;i<count;i++) {
            Species sp=waveNum==1||i%3==0?Species.MINION:waveNum>=2&&i%3==2?Species.STALKER:Species.CASTER;
            double angle = (2.0 * Math.PI * i) / Math.max(1, count);
            int sx = (int) Math.round(Math.cos(angle) * 9.0);
            int sz = (int) Math.round(Math.sin(angle) * 9.0);
            Location loc=position(enc.site,sx,97,sz);
            spawn(enc,sp,loc,team.size());
        }
    }

    private void spawnBoss(Encounter enc) {
        enc.bossStarted=true;
        List<Player> team=players(enc.site);
        enc.bosses.clear();
        Location loc=position(enc.site,0,97,2);
        LivingEntity boss;
        if(enc.site.tier()>=7) {
            // Tier VII-VIII: two bosses side by side.
            boss=spawn(enc,Species.BOSS,position(enc.site,-3,97,2),team.size());
            LivingEntity twin=spawn(enc,Species.BOSS,position(enc.site,3,97,2),team.size());
            if(twin!=null)enc.bosses.add(twin.getUniqueId());
            if(boss==null)boss=twin;
            else enc.bosses.add(0,boss.getUniqueId());
        } else {
            boss=spawn(enc,Species.BOSS,loc,team.size());
            if(boss!=null)enc.bosses.add(boss.getUniqueId());
        }
        if(boss!=null) {
            String baseTitle=switch(enc.site.kind()) {
                case SANCTUM_DARK -> "จอมมารแห่งความมืด (Shadow Overlord)";
                case SANCTUM_ASTRAL -> "อัครเทวทูตดวงดาว (Astral Archon)";
                case SANCTUM_TIME -> "ผู้พิทักษ์กาลเวลา (Chronos Vanguard)";
            };
            String barTitle = com.example.voidscape.boss.BossNames.legacy("[Tier " + enc.site.romanTier() + "] " + baseTitle)
                + (enc.bosses.size() > 1 ? " §f×" + enc.bosses.size() : "");
            enc.bar=Bukkit.createBossBar(barTitle,tierBarColor(enc.site.tier()),BarStyle.SEGMENTED_10);
            for(Player p:team) {
                enc.bar.addPlayer(p);
                plugin.message(p,"⚠ บอสแห่งวิหาร "+enc.site.tierColorCode()+"[Tier "+enc.site.romanTier()+"] §fปรากฏตัว: "+baseTitle+"!");
                p.playSound(p.getLocation(),Sound.ENTITY_WITHER_SPAWN,0.7f,1.0f);
            }
        }
    }

    private LivingEntity spawn(Encounter enc,Species species,Location where,int teamSize) {
        return spawn(enc,species,where,teamSize,null);
    }

    private LivingEntity spawn(Encounter enc,Species species,Location where,int teamSize,Class<? extends Mob> forced) {
        if(where.getWorld()!=plugin.world()||!where.getWorld().isChunkLoaded(where.getBlockX()>>4,where.getBlockZ()>>4))return null;
        if(owners.size()>=plugin.integer("performance.max-dungeon-mobs",64,4,128))return null;

        Class<? extends Mob> type=forced!=null?forced:switch(species) {
            case BOSS -> switch(enc.site.kind()) {
                case SANCTUM_DARK -> WitherSkeleton.class;
                case SANCTUM_ASTRAL -> Stray.class;
                case SANCTUM_TIME -> PiglinBrute.class;
            };
            case CASTER -> Evoker.class;
            case STALKER -> Husk.class;
            case MINION -> switch(enc.site.kind()) {
                case SANCTUM_DARK -> WitherSkeleton.class;
                case SANCTUM_ASTRAL -> Stray.class;
                case SANCTUM_TIME -> PiglinBrute.class;
            };
            case VEX -> Vex.class;
        };

        int tier = Math.clamp(enc.site.tier(), 1, 5);
        int sealTier = enc.site.tier();

        Mob mob=plugin.world().spawn(where,type,m->{
            // Size first: a slime's size resets its health and attack attributes.
            if(m instanceof MagmaCube cube)cube.setSize(3);
            m.getPersistentDataContainer().set(mobKey,PersistentDataType.BYTE,(byte)1);
            m.getPersistentDataContainer().set(runKey,PersistentDataType.STRING,runId);
            m.setRemoveWhenFarAway(false);
            m.setPersistent(true);

            DungeonBalance.Stats stats = DungeonBalance.stats(species, tier, teamSize);
            double baseHp = stats.health() * DungeonBalance.healthMultiplier(sealTier) * (m instanceof Ravager ? 1.8 : 1.0)
                * (species == Species.BOSS ? DungeonBalance.bossHealthMultiplier(sealTier) : 1.0);
            double attackDamage = stats.damage() * DungeonBalance.damageMultiplier(sealTier);
            double wantedHp = baseHp;
            if (species == Species.BOSS) {
                double configuredCap = plugin.getConfig().getDouble("combat.boss-health", 1024.0);
                if (Double.isFinite(configuredCap) && configuredCap > 0) baseHp = Math.min(baseHp, configuredCap);
                if (m.getAttribute(Attribute.ARMOR) != null) m.getAttribute(Attribute.ARMOR).setBaseValue(DungeonBalance.armor(tier));
                if (m.getAttribute(Attribute.ARMOR_TOUGHNESS) != null) m.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(DungeonBalance.toughness(tier));
                if (m.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) m.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
                if (m.getAttribute(Attribute.SCALE) != null) m.getAttribute(Attribute.SCALE).setBaseValue(1.50 + tier * 0.08);
            } else if (species == Species.VEX && m.getAttribute(Attribute.SCALE) != null) {
                m.getAttribute(Attribute.SCALE).setBaseValue(1.25);
            }

            if (species != Species.BOSS) {
                if (m.getAttribute(Attribute.ARMOR) != null) m.getAttribute(Attribute.ARMOR).setBaseValue(0);
                if (m.getAttribute(Attribute.ARMOR_TOUGHNESS) != null) m.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(0);
            }
            baseHp = Math.min(1024.0, Math.round(baseHp));
            // Above the 1024 health ceiling the rest of the toughness is carried as damage reduction.
            if (wantedHp > baseHp + 0.5) m.getPersistentDataContainer().set(hpScaleKey, PersistentDataType.DOUBLE, wantedHp / baseHp);

            com.example.voidscape.compat.LevelledMobsCompat.tagMob(m, plugin, species == Species.BOSS);
            m.getPersistentDataContainer().set(mobTargetHpKey, PersistentDataType.DOUBLE, baseHp);
            m.getPersistentDataContainer().set(mobTargetDmgKey, PersistentDataType.DOUBLE, attackDamage);

            if (m.getAttribute(Attribute.MAX_HEALTH) != null) {
                var hpAttr = m.getAttribute(Attribute.MAX_HEALTH);
                for (org.bukkit.attribute.AttributeModifier mod : new ArrayList<>(hpAttr.getModifiers())) {
                    hpAttr.removeModifier(mod);
                }
                hpAttr.setBaseValue(baseHp);
                m.setHealth(baseHp);
            }
            if (m.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                var dmgAttr = m.getAttribute(Attribute.ATTACK_DAMAGE);
                for (org.bukkit.attribute.AttributeModifier mod : new ArrayList<>(dmgAttr.getModifiers())) {
                    dmgAttr.removeModifier(mod);
                }
                dmgAttr.setBaseValue(attackDamage);
            }

            String baseName = species == Species.BOSS ?
                (enc.site.kind() == DungeonLayout.Kind.SANCTUM_DARK ? "จอมมารแห่งความมืด (Shadow Overlord)" :
                 enc.site.kind() == DungeonLayout.Kind.SANCTUM_ASTRAL ? "อัครเทวทูตดวงดาว (Astral Archon)" : "ผู้พิทักษ์กาลเวลา (Chronos Vanguard)") :
                (species == Species.VEX ? "วิญญาณรังควาน (Void Vex)" :
                 species == Species.CASTER ? "ภูตพลังเวท" : species == Species.MINION ? "อัศวินแห่งวิหาร" : "นักล่ามิติ");
            String name = "[Tier " + enc.site.romanTier() + "] " + baseName;
            m.getPersistentDataContainer().set(mobTargetNameKey, PersistentDataType.STRING, name);
            m.customName(species == Species.BOSS ? com.example.voidscape.boss.BossNames.styled(name)
                : Component.text(name, species == Species.VEX ? NamedTextColor.RED : tierNamedColor(tier)));
            if (species == Species.BOSS) BossDamage.tag(m, name);
            m.setCustomNameVisible(true);

            if(m instanceof Vex vex) {
                vex.setLimitedLifetime(false);
                vex.setCharging(true);
                if (m.getEquipment() != null) {
                    m.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                    m.getEquipment().setItemInMainHandDropChance(0f);
                }
            }
            if(m instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
            if(species==Species.STALKER && m.getAttribute(Attribute.MOVEMENT_SPEED)!=null) m.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.30 + tier * 0.008);
            if(m.getEquipment()!=null) {
                if(species==Species.BOSS) {
                    m.getEquipment().setChestplate(new ItemStack(tier <= 2 ? Material.DIAMOND_CHESTPLATE : Material.NETHERITE_CHESTPLATE));
                    m.getEquipment().setChestplateDropChance(0);
                    m.getEquipment().setItemInMainHand(new ItemStack(tier <= 2 ? Material.DIAMOND_SWORD : Material.NETHERITE_SWORD));
                    m.getEquipment().setItemInMainHandDropChance(0);
                } else if(m instanceof WitherSkeleton||m instanceof PiglinBrute) {
                    m.getEquipment().setItemInMainHand(new ItemStack(tier == 1 ? Material.IRON_SWORD : Material.DIAMOND_SWORD));
                    m.getEquipment().setItemInMainHandDropChance(0);
                }
            }
            if(species != Species.VEX && plugin.getConfig().getBoolean("combat.custom-appearance",true)) GuardianAppearance.apply(m,enc.site.kind(),species==Species.BOSS);
        });

        if(!mob.isValid()||mob.isDead())return null;
        enc.mobs.put(mob.getUniqueId(),species);
        owners.put(mob.getUniqueId(),enc);
        if(sealTier>=6&&species!=Species.BOSS&&species!=Species.VEX&&ThreadLocalRandom.current().nextDouble()<0.20)makeUnique(enc,mob,sealTier);

        // Schedule LevelledMobs immunity check 1 tick later to strip any external level modifications
        final double finalHp = mob.getPersistentDataContainer().getOrDefault(mobTargetHpKey, PersistentDataType.DOUBLE, 90.0);
        final double finalDmg = mob.getPersistentDataContainer().getOrDefault(mobTargetDmgKey, PersistentDataType.DOUBLE, 16.0);
        final String finalName = mob.getPersistentDataContainer().getOrDefault(mobTargetNameKey, PersistentDataType.STRING, "อัศวินแห่งวิหาร");
        final NamedTextColor finalColor = species == Species.BOSS ? NamedTextColor.GOLD : species == Species.VEX ? NamedTextColor.RED : tierNamedColor(sealTier);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            cleanseAndLockMob(mob, finalHp, finalDmg, finalName, finalColor, species == Species.BOSS);
        }, 1L);

        return mob;
    }

    public void cleanseAndLockMob(Mob mob, double targetHp, double targetDmg, String targetName, NamedTextColor color, boolean isBoss) {
        if (mob == null || !mob.isValid() || mob.isDead()) return;

        com.example.voidscape.compat.LevelledMobsCompat.tagMob(mob, plugin, isBoss);

        // Strip ALL AttributeModifiers and enforce Max Health and clamp
        var hpAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            for (org.bukkit.attribute.AttributeModifier mod : new ArrayList<>(hpAttr.getModifiers())) {
                hpAttr.removeModifier(mod);
            }
            if (hpAttr.getBaseValue() != targetHp) {
                hpAttr.setBaseValue(targetHp);
            }
            if (mob.getHealth() > targetHp) {
                mob.setHealth(targetHp);
            }
        }

        // Strip ALL AttributeModifiers and enforce Attack Damage
        var dmgAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            for (org.bukkit.attribute.AttributeModifier mod : new ArrayList<>(dmgAttr.getModifiers())) {
                dmgAttr.removeModifier(mod);
            }
            dmgAttr.setBaseValue(targetDmg);
        }

        Encounter enc = owners.get(mob.getUniqueId());
        int tier = enc != null ? enc.site.tier() : 3;

        // Cleanse Armor & Toughness modifiers
        var armorAttr = mob.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            for (org.bukkit.attribute.AttributeModifier mod : new ArrayList<>(armorAttr.getModifiers())) {
                armorAttr.removeModifier(mod);
            }
            armorAttr.setBaseValue(isBoss ? DungeonBalance.armor(tier) : 0.0);
        }
        var toughAttr = mob.getAttribute(Attribute.ARMOR_TOUGHNESS);
        if (toughAttr != null) {
            for (org.bukkit.attribute.AttributeModifier mod : new ArrayList<>(toughAttr.getModifiers())) {
                toughAttr.removeModifier(mod);
            }
            toughAttr.setBaseValue(isBoss ? DungeonBalance.toughness(tier) : 0.0);
        }

        // Restore Custom Name
        if (targetName != null) {
            Integer unique = mob.getPersistentDataContainer().get(uniqueColorKey, PersistentDataType.INTEGER);
            net.kyori.adventure.text.format.TextColor shade = unique != null ? net.kyori.adventure.text.format.TextColor.color(unique)
                : color != null ? color : (isBoss ? NamedTextColor.GOLD : tierNamedColor(tier));
            mob.customName(Component.text(targetName, shade));
            mob.setCustomNameVisible(true);
        }
    }

    public void cleanseMobIfTagged(Mob mob) {
        if (mob == null || !mob.isValid() || mob.isDead()) return;
        Double targetHp = mob.getPersistentDataContainer().get(mobTargetHpKey, PersistentDataType.DOUBLE);
        if (targetHp == null || targetHp <= 0) return;
        Double targetDmg = mob.getPersistentDataContainer().get(mobTargetDmgKey, PersistentDataType.DOUBLE);
        if (targetDmg == null) targetDmg = 16.0;
        String targetName = mob.getPersistentDataContainer().get(mobTargetNameKey, PersistentDataType.STRING);
        boolean isBoss = owners.containsKey(mob.getUniqueId()) && owners.get(mob.getUniqueId()).mobs.get(mob.getUniqueId()) == Species.BOSS;

        var hpAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        boolean needsCleanse = false;
        if (hpAttr != null) {
            if (!hpAttr.getModifiers().isEmpty() || hpAttr.getBaseValue() != targetHp || mob.getHealth() > targetHp) {
                needsCleanse = true;
            }
        }

        var attackAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        var armorAttr = mob.getAttribute(Attribute.ARMOR);
        var toughnessAttr = mob.getAttribute(Attribute.ARMOR_TOUGHNESS);
        Encounter enc = owners.get(mob.getUniqueId());
        int tier = enc != null ? enc.site.tier() : 3;
        needsCleanse |= attackAttr != null && (!attackAttr.getModifiers().isEmpty() || attackAttr.getBaseValue() != targetDmg);
        // Equipment legitimately contributes modifiers; compare only base defenses here.
        needsCleanse |= armorAttr != null && armorAttr.getBaseValue() != (isBoss ? DungeonBalance.armor(tier) : 0.0);
        needsCleanse |= toughnessAttr != null && toughnessAttr.getBaseValue() != (isBoss ? DungeonBalance.toughness(tier) : 0.0);

        if (needsCleanse) {
            cleanseAndLockMob(mob, targetHp, targetDmg, targetName, null, isBoss);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void creature(CreatureSpawnEvent e) {
        if(e.getEntity().getPersistentDataContainer().has(plugin.key("world_boss_entity"),PersistentDataType.BYTE))return;
        if(e.getEntity().getPersistentDataContainer().has(plugin.key("pet_model_preview"),PersistentDataType.BYTE))return;
        if(e.getEntity().getPersistentDataContainer().has(plugin.key("seraph_trader"),PersistentDataType.BYTE))return;
        // Only the explicit wild-pet spawn consumer sets this marker before the event.
        if(e.getSpawnReason()==CreatureSpawnEvent.SpawnReason.CUSTOM
                &&(e.getEntity().getPersistentDataContainer().has(plugin.key("wild_pet"),PersistentDataType.BYTE)
                ||e.getEntity().getPersistentDataContainer().has(plugin.key("pet_body"),PersistentDataType.BYTE)))return;
        if(e.getEntity() instanceof ArmorStand && e.getEntity().getPersistentDataContainer()
                .has(plugin.key("portal_visual"), PersistentDataType.STRING)) return;
        if(e.getEntity() instanceof ArmorStand && e.getEntity().getPersistentDataContainer()
                .has(plugin.key("boss_interior_display"), PersistentDataType.STRING)) return;
        // Advance Magic sets this owner tag in its pre-spawn consumer. Only
        // its flying staff stand bypasses our filter; mob spawning stays blocked.
        if(e.getEntity() instanceof ArmorStand && e.getEntity().getPersistentDataContainer()
                .has(new NamespacedKey("advance-magic", "flying_staff_entity"), PersistentDataType.STRING)) return;
        if(e.getEntity() instanceof Villager) return;
        if(e.getLocation().getWorld()==plugin.world()&&!runId.equals(e.getEntity().getPersistentDataContainer().get(runKey,PersistentDataType.STRING)))e.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent e) {
        Encounter enc=owners.get(e.getEntity().getUniqueId());
        Entity source=e.getDamager();
        // Encounter mobs never hurt each other (blaze fire, evoker fangs, ravager trampling).
        if(enc!=null&&encounterSource(source)!=null){e.setCancelled(true);return;}
        Player attacker=source instanceof Player p?p:source instanceof Projectile pr&&pr.getShooter() instanceof Player p?p:null;
        if(enc!=null&&attacker!=null) {
            if(attacker.getWorld()!=e.getEntity().getWorld()) {
                e.setCancelled(true);return;
            }
            if(attacker.getGameMode()!=GameMode.CREATIVE&&!inArena(enc.site,attacker)) {
                e.setCancelled(true);
                attacker.sendActionBar(Component.text("⚠ โจมตีได้เฉพาะในลานประลอง (ห้ามยืนบนเสา/ซุ้ม หรือยิงจากนอกวง)",NamedTextColor.RED));
                return;
            }
            combatUntil.put(attacker.getUniqueId(),System.currentTimeMillis()+10000);

            // Modest resistance, on top of native armor and cosmetic equipment.
            Species victimSpecies = enc.mobs.get(e.getEntity().getUniqueId());
            if (enc.site.tier() >= 6) tierDefence(e, enc, attacker, victimSpecies);
            if (victimSpecies == Species.BOSS) {
                int tier = enc.site.tier();
                double mult = DungeonBalance.incomingMultiplier(tier);
                e.setDamage(e.getDamage() * mult);
                if (e.getDamage() > 450.0) {
                    e.setDamage(450.0);
                }
            }
        }
        Mob mobDamager=source instanceof Mob m?m:source instanceof Projectile pr&&pr.getShooter() instanceof Mob m?m:null;
        if(mobDamager!=null&&e.getEntity() instanceof Player victim&&playable(victim)) {
            Encounter mobEnc=owners.get(mobDamager.getUniqueId());
            if(mobEnc!=null) {
                // Scripted attacks already have their own damage, warning and debuffs.
                // Never reapply the basic-hit curse/cap to an AoE, laser or hazard.
                if (source instanceof Projectile volley && volley.getPersistentDataContainer().has(volleyKey)) {
                    // Cocytus volley: fixed arrow damage and frost, never the boss's basic-hit curse.
                    e.setDamage(6.0 * DungeonBalance.damageMultiplier(mobEnc.site.tier()));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    victim.setFreezeTicks(Math.min(victim.getMaxFreezeTicks(), victim.getFreezeTicks() + 60));
                    return;
                }
                Double skillBudget = scriptedImpacts.get(victim.getUniqueId());
                if (skillBudget != null) {
                    // Hard difficulty scales attributed mob damage before this event.
                    e.setDamage(Math.min(e.getDamage(), skillBudget));
                    return;
                }
                if (e.getFinalDamage() <= 0) return;
                int tier = mobEnc.site.tier();
                Species species=mobEnc.mobs.get(mobDamager.getUniqueId());
                if (species == Species.VEX) {
                    Vector impulse = victim.getLocation().toVector().subtract(mobDamager.getLocation().toVector()).normalize().multiply(0.85);
                    impulse.setY(0.35);
                    victim.setVelocity(victim.getVelocity().add(impulse));
                    victim.playSound(victim.getLocation(), Sound.ENTITY_VEX_HURT, 1.0f, 0.8f);
                    victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
                    plugin.message(victim, "⚠ วิญญาณ Void Vex โจมตีทะลวง! กระเด็นถอยหลัง!");
                } else {
                    if(plugin.getConfig().getBoolean("combat.apply-weakness",false))
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,120,0));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60 + tier * 10, tier >= 4 ? 1 : 0));
                    if (tier >= 2) {
                        maybePlaceWeb(victim, 4000L + tier * 500L);
                    }
                }
                // Native weapons and external levelers must not exceed the authored hit budget.
                double hitCap = mobDamager.getPersistentDataContainer().getOrDefault(mobTargetDmgKey,
                        PersistentDataType.DOUBLE, DungeonBalance.stats(species, tier, 1).damage());
                if (mobEnc.berserkUntil.getOrDefault(mobDamager.getUniqueId(), 0L) > System.currentTimeMillis()) {
                    // Styx berserk: 35% harder hits.
                    hitCap *= 1.35;
                    e.setDamage(e.getDamage() * 1.35);
                }
                e.setDamage(Math.min(e.getDamage(), hitCap));
                double rend = hitCap * DungeonBalance.rendRatio(tier);
                if (rend >= 0.5) {
                    Mob striker = mobDamager;
                    Bukkit.getScheduler().runTask(plugin, () -> rend(victim, striker, rend));
                }
                if (species == Species.BOSS) {
                    // True Death Curse only activates on Tier 3+ sanctums so Tier 1-2 Diamond players can fight fairly
                    if (tier >= 3) {
                        recordTrueDeathHit(victim, bossName(mobDamager));
                    }
                    if (mobEnc.site.kind() == DungeonLayout.Kind.SANCTUM_DARK) {
                        BossDamage.effect(victim, new PotionEffect(PotionEffectType.WITHER, 60 + tier * 15, tier >= 4 ? 1 : 0), bossName(mobDamager));
                    } else if (mobEnc.site.kind() == DungeonLayout.Kind.SANCTUM_ASTRAL) {
                        victim.setFreezeTicks(Math.min(victim.getMaxFreezeTicks(), victim.getFreezeTicks() + 80 + tier * 20));
                    } else if (mobEnc.site.kind() == DungeonLayout.Kind.SANCTUM_TIME) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60 + tier * 15, tier >= 3 ? 2 : 1));
                    }
                }
                victim.playSound(victim.getLocation(),Sound.ENTITY_SPLASH_POTION_BREAK,0.7f,0.9f);
                victim.getWorld().spawnParticle(Particle.SQUID_INK,victim.getLocation().add(0,1,0),12,0.3,0.4,0.3,0.05);
            }
        }
    }

    private int trueDeathHits(Player p){return p.getPersistentDataContainer().getOrDefault(trueDeathHitsKey,PersistentDataType.INTEGER,0);}
    private int trueDeathLevel(Player p){return p.getPersistentDataContainer().getOrDefault(trueDeathLevelKey,PersistentDataType.INTEGER,0);}
    private int trueDeathHitsPerLevel(){return plugin.integer("combat.true-death.hits-per-level",10,1,100);}
    private int trueDeathMaxLevel(){return plugin.integer("combat.true-death.max-level",5,1,10);}

    private String bossName(Mob boss) {
        return boss.getPersistentDataContainer().getOrDefault(mobTargetNameKey, PersistentDataType.STRING, "บอสแห่งวิหาร");
    }

    private void recordTrueDeathHit(Player victim, String bossName) {
        TrueDeathProgress progress=TrueDeathProgress.hit(trueDeathHits(victim),trueDeathLevel(victim),trueDeathHitsPerLevel(),trueDeathMaxLevel());
        victim.getPersistentDataContainer().set(trueDeathHitsKey,PersistentDataType.INTEGER,progress.hits());
        victim.getPersistentDataContainer().set(trueDeathLevelKey,PersistentDataType.INTEGER,progress.level());
        updateTrueDeathBar(victim);
        victim.sendActionBar(Component.text("☠ True Death "+roman(progress.level())+"/"+trueDeathMaxLevel()+" · "+progress.hits()+"/"+trueDeathHitsPerLevel()+" hits",NamedTextColor.RED));
        if(!progress.triggered())return;

        UUID id=victim.getUniqueId();int expectedLevel=progress.level();
        if(progress.finalDeath()) {
            victim.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("☠ TRUE DEATH "+roman(progress.level()),NamedTextColor.DARK_RED),
                    Component.text("คำสาปสมบูรณ์ · ความตายกลืนกินวิญญาณ (ทะลวง Totem 100%)",NamedTextColor.RED)));
            victim.getWorld().playSound(victim.getLocation(),Sound.ENTITY_WARDEN_SONIC_BOOM,1.0f,0.55f);
            victim.getWorld().spawnParticle(Particle.SCULK_SOUL,victim.getLocation().add(0,1,0),50,0.5,1.0,0.5,0.1);
            Bukkit.getScheduler().runTask(plugin,()->{
                Player current=Bukkit.getPlayer(id);
                if(current==null||!current.isOnline()||current.isDead()||trueDeathLevel(current)!=expectedLevel)return;
                // Direct death deliberately bypasses Totem at the final stack (Level 5).
                BossDamage.scoped(current, bossName, () -> current.setHealth(0.0));
            });
        } else {
            victim.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("☠ TRUE DEATH "+roman(progress.level()),NamedTextColor.RED),
                    Component.text("คำสาประดับ "+roman(progress.level())+"/"+trueDeathMaxLevel()+" · ดื่มนมเพื่อชำระล้าง!",NamedTextColor.GOLD)));
            victim.getWorld().playSound(victim.getLocation(),Sound.ENTITY_WARDEN_HEARTBEAT,1.0f,0.8f);
            victim.getWorld().spawnParticle(Particle.SCULK_SOUL,victim.getLocation().add(0,1,0),25,0.3,0.6,0.3,0.06);
            Bukkit.getScheduler().runTask(plugin,()->{
                Player current=Bukkit.getPlayer(id);
                if(current==null||!current.isOnline()||current.isDead()||trueDeathLevel(current)!=expectedLevel)return;
                double pulseDamage = plugin.getConfig().getDouble("combat.true-death.pulse-damage-per-level", 4.0) * expectedLevel;
                if(pulseDamage > 0.0) {
                    // Normal damage: vanilla Totem protects if damage is lethal.
                    BossDamage.scoped(current, bossName, () -> current.damage(pulseDamage));
                }
                current.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
                BossDamage.effect(current, new PotionEffect(PotionEffectType.WITHER, 60, Math.min(1, expectedLevel - 1)), bossName);
            });
        }
    }

    private String roman(int level){return switch(level){case 1->"I";case 2->"II";case 3->"III";case 4->"IV";case 5->"V";default->Integer.toString(level);};}

    private void updateTrueDeathBar(Player p) {
        int level=trueDeathLevel(p),hits=trueDeathHits(p);
        if(level<=0&&hits<=0){removeTrueDeathBar(p.getUniqueId());return;}
        BossBar bar=trueDeathBars.computeIfAbsent(p.getUniqueId(),id->Bukkit.createBossBar("",BarColor.RED,BarStyle.SEGMENTED_10));
        bar.setTitle("☠ TRUE DEATH "+roman(level)+"/"+trueDeathMaxLevel()+" · "+hits+"/"+trueDeathHitsPerLevel()+" hits");
        bar.setProgress(Math.max(0.0,Math.min(1.0,hits/(double)trueDeathHitsPerLevel())));
        if(!bar.getPlayers().contains(p))bar.addPlayer(p);
    }

    private void removeTrueDeathBar(UUID id){BossBar bar=trueDeathBars.remove(id);if(bar!=null)bar.removeAll();}
    private boolean clearTrueDeath(Player p) {
        boolean active=trueDeathLevel(p)>0||trueDeathHits(p)>0;
        p.getPersistentDataContainer().remove(trueDeathHitsKey);p.getPersistentDataContainer().remove(trueDeathLevelKey);
        removeTrueDeathBar(p.getUniqueId());return active;
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void drinkMilk(PlayerItemConsumeEvent e) {
        if(e.getItem().getType()!=Material.MILK_BUCKET||!clearTrueDeath(e.getPlayer()))return;
        plugin.message(e.getPlayer(),"นมชำระล้างคำสาป True Death และตัวนับการโจมตีทั้งหมดแล้ว");
        e.getPlayer().playSound(e.getPlayer().getLocation(),Sound.BLOCK_BEACON_DEACTIVATE,0.7f,1.4f);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void playerDeath(PlayerDeathEvent e){clearTrueDeath(e.getPlayer());}

    @EventHandler(priority=EventPriority.MONITOR)
    public void playerQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        removeTrueDeathBar(id);
        seen.remove(id);
        combatUntil.remove(id);
    }

    /** Prevents Ender Pearl / Chorus Fruit cheesing onto temple pillars/arches during active Sanctum combat. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onCombatTeleport(PlayerTeleportEvent e) {
        String causeName = e.getCause().name();
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && !causeName.equals("CHORUS_FRUIT")
                && !causeName.equals("CONSUMABLE_EFFECT")) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || e.getTo() == null || e.getTo().getWorld() != plugin.world()) return;
        Site site = plugin.layout().at(e.getTo().getBlockX(), e.getTo().getBlockZ(), 12);
        if (site == null) site = plugin.layout().at(p.getLocation().getBlockX(), p.getLocation().getBlockZ(), 12);
        if (site != null && active.containsKey(site.id())) {
            double r = Math.hypot(e.getTo().getX() - (site.x() + 0.5), e.getTo().getZ() - (site.z() + 0.5));
            if (e.getTo().getY() > 100.0 || e.getTo().getY() < 95.5 || r > 20.0) {
                e.setCancelled(true);
                plugin.message(p, "⚠ สนามพลังวิหารป้องกันการปา Ender Pearl ขึ้นที่สูงหรือออกนอกวงประลอง!");
                p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.2f);
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void death(EntityDeathEvent e) {
        Encounter enc=owners.remove(e.getEntity().getUniqueId());if(enc==null)return;
        Species species=enc.mobs.remove(e.getEntity().getUniqueId());
        // A unique keeps the EvergardenForge loot its elite rank dropped; nothing else drops.
        if(enc.uniques.remove(e.getEntity().getUniqueId()))e.getDrops().removeIf(item->!forgeItem(item));
        else e.getDrops().clear();
        e.setDroppedExp(0);
        if(!runId.equals(e.getEntity().getPersistentDataContainer().get(runKey,PersistentDataType.STRING)))return;

        if(species==Species.BOSS) {
            enc.bosses.remove(e.getEntity().getUniqueId());
            enc.berserkUntil.remove(e.getEntity().getUniqueId());
            for(var seal:new ArrayList<>(enc.seals))if(seal.boss().equals(e.getEntity().getUniqueId())){enc.seals.remove(seal);sigils.remove(seal.key());}
            if(!enc.bosses.isEmpty()) {
                for(Player p:players(enc.site))plugin.message(p,"⚔ บอสหนึ่งตนล้มลง · เหลืออีก "+enc.bosses.size()+" ตน!");
                return;
            }
            finish(enc);
            return;
        }

        if(enc.mobs.isEmpty()&&!enc.bossStarted) {
            if(enc.wave<enc.totalWaves) {
                startWave(enc,enc.wave+1);
            } else {
                spawnBoss(enc);
            }
        }
    }

    private void finish(Encounter enc) {
        if(enc.finished)return;
        enc.finished=true;
        ledger.set(path(enc.site)+".next-open",System.currentTimeMillis()+plugin.integer("structures.minor.reset-hours",2,1,72)*3600000L);
        save();

        for(var member:enc.presence.entrySet()) {
            Player p=Bukkit.getPlayer(member.getKey());
            if(p!=null&&playable(p)&&enc.site.contains(p.getX(),p.getZ(),18)) {
                ItemStack key=plugin.relics().createVoidKey(enc.site.tier());
                var leftover=p.getInventory().addItem(key);
                if(!leftover.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(),key);

                plugin.message(p,"✦ พิชิต "+enc.site.kind().displayName+" "+enc.site.tierColorCode()+"[Tier "+enc.site.romanTier()+"] §fสำเร็จ! ได้รับ Evergarden Key [Tier "+enc.site.romanTier()+"]");
                p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.8f,1.0f);
            }
        }
        remove(enc);
        active.remove(enc.site.id());
    }

    public void tick() {
        long now=System.currentTimeMillis();combatUntil.values().removeIf(t->t<now);
        if(++expansionTicks%10==0)expansion.decideAroundPlayers();
        for(Player p:Bukkit.getOnlinePlayers())if(trueDeathLevel(p)>0||trueDeathHits(p)>0)updateTrueDeathBar(p);
        if(!tempWebs.isEmpty()) {
            Iterator<Map.Entry<Block,Long>> it=tempWebs.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<Block,Long> entry=it.next();
                if(now>=entry.getValue()) {
                    Block b=entry.getKey();
                    if(b.getType()==Material.COBWEB)b.setType(Material.AIR,false);
                    it.remove();
                }
            }
        }
        for(Player p:plugin.world().getPlayers()) {
            Site s=plugin.layout().at(p.getLocation().getBlockX(),p.getLocation().getBlockZ(),12);
            if(s!=null&&!s.id().equals(seen.put(p.getUniqueId(),s.id()))) {
                plugin.message(p,"ค้นพบ "+s.kind().displayName+" "+s.tierColorCode()+"[Tier "+s.romanTier()+"] §7("+s.difficultyDescription()+") §f· คลิกแท่น Lodestone กลางวิหารเพื่อเริ่มการท้าทาย");
                p.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(s.kind().displayName + " · Tier " + s.romanTier(), tierNamedColor(s.tier())),
                    Component.text(s.difficultyDescription() + " · คลิก Lodestone เพื่อท้าทาย", NamedTextColor.GRAY),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofMillis(2800), java.time.Duration.ofMillis(600))
                ));
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.1f);
            }
        }
        for(Encounter enc:new ArrayList<>(active.values())) {
            List<Player> nearby=players(enc.site);
            // Players may leave the floor (wind charges, maces, knock-backs). As in the large sanctuaries,
            // the vault resets only when nobody has been inside the arena for 15 seconds.
            List<Player> team=nearby.stream().filter(p->p.getGameMode()==GameMode.CREATIVE||inArena(enc.site,p)).toList();
            if(team.isEmpty()) {
                long limit=plugin.integer("structures.minor.leave-reset-seconds",15,5,120)*1000L;
                long away=now-enc.lastPresent;
                if(away>=limit){
                    for(Player p:nearby){
                        plugin.message(p,"§cวิหารรีเซ็ต: ไม่มีผู้เล่นอยู่ในลานประลองครบ "+limit/1000+" วินาที §7· คลิก Lodestone เพื่อเริ่มใหม่");
                        p.sendActionBar(Component.text("วิหารรีเซ็ตแล้ว",NamedTextColor.RED));
                    }
                    remove(enc);active.remove(enc.site.id());continue;
                }
                long remaining=Math.max(1,(limit-away+999)/1000);
                for(Player p:nearby){
                    if(!enc.warnedAway)plugin.message(p,"§c⚠ ออกนอกลานประลอง! กลับเข้าวงภายใน "+remaining+" วินาที มิฉะนั้นวิหารจะรีเซ็ต");
                    p.sendActionBar(Component.text("⚠ ออกนอกลานประลอง! กลับเข้าวงภายใน "+remaining+" วินาที ก่อนวิหารรีเซ็ต",NamedTextColor.RED));
                    p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.8f,remaining<=5?1.5f:.9f);
                }
                enc.warnedAway=true;
                if(enc.bar!=null)for(Player p:new ArrayList<>(enc.bar.getPlayers()))if(!nearby.contains(p))enc.bar.removePlayer(p);
                for(UUID id:enc.mobs.keySet())if(Bukkit.getEntity(id) instanceof Mob m){m.setTarget(null);m.setAI(false);}
                continue;
            }
            enc.lastPresent=now;enc.warnedAway=false;
            for(Player p:nearby)if(!team.contains(p)&&p.getGameMode()!=GameMode.CREATIVE)
                p.sendActionBar(Component.text("⚠ คุณอยู่นอกลานประลอง: กลับเข้าวงเพื่อโจมตีได้",NamedTextColor.YELLOW));
            // Capacity may be exhausted by another shrine. Retry instead of leaving an empty run stuck.
            if(enc.mobs.isEmpty()) {
                if(enc.bossStarted)spawnBoss(enc);else startWave(enc,Math.max(1,enc.wave));
            }
            for(Player p:team){enc.presence.merge(p.getUniqueId(),1,Integer::sum);combatUntil.put(p.getUniqueId(),now+10000);}
            for(UUID id:enc.mobs.keySet()){if(Bukkit.getEntity(id) instanceof Mob m&&m.isValid()&&!m.isDead()){cleanseMobIfTagged(m);}}

            if(enc.bar!=null) {
                for(Player p:new ArrayList<>(enc.bar.getPlayers()))if(!nearby.contains(p))enc.bar.removePlayer(p);
                for(Player p:nearby)enc.bar.addPlayer(p);
            }

            int tier = enc.site.tier();
            double skillMult = DungeonBalance.skillMultiplier(tier);

            for(var entry:new ArrayList<>(enc.mobs.entrySet())) {
                if(!(Bukkit.getEntity(entry.getKey()) instanceof Mob mob))continue;

                // Anti-Boat Cheese: Force eject and delete any vehicle immediately (an Evoker may ride its Ravager)
                if(mob.isInsideVehicle()&&!(mob.getVehicle()!=null&&owners.containsKey(mob.getVehicle().getUniqueId()))) {
                    Entity vehicle=mob.getVehicle();
                    mob.leaveVehicle();
                    if(vehicle instanceof Boat||vehicle instanceof Minecart) vehicle.remove();
                }

                mob.setAI(true);
                Player target=team.stream().min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(mob.getLocation()))).orElse(null);
                mob.setTarget(target);

                // Circular arena mob leash (keeps mobs inside the flat r <= 19 combat pit)
                double mobRadial = Math.hypot(mob.getX() - (enc.site.x() + 0.5), mob.getZ() - (enc.site.z() + 0.5));
                double ceiling = entry.getValue() == Species.VEX ? 1000 : mob instanceof Blaze ? 108 : 101.5;
                if(!mob.isInsideVehicle()&&(mob.getY() < 95.2 || mob.getY() > ceiling || mobRadial > 19.2)) {
                    Location back=position(enc.site,0,97,4);
                    owners.remove(mob.getUniqueId());mob.teleport(back,io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);owners.put(mob.getUniqueId(),enc);
                }

                // Caster ranged debuff & cobweb curse
                if(entry.getValue()==Species.CASTER&&target!=null) {
                    long lastCast=enc.casterCooldowns.getOrDefault(entry.getKey(),0L);
                    if(now-lastCast>plugin.integer("combat.caster-interval-ms",7000,3000,30000)&&mob.getLocation().distanceSquared(target.getLocation())<=256) {
                        enc.casterCooldowns.put(entry.getKey(),now);
                        Location targetLoc=target.getLocation();
                        if(plugin.getConfig().getBoolean("combat.apply-weakness",false))
                            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,160,0));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,80 + tier * 15, tier >= 4 ? 1 : 0));
                        if (tier >= 2) maybePlaceWeb(target,5000L);
                        target.getWorld().spawnParticle(Particle.WITCH,targetLoc.clone().add(0,1,0),25,0.4,0.6,0.4,0.05);
                        target.getWorld().spawnParticle(Particle.ENCHANTED_HIT,targetLoc.clone().add(0,1,0),20,0.3,0.4,0.3,0.1);
                        target.playSound(targetLoc,Sound.ENTITY_SPLASH_POTION_BREAK,1.0f,0.8f);
                        target.playSound(targetLoc,Sound.ENTITY_EVOKER_CAST_SPELL,0.8f,1.2f);
                        plugin.message(target,"⚠ ภูตพลังเวทร่ายคำสาปบั่นทอนกำลังใส่คุณ!");
                    }

                    // Caster Void Vex Summoning (anti-high ground & anti-pillar)
                    long lastVex=enc.casterVexCooldowns.getOrDefault(entry.getKey(),0L);
                    boolean highGroundTarget=target.getLocation().getY()>99.0;
                    long vexCooldown=highGroundTarget?8000L:(20000L - tier * 1500L);
                    if(now-lastVex>vexCooldown && (tier >= 2 || highGroundTarget)) {
                        long activeVexes=enc.mobs.values().stream().filter(s->s==Species.VEX).count();
                        int maxVex = tier <= 2 ? 2 : 3;
                        if(activeVexes<maxVex&&owners.size()<plugin.integer("performance.max-dungeon-mobs",64,4,128)) {
                            enc.casterVexCooldowns.put(entry.getKey(),now);
                            int spawnCount = (int)Math.min(tier <= 2 ? 1 : 2, maxVex - activeVexes);
                            for(int vi=0;vi<spawnCount;vi++) {
                                Location vexSpawn=mob.getLocation().clone().add((Math.random()-0.5)*2.0,1.2,(Math.random()-0.5)*2.0);
                                LivingEntity vex=spawn(enc,Species.VEX,vexSpawn,team.size());
                                if(vex instanceof Mob vm) {
                                    vm.setTarget(target);
                                }
                            }
                            mob.getWorld().spawnParticle(Particle.WITCH,mob.getLocation(),20,0.5,0.5,0.5,0.05);
                            mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,mob.getLocation().add(0,1,0),20,0.4,0.5,0.4,0.08);
                            mob.getWorld().playSound(mob.getLocation(),Sound.ENTITY_EVOKER_PREPARE_SUMMON,1.0f,1.0f);
                            for(Player p:team) {
                                plugin.message(p,"⚠ ภูตพลังเวทอัญเชิญ 'วิญญาณรังควาน (Void Vex)' ออกมา "+spawnCount+" ตน!");
                            }
                        }
                    }
                }

                // Boss skills & ultimate abilities
                if(entry.getValue()==Species.BOSS&&target!=null) {
                    double maxHp = mob.getAttribute(Attribute.MAX_HEALTH) != null ? mob.getAttribute(Attribute.MAX_HEALTH).getValue() : 1024.0;
                    double hpRatio = Math.max(0.0, Math.min(1.0, mob.getHealth() / maxHp));
                    if(enc.bar!=null) {
                        enc.bar.setProgress(bossBarProgress(enc, hpRatio));
                    }
                    if (enc.site.tier() >= 6) tierSkills(enc, mob, target, team, now);

                    // Tier 4 & Tier 5 Boss Enrage Phase (< 50% HP)
                    if (tier >= 4 && !enc.bossEnraged && hpRatio <= 0.50) {
                        enc.bossEnraged = true;
                        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.85f);
                        mob.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, mob.getLocation().add(0, 1.2, 0), 45, 0.6, 0.9, 0.6, 0.15);
                        mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, mob.getLocation().add(0, 1.0, 0), 35, 0.8, 0.8, 0.8, 0.08);
                        if (owners.size() + 2 <= plugin.integer("performance.max-dungeon-mobs", 64, 4, 128)) {
                            spawn(enc, Species.MINION, position(enc.site, -4, 97, 2), team.size());
                            spawn(enc, Species.STALKER, position(enc.site, 4, 97, 2), team.size());
                        }
                        for (Player p : team) {
                            plugin.message(p, "🔥 บอสแห่งวิหาร [Tier " + enc.site.romanTier() + "] เข้าสู่สถานะคลั่ง (ENRAGE PHASE) · อัญเชิญองครักษ์พิทักษ์วิหารและเร่งพลังวงเวท!");
                            p.showTitle(net.kyori.adventure.title.Title.title(
                                Component.text("🔥 BOSS ENRAGED", NamedTextColor.RED),
                                Component.text("บอสคลั่งที่ 50% HP · ระวังองครักษ์และวงเวทคู่!", NamedTextColor.GOLD),
                                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1800), java.time.Duration.ofMillis(400))
                            ));
                        }
                    }

                    // Tier 5 Cataclysm Elemental Arena Hazard (every 24s on the outer rune/gear ring)
                    if (tier >= 5 && now - enc.lastHazard > 24000L) {
                        enc.lastHazard = now;
                        triggerTier5ArenaHazard(enc, mob, team);
                    }

                    // Boss Targeting Laser -> Elemental Sonic Blast
                    Player highGroundPlayer=team.stream()
                        .filter(p->p.isValid()&&p.getGameMode()!=GameMode.CREATIVE&&p.getLocation().getY()>99.0)
                        .findFirst().orElse(null);

                    boolean triggerLaser=false;
                    Player laserTarget=null;

                    long laserInterval = Math.max(15000L, plugin.integer("combat.boss-laser-interval-ms",90000,7000,300000) - (tier - 1) * 12000L);
                    if(!enc.laserActive) {
                        if(highGroundPlayer!=null&&now-enc.lastLaser>6000L) {
                            triggerLaser=true;
                            laserTarget=highGroundPlayer;
                        } else if(now-enc.lastLaser>laserInterval) {
                            triggerLaser=true;
                            laserTarget=target;
                        }
                    }

                    if(triggerLaser&&laserTarget!=null) {
                        fireBossLaser(enc,mob,laserTarget,team);
                    }

                    long cataclysmCooldown = Math.max(22000L, (enc.bossEnraged ? 36000L : 48000L) - (tier - 1) * 3500L);
                    boolean sonicBoomReady = (now - enc.lastSonicBoom) > cataclysmCooldown;
                    if(!enc.laserActive&&enc.warningAt==0&&sonicBoomReady&&now-enc.lastSkill>plugin.integer("combat.boss-skill-interval-ms",7000,3000,25000)) {
                        enc.warning=target.getLocation().clone();
                        enc.extraWarnings.clear();
                        if (tier >= 4 || enc.bossEnraged) {
                            // Add a 2nd/3rd flanking AoE circle on other party members or flanking offsets
                            for (Player other : team) {
                                if (!other.equals(target) && enc.extraWarnings.size() < 2) {
                                    enc.extraWarnings.add(other.getLocation().clone());
                                }
                            }
                            if (enc.extraWarnings.isEmpty()) {
                                enc.extraWarnings.add(position(enc.site, (ThreadLocalRandom.current().nextBoolean() ? 6 : -6), 97, 0));
                            }
                        }
                        enc.warningAt=now+2000;
                        enc.lastSkill=now;
                        enc.lastSonicBoom=now;

                        String skillNotice=switch(enc.site.kind()) {
                            case SANCTUM_DARK -> "⚠ จอมมารร่าย 'มหาพายุทมิฬ (Abyssal Cataclysm)' · หลบออกจากวงเวท!";
                            case SANCTUM_ASTRAL -> "⚠ อัครเทวทูตร่าย 'ฝนดวงดาวมฤตยู (Starlight Supernova)' · หลบออกจากวงเวท!";
                            case SANCTUM_TIME -> "⚠ ผู้พิทักษ์ร่าย 'มิติกาลเวลาหยุดนิ่ง (Chronos Rift)' · หลบออกจากวงเวท!";
                        };
                        for(Player p:team) {
                            plugin.message(p,skillNotice);
                            p.playSound(enc.warning,Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,0.7f,0.6f);
                        }
                    }
                    if(enc.warningAt>0) {
                        Particle circleParticle=switch(enc.site.kind()) {
                            case SANCTUM_DARK -> Particle.SOUL_FIRE_FLAME;
                            case SANCTUM_ASTRAL -> Particle.END_ROD;
                            case SANCTUM_TIME -> Particle.REVERSE_PORTAL;
                        };
                        List<Location> allZones = new ArrayList<>(1 + enc.extraWarnings.size());
                        if (enc.warning != null) allZones.add(enc.warning);
                        allZones.addAll(enc.extraWarnings);
                        for(Player p:team) {
                            for (Location zLoc : allZones) {
                                for(int i=0;i<12;i++) {
                                    double angle=i*Math.PI/6;
                                    p.spawnParticle(circleParticle,zLoc.clone().add(Math.cos(angle)*4.0,0.15,Math.sin(angle)*4.0),1,0,0,0,0);
                                }
                            }
                        }
                        if(now>=enc.warningAt) {
                            enc.warningAt=0;
                            for (Location blastLoc : allZones) {
                                detonateCataclysmZone(enc, mob, blastLoc, team, tier, skillMult);
                            }
                            enc.extraWarnings.clear();
                        }
                    }
                }
            }
        }
    }

    private void skillHit(Player player, double damage, Mob boss) {
        Double previous = scriptedImpacts.put(player.getUniqueId(), damage);
        try { player.damage(damage, boss); }
        finally {
            if (previous == null) scriptedImpacts.remove(player.getUniqueId());
            else scriptedImpacts.put(player.getUniqueId(), previous);
        }
        Encounter enc = owners.get(boss.getUniqueId());
        if (enc != null) rend(player, boss, damage * DungeonBalance.rendRatio(enc.site.tier()));
    }

    /**
     * Tier 6-8: a second, separate impact that ignores armor and Protection (sonic boom). It goes through the
     * scripted-impact budget, so this handler never caps, curses or rends it again.
     */
    private void rend(Player player, Mob source, double amount) {
        if (amount < 0.5 || !playable(player) || source == null || !source.isValid()) return;
        Double previous = scriptedImpacts.put(player.getUniqueId(), amount);
        try {
            player.setNoDamageTicks(0);
            player.damage(amount, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.SONIC_BOOM)
                .withCausingEntity(source).withDirectEntity(source).build());
        } finally {
            if (previous == null) scriptedImpacts.remove(player.getUniqueId());
            else scriptedImpacts.put(player.getUniqueId(), previous);
        }
    }

    private void detonateCataclysmZone(Encounter enc, Mob mob, Location blastLoc, List<Player> team, int tier, double skillMult) {
        if (blastLoc == null || blastLoc.getWorld() == null) return;
        switch(enc.site.kind()) {
            case SANCTUM_DARK -> {
                blastLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE,blastLoc.clone().add(0,1,0),40,1.0,1.0,1.0,0.05);
                blastLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,blastLoc.clone().add(0,1,0),35,1.5,1.0,1.5,0.08);
                for(Player p:team) {
                    if(p.getWorld()==blastLoc.getWorld()&&p.getLocation().distanceSquared(blastLoc)<=25) {
                        skillHit(p, 26.0 * skillMult, mob);
                        BossDamage.effect(p, new PotionEffect(PotionEffectType.WITHER,100 + tier * 15, tier >= 4 ? 2 : 1), bossName(mob));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,1));
                        if (tier >= 2) maybePlaceWeb(p,6000L);
                    }
                    p.playSound(blastLoc,Sound.ENTITY_WITHER_SHOOT,0.9f,0.8f);
                    p.playSound(blastLoc,Sound.ENTITY_WARDEN_SONIC_BOOM,0.7f,0.7f);
                }
            }
            case SANCTUM_ASTRAL -> {
                blastLoc.getWorld().spawnParticle(Particle.FLASH,blastLoc.clone().add(0,1,0),5,0.2,0.5,0.2,0.0,Color.WHITE);
                blastLoc.getWorld().spawnParticle(Particle.FIREWORK,blastLoc.clone().add(0,1,0),50,1.5,1.5,1.5,0.1);
                blastLoc.getWorld().strikeLightningEffect(blastLoc);
                for(Player p:team) {
                    if(p.getWorld()==blastLoc.getWorld()&&p.getLocation().distanceSquared(blastLoc)<=25) {
                        skillHit(p, 24.0 * skillMult, mob);
                        p.setVelocity(p.getVelocity().add(new Vector(0,0.85,0)));
                        p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(),p.getFreezeTicks()+120+tier*20));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,tier >= 4 ? 2 : 1));
                    }
                    p.playSound(blastLoc,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,0.8f,1.2f);
                }
            }
            case SANCTUM_TIME -> {
                blastLoc.getWorld().spawnParticle(Particle.SONIC_BOOM,blastLoc.clone().add(0,1,0),1);
                blastLoc.getWorld().spawnParticle(Particle.ENCHANT,blastLoc.clone().add(0,1,0),60,2.0,1.5,2.0,0.1);
                for(Player p:team) {
                    if(p.getWorld()==blastLoc.getWorld()&&p.getLocation().distanceSquared(blastLoc)<=36) {
                        skillHit(p, 22.0 * skillMult, mob);
                        Vector dir=blastLoc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.8);
                        dir.setY(0.2);
                        p.setVelocity(dir);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,tier >= 4 ? 3 : 1));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,120,tier >= 3 ? 2 : 1));
                        if (tier >= 2) maybePlaceWeb(p,5000L);
                    }
                    p.playSound(blastLoc,Sound.BLOCK_BEACON_DEACTIVATE,0.9f,0.7f);
                    p.playSound(blastLoc,Sound.ENTITY_WARDEN_SONIC_BOOM,0.8f,0.9f);
                }
            }
        }
    }

    /** Lightweight Tier 5 Cataclysm Arena Hazard triggered along the outer rune/gear ring (r = 12..15). */
    private void triggerTier5ArenaHazard(Encounter enc, Mob boss, List<Player> team) {
        World world = plugin.world();
        switch (enc.site.kind()) {
            case SANCTUM_DARK -> {
                for (int i = 0; i < 8; i++) {
                    double rad = i * Math.PI / 4.0;
                    Location node = position(enc.site, (int) Math.round(Math.cos(rad) * 13.0), 97, (int) Math.round(Math.sin(rad) * 13.0));
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, node.clone().add(0, 1.0, 0), 25, 0.5, 1.2, 0.5, 0.05);
                    world.spawnParticle(Particle.SCULK_SOUL, node.clone().add(0, 1.0, 0), 15, 0.4, 0.8, 0.4, 0.04);
                }
                for (Player p : team) {
                    double r = Math.hypot(p.getX() - (enc.site.x() + 0.5), p.getZ() - (enc.site.z() + 0.5));
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 0.7f);
                    if (Math.abs(r - 13.5) <= 2.2) {
                        skillHit(p, 14.0, boss);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                        BossDamage.effect(p, new PotionEffect(PotionEffectType.WITHER, 80, 1), bossName(boss));
                        p.sendActionBar(Component.text("☠ วงแหวนสุริยุปราคาปะทุ! ก้าวออกจากวงแหวนรอบนอก!", NamedTextColor.DARK_PURPLE));
                    }
                }
            }
            case SANCTUM_ASTRAL -> {
                for (int i = 0; i < 8; i++) {
                    double rad = i * Math.PI / 4.0;
                    Location starNode = position(enc.site, (int) Math.round(Math.cos(rad) * 12.0), 97, (int) Math.round(Math.sin(rad) * 12.0));
                    world.spawnParticle(Particle.END_ROD, starNode.clone().add(0, 1.5, 0), 30, 0.6, 1.5, 0.6, 0.08);
                    world.spawnParticle(Particle.FIREWORK, starNode.clone().add(0, 0.5, 0), 20, 0.8, 0.4, 0.8, 0.05);
                    if (i % 2 == 0) world.strikeLightningEffect(starNode);
                    for (Player p : team) {
                        if (p.getLocation().distanceSquared(starNode) <= 9.0) {
                            skillHit(p, 14.0, boss);
                            p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(), p.getFreezeTicks() + 140));
                            p.sendActionBar(Component.text("🌠 ดาวตกแปดทิศผ่าลงจุดเข็มทิศดารา!", NamedTextColor.AQUA));
                        }
                    }
                }
            }
            case SANCTUM_TIME -> {
                for (int i = 0; i < 12; i++) {
                    double rad = i * Math.PI / 6.0;
                    Location gearNode = position(enc.site, (int) Math.round(Math.cos(rad) * 15.0), 97, (int) Math.round(Math.sin(rad) * 15.0));
                    world.spawnParticle(Particle.REVERSE_PORTAL, gearNode.clone().add(0, 0.8, 0), 25, 0.6, 0.6, 0.6, 0.06);
                    if (i % 3 == 0) world.spawnParticle(Particle.SONIC_BOOM, gearNode.clone().add(0, 1.0, 0), 1);
                }
                Location center = position(enc.site, 0, 97, 0);
                for (Player p : team) {
                    double r = Math.hypot(p.getX() - (enc.site.x() + 0.5), p.getZ() - (enc.site.z() + 0.5));
                    p.playSound(p.getLocation(), Sound.BLOCK_COPPER_BULB_TURN_ON, 1.0f, 0.7f);
                    if (r >= 13.0) {
                        skillHit(p, 12.0, boss);
                        Vector pull = center.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.65);
                        pull.setY(0.25);
                        p.setVelocity(p.getVelocity().add(pull));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                        p.sendActionBar(Component.text("⏳ ฟันเฟืองกาลเวลาหมุนย้อน! ดึงร่างกลับเข้ากลางลาน!", NamedTextColor.GOLD));
                    }
                }
            }
        }
    }

    private void fireBossLaser(Encounter enc, Mob boss, Player target, List<Player> team) {
        enc.laserActive = true;
        enc.lastLaser = System.currentTimeMillis();
        final double laserDmgMult = DungeonBalance.skillMultiplier(enc.site.tier());

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 24; // 1.2 seconds total (24 ticks / 2 = 12 steps)
            Location lockedAimLoc = null;

            @Override
            public void run() {
                if (!boss.isValid() || !active.containsKey(enc.site.id()) || enc.finished) {
                    enc.laserActive = false;
                    cancel();
                    return;
                }
                if (!target.isOnline() || !target.isValid() || target.getWorld() != boss.getWorld()) {
                    enc.laserActive = false;
                    cancel();
                    return;
                }

                ticks += 2;

                Location eyeLoc = boss.getEyeLocation();
                // Snapshot aim at the initial round (no homing/tracking), giving player full 1.2s to dodge!
                if (lockedAimLoc == null) {
                    lockedAimLoc = target.getLocation().add(0, 1.0, 0);
                }

                // Draw targeting laser line
                Vector dir = lockedAimLoc.toVector().subtract(eyeLoc.toVector());
                double dist = dir.length();
                if (dist > 0.1 && dist <= 40.0) {
                    Vector step = dir.clone().normalize().multiply(0.7);
                    Location current = eyeLoc.clone();
                    // Red laser when targeting, flashing white/electric when about to fire!
                    Particle.DustOptions laserColor = ticks > 18
                        ? new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.4f)
                        : new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.2f);
                    int steps = (int) (dist / 0.7);
                    for (int i = 0; i < steps; i++) {
                        current.add(step);
                        current.getWorld().spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0, laserColor);
                    }
                }

                // Warning sound & Action Bar countdown
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, ticks > 18 ? 2.0f : 1.6f);
                double remainingSec = Math.max(0.0, (maxTicks - ticks) * 0.05);
                String msg = ticks > 18
                    ? "⚠ ลำแสงล็อคเป้าแล้ว! (0." + (maxTicks - ticks) + "s) แดชหลบทันที!"
                    : "⚠ บอสล็อคทิศทาง Sonic Boom! (" + String.format(Locale.ROOT, "%.1f", remainingSec) + "s) ก้าวหลบออกจากแนวเลเซอร์!";
                target.sendActionBar(Component.text(msg, ticks > 18 ? NamedTextColor.YELLOW : NamedTextColor.RED));

                // At 1.2s -> FIRE!
                if (ticks >= maxTicks) {
                    enc.laserActive = false;
                    cancel();

                    Location fireOrigin = boss.getEyeLocation();
                    Vector fireDir = lockedAimLoc.toVector().subtract(fireOrigin.toVector()).normalize();
                    double maxBeamRange = 36.0;

                    World world = boss.getWorld();
                    world.playSound(fireOrigin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f, 0.85f);
                    world.playSound(fireOrigin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 1.8f);

                    // Spawn dense beam particles
                    Location beamStep = fireOrigin.clone();
                    Vector increment = fireDir.clone().multiply(0.6);
                    int totalSteps = (int) (maxBeamRange / 0.6);

                    for (int i = 0; i < totalSteps; i++) {
                        beamStep.add(increment);

                        if (i % 4 == 0) {
                            world.spawnParticle(Particle.SONIC_BOOM, beamStep, 1);
                        }

                        switch (enc.site.kind()) {
                            case SANCTUM_DARK -> {
                                world.spawnParticle(Particle.SOUL_FIRE_FLAME, beamStep, 2, 0.1, 0.1, 0.1, 0.02);
                                world.spawnParticle(Particle.SQUID_INK, beamStep, 1, 0.05, 0.05, 0.05, 0.01);
                            }
                            case SANCTUM_ASTRAL -> {
                                world.spawnParticle(Particle.END_ROD, beamStep, 2, 0.1, 0.1, 0.1, 0.03);
                                world.spawnParticle(Particle.FIREWORK, beamStep, 1, 0.05, 0.05, 0.05, 0.02);
                            }
                            case SANCTUM_TIME -> {
                                world.spawnParticle(Particle.REVERSE_PORTAL, beamStep, 3, 0.1, 0.1, 0.1, 0.05);
                                world.spawnParticle(Particle.COPPER_FIRE_FLAME, beamStep, 1, 0.1, 0.1, 0.1, 0.02);
                            }
                        }
                    }

                    // Hit detection: Pierces blocks and checks players within 2.2 blocks of the beam ray
                    for (Player p : team) {
                        if (!p.isValid() || p.getWorld() != world || p.getGameMode() == GameMode.CREATIVE) continue;

                        Location pCenter = p.getLocation().add(0, 1.0, 0);
                        Vector ap = pCenter.toVector().subtract(fireOrigin.toVector());
                        double projection = ap.dot(fireDir);

                        if (projection >= 0 && projection <= maxBeamRange) {
                            Vector closestPoint = fireOrigin.toVector().add(fireDir.clone().multiply(projection));
                            double distSq = pCenter.toVector().distanceSquared(closestPoint);

                            if (distSq <= 2.2 * 2.2) {
                                skillHit(p, 26.0 * laserDmgMult, boss);

                                Vector impulse = fireDir.clone().multiply(1.2);
                                impulse.setY(0.45);
                                p.setVelocity(p.getVelocity().add(impulse));

                                switch (enc.site.kind()) {
                                    case SANCTUM_DARK -> {
                                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
                                        BossDamage.effect(p, new PotionEffect(PotionEffectType.WITHER, 80, 1), bossName(boss));
                                        p.sendActionBar(Component.text("💥 โดนลำแสงความมืดทะลวง! ติดตาบอดและคำสาป Wither!", NamedTextColor.DARK_PURPLE));
                                    }
                                    case SANCTUM_ASTRAL -> {
                                        p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(), p.getFreezeTicks() + 160));
                                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                                        p.sendActionBar(Component.text("💥 โดนลำแสงดวงดาวแช่แข็ง! ติด Slow และ Freeze!", NamedTextColor.AQUA));
                                    }
                                    case SANCTUM_TIME -> {
                                        ItemStack mainHand = p.getInventory().getItemInMainHand();
                                        if (mainHand != null && mainHand.getType() != Material.AIR) {
                                            p.setCooldown(mainHand.getType(), 80);
                                        }
                                        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 120, 1));
                                        p.sendActionBar(Component.text("💥 โดนลำแสงกาลเวลาหยุดนิ่ง! อาวุธติดคูลดาวน์ 4 วินาที!", NamedTextColor.GOLD));
                                    }
                                }
                                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.2f);
                                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    public boolean isSpawnIsland(Block b) {
        if (b == null || b.getWorld() != plugin.world()) return false;
        if (!plugin.getConfig().getBoolean("spawn-protection.enabled", true)) return false;
        double radius = plugin.getConfig().getDouble("spawn-protection.radius", 80.0);
        double x=b.getX(),z=b.getZ();
        return x*x+z*z<=radius*radius;
    }

    public boolean canBypassProtection(Player p) {
        if (p == null) return false;
        return p.getGameMode() == GameMode.CREATIVE && (p.isOp() || p.hasPermission("voidscape.admin") || p.hasPermission("evergarden.admin"));
    }

    private boolean protectedBlock(Block b) {
        if (b == null || b.getWorld() != plugin.world()) return false;
        if (isSpawnIsland(b)) return true;
        return plugin.layout().at(b.getX(), b.getZ(), 0) != null && b.getY() >= 94 && b.getY() <= 140;
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){
        Block b=e.getBlock();
        if(tempWebs.containsKey(b)||(b.getType()==Material.COBWEB&&plugin.layout().at(b.getX(),b.getZ(),0)!=null)){
            tempWebs.remove(b);
            e.setDropItems(false);
            return;
        }
        if(protectedBlock(b)&&!canBypassProtection(e.getPlayer())) {
            e.setCancelled(true);
            if (isSpawnIsland(b)) {
                e.getPlayer().sendActionBar(Component.text("✦ เกาะหลัก (Spawn Island) ได้รับการคุ้มครอง ไม่อนุญาตให้ขุดหรือทำลายบล็อก", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void placeBlock(BlockPlaceEvent e){
        if(protectedBlock(e.getBlock())&&!canBypassProtection(e.getPlayer())) {
            e.setCancelled(true);
            if (isSpawnIsland(e.getBlock())) {
                e.getPlayer().sendActionBar(Component.text("✦ เกาะหลัก (Spawn Island) ได้รับการคุ้มครอง ไม่อนุญาตให้วางบล็อก", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketEmpty(PlayerBucketEmptyEvent e){
        if(protectedBlock(e.getBlock())&&!canBypassProtection(e.getPlayer())) {
            e.setCancelled(true);
            if (isSpawnIsland(e.getBlock())) {
                e.getPlayer().sendActionBar(Component.text("✦ เกาะหลัก (Spawn Island) ได้รับการคุ้มครอง ไม่อนุญาตให้เทของเหลว", NamedTextColor.RED));
            } else {
                e.getPlayer().sendActionBar(Component.text("✦ วิหารศักดิ์สิทธิ์ได้รับการคุ้มครอง ไม่อนุญาตให้เทของเหลวหรือลาวา", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketFill(PlayerBucketFillEvent e){
        if(protectedBlock(e.getBlock())&&!canBypassProtection(e.getPlayer())) {
            e.setCancelled(true);
            if (isSpawnIsland(e.getBlock())) {
                e.getPlayer().sendActionBar(Component.text("✦ เกาะหลัก (Spawn Island) ได้รับการคุ้มครอง ไม่อนุญาตให้ตักของเหลว", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onMobEnvironmentalDamage(EntityDamageEvent e) {
        if (owners.containsKey(e.getEntity().getUniqueId())) {
            EntityDamageEvent.DamageCause cause = e.getCause();
            if (cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.DROWNING
                || cause == EntityDamageEvent.DamageCause.SUFFOCATION
                || cause == EntityDamageEvent.DamageCause.FALL) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onMobRegainHealth(EntityRegainHealthEvent e) {
        if (owners.containsKey(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onMobTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (e.getEntity() instanceof Mob mob && mob.getPersistentDataContainer().has(mobKey)) {
            cleanseMobIfTagged(mob);
            // Never each other: a stray hit or an illager grudge must not start a fight between them.
            if (e.getTarget() != null && owners.containsKey(e.getTarget().getUniqueId())) e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void sanctumIgnite(BlockIgniteEvent e) {
        if (encounterSource(e.getIgnitingEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void sanctumGrief(EntityChangeBlockEvent e) {
        if (owners.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void rootDeath(PlayerDeathEvent e) { unroot(e.getEntity().getUniqueId()); }

    @EventHandler(priority=EventPriority.MONITOR)
    public void rootQuit(PlayerQuitEvent e) { unroot(e.getPlayer().getUniqueId()); }

    // ---- Tier VI-VIII ---------------------------------------------------------------------------

    private String tierRules(int tier) {
        return switch (tier) {
            case 6 -> "§9✦ วิหารผนึกคราม [Tier VI] §f· บอสร่ายผนึกครามลงพื้น: 25% ของพลังชีวิต + ช้าลง · หลบออกจากวง!";
            case 7 -> "§5✦ วิหารผนึกม่วง [Tier VII] §f· มอนทุกตัวต้านอาวุธประชิด 70% (ใช้ธนูหรือเวท) · บอส 2 ตน · ผนึกม่วงทำให้อ่อนแรง";
            default -> "§4✦ วิหารผนึกโลหิต [Tier VIII] §f· มอนทุกตัวต้านธนูและเวท 50% (ใช้อาวุธประชิด) · บอส 2 ตน · ผนึกโลหิต + สกิลบอสแห่ง Sanctuary";
        };
    }

    /** The encounter that owns a damage or ignition source (a mob, its projectile, its evoker fangs), or null. */
    private Encounter encounterSource(Entity source) {
        if (source == null) return null;
        Entity owner = source;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) owner = shooter;
        else if (source instanceof EvokerFangs fangs && fangs.getOwner() != null) owner = fangs.getOwner();
        else if (source instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity caster) owner = caster;
        return owner instanceof Player ? null : owners.get(owner.getUniqueId());
    }

    private static boolean forgeItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> key.getNamespace().equals("evergardenforge"));
    }

    /** Tier VI-VIII mobs: one in five becomes an EvergardenForge elite of a rarity that rises with the tier. */
    private void makeUnique(Encounter enc, Mob mob, int tier) {
        var forge = Bukkit.getPluginManager().getPlugin("EvergardenForge");
        if (forge == null || !forge.isEnabled()) return;
        boolean high = ThreadLocalRandom.current().nextInt(100) < (tier >= 8 ? 40 : 50);
        String rarity = tier >= 8 ? (high ? "MYTHIC" : "EPIC") : tier == 7 ? (high ? "EPIC" : "RARE") : (high ? "RARE" : "UNCOMMON");
        try {
            Class<?> api = Class.forName("com.example.forge.ForgeApi", true, forge.getClass().getClassLoader());
            if (!(boolean) api.getMethod("makeElite", LivingEntity.class, String.class).invoke(null, mob, rarity)) {
                plugin.getLogger().info("[Sanctum] Forge refused a " + rarity + " unique " + mob.getType());
                return;
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
            plugin.getLogger().warning("[Sanctum] EvergardenForge elites unavailable: " + error);
            return;
        }
        var data = mob.getPersistentDataContainer();
        // The sanctum keeps authority over the numbers: +60% health and +25% damage, not the elite multipliers.
        double hp = Math.min(1024.0, Math.round(data.getOrDefault(mobTargetHpKey, PersistentDataType.DOUBLE, 100.0) * 1.6));
        data.set(mobTargetHpKey, PersistentDataType.DOUBLE, hp);
        data.set(mobTargetDmgKey, PersistentDataType.DOUBLE, data.getOrDefault(mobTargetDmgKey, PersistentDataType.DOUBLE, 10.0) * 1.25);
        String label = switch (rarity) { case "UNCOMMON" -> "Tempered"; case "RARE" -> "Arcane"; case "EPIC" -> "Exalted"; default -> "Divine"; };
        int color = switch (rarity) { case "UNCOMMON" -> 0x5EE87A; case "RARE" -> 0x4FB4FF; case "EPIC" -> 0xB47CFF; default -> 0xFF9A3C; };
        String base = data.getOrDefault(mobTargetNameKey, PersistentDataType.STRING, "[Tier " + enc.site.romanTier() + "]");
        data.set(mobTargetNameKey, PersistentDataType.STRING, base.replace("] ", "] ✦ " + label + " · "));
        data.set(uniqueColorKey, PersistentDataType.INTEGER, color);
        cleanseAndLockMob(mob, hp, data.get(mobTargetDmgKey, PersistentDataType.DOUBLE), data.get(mobTargetNameKey, PersistentDataType.STRING), null, false);
        mob.setHealth(hp);
        enc.uniques.add(mob.getUniqueId());
        plugin.getLogger().info("[Sanctum] unique " + rarity + " " + mob.getType() + " in " + enc.site.id());
    }

    /** Tier VII melee, Tier VIII arrows and spells, Seraph's undead bonus and the health ceiling. */
    private void tierDefence(EntityDamageByEntityEvent e, Encounter enc, Player attacker, Species victimSpecies) {
        int tier = enc.site.tier();
        var type = e.getDamageSource().getDamageType();
        boolean magic = type == org.bukkit.damage.DamageType.MAGIC || type == org.bukkit.damage.DamageType.INDIRECT_MAGIC;
        boolean ranged = !magic && e.getDamager() instanceof Projectile;
        double taken = magic ? DungeonBalance.magicTaken(tier) : ranged ? DungeonBalance.rangedTaken(tier) : DungeonBalance.meleeTaken(tier);
        if (taken < 1) {
            e.setDamage(e.getDamage() * taken);
            long now = System.currentTimeMillis();
            if (now >= resistHint.getOrDefault(attacker.getUniqueId(), 0L)) {
                resistHint.put(attacker.getUniqueId(), now + 4000);
                attacker.sendActionBar(Component.text(tier == 7 ? "⛨ ผนึกม่วงต้านอาวุธประชิด 70% · ใช้ธนูหรือเวทมนตร์"
                    : "⛨ ผนึกโลหิตต้านธนูและเวท 50% · ใช้อาวุธประชิด", tierNamedColor(tier)));
            }
        }
        // Full Seraph deals x4 to undead: Tier VI-VIII bosses take it as x2, like any other mob.
        if (victimSpecies == Species.BOSS && e.getEntity() instanceof LivingEntity victim && seraphUndead(attacker, victim)) e.setDamage(e.getDamage() * 0.5);
        Double scale = e.getEntity().getPersistentDataContainer().get(hpScaleKey, PersistentDataType.DOUBLE);
        if (scale != null && scale > 1) e.setDamage(e.getDamage() / scale);
    }

    private boolean seraphUndead(Player player, LivingEntity victim) {
        if (!(Bukkit.getPluginManager().getPlugin("advance-magic") instanceof com.example.advancemagic.AdvanceMagicPlugin magic) || !magic.isEnabled()) return false;
        var armor = magic.armorCombat();
        return armor != null && armor.fullSeraph(player) && com.example.advancemagic.item.ArmorCombatService.undead(victim);
    }

    /** With two bosses the bar shows their combined health. */
    private double bossBarProgress(Encounter enc, double single) {
        if (enc.bosses.size() <= 1) return single;
        double health = 0, max = 0;
        for (UUID id : enc.bosses) if (Bukkit.getEntity(id) instanceof LivingEntity boss && boss.isValid()) {
            health += boss.getHealth();
            var attribute = boss.getAttribute(Attribute.MAX_HEALTH);
            max += attribute == null ? boss.getHealth() : attribute.getValue();
        }
        return max <= 0 ? single : Math.max(0, Math.min(1, health / max));
    }

    private void tierSkills(Encounter enc, Mob boss, Player target, List<Player> team, long now) {
        int tier = enc.site.tier(), index = Math.max(0, enc.bosses.indexOf(boss.getUniqueId()));
        UUID id = boss.getUniqueId();
        long nextSeal = enc.nextSeal.computeIfAbsent(id, k -> now + 6000 + index * 5000L);
        if (now >= nextSeal && !enc.laserActive) {
            enc.nextSeal.put(id, now + (tier == 6 ? 13000 : 16000));
            castSeal(enc, boss, target, tier);
        }
        if (tier >= 8) {
            long nextSpecial = enc.nextSpecial.computeIfAbsent(id, k -> now + 11000 + index * 6000L);
            if (now >= nextSpecial && !enc.laserActive) {
                enc.nextSpecial.put(id, now + 21000);
                switch (ThreadLocalRandom.current().nextInt(3)) {
                    case 0 -> acheronSnare(enc, boss, target, team);
                    case 1 -> cocytusVolley(enc, boss, team);
                    default -> styxBerserk(enc, boss, team, now);
                }
            }
        }
        if (enc.berserkUntil.getOrDefault(id, 0L) > now) {
            boss.getWorld().spawnParticle(Particle.DUST, boss.getLocation().add(0, 1.4, 0), 14, 0.5, 0.8, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(0xD0102E), 1.6f));
        }
    }

    private Location floor(Encounter enc, Location at) {
        Location ground = at.clone();
        ground.setY(97.06);
        return ground;
    }

    /** The tier seal: azure (VI), violet (VII) or crimson (VIII), bursting 2.2 seconds later. */
    private void castSeal(Encounter enc, Mob boss, Player target, int tier) {
        String model = tier == 6 ? com.example.voidscape.boss.SanctumSigils.AZURE : tier == 7 ? com.example.voidscape.boss.SanctumSigils.VIOLET
            : com.example.voidscape.boss.SanctumSigils.CRIMSON;
        long now = System.currentTimeMillis();
        String key = "sanctum:" + enc.site.id() + ":seal:" + (enc.sealSerial++);
        enc.seals.add(new Seal(key, model, floor(enc, target.getLocation()), 5.5, now, now + 2200, boss.getUniqueId(), 0));
        String name = tier == 6 ? "ผนึกคราม" : tier == 7 ? "ผนึกม่วง" : "ผนึกโลหิต";
        for (Player p : players(enc.site)) {
            plugin.message(p, (tier == 6 ? "§9" : tier == 7 ? "§5" : "§4") + "⚠ บอสร่าย " + name + " · ออกจากวงเวท!");
            p.playSound(target.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, tier == 6 ? 1.4f : tier == 7 ? 1.0f : 0.6f);
        }
    }

    /** Acheron's binding circle: anyone still inside when it closes is rooted for three seconds. */
    private void acheronSnare(Encounter enc, Mob boss, Player target, List<Player> team) {
        long now = System.currentTimeMillis();
        String key = "sanctum:" + enc.site.id() + ":snare:" + (enc.sealSerial++);
        enc.seals.add(new Seal(key, com.example.voidscape.boss.SanctumSigils.ACHERON, floor(enc, target.getLocation()), 4.5, now, now + 1800, boss.getUniqueId(), 1));
        for (Player p : team) {
            plugin.message(p, "§c⚠ บอสใช้ 'วงผนึก Acheron' · ออกจากวงก่อนถูกตรึงขา!");
            p.playSound(target.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 0.7f);
        }
    }

    /** Cocytus' volley: three rings of sixteen arrows from the boss. */
    private void cocytusVolley(Encounter enc, Mob boss, List<Player> team) {
        for (Player p : team) plugin.message(p, "§b⚠ บอสใช้ 'ห่าธนูน้ำแข็ง Cocytus' · หาที่หลบ!");
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.2f, 0.5f);
        for (int ring = 0; ring < 3; ring++) {
            int wave = ring;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!boss.isValid() || boss.isDead() || enc.finished) return;
                Location eye = boss.getEyeLocation();
                for (int i = 0; i < 16; i++) {
                    double angle = Math.PI * 2 * i / 16 + wave * Math.PI / 16;
                    Vector direction = new Vector(Math.cos(angle), -0.04, Math.sin(angle)).normalize();
                    Arrow arrow = boss.getWorld().spawnArrow(eye.clone().add(direction.clone().multiply(1.2)), direction, 1.6f, 0f);
                    arrow.setShooter(boss);
                    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    arrow.setPersistent(false);
                    arrow.getPersistentDataContainer().set(volleyKey, PersistentDataType.BYTE, (byte) 1);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> { if (arrow.isValid()) arrow.remove(); }, 60);
                }
                boss.getWorld().spawnParticle(Particle.SNOWFLAKE, eye, 40, 0.6, 0.4, 0.6, 0.08);
                boss.getWorld().playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);
            }, 10L + ring * 6L);
        }
    }

    /** Styx' berserk: eight seconds of speed and 35% harder hits. */
    private void styxBerserk(Encounter enc, Mob boss, List<Player> team, long now) {
        enc.berserkUntil.put(boss.getUniqueId(), now + 8000);
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1, false, false));
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
        boss.getWorld().spawnParticle(Particle.DUST, boss.getLocation().add(0, 1.2, 0), 60, 0.8, 1.0, 0.8, 0,
            new Particle.DustOptions(Color.fromRGB(0xD0102E), 2.0f));
        for (Player p : team) {
            plugin.message(p, "§4⚠ บอสเข้าสู่ 'โหมด Berserker แห่ง Styx' 8 วินาที · ตีแรงขึ้น 35% และเร็วขึ้น!");
            p.showTitle(net.kyori.adventure.title.Title.title(Component.text("☠ BERSERK", NamedTextColor.DARK_RED),
                Component.text("Styx · 8 วินาที", NamedTextColor.RED),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(300))));
        }
    }

    /** Every two ticks: draw the seals growing, burst the ripe ones, hold rooted players. */
    private void sealTick() {
        long now = System.currentTimeMillis();
        for (Encounter enc : active.values()) {
            if (enc.seals.isEmpty() && enc.berserkUntil.isEmpty()) continue;
            sigils.frame(position(enc.site, 0, 97, 0), 72);
            for (var seal : new ArrayList<>(enc.seals)) {
                double grow = Math.min(1, (now - seal.born()) / (double) Math.max(1, seal.burstAt() - seal.born()) * 1.6);
                sigils.ground(seal.key(), seal.model(), seal.at(), seal.radius() * (0.35 + 0.65 * grow), now * 0.003 * (seal.kind() == 1 ? -1 : 1));
                if (now < seal.burstAt()) continue;
                enc.seals.remove(seal);
                sigils.remove(seal.key());
                Entity owner = Bukkit.getEntity(seal.boss());
                if (owner instanceof Mob boss && boss.isValid() && !boss.isDead()) burst(enc, seal, boss);
            }
            for (var entry : enc.berserkUntil.entrySet()) {
                if (entry.getValue() > now && Bukkit.getEntity(entry.getKey()) instanceof Mob boss && boss.isValid())
                    sigils.ground("sanctum:" + enc.site.id() + ":styx:" + entry.getKey(), com.example.voidscape.boss.SanctumSigils.STYX, floor(enc, boss.getLocation()), 2.6, now * 0.004);
                else sigils.remove("sanctum:" + enc.site.id() + ":styx:" + entry.getKey());
            }
            enc.berserkUntil.values().removeIf(until -> until <= now - 1000);
        }
        for (var entry : new ArrayList<>(roots.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || now >= entry.getValue().until()) { unroot(entry.getKey()); continue; }
            p.setVelocity(new Vector());
            if (now % 4 < 2) {
                Encounter enc = active.get(entry.getValue().site());
                if (enc != null) {
                    sigils.frame(position(enc.site, 0, 97, 0), 72);
                    sigils.ground("sanctum:" + enc.site.id() + ":root:" + entry.getKey(), com.example.voidscape.boss.SanctumSigils.ACHERON, floor(enc, p.getLocation()), 0.9, now * 0.006);
                }
            }
        }
    }

    private void burst(Encounter enc, Seal seal, Mob boss) {
        World world = seal.at().getWorld();
        int tier = enc.site.tier();
        Color color = seal.kind() == 1 ? Color.fromRGB(0xF54268) : tier == 6 ? Color.fromRGB(34, 180, 216) : tier == 7 ? Color.fromRGB(184, 84, 255) : Color.fromRGB(255, 48, 93);
        world.spawnParticle(Particle.DUST, seal.at().clone().add(0, 0.6, 0), 80, seal.radius() * 0.5, 0.5, seal.radius() * 0.5, 0, new Particle.DustOptions(color, 2.0f));
        world.spawnParticle(Particle.END_ROD, seal.at().clone().add(0, 0.4, 0), 30, seal.radius() * 0.4, 0.3, seal.radius() * 0.4, 0.05);
        world.playSound(seal.at(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.2f, seal.kind() == 1 ? 0.5f : 0.8f);
        world.playSound(seal.at(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        String bossName = bossName(boss);
        for (Player p : players(enc.site)) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getWorld() != world) continue;
            Location at = p.getLocation();
            if (Math.hypot(at.getX() - seal.at().getX(), at.getZ() - seal.at().getZ()) > seal.radius() || Math.abs(at.getY() - seal.at().getY()) > 3.5) continue;
            if (seal.kind() == 1) {
                sealHit(p, 6.0 * DungeonBalance.damageMultiplier(tier), boss);
                root(p, enc, 3000);
                p.sendActionBar(Component.text("⛓ ผนึก Acheron ตรึงขา 3 วินาที!", NamedTextColor.RED));
                continue;
            }
            // A quarter of the player's maximum health, through armour and Protection.
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            sealHit(p, (max == null ? 20 : max.getValue()) * 0.25, boss);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
            if (tier >= 7) p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
            if (tier >= 8) {
                BossDamage.effect(p, new PotionEffect(PotionEffectType.WITHER, 100, 1), bossName);
                BossDamage.effect(p, new PotionEffect(PotionEffectType.POISON, 100, 0), bossName);
            }
        }
    }

    /** Seal damage: exact (difficulty never rescales it), through armour and Protection; the boss is named on death. */
    private void sealHit(Player player, double damage, Mob boss) {
        exactSeal.put(player.getUniqueId(), damage);
        try {
            BossDamage.scoped(player, bossName(boss), () -> player.damage(damage,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.SONIC_BOOM).build()));
        } finally {
            exactSeal.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void sealExact(EntityDamageEvent e) {
        Double exact = exactSeal.get(e.getEntity().getUniqueId());
        if (exact != null && e.getDamageSource().getDamageType() == org.bukkit.damage.DamageType.SONIC_BOOM) e.setDamage(exact);
    }

    private void root(Player player, Encounter enc, long millis) {
        roots.put(player.getUniqueId(), new Root(System.currentTimeMillis() + millis, enc.site.id(), player.getLocation()));
        for (Attribute attribute : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH)) {
            var instance = player.getAttribute(attribute);
            if (instance == null) continue;
            instance.removeModifier(rootKey);
            instance.addTransientModifier(new org.bukkit.attribute.AttributeModifier(rootKey, -1, org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void unroot(UUID id) {
        Root root = roots.remove(id);
        if (root != null) sigils.remove("sanctum:" + root.site() + ":root:" + id);
        Player player = Bukkit.getPlayer(id);
        if (player == null) return;
        for (Attribute attribute : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH)) {
            var instance = player.getAttribute(attribute);
            if (instance != null) instance.removeModifier(rootKey);
        }
    }

    /** Test hook: the expansion deciding Tier VI-VIII shrines. */
    public com.example.voidscape.world.SanctumExpansion expansion() { return expansion; }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void trample(PlayerInteractEvent e){
        if(e.getAction()==Action.PHYSICAL&&e.getClickedBlock()!=null) {
            if(protectedBlock(e.getClickedBlock())&&!canBypassProtection(e.getPlayer())) {
                e.setCancelled(true);
            }
        } else if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&e.getClickedBlock()!=null) {
            if(protectedBlock(e.getClickedBlock())&&!canBypassProtection(e.getPlayer())) {
                ItemStack item = e.getItem();
                if(item != null) {
                    String name = item.getType().name();
                    if(name.endsWith("_HOE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE")
                        || item.getType() == Material.FLINT_AND_STEEL || item.getType() == Material.FIRE_CHARGE) {
                        e.setCancelled(true);
                        e.getPlayer().sendActionBar(Component.text("✦ เกาะหลัก (Spawn Island) ได้รับการคุ้มครอง ไม่อนุญาตให้ดัดแปลงบล็อก", NamedTextColor.RED));
                    }
                }
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(EntityExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(BlockExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pistonExtend(org.bukkit.event.block.BlockPistonExtendEvent e){
        if(protectedBlock(e.getBlock().getRelative(e.getDirection()))||e.getBlocks().stream().anyMatch(b->protectedBlock(b)||protectedBlock(b.getRelative(e.getDirection()))))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pistonRetract(org.bukkit.event.block.BlockPistonRetractEvent e){
        if(e.getBlocks().stream().anyMatch(b->protectedBlock(b)||protectedBlock(b.getRelative(e.getDirection()))))e.setCancelled(true);
    }
    @EventHandler public void quit(PlayerQuitEvent e){seen.remove(e.getPlayer().getUniqueId());removeTrueDeathBar(e.getPlayer().getUniqueId());}
    @EventHandler public void changeWorld(PlayerChangedWorldEvent e){seen.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void chunk(org.bukkit.event.world.ChunkLoadEvent e) {
        if(e.getWorld()!=plugin.world())return;
        for(Entity entity:e.getChunk().getEntities())if(entity.getPersistentDataContainer().has(mobKey)&&!owners.containsKey(entity.getUniqueId()))entity.remove();
    }
    private void remove(Encounter enc) {
        for(UUID id:enc.mobs.keySet()){owners.remove(id);Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
        enc.mobs.clear();enc.casterCooldowns.clear();enc.casterVexCooldowns.clear();
        enc.laserActive = false;
        for(var seal:enc.seals)sigils.remove(seal.key());
        enc.seals.clear();enc.bosses.clear();enc.uniques.clear();enc.berserkUntil.clear();
        sigils.removePrefix("sanctum:"+enc.site.id()+":");
        for(var entry:new ArrayList<>(roots.entrySet()))if(entry.getValue().site().equals(enc.site.id()))unroot(entry.getKey());
        clearWebs(enc.site);
        if(enc.bar!=null)enc.bar.removeAll();
    }
    public boolean resetAllCooldowns() {
        if(!storageHealthy)return false;
        Map<String,Object> previous=new LinkedHashMap<>();
        var sites=ledger.getConfigurationSection("sites");
        if(sites!=null)for(String siteId:sites.getKeys(false)) {
            String cooldownPath="sites."+siteId+".next-open";
            if(ledger.contains(cooldownPath))previous.put(cooldownPath,ledger.get(cooldownPath));
        }
        previous.keySet().forEach(key->ledger.set(key,null));
        if(save())return true;
        previous.forEach(ledger::set);
        return false;
    }
    public void clearAllDungeonMobs() {
        for(Encounter enc : new ArrayList<>(active.values())) remove(enc);
        active.clear();
        clearWebs(null);
    }
    public LivingEntity spawnTestBoss(Location where, DungeonLayout.Kind kind) {
        Encounter enc = new Encounter(new DungeonLayout.Site(kind, where.getBlockX(), where.getBlockZ(), 0L, 3), 1);
        enc.bossStarted = true;
        active.put(enc.site.id(), enc);
        LivingEntity b = spawn(enc, Species.BOSS, where, 1);
        if (b != null) {
            String title = switch(kind) {
                case SANCTUM_DARK -> "จอมมารแห่งความมืด (Shadow Overlord)";
                case SANCTUM_ASTRAL -> "อัครเทวทูตดวงดาว (Astral Archon)";
                case SANCTUM_TIME -> "ผู้พิทักษ์กาลเวลา (Chronos Vanguard)";
            };
            enc.bar = Bukkit.createBossBar(com.example.voidscape.boss.BossNames.legacy("[TEST BOSS] " + title), BarColor.PURPLE, BarStyle.SEGMENTED_10);
            for (Player p : where.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(where) <= 2500) {
                    enc.bar.addPlayer(p);
                }
            }
        }
        return b;
    }
    public void close(){for(Encounter enc:active.values())remove(enc);active.clear();clearWebs(null);
        for(UUID id:new ArrayList<>(roots.keySet()))unroot(id);sigils.close();expansion.flush();trueDeathBars.values().forEach(BossBar::removeAll);trueDeathBars.clear();if(storageHealthy)save();}
    public int waveCount(){return plugin.integer("combat.waves",5,2,12);}
    public int waveCount(Site site) {
        if (site == null) return waveCount();
        return switch (Math.clamp(site.tier(), 1, 5)) {
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 4;
            case 4 -> 5;
            default -> 5;
        };
    }
}
