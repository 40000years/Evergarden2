from pathlib import Path
import sys,zipfile
root=Path.cwd();sys.path.insert(0,str(root/'tools'))
import build_plugin_jars as build
out=root/'output/nether-arena-prototype-20261029';out.mkdir(parents=True,exist_ok=True)
build.OUT=out
src=root/'evergarden/src/main/java'
own=('com/example/voidscape/VoidscapePlugin','com/example/voidscape/world/OverworldSanctuaries','com/example/voidscape/world/NetherArenaWorldgen')
sources=[src/(p+'.java') for p in own]
build.compile_sources('nether-arena',sources,[str(root/'dist/evergarden.jar'),str(root/'dist/advance-magic.jar')]+build.pinned_classpath(build.EVERGARDEN_ARGS),out/'classes')
def replaced(name):return any(name==p+'.class' or name.startswith(p+'$') for p in own)
asset='dimensional-sanctuaries/nether-arena.ows'
with zipfile.ZipFile(root/'dist/evergarden.jar') as base,zipfile.ZipFile(out/'evergarden.jar','w',zipfile.ZIP_DEFLATED) as jar:
    for item in base.infolist():
        if replaced(item.filename) or item.filename in (asset,'plugin.yml','config.yml'):continue
        jar.writestr(item,base.read(item.filename))
    for path in sorted((out/'classes').rglob('*.class')):
        name=path.relative_to(out/'classes').as_posix()
        if not replaced(name):raise RuntimeError('Unexpected class '+name)
        jar.write(path,name)
    plugin=base.read('plugin.yml').decode('utf8').splitlines(keepends=True)
    plugin=["version: '3.0.0-nether-arena-prototype'\n" if line.startswith('version:') else line for line in plugin]
    jar.writestr('plugin.yml',''.join(plugin).encode('utf8'))
    config=base.read('config.yml').decode('utf8')
    if '\nnether-arena:' not in config:
        config += '\n# Experimental buried arena; enable only in a test Nether.\nnether-arena:\n  enabled: false\n  worlds: []\n'
    jar.writestr('config.yml',config.encode('utf8'))
    jar.write(root/'evergarden/src/main/resources'/asset,asset)
build.check_jar(out/'evergarden.jar')
print(out/'evergarden.jar')
