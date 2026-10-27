from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# 1. Patch PetTerrain.java
terrain_path = ROOT / "src/main/java/com/example/voidscape/pet/PetTerrain.java"
terrain_code = terrain_path.read_text(encoding="utf-8")

old_terrain = """        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(!world.getWorldBorder().isInside(new Location(world,x+dx+.5,y,z+dz+.5)))return "นอกขอบโลก";
            Block floor=world.getBlockAt(x+dx,y-1,z+dz);Material ground=floor.getType();
            if(!ground.isSolid())return "ไม่มีพื้นแข็ง ("+ground.name().toLowerCase()+")";
            if(HAZARDS.contains(ground))return "พื้นอันตราย ("+ground.name().toLowerCase()+")";
            if(wild&&(Tag.LEAVES.isTagged(ground)||Tag.LOGS.isTagged(ground)))return "พื้นเป็นใบไม้/ขอนไม้";
            if(wild&&plugin.structureProtection()!=null&&plugin.structureProtection().protects(floor))return "พื้นเป็นสิ่งก่อสร้างที่ป้องกันไว้";
            for(int dy=0;dy<headroom;dy++){
                Block space=world.getBlockAt(x+dx,y+dy,z+dz);
                if(!space.isPassable()||space.isLiquid()||HAZARDS.contains(space.getType()))return "ที่ว่างเหนือพื้นไม่พอ ("+space.getType().name().toLowerCase()+")";
            }
        }"""

new_terrain = """        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(!world.getWorldBorder().isInside(new Location(world,x+dx+.5,y,z+dz+.5)))return "นอกขอบโลก";
            int fy=y-1;
            Block floor=world.getBlockAt(x+dx,fy,z+dz);
            if((dx!=0||dz!=0)&&!floor.getType().isSolid()){
                Block step=world.getBlockAt(x+dx,fy-1,z+dz);
                if(step.getType().isSolid()&&floor.isPassable()){floor=step;fy--;}
            }else if((dx!=0||dz!=0)&&world.getBlockAt(x+dx,y,z+dz).getType().isSolid()){
                Block step=world.getBlockAt(x+dx,y,z+dz);
                if(step.getType().isSolid()){floor=step;fy=y;}
            }
            Material ground=floor.getType();
            if(!ground.isSolid())return "ไม่มีพื้นแข็ง ("+ground.name().toLowerCase()+")";
            if(HAZARDS.contains(ground))return "พื้นอันตราย ("+ground.name().toLowerCase()+")";
            if(wild&&(Tag.LEAVES.isTagged(ground)||Tag.LOGS.isTagged(ground)))return "พื้นเป็นใบไม้/ขอนไม้";
            if(wild&&plugin.structureProtection()!=null&&plugin.structureProtection().protects(floor))return "พื้นเป็นสิ่งก่อสร้างที่ป้องกันไว้";
            for(int dy=fy+1;dy<=fy+headroom;dy++){
                Block space=world.getBlockAt(x+dx,dy,z+dz);
                if(!space.isPassable()||space.isLiquid()||HAZARDS.contains(space.getType()))return "ที่ว่างเหนือพื้นไม่พอ ("+space.getType().name().toLowerCase()+")";
            }
        }"""

assert old_terrain in terrain_code.replace("\r\n", "\n")
terrain_code = terrain_code.replace("\r\n", "\n").replace(old_terrain, new_terrain)
terrain_path.write_text(terrain_code, encoding="utf-8")
print("PetTerrain.java patched successfully")

# 2. Patch WildPetSpawns.java
spawns_path = ROOT / "src/main/java/com/example/voidscape/pet/WildPetSpawns.java"
spawns_code = spawns_path.read_text(encoding="utf-8")

# Deeper ground scanning
old_ground = """    private static double groundLevel(Player player){
        Location at=player.getLocation();double ground=PetTerrain.groundBelow(at,64);
        return Double.isNaN(ground)?at.getY():ground;
    }"""
new_ground = """    private static double groundLevel(Player player){
        Location at=player.getLocation();double ground=PetTerrain.groundBelow(at,128);
        return Double.isNaN(ground)?at.getY():ground;
    }"""
assert old_ground in spawns_code.replace("\r\n", "\n")
spawns_code = spawns_code.replace("\r\n", "\n").replace(old_ground, new_ground)

# Dynamic fallback for stage & floor scanning
old_scan = "double y=PetTerrain.floorBetween(world,x,z,base+12,base-24);"
new_scan = "double y=PetTerrain.floorBetween(world,x,z,base+16,base-36);"
assert old_scan in spawns_code
spawns_code = spawns_code.replace(old_scan, new_scan)

old_attempt = """            Location at=findNaturalSpot(player,stage);
            if(at==null){rolls.put(id,lastRoll);continue;}
            var species=chooseSpecies(random);"""

new_attempt = """            Location at=findNaturalSpot(player,stage);
            if(at==null&&stage!=PetCatalog.Stage.BABY){
                stage=stage==PetCatalog.Stage.ADULT?PetCatalog.Stage.MEDIUM:PetCatalog.Stage.BABY;
                at=findNaturalSpot(player,stage);
                if(at==null&&stage!=PetCatalog.Stage.BABY){
                    stage=PetCatalog.Stage.BABY;
                    at=findNaturalSpot(player,stage);
                }
            }
            if(at==null){rolls.put(id,lastRoll);continue;}
            var species=chooseSpecies(random);
            List<PetCatalog.Species> recent=recentHistory.get(id);
            if(recent!=null&&recent.contains(species)&&random.nextDouble()<0.75){
                species=chooseSpecies(random);
            }"""

assert old_attempt in spawns_code
spawns_code = spawns_code.replace(old_attempt, new_attempt)

# Add recentHistory map & recording
old_map = "    private final Map<UUID,Long> drought=new HashMap<>();"
new_map = """    private final Map<UUID,Long> drought=new HashMap<>();
    private final Map<UUID,List<PetCatalog.Species>> recentHistory=new HashMap<>();"""
assert old_map in spawns_code
spawns_code = spawns_code.replace(old_map, new_map)

old_record = "            drought.remove(id);"
new_record = """            drought.remove(id);
            recentHistory.computeIfAbsent(id,k->new ArrayList<>()).add(species);
            if(recentHistory.get(id).size()>4)recentHistory.get(id).remove(0);"""
assert old_record in spawns_code
spawns_code = spawns_code.replace(old_record, new_record)

spawns_path.write_text(spawns_code, encoding="utf-8")
print("WildPetSpawns.java patched successfully")
