"""Patch Evergarden Pet system to add floating nametags, bilingual taming guides, and right-click info."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def patch_pet_taming():
    p = ROOT / 'evergarden/src/main/java/com/example/voidscape/pet/PetTaming.java'
    content = p.read_text(encoding='utf-8')

    # 1. Add defaultFood and foodNameThai
    old_target = """    Material food(PetCatalog.Species species){
        String configured=plugin.getConfig().getString("pets.taming.food."+species.id);
        Material material=configured==null?null:Material.matchMaterial(configured);
        return material!=null&&material.isItem()?material:DEFAULT_FOOD.get(species);
    }
    static String foodName(Material material){"""

    new_replacement = """    static Material defaultFood(PetCatalog.Species species){return DEFAULT_FOOD.get(species);}
    Material food(PetCatalog.Species species){
        String configured=plugin.getConfig().getString("pets.taming.food."+species.id);
        Material material=configured==null?null:Material.matchMaterial(configured);
        return material!=null&&material.isItem()?material:DEFAULT_FOOD.get(species);
    }
    static String foodName(Material material){"""
    
    assert old_target in content, "Could not find food target in PetTaming.java"
    content = content.replace(old_target, new_replacement)

    # 2. Add foodNameThai method
    old_foodname = """    static String foodName(Material material){
        String raw=material.name().toLowerCase(Locale.ROOT).replace('_',' ');
        StringBuilder name=new StringBuilder();
        for(String word:raw.split(" "))name.append(name.isEmpty()?"":" ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return name.toString();
    }"""

    new_foodname = """    static String foodName(Material material){
        String raw=material.name().toLowerCase(Locale.ROOT).replace('_',' ');
        StringBuilder name=new StringBuilder();
        for(String word:raw.split(" "))name.append(name.isEmpty()?"":" ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return name.toString();
    }
    static String foodNameThai(Material material){
        if(material==null)return "ไม่มี";
        return switch(material){
            case SWEET_BERRIES -> "สวีทเบอร์รี่ (Sweet Berries)";
            case COD -> "ปลาค็อดดิบ (Raw Cod)";
            case GLOW_BERRIES -> "โกลว์เบอร์รี่ (Glow Berries)";
            case CRIMSON_FUNGUS -> "เห็ดคริมสัน (Crimson Fungus)";
            case MUTTON -> "เนื้อแกะดิบ (Raw Mutton)";
            case REDSTONE -> "ผงเรดสโตน (Redstone Dust)";
            case LAPIS_LAZULI -> "ลาพิส ลาซูลี (Lapis Lazuli)";
            case BONE_MEAL -> "ผงกระดูก (Bone Meal)";
            case SEAGRASS -> "หญ้าทะเล (Seagrass)";
            case PRISMARINE_CRYSTALS -> "คริสตัลปริซมารีน (Prismarine Crystals)";
            case GOLD_NUGGET -> "เม็ดทองคำ (Gold Nugget)";
            case HONEY_BOTTLE -> "ขวดน้ำผึ้ง (Honey Bottle)";
            case GOLDEN_CARROT -> "แครอททองคำ (Golden Carrot)";
            case BLAZE_POWDER -> "ผงเบลซ (Blaze Powder)";
            case FEATHER -> "ขนนก (Feather)";
            case GLOWSTONE_DUST -> "ผงโกลว์สโตน (Glowstone Dust)";
            case BEEF -> "เนื้อวัวดิบ (Raw Beef)";
            default -> foodName(material);
        };
    }"""
    assert old_foodname in content, "Could not find old_foodname in PetTaming.java"
    content = content.replace(old_foodname, new_foodname)

    # 3. Update feed method and add showTamingInfo
    old_feed = """        if(hand.getType()==wanted&&!plain(hand)){plugin.message(player,"สัตว์กินได้เฉพาะ "+foodName(wanted)+" ธรรมดา ไม่ใช่ไอเทมพิเศษ");return true;}
        if(hand.getType()!=wanted){
            if(isFood(hand.getType())){
                plugin.message(player,body.species.display+" ไม่สนใจสิ่งนี้ · ชอบ "+foodName(wanted));
                player.playSound(body.location(),Sound.ENTITY_VILLAGER_NO,.6f,1.4f);return true;
            }
            return false;
        }"""

    new_feed = """        String wantedNameThai=foodNameThai(wanted);
        if(hand.getType()==wanted&&!plain(hand)){plugin.message(player,"สัตว์กินได้เฉพาะ "+wantedNameThai+" ธรรมดา ไม่ใช่ไอเทมพิเศษ");return true;}
        if(hand.getType()!=wanted){
            if(body.tamer!=null&&!mine&&now<body.tamerUntil){
                Player other=Bukkit.getPlayer(body.tamer);
                String otherName=other!=null?other.getName():"ผู้เล่นอื่น";
                plugin.message(player,"มีผู้เล่น ("+otherName+") กำลังผูกมิตรกับตัวนี้อยู่ · รอสักครู่");
                player.sendActionBar(Component.text(body.species.thai+" · กำลังผูกมิตรกับ "+otherName,NamedTextColor.YELLOW));
                player.playSound(body.location(),Sound.ENTITY_VILLAGER_NO,.6f,1.4f);
                return true;
            }
            if(isFood(hand.getType())){
                plugin.message(player,body.species.thai+" ("+body.species.display+") ไม่สนใจสิ่งนี้ · ชอบ "+wantedNameThai);
                player.sendActionBar(Component.text(body.species.thai+" ชอบ "+wantedNameThai,NamedTextColor.YELLOW));
                player.playSound(body.location(),Sound.ENTITY_VILLAGER_NO,.6f,1.4f);
                return true;
            }
            showTamingInfo(body,player,wanted,wantedNameThai);
            return true;
        }"""
    assert old_feed in content, "Could not find old_feed in PetTaming.java"
    content = content.replace(old_feed, new_feed)

    # 4. Add body.updateNametag() after feed
    old_after_gain = """            // A happy wild pet sometimes sheds a Seraph Feather (daily-capped).
            double drop=plugin.getConfig().getDouble("seraph-trader.feathers.wild-feed-chance",.2);
            if(plugin.seraphTrader()!=null&&random.nextDouble()<drop)plugin.seraphTrader().feathers().grant(player,1,body.species.display+" ทำขนนกร่วง",true);
        }
        wild.greet(body,player,true);"""

    new_after_gain = """            // A happy wild pet sometimes sheds a Seraph Feather (daily-capped).
            double drop=plugin.getConfig().getDouble("seraph-trader.feathers.wild-feed-chance",.2);
            if(plugin.seraphTrader()!=null&&random.nextDouble()<drop)plugin.seraphTrader().feathers().grant(player,1,body.species.display+" ทำขนนกร่วง",true);
        }
        body.updateNametag();
        wild.greet(body,player,true);"""
    assert old_after_gain in content, "Could not find old_after_gain in PetTaming.java"
    content = content.replace(old_after_gain, new_after_gain)

    # 5. Make bar() package-private and add showTamingInfo method
    old_bar = "    private static String bar(int value){int filled=Math.round(value/10f);return \"▮\".repeat(filled)+\"▯\".repeat(10-filled);}"
    new_bar = """    void showTamingInfo(PetBody body,Player player,Material wanted,String wantedNameThai){
        player.playSound(body.location(),Sound.ENTITY_FOX_SNIFF,.8f,1.2f);
        Location at=body.location().add(0,1,0);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,at,4,.3,.3,.3,0);
        player.sendActionBar(Component.text("✦ " + body.species.thai + " · อาหารที่ชอบ: " + wantedNameThai, NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ สัตว์เลี้ยงป่า: ", NamedTextColor.GOLD)
            .append(Component.text(body.species.thai + " (" + body.species.display + ")", NamedTextColor.AQUA))
            .append(Component.text(" · " + body.stage.thai, NamedTextColor.LIGHT_PURPLE)));
        player.sendMessage(Component.text(" • อาหารที่ชอบ: ", NamedTextColor.GRAY)
            .append(Component.text(wantedNameThai, NamedTextColor.YELLOW)));
        player.sendMessage(Component.text(" • ความไว้ใจ: ", NamedTextColor.GRAY)
            .append(Component.text(bar(body.trust) + " " + body.trust + "%", NamedTextColor.LIGHT_PURPLE)));
        if(body.stage == PetCatalog.Stage.BABY){
            player.sendMessage(Component.text(" • วิธีเทม: ", NamedTextColor.GRAY)
                .append(Component.text("ถือ " + wantedNameThai + " ในมือหลัก แล้วคลิกขวาเพื่อป้อนอาหารจนไว้ใจครบ 100%", NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text(" • วิธีเทม: ", NamedTextColor.GRAY)
                .append(Component.text("ถือ " + wantedNameThai + " แล้วคลิกขวาเพื่อป้อนจนครบ 100% ", NamedTextColor.WHITE))
                .append(Component.text("(ตัวโตแล้วต้องใช้เชือกจูง Lead 1 เส้นเพื่อพากลับ)", NamedTextColor.YELLOW)));
        }
        player.sendMessage(Component.empty());
        wild.greet(body,player,true);
    }
    static String bar(int value){int filled=Math.round(value/10f);return "▮".repeat(filled)+"▯".repeat(10-filled);}"""
    assert old_bar in content, "Could not find old_bar in PetTaming.java"
    content = content.replace(old_bar, new_bar)

    p.write_text(content, encoding='utf-8')
    print("Patched PetTaming.java successfully!")

def patch_pet_body():
    p = ROOT / 'evergarden/src/main/java/com/example/voidscape/pet/PetBody.java'
    content = p.read_text(encoding='utf-8')

    # Imports check: TextDisplay, Display, Transformation, Vector3f, Quaternionf
    old_imports = """import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;"""
    new_imports = """import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.format.TextColor;"""
    assert old_imports in content, "Could not find old_imports in PetBody.java"
    content = content.replace(old_imports, new_imports)

    # Field addition
    old_fields = """    final Rabbit shell;
    final ArmorStand carrier;
    final NativePetRenderer.Rig rig;"""
    new_fields = """    final Rabbit shell;
    final ArmorStand carrier;
    final NativePetRenderer.Rig rig;
    final TextDisplay nametag;"""
    assert old_fields in content, "Could not find old_fields in PetBody.java"
    content = content.replace(old_fields, new_fields)

    # Constructor update
    old_ctor = """    private PetBody(VoidscapePlugin plugin,Rabbit shell,ArmorStand carrier,NativePetRenderer.Rig rig,PetCatalog.Species species,PetCatalog.Stage stage){
        this.plugin=plugin;this.shell=shell;this.carrier=carrier;this.rig=rig;this.species=species;this.stage=stage;
        home=shell.getLocation();yaw=targetYaw=home.getYaw();
    }"""
    new_ctor = """    private PetBody(VoidscapePlugin plugin,Rabbit shell,ArmorStand carrier,NativePetRenderer.Rig rig,TextDisplay nametag,PetCatalog.Species species,PetCatalog.Stage stage){
        this.plugin=plugin;this.shell=shell;this.carrier=carrier;this.rig=rig;this.nametag=nametag;this.species=species;this.stage=stage;
        home=shell.getLocation();yaw=targetYaw=home.getYaw();
    }"""
    assert old_ctor in content, "Could not find old_ctor in PetBody.java"
    content = content.replace(old_ctor, new_ctor)

    # Spawn update
    old_spawn_vars = "        Rabbit shell=null;NativePetRenderer.Rig rig=null;ArmorStand carrier=null;"
    new_spawn_vars = "        Rabbit shell=null;NativePetRenderer.Rig rig=null;ArmorStand carrier=null;TextDisplay nametag=null;"
    assert old_spawn_vars in content, "Could not find old_spawn_vars in PetBody.java"
    content = content.replace(old_spawn_vars, new_spawn_vars)

    old_spawn_body = """            PetBody body=new PetBody(plugin,shell,carrier,rig,species,stage);
            body.owner=owner;body.record=record;body.perched=perch>0;return body;"""
    new_spawn_body = """            if(perch<=0){
                float tagHeight=(switch(stage){case BABY->0.75f;case MEDIUM->1.15f;case ADULT->1.55f;})*size+0.15f;
                nametag=at.getWorld().spawn(at,TextDisplay.class,e->{
                    e.setPersistent(false);e.setBillboard(Display.Billboard.CENTER);
                    e.setAlignment(TextDisplay.TextAlignment.CENTER);
                    e.setViewRange(0.8f);e.setShadowed(true);e.setDefaultBackground(true);
                    e.setVisibleByDefault(false);
                    e.getPersistentDataContainer().set(plugin.key("pet_body"),PersistentDataType.BYTE,(byte)1);
                    e.getPersistentDataContainer().set(plugin.key("wild_pet_visual"),PersistentDataType.STRING,finalShell.getUniqueId().toString());
                    e.setTransformation(new Transformation(
                        new Vector3f(0,tagHeight,0),new Quaternionf(),new Vector3f(0.85f),new Quaternionf()
                    ));
                });
                shell.addPassenger(nametag);
            }
            PetBody body=new PetBody(plugin,shell,carrier,rig,nametag,species,stage);
            body.owner=owner;body.record=record;body.perched=perch>0;
            body.updateNametag();
            return body;"""
    
    # We need finalShell reference for lambda
    old_shell_def = "        try{\n            shell=at.getWorld().spawn("
    new_shell_def = "        try{\n            Rabbit spawnedShell=at.getWorld().spawn("
    # Actually let's make it clean without reassigning
    old_spawn_block = """        Rabbit shell=null;NativePetRenderer.Rig rig=null;ArmorStand carrier=null;
        boolean bedrock=plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().ready();
        try{
            shell=at.getWorld().spawn(at,Rabbit.class,CreatureSpawnEvent.SpawnReason.CUSTOM,entity->{"""
    
    new_spawn_block = """        Rabbit shell=null;NativePetRenderer.Rig rig=null;ArmorStand carrier=null;TextDisplay nametag=null;
        boolean bedrock=plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().ready();
        try{
            shell=at.getWorld().spawn(at,Rabbit.class,CreatureSpawnEvent.SpawnReason.CUSTOM,entity->{"""
    
    # Let's check old_spawn_body context
    old_spawn_tail = """            PetBody body=new PetBody(plugin,shell,carrier,rig,species,stage);
            body.owner=owner;body.record=record;body.perched=perch>0;return body;
        }catch(RuntimeException|LinkageError error){
            if(rig!=null)try{rig.close();}catch(RuntimeException|LinkageError ignored){}
            if(carrier!=null){plugin.arkhaiaBedrock().unregister(carrier.getUniqueId());carrier.remove();}
            if(shell!=null)shell.remove();"""

    new_spawn_tail = """            final UUID shellId=shell.getUniqueId();
            if(perch<=0){
                float tagHeight=(switch(stage){case BABY->0.75f;case MEDIUM->1.15f;case ADULT->1.55f;})*size+0.15f;
                nametag=at.getWorld().spawn(at,TextDisplay.class,e->{
                    e.setPersistent(false);e.setBillboard(Display.Billboard.CENTER);
                    e.setAlignment(TextDisplay.TextAlignment.CENTER);
                    e.setViewRange(0.8f);e.setShadowed(true);e.setDefaultBackground(true);
                    e.setVisibleByDefault(false);
                    e.getPersistentDataContainer().set(plugin.key("pet_body"),PersistentDataType.BYTE,(byte)1);
                    e.getPersistentDataContainer().set(plugin.key("wild_pet_visual"),PersistentDataType.STRING,shellId.toString());
                    e.setTransformation(new Transformation(
                        new Vector3f(0,tagHeight,0),new Quaternionf(),new Vector3f(0.85f),new Quaternionf()
                    ));
                });
                shell.addPassenger(nametag);
            }
            PetBody body=new PetBody(plugin,shell,carrier,rig,nametag,species,stage);
            body.owner=owner;body.record=record;body.perched=perch>0;
            body.updateNametag();
            return body;
        }catch(RuntimeException|LinkageError error){
            if(nametag!=null)nametag.remove();
            if(rig!=null)try{rig.close();}catch(RuntimeException|LinkageError ignored){}
            if(carrier!=null){plugin.arkhaiaBedrock().unregister(carrier.getUniqueId());carrier.remove();}
            if(shell!=null)shell.remove();"""

    assert old_spawn_vars in content, "Could not find old_spawn_vars in PetBody.java"
    content = content.replace(old_spawn_vars, new_spawn_vars)

    assert old_spawn_tail in content, "Could not find old_spawn_tail in PetBody.java"
    content = content.replace(old_spawn_tail, new_spawn_tail)

    # Update remove()
    old_remove = """    void remove(){
        try{rig.close();}
        finally{
            shell.remove();"""
    new_remove = """    void remove(){
        try{rig.close();}
        finally{
            if(nametag!=null)nametag.remove();
            shell.remove();"""
    assert old_remove in content, "Could not find old_remove in PetBody.java"
    content = content.replace(old_remove, new_remove)

    # Update adopt()
    old_adopt = """        shell.customName(Component.text(newRecord.name()+" · "+species.display+" · "+stage.thai,NamedTextColor.AQUA));
        if(carrier!=null)carrier.getPersistentDataContainer().remove(plugin.key("wild_pet"));
    }"""
    new_adopt = """        shell.customName(Component.text(newRecord.name()+" · "+species.display+" · "+stage.thai,NamedTextColor.AQUA));
        if(carrier!=null)carrier.getPersistentDataContainer().remove(plugin.key("wild_pet"));
        updateNametag();
    }"""
    assert old_adopt in content, "Could not find old_adopt in PetBody.java"
    content = content.replace(old_adopt, new_adopt)

    # Add updateNametag method
    method_to_add = """    void updateNametag(){
        Component title;
        if(wild()){
            TextColor titleColor=species.mythical()?NamedTextColor.GOLD:NamedTextColor.AQUA;
            Component line1=Component.text("✦ "+species.thai+" · "+stage.thai,titleColor)
                .append(Component.text(" ("+species.display+")",NamedTextColor.GRAY));
            Component line2;
            if(trust>=100){
                if(stage==PetCatalog.Stage.BABY)line2=Component.text("✔ พร้อมผูกมิตร! (คลิกขวา)",NamedTextColor.GREEN);
                else line2=Component.text("✔ ไว้ใจแล้ว! (ต้องใช้เชือกจูง Lead พากลับ)",NamedTextColor.YELLOW);
            }else if(trust>0){
                Player tamerPlayer=tamer!=null?Bukkit.getPlayer(tamer):null;
                String tamerName=tamerPlayer!=null?tamerPlayer.getName():"ผู้เล่นอื่น";
                line2=Component.text("ความไว้ใจ: ",NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(PetTaming.bar(trust)+" "+trust+"%",NamedTextColor.WHITE))
                    .append(Component.text(" ("+tamerName+")",NamedTextColor.GRAY));
            }else{
                Material food=PetTaming.defaultFood(species);
                String foodStr=PetTaming.foodNameThai(food);
                line2=Component.text("อาหาร: ",NamedTextColor.GOLD)
                    .append(Component.text(foodStr,NamedTextColor.YELLOW))
                    .append(Component.text(" · คลิกขวาดูวิธีเทม",NamedTextColor.GRAY));
            }
            title=line1.append(Component.newline()).append(line2);
            if(carrier!=null&&carrier.isValid()){
                carrier.customName(Component.text("✦ "+species.thai+" · "+stage.thai+" ("+PetTaming.foodNameThai(PetTaming.defaultFood(species))+")",NamedTextColor.AQUA));
                carrier.setCustomNameVisible(true);
            }
        }else{
            String petName=record!=null?record.name():species.thai;
            TextColor nameColor=record!=null&&record.aura?NamedTextColor.GOLD:NamedTextColor.AQUA;
            Component line1=Component.text(petName,nameColor).append(Component.text(" · "+stage.thai,NamedTextColor.GRAY));
            Player ownerPlayer=owner!=null?Bukkit.getPlayer(owner):null;
            String ownerName=ownerPlayer!=null?ownerPlayer.getName():"ผู้เล่น";
            String modeName=record!=null?PetSkills.modeName(record.mode):"เดินตาม";
            Component line2=Component.text("สัตว์เลี้ยงของ ",NamedTextColor.DARK_AQUA)
                .append(Component.text(ownerName,NamedTextColor.WHITE))
                .append(Component.text(" · "+modeName,NamedTextColor.YELLOW));
            title=line1.append(Component.newline()).append(line2);
            if(carrier!=null&&carrier.isValid()){
                carrier.customName(Component.text(petName+" · สัตว์เลี้ยงของ "+ownerName,NamedTextColor.AQUA));
                carrier.setCustomNameVisible(true);
            }
        }
        if(nametag!=null&&nametag.isValid())nametag.text(title);
    }
}"""
    # Replace last closing brace
    assert content.endswith('}\n') or content.endswith('}'), "Content does not end with brace"
    idx = content.rfind('}')
    content = content[:idx] + method_to_add + "\n"

    p.write_text(content, encoding='utf-8')
    print("Patched PetBody.java successfully!")

def patch_pet_bodies():
    p = ROOT / 'evergarden/src/main/java/com/example/voidscape/pet/PetBodies.java'
    content = p.read_text(encoding='utf-8')

    # 1. add / remove tracking parts
    old_add = """    void add(PetBody body,Manager manager){
        managers.put(body,manager);parts.put(body.shell.getUniqueId(),body);
        if(body.carrier!=null)parts.put(body.carrier.getUniqueId(),body);
    }"""
    new_add = """    void add(PetBody body,Manager manager){
        managers.put(body,manager);parts.put(body.shell.getUniqueId(),body);
        if(body.carrier!=null)parts.put(body.carrier.getUniqueId(),body);
        if(body.nametag!=null)parts.put(body.nametag.getUniqueId(),body);
    }"""
    assert old_add in content, "Could not find old_add in PetBodies.java"
    content = content.replace(old_add, new_add)

    old_remove = """    void remove(PetBody body){
        managers.remove(body);parts.remove(body.shell.getUniqueId());
        if(body.carrier!=null)parts.remove(body.carrier.getUniqueId());
        for(Viewer viewer:viewers.values())viewer.shown.remove(body);
        body.remove();
    }"""
    new_remove = """    void remove(PetBody body){
        managers.remove(body);parts.remove(body.shell.getUniqueId());
        if(body.carrier!=null)parts.remove(body.carrier.getUniqueId());
        if(body.nametag!=null)parts.remove(body.nametag.getUniqueId());
        for(Viewer viewer:viewers.values())viewer.shown.remove(body);
        body.remove();
    }"""
    assert old_remove in content, "Could not find old_remove in PetBodies.java"
    content = content.replace(old_remove, new_remove)

    # 2. show / hide
    old_show = """    private void show(Player player,PetBody body,String kind){
        if(kind.equals("java")){
            // The receiver carries the parts; a client must track it to place them. A perched
            // body's parts move on their own, and its receiver would only block the owner's clicks.
            if(!body.perched)player.showEntity(plugin,body.shell);
            for(ItemDisplay part:body.rig.displays())player.showEntity(plugin,part);
        }else if(kind.equals("bedrock")&&body.carrier!=null){"""
    new_show = """    private void show(Player player,PetBody body,String kind){
        if(kind.equals("java")){
            // The receiver carries the parts; a client must track it to place them. A perched
            // body's parts move on their own, and its receiver would only block the owner's clicks.
            if(!body.perched)player.showEntity(plugin,body.shell);
            for(ItemDisplay part:body.rig.displays())player.showEntity(plugin,part);
            if(body.nametag!=null&&!body.perched)player.showEntity(plugin,body.nametag);
        }else if(kind.equals("bedrock")&&body.carrier!=null){"""
    assert old_show in content, "Could not find old_show in PetBodies.java"
    content = content.replace(old_show, new_show)

    old_hide = """    private void hide(Player player,PetBody body){
        player.hideEntity(plugin,body.shell);
        for(ItemDisplay part:body.rig.displays())player.hideEntity(plugin,part);
        if(body.carrier!=null)player.hideEntity(plugin,body.carrier);
        body.bedrockViewers.remove(player.getUniqueId());
    }"""
    new_hide = """    private void hide(Player player,PetBody body){
        player.hideEntity(plugin,body.shell);
        for(ItemDisplay part:body.rig.displays())player.hideEntity(plugin,part);
        if(body.nametag!=null)player.hideEntity(plugin,body.nametag);
        if(body.carrier!=null)player.hideEntity(plugin,body.carrier);
        body.bedrockViewers.remove(player.getUniqueId());
    }"""
    assert old_hide in content, "Could not find old_hide in PetBodies.java"
    content = content.replace(old_hide, new_hide)

    # 3. cleanOrphans
    old_clean = """    private void cleanOrphans(Collection<Entity> entities){
        for(Entity entity:entities)if(part(entity)&&!parts.containsKey(entity.getUniqueId())){
            String owner=entity.getPersistentDataContainer().get(plugin.key("wild_pet_visual"),PersistentDataType.STRING);
            PetBody body=owner==null?null:parts.get(PetStable.uuid(owner));
            if(body==null||!body.rig.displays().contains(entity))entity.remove();
        }
    }"""
    new_clean = """    private void cleanOrphans(Collection<Entity> entities){
        for(Entity entity:entities)if(part(entity)&&!parts.containsKey(entity.getUniqueId())){
            String owner=entity.getPersistentDataContainer().get(plugin.key("wild_pet_visual"),PersistentDataType.STRING);
            PetBody body=owner==null?null:parts.get(PetStable.uuid(owner));
            if(body==null||(!body.rig.displays().contains(entity)&&body.nametag!=entity))entity.remove();
        }
    }"""
    assert old_clean in content, "Could not find old_clean in PetBodies.java"
    content = content.replace(old_clean, new_clean)

    p.write_text(content, encoding='utf-8')
    print("Patched PetBodies.java successfully!")

def patch_pet_menu():
    p = ROOT / 'evergarden/src/main/java/com/example/voidscape/pet/PetMenu.java'
    content = p.read_text(encoding='utf-8')

    # 1. Update rename to call body.updateNametag()
    old_rename_body = """        PetBody body=companions.body(player.getUniqueId());
        if(body!=null&&body.record==record)body.shell.customName(Component.text(name+" · "+record.species.display+" · "+record.stage.thai,NamedTextColor.AQUA));
        plugin.message(player,"ตั้งชื่อเป็น "+name+" แล้ว");"""
    new_rename_body = """        PetBody body=companions.body(player.getUniqueId());
        if(body!=null&&body.record==record){
            body.shell.customName(Component.text(name+" · "+record.species.display+" · "+record.stage.thai,NamedTextColor.AQUA));
            body.updateNametag();
        }
        plugin.message(player,"ตั้งชื่อเป็น "+name+" แล้ว");"""
    assert old_rename_body in content, "Could not find old_rename_body in PetMenu.java"
    content = content.replace(old_rename_body, new_rename_body)

    # 2. Update setMode to call body.updateNametag()
    old_setmode = """    private void setMode(Player player,UUID id,String mode){
        if(!PetSkills.MODES.contains(mode)){plugin.message(player,"โหมดที่ใช้ได้: follow, stay, perch, assist");return;}
        PetStable stable=companions.stable(player);PetRecord record=stable.get(id);if(record==null)return;
        record.mode=mode;stable.dirty=true;
        plugin.message(player,"เปลี่ยนโหมดของ "+record.name()+" เป็น "+PetSkills.modeName(mode));
        if(mode.equals("perch")||id.equals(stable.active)&&companions.body(player.getUniqueId())!=null&&companions.body(player.getUniqueId()).perched)
            companions.rebuild(player);
    }"""
    new_setmode = """    private void setMode(Player player,UUID id,String mode){
        if(!PetSkills.MODES.contains(mode)){plugin.message(player,"โหมดที่ใช้ได้: follow, stay, perch, assist");return;}
        PetStable stable=companions.stable(player);PetRecord record=stable.get(id);if(record==null)return;
        record.mode=mode;stable.dirty=true;
        plugin.message(player,"เปลี่ยนโหมดของ "+record.name()+" เป็น "+PetSkills.modeName(mode));
        if(mode.equals("perch")||id.equals(stable.active)&&companions.body(player.getUniqueId())!=null&&companions.body(player.getUniqueId()).perched)
            companions.rebuild(player);
        else{
            PetBody body=companions.body(player.getUniqueId());
            if(body!=null&&body.record==record)body.updateNametag();
        }
    }"""
    assert old_setmode in content, "Could not find old_setmode in PetMenu.java"
    content = content.replace(old_setmode, new_setmode)

    # 3. Update chestStable book lore
    old_chest_book = """        List<String> foods=new ArrayList<>();
        for(var species:WildPetSpawns.eligibleSpecies())foods.add(species.display+": "+PetTaming.foodName(taming.food(species)));
        foods.add("");foods.add("ร่างกลางและโตเต็มวัยต้องมีเชือกจูง (Lead)");
        put(holder,51,button(Material.BOOK,"วิธีผูกมิตร · อาหารที่ชอบ",NamedTextColor.YELLOW,foods),null);"""

    new_chest_book = """        List<String> foods=new ArrayList<>();
        for(var species:WildPetSpawns.eligibleSpecies())foods.add(species.thai+" ("+species.display+"): "+PetTaming.foodNameThai(taming.food(species)));
        foods.add("");foods.add("คลิกขวาป้อนอาหารจนไว้ใจครบ 100%");foods.add("ร่างกลางและโตเต็มวัยต้องมีเชือกจูง (Lead) พากลับ");
        put(holder,51,button(Material.BOOK,"วิธีผูกมิตร · อาหารที่ชอบ",NamedTextColor.YELLOW,foods),null);"""
    assert old_chest_book in content, "Could not find old_chest_book in PetMenu.java"
    content = content.replace(old_chest_book, new_chest_book)

    # 4. Update formGuide for Bedrock
    old_form_guide = """    private void formGuide(Player player){
        StringBuilder text=new StringBuilder("หาสัตว์ป่าใน Evergarden แล้วคลิกขวาพร้อมอาหารที่มันชอบจนความไว้ใจครบ 100%\\nร่างกลางและโตเต็มวัยต้องมีเชือกจูง (Lead)\\n");
        for(var species:WildPetSpawns.eligibleSpecies())text.append("\\n§e").append(species.display).append("§f: ").append(PetTaming.foodName(taming.food(species)));
        send(player,handle(SimpleForm.builder().title("วิธีผูกมิตร").content(text.toString()).button("« กลับ"),List.of(()->formStable(player))).build());
    }"""

    new_form_guide = """    private void formGuide(Player player){
        StringBuilder text=new StringBuilder("หาสัตว์ป่าใน Evergarden แล้วคลิกขวาพร้อมอาหารที่มันชอบจนความไว้ใจครบ 100%\\nร่างกลางและโตเต็มวัยต้องมีเชือกจูง (Lead) พากลับ\\n");
        for(var species:WildPetSpawns.eligibleSpecies())text.append("\\n§e").append(species.thai).append(" (").append(species.display).append(")\\n§f  ชอบ: §a").append(PetTaming.foodNameThai(taming.food(species)));
        send(player,handle(SimpleForm.builder().title("วิธีผูกมิตร · อาหารที่ชอบ").content(text.toString()).button("« กลับ"),List.of(()->formStable(player))).build());
    }"""
    assert old_form_guide in content, "Could not find old_form_guide in PetMenu.java"
    content = content.replace(old_form_guide, new_form_guide)

    # 5. Add /pet foods and /pet guide command
    old_command_switch = """            case "hide"->{stable.hideOthers=!stable.hideOthers;stable.dirty=true;plugin.message(player,"ซ่อนสัตว์เลี้ยงของคนอื่น: "+(stable.hideOthers?"เปิด":"ปิด"));}
            case "list"->{"""

    new_command_switch = """            case "foods","food","guide","tame","taming"->{
                plugin.message(player,"=== รายการอาหารสำหรับเทมสัตว์เลี้ยง 12 ชนิด ===");
                for(var sp:WildPetSpawns.eligibleSpecies()){
                    Material f=taming.food(sp);
                    player.sendMessage(Component.text("✦ "+sp.thai+" ("+sp.display+"): ",NamedTextColor.AQUA)
                        .append(Component.text(PetTaming.foodNameThai(f),NamedTextColor.YELLOW)));
                }
                player.sendMessage(Component.text("วิธีเทม: คลิกขวาป้อนอาหารจนไว้ใจครบ 100% (ตัวโตต้องมีเชือกจูง Lead พากลับ)",NamedTextColor.GRAY));
            }
            case "hide"->{stable.hideOthers=!stable.hideOthers;stable.dirty=true;plugin.message(player,"ซ่อนสัตว์เลี้ยงของคนอื่น: "+(stable.hideOthers?"เปิด":"ปิด"));}
            case "list"->{"""
    assert old_command_switch in content, "Could not find old_command_switch in PetMenu.java"
    content = content.replace(old_command_switch, new_command_switch)

    # 6. Add to tab complete
    old_tab = 'return List.of("summon","dismiss","list","name","mode","skills","hide").stream()'
    new_tab = 'return List.of("summon","dismiss","list","name","mode","skills","hide","foods","guide").stream()'
    assert old_tab in content, "Could not find old_tab in PetMenu.java"
    content = content.replace(old_tab, new_tab)

    p.write_text(content, encoding='utf-8')
    print("Patched PetMenu.java successfully!")

def main():
    patch_pet_taming()
    patch_pet_body()
    patch_pet_bodies()
    patch_pet_menu()
    print("All pet patches applied successfully!")

if __name__ == '__main__':
    main()
