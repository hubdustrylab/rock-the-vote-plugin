"""Run the plugin in an isolated headless server. Do not open a network port."""
import argparse
import os
from pathlib import Path
import queue
import shutil
import subprocess
import tempfile
import threading
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", choices=["v160", "v160.7"], default="v160.7")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    engine = root / "build" / "dependencies" / f"mindustry-{args.version}.jar"
    candidates = list((root / "build" / "libs").glob("rock-the-vote-*.jar"))
    probe = root / "build" / "smoke-probe" / "rtv-native-smoke.jar"
    if len(candidates) != 1 or not engine.is_file() or not probe.is_file():
        raise SystemExit("Run gradlew build smokeJar before this command.")
    output = root / "build" / "smoke"
    output.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix=args.version + "-", dir=output))
    mods = work / "config" / "mods"
    settings = mods / "rock-the-vote" / "config.properties"
    settings.parent.mkdir(parents=True)
    text = (root / "examples" / "config.properties").read_text(encoding="utf-8")
    for key, value in [
        ("mapStartDelaySeconds", "30"), ("cooldownSeconds", "120"), ("changeDelaySeconds", "5")
    ]:
        text = text.replace(f"{key}={value}", f"{key}=0")
    settings.write_text(text, encoding="utf-8")
    shutil.copy2(candidates[0], mods)
    shutil.copy2(probe, mods)
    process = subprocess.Popen(
        ["java", "-Xmx512m", "-Djava.awt.headless=true", "-jar", str(engine)],
        cwd=work, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", errors="replace", bufsize=1,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    lines = queue.Queue()
    transcript = []

    def read_output():
        for line in process.stdout:
            transcript.append(line)
            lines.put(line)

    threading.Thread(target=read_output, daemon=True).start()

    def wait_for(marker, timeout=45):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=0.2)
            except queue.Empty:
                if process.poll() is not None:
                    break
                continue
            if "RTV_PROBE_FAIL" in line or "RTV_PROBE_BLOCKED" in line:
                raise RuntimeError(line.strip())
            if marker in line:
                return
        raise RuntimeError(f"Missing marker: {marker}")

    def command(value, marker):
        process.stdin.write(value + "\n")
        process.stdin.flush()
        wait_for(marker)

    try:
        wait_for("Server loaded.")
        command("rtv status", "Status: idle")
        command("rtv off", "Voting is disabled")
        command("rtv on", "Enabled")
        settings.write_text(text + "\nunknown-setting=true\n", encoding="utf-8")
        command("rtv reload", "Configuration reload failed")
        command("rtv status", "Status: idle")
        settings.write_text(text, encoding="utf-8")
        command("rtv reload", "Configuration reloaded")
        command("rtv-probe", "RTV_PROBE_PASS")
        process.stdin.write("exit\n")
        process.stdin.flush()
        process.wait(timeout=15)
        if process.returncode != 0:
            raise RuntimeError(f"Server exit code: {process.returncode}")
        print(f"PASS {args.version}: plugin load, commands, atomic reload, synthetic-player map vote.")
        print(f"Log: {work / 'server.log'}")
    except Exception:
        print("".join(transcript[-45:]))
        raise
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        (work / "server.log").write_text("".join(transcript), encoding="utf-8")


if __name__ == "__main__":
    main()
