"""Start the dev server, run a scripted scenario through the console, collect the log.

Usage: python3 tools/servertest/driver.py <repo dir> <scenario.txt> <output.log>

Scenario lines: a console command, `wait <seconds>`, `== label` (written into the log) or `# comment`.
Uses Carpet's fake players (`player <name> spawn`) as test players. WARNING: deletes run/world and
overwrites run/server.properties with a flat test world every time.
"""
import os, subprocess, sys, time, threading, glob, shutil

repo, script, logpath = sys.argv[1], sys.argv[2], sys.argv[3]
run = os.path.join(repo, "run")
os.makedirs(run, exist_ok=True)
shutil.rmtree(os.path.join(run, "world"), ignore_errors=True)
open(os.path.join(run, "eula.txt"), "w").write("eula=true\n")
open(os.path.join(run, "server.properties"), "w").write(
    "online-mode=false\nview-distance=4\nsimulation-distance=4\ndifficulty=normal\nspawn-protection=0\n"
    "generate-structures=false\nlevel-type=minecraft\\:flat\n"
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":3},'
    '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n')

env = dict(os.environ)
jdks = glob.glob(os.path.expanduser("~/.jdks/jdk-25*"))
if jdks:
    env["JAVA_HOME"] = jdks[0]
    env["PATH"] = jdks[0] + "/bin:" + env["PATH"]
log = open(logpath, "w")
proc = subprocess.Popen(["./gradlew", "runServer", "--no-daemon", "--offline", "--console=plain", "-q"], cwd=repo,
                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env, text=True, bufsize=1)
ready = threading.Event()

def pump():
    for line in proc.stdout:
        log.write(line); log.flush()
        if "Done (" in line:
            ready.set()

threading.Thread(target=pump, daemon=True).start()
deadline = time.time() + 600
while not ready.wait(1):
    if proc.poll() is not None or time.time() > deadline:
        print("server did not start"); proc.kill(); sys.exit(1)

def send(cmd):
    proc.stdin.write(cmd + "\n"); proc.stdin.flush()

for raw in open(script):
    line = raw.strip()
    if not line or line.startswith("#"):
        continue
    if line.startswith("wait "):
        time.sleep(float(line.split()[1]))
    elif line.startswith("== "):
        log.write("\n#### " + line[3:] + "\n"); log.flush()
        time.sleep(0.3)
    else:
        send(line)
        time.sleep(0.4)

send("stop")
proc.wait(timeout=120)
log.close()
print("exit", proc.returncode)
