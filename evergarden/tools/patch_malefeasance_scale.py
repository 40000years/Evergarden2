import re

# 1. RiverVisual.java
with open('evergarden/src/main/java/com/example/voidscape/boss/RiverVisual.java', 'r', encoding='utf-8') as f:
    rv = f.read()

old_rv_part = """    static final double SCALE=4.0;
    // Sized from the approved rigs at scale 1; leave air above crowns and horns.
    static double haloHeight(ArkhaiaWaves.Kind kind){
        return SCALE*switch(kind){
            case ACHERON,MALEFEASANCE->2.725;case PHLEGETHON->2.65;
            case COCYTUS->2.975;case BANSHEE_WAIL->3.075;case STYX->2.9;
        };
    }
    static double haloRadius(ArkhaiaWaves.Kind kind){
        return SCALE*switch(kind){
            case ACHERON->.83;case PHLEGETHON->.97;case COCYTUS->.84;
            case BANSHEE_WAIL->1;case MALEFEASANCE->1.09;case STYX->1.01;
        };
    }"""

new_rv_part = """    static final double SCALE=4.0;
    static double scale(ArkhaiaWaves.Kind kind){
        return kind == ArkhaiaWaves.Kind.MALEFEASANCE ? 2.0 : SCALE;
    }
    static double scale(String model){
        return (model != null && model.contains("malefeasance")) ? 2.0 : SCALE;
    }
    // Sized from the approved rigs at scale 1; leave air above crowns and horns.
    static double haloHeight(ArkhaiaWaves.Kind kind){
        return scale(kind)*switch(kind){
            case ACHERON,MALEFEASANCE->2.725;case PHLEGETHON->2.65;
            case COCYTUS->2.975;case BANSHEE_WAIL->3.075;case STYX->2.9;
        };
    }
    static double haloRadius(ArkhaiaWaves.Kind kind){
        return scale(kind)*switch(kind){
            case ACHERON->.83;case PHLEGETHON->.97;case COCYTUS->.84;
            case BANSHEE_WAIL->1;case MALEFEASANCE->1.09;case STYX->1.01;
        };
    }"""

rv_unified = rv.replace('\r\n', '\n')
assert old_rv_part in rv_unified, "old_rv_part not found"
rv_mod = rv_unified.replace(old_rv_part, new_rv_part)
rv_mod = rv_mod.replace('eyeHeight=SCALE*(echo?.6:1)', 'eyeHeight=scale(kind)*(echo?.6:1)')
with open('evergarden/src/main/java/com/example/voidscape/boss/RiverVisual.java', 'w', encoding='utf-8', newline='\r\n') as f:
    f.write(rv_mod)
print('RiverVisual.java patched successfully!')

# 2. ModelEngineArkhaia.java
with open('evergarden/src/main/java/com/example/voidscape/boss/ModelEngineArkhaia.java', 'r', encoding='utf-8') as f:
    me = f.read()
old_me_line = '@Override public ArkhaiaModelSupport.Rig attachRiver(Mob target,String model){return new Rig(target,model,RiverVisual.SCALE);}'
new_me_line = '@Override public ArkhaiaModelSupport.Rig attachRiver(Mob target,String model){return new Rig(target,model,RiverVisual.scale(model));}'
me_unified = me.replace('\r\n', '\n')
assert old_me_line in me_unified, "old_me_line not found"
me_mod = me_unified.replace(old_me_line, new_me_line)
with open('evergarden/src/main/java/com/example/voidscape/boss/ModelEngineArkhaia.java', 'w', encoding='utf-8', newline='\r\n') as f:
    f.write(me_mod)
print('ModelEngineArkhaia.java patched successfully!')

# 3. ArkhaiaMinions.java
with open('evergarden/src/main/java/com/example/voidscape/boss/ArkhaiaMinions.java', 'r', encoding='utf-8') as f:
    am = f.read()
old_am_line = 'double size(){return echo?2.4:RiverVisual.SCALE;}'
new_am_line = 'double size(){return kind==ArkhaiaWaves.Kind.MALEFEASANCE?(echo?1.2:2.0):(echo?2.4:RiverVisual.SCALE);}'
am_unified = am.replace('\r\n', '\n')
assert old_am_line in am_unified, "old_am_line not found"
am_mod = am_unified.replace(old_am_line, new_am_line)
with open('evergarden/src/main/java/com/example/voidscape/boss/ArkhaiaMinions.java', 'w', encoding='utf-8', newline='\r\n') as f:
    f.write(am_mod)
print('ArkhaiaMinions.java patched successfully!')

# 4. arkhaia_river_assets.py
with open('evergarden/tools/arkhaia_river_assets.py', 'r', encoding='utf-8') as f:
    ra = f.read()
old_ra_call = 'bedrock.update(export_model(model,model_id,4))'
new_ra_call = 'bedrock.update(export_model(model,model_id,2 if name == "malefeasance" else 4))'
old_p2_call = "bedrock.update(export_model(phase2,'arkhaia_malefeasance_phase2',4))"
new_p2_call = "bedrock.update(export_model(phase2,'arkhaia_malefeasance_phase2',2))"
assert old_ra_call in ra, "old_ra_call not found"
assert old_p2_call in ra, "old_p2_call not found"
ra_mod = ra.replace(old_ra_call, new_ra_call).replace(old_p2_call, new_p2_call)
with open('evergarden/tools/arkhaia_river_assets.py', 'w', encoding='utf-8', newline='\n') as f:
    f.write(ra_mod)
print('arkhaia_river_assets.py patched successfully!')
