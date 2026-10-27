"""Patch Evergarden Pet system: PetCompanions.java and PetMenu.java."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def patch_pet_companions():
    p = ROOT / 'evergarden/src/main/java/com/example/voidscape/pet/PetCompanions.java'
    content = p.read_text(encoding='utf-8')

    old_target = """        PetBody body=out.get(player.getUniqueId());
        if(body!=null&&body.record==record){
            body.target=null;body.stuck=0;
            if(body.perched!=mode.equals("perch"))rebuild(player);
        }"""

    new_replacement = """        PetBody body=out.get(player.getUniqueId());
        if(body!=null&&body.record==record){
            body.target=null;body.stuck=0;
            if(body.perched!=mode.equals("perch"))rebuild(player);
            else body.updateNametag();
        }"""
    assert old_target in content, "Could not find mode update in PetCompanions.java"
    content = content.replace(old_target, new_replacement)
    p.write_text(content, encoding='utf-8')
    print("Patched PetCompanions.java successfully!")

def patch_pet_menu():
    p = ROOT / 'evergarden/src/main/java/com/example/voidscape/pet/PetMenu.java'
    content = p.read_text(encoding='utf-8')

    # 1. Update applyName to call body.updateNametag()
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

    # 2. Update chestStable book lore
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

    # 3. Update formGuide for Bedrock
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

    # 4. Add /pet foods and /pet guide command
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

    # 5. Add to tab complete
    old_tab = 'return List.of("summon","dismiss","list","name","mode","skills","hide").stream()'
    new_tab = 'return List.of("summon","dismiss","list","name","mode","skills","hide","foods","guide").stream()'
    assert old_tab in content, "Could not find old_tab in PetMenu.java"
    content = content.replace(old_tab, new_tab)

    p.write_text(content, encoding='utf-8')
    print("Patched PetMenu.java successfully!")

def main():
    patch_pet_companions()
    patch_pet_menu()
    print("PetCompanions and PetMenu patched successfully!")

if __name__ == '__main__':
    main()
