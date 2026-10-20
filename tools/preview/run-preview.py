"""Capture the plugin panels with a native Mindustry client and an isolated server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[2]
CLIENT_HASH = "36d94941a1639478ad1d9be25a9d802c6f77552f400c6da0338b7b019f63a0d1"


def checked_run(arguments):
    subprocess.run(arguments, cwd=ROOT, check=True,
                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)


class LoggedProcess:
    def __init__(self, arguments, directory, log_path):
        self.log_path = log_path
        self.lines = []
        self.events = queue.Queue()
        self.process = subprocess.Popen(
            arguments, cwd=directory, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", bufsize=1,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        self.reader = threading.Thread(target=self.read, daemon=True)
        self.reader.start()

    def read(self):
        for line in self.process.stdout:
            self.lines.append(line)
            self.events.put(line)

    def wait_for(self, marker, timeout=60):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                line = self.events.get(timeout=0.2)
            except queue.Empty:
                if self.process.poll() is not None:
                    break
                continue
            if "RTV_PREVIEW_CLIENT_FAIL" in line:
                raise RuntimeError(line.strip())
            if marker in line:
                return line
        raise RuntimeError(f"Missing marker: {marker}\n{''.join(self.lines[-25:])}")

    def command(self, command, marker):
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()
        return self.wait_for(marker)

    def close(self):
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()
        self.reader.join(timeout=2)
        self.log_path.write_text("".join(self.lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "build" / "preview-images")
    parser.add_argument("--width", type=int, default=1440)
    parser.add_argument("--height", type=int, default=900)
    parser.add_argument("--portrait", action="store_true", help="Use a 720x1280 native client viewport.")
    parser.add_argument("--narrow", action="store_true", help="Use a 390x844 narrow native client viewport.")
    parser.add_argument("--all-viewports", action="store_true", help="Run all six native preview viewports.")
    parser.add_argument("--resize-test", action="store_true", help="Resize one native client and preserve an unsent search draft.")
    parser.add_argument("--map-count", type=int, default=0, help="Add this many valid custom map fixtures to the native preview server.")
    arguments = parser.parse_args()
    if arguments.all_viewports:
        viewports = [
            (1440, 900, "desktop"), (900, 1440, "desktop-portrait"),
            (1024, 768, "tablet-landscape"), (768, 1024, "tablet-portrait"),
            (844, 390, "mobile-landscape"), (390, 844, "mobile-portrait"),
        ]
        for width, height, name in viewports:
            checked_run([sys.executable, str(Path(__file__)), "--width", str(width), "--height", str(height),
                         "--output", str(arguments.output / name), "--map-count", str(arguments.map_count)])
        return
    if arguments.portrait:
        arguments.width, arguments.height = 720, 1280
    if arguments.narrow:
        arguments.width, arguments.height = 390, 844
    if arguments.resize_test:
        arguments.width, arguments.height = 390, 844
    output = arguments.output.resolve()
    engine = ROOT / "build/dependencies/mindustry-v160.7.jar"
    client_jar = ROOT / "build/dependencies/mindustry-client-v160.7.jar"
    plugin_jars = list((ROOT / "build/libs").glob("rock-the-vote-*.jar"))
    if not engine.is_file() or len(plugin_jars) != 1:
        raise SystemExit("Run the default Gradle build before this command.")
    if not client_jar.is_file():
        urllib.request.urlretrieve(
            "https://github.com/Anuken/Mindustry/releases/download/v160.7/Mindustry.jar", client_jar)
    with client_jar.open("rb") as client_file:
        digest = hashlib.file_digest(client_file, "sha256").hexdigest()
    if digest != CLIENT_HASH:
        raise SystemExit("The native client SHA-256 does not match the official release.")

    work = Path(tempfile.mkdtemp(prefix="readme-preview-", dir=ROOT / "build"))
    server_dir = work / "server"
    client_dir = work / "client"
    server_classes = work / "server-classes"
    client_classes = work / "client-classes"
    mods = server_dir / "config/mods"
    settings = mods / "rock-the-vote/config.properties"
    for directory in [settings.parent, client_dir, server_classes, client_classes, output]:
        directory.mkdir(parents=True, exist_ok=True)
    config = (ROOT / "examples/config.properties").read_text(encoding="utf-8")
    for key, value in {"mapStartDelaySeconds": 0, "cooldownSeconds": 0,
                       "voteDurationSeconds": 120}.items():
        config = re.sub(rf"^{key}=.*$", f"{key}={value}", config, flags=re.MULTILINE)
    settings.write_text(config, encoding="utf-8")
    color_resource = server_dir / "sprites/block_colors.png"
    color_resource.parent.mkdir(parents=True)
    with zipfile.ZipFile(client_jar) as client_assets:
        color_resource.write_bytes(client_assets.read("sprites/block_colors.png"))

    helper_dir = ROOT / "tools/preview"
    checked_run(["javac", "--release", "17", "-cp", os.pathsep.join(map(str, [engine, plugin_jars[0]])),
                 "-d", str(server_classes), str(helper_dir / "PreviewServerPlugin.java")])
    checked_run(["javac", "--release", "17", "-cp", str(client_jar), "-d", str(client_classes),
                 str(helper_dir / "PreviewClient.java")])
    shutil.copy2(plugin_jars[0], mods)
    with zipfile.ZipFile(mods / "rtv-preview-helper.jar", "w", zipfile.ZIP_DEFLATED) as archive:
        archive.write(helper_dir / "plugin.json", "plugin.json")
        for file in server_classes.rglob("*.class"):
            archive.write(file, file.relative_to(server_classes).as_posix())

    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    server = LoggedProcess(["java", "-Xmx512m", "-Djava.awt.headless=true",
                            f"-Drtv.preview.mapCount={arguments.map_count}", "-jar", str(engine)],
                           server_dir, work / "server.log")
    client = None
    try:
        server.wait_for("Server loaded.")
        server.command(f"config port {port}", "port set to")
        server.command("host Wasteland survival", "Map loaded.")
        ready = server.command("rtv-preview-setup", "RTV_PREVIEW_SERVER_READY target=")
        match = re.search(r"RTV_PREVIEW_SERVER_READY target=(\S+) maps=(\d+)", ready)
        if match is None:
            raise RuntimeError(f"Invalid preview server marker: {ready.strip()}")
        target, card_count = match.group(1), int(match.group(2))
        client = LoggedProcess([
            "java", "-Xmx1g", "-Dnodiscord=true", f"-Drtv.preview.port={port}",
            f"-Drtv.preview.mapId={target}", f"-Drtv.preview.output={output}",
            f"-Drtv.preview.cardCount={card_count}", "-Drtv.preview.catalogImages=3",
            "-Drtv.preview.resize=true" if arguments.resize_test else "-Drtv.preview.resize=false",
            "-cp", os.pathsep.join(map(str, [client_jar, client_classes])),
            f"-Drtv.preview.width={arguments.width}", f"-Drtv.preview.height={arguments.height}",
            "dev.hubdustry.rtv.preview.PreviewClient",
        ], client_dir, work / "client.log")
        client.wait_for("RTV_PREVIEW_CLIENT_PASS", timeout=150)
        client.process.wait(timeout=20)
        if client.process.returncode != 0:
            raise RuntimeError("The native preview client did not exit successfully.")
        for name in ["catalog.png", "vote.png"]:
            if not (output / name).is_file():
                raise RuntimeError(f"Missing preview: {name}")
        (work / "capture.json").write_text(json.dumps({
            "engine": "v160.7", "target": target, "output": str(output),
            "clientSha256": CLIENT_HASH, "syntheticVoters": 4,
            "width": arguments.width, "height": arguments.height,
        }, indent=2), encoding="utf-8")
        print(f"Native preview captured: {output}")
        print(f"Logs: {work}")
    except Exception:
        if client is not None:
            print("".join(client.lines[-25:]))
        print("".join(server.lines[-20:]))
        raise
    finally:
        if client is not None:
            client.close()
        server.close()


if __name__ == "__main__":
    main()
