"""Run portal routing regression checks on an isolated local Paper server."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-server', type=Path, required=True)
parser.add_argument('--garden-jar', type=Path, required=True)
parser.add_argument('--server', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
server = args.server.resolve()
server.mkdir(parents=True, exist_ok=False)
source = args.source_server.resolve()
for name in ('libraries', 'versions', 'cache'):
    shutil.copytree(source / name, server / name)
plugins = server / 'plugins'
plugins.mkdir()
shutil.copy2(args.garden_jar, plugins / 'evergarden.jar')
shutil.copy2(root / 'dist/advance-magic.jar', plugins / 'advance-magic.jar')
for name in ('Evergarden', 'advance-magic'):
    folder = plugins / name
    folder.mkdir()
    (folder / 'config.yml').write_text('config-version: 2\nresource-pack:\n  enabled: false\n'
        '  host:\n    enabled: false\n  geyser:\n    auto-install: false\n'
        'world-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n', encoding='utf8')
deps = [*plugins.glob('*.jar'), *(server / 'versions').rglob('*.jar'), *(server / 'libraries').rglob('*.jar')]
classes = server / 'check-classes'
classes.mkdir()
probe = server / 'PortalProbe.java'
probe.write_text('''import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.example.voidscape.VoidscapePlugin;
import java.nio.file.*;
public final class PortalProbe extends JavaPlugin {
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
                if (!garden.isEnabled()) throw new AssertionError("Evergarden unavailable");
                PortalTravelChecks.run(garden);
                Files.writeString(Path.of("portal-result.txt"),"PASS portal routing regression");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE,"Portal checks failed",error);
                try { Files.writeString(Path.of("portal-result.txt"),"FAIL " + error); }
                catch (Exception ignored) {}
            } finally { Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5); }
        },40);
    }
}''', encoding='utf8')
subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, deps)),
                '-d', str(classes), str(probe), str(Path(__file__).with_name('PortalTravelChecks.java'))], check=True)
(classes / 'plugin.yml').write_text("name: PortalProbe\nversion: '1.0'\nmain: PortalProbe\napi-version: '26.2'\ndepend: [Evergarden, advance-magic]\n", encoding='utf8')
subprocess.run(['jar', '--create', '--file', str(plugins / 'portal-probe.jar'), '-C', str(classes), '.'], check=True)
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
    'level-name=world\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\n'
    'spawn-protection=0\nmax-tick-time=120000\n')
shutil.copy2(source / 'paper.jar', server / 'paper.jar')
print('Isolated server:', server, flush=True)
with (server / 'console.log').open('w', encoding='utf8') as log:
    subprocess.run(['java', '-Xms256M', '-Xmx1536M', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                    '-jar', 'paper.jar', '--nogui', '--noconsole'], cwd=server, stdout=log,
                   stderr=subprocess.STDOUT, timeout=240, check=True,
                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
report = (server / 'portal-result.txt').read_text(encoding='utf8')
print(report, flush=True)
raise SystemExit(0 if report.startswith('PASS') else 1)
