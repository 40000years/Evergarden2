with open('evergarden/src/main/java/com/example/voidscape/boss/RiverVisual.java', 'r', encoding='utf-8') as f:
    code = f.read()

code = code.replace('private final ArkhaiaModelSupport.Rig rig;', 'private ArkhaiaModelSupport.Rig rig;', 1)

method = """
    void switchModel(String newModelId){
        if(closed)return;
        rig.close();
        rig=java.util.Objects.requireNonNull(plugin.arkhaiaModels().attachRiver(mob,newModelId),"River model unavailable: "+newModelId);
        clip="";
        if(carrier!=null&&plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().ready()){
            plugin.arkhaiaBedrock().register(carrier.getUniqueId(),newModelId);
        }
    }
"""

idx = code.find('carrier=spawned;\n    }')
if idx == -1:
    idx = code.find('carrier=spawned;\r\n    }')
    end_bracket = idx + len('carrier=spawned;\r\n    }')
else:
    end_bracket = idx + len('carrier=spawned;\n    }')

code = code[:end_bracket] + method + code[end_bracket:]

with open('evergarden/src/main/java/com/example/voidscape/boss/RiverVisual.java', 'w', encoding='utf-8') as f:
    f.write(code)

print('Patched RiverVisual.java successfully!')
