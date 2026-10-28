"""Drives Volta on an emulator: simulates battery changes, visits every screen and saves screenshots.

Run by .github/workflows/emulator.yml. Screenshots and logs go to ./out.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.alexmaxham.volta"
OUT = "out"
os.makedirs(OUT, exist_ok=True)
steps = []


def adb(*args, check=False):
    r = subprocess.run(["adb", *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {r.stderr}")
    return r.stdout


def sh(cmd):
    return adb("shell", cmd)


def log(msg):
    print(msg, flush=True)
    steps.append(msg)


def shot(name, wait=1.5):
    time.sleep(wait)
    with open(f"{OUT}/{name}.png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    log(f"screenshot {name}")


def nodes():
    sh("uiautomator dump /sdcard/ui.xml")
    xml = sh("cat /sdcard/ui.xml")
    if "<?xml" not in xml:
        return []
    root = ET.fromstring(xml[xml.index("<?xml"):])
    return list(root.iter("node"))


def find(label, exact=True):
    for n in nodes():
        for attr in ("text", "content-desc"):
            v = n.get(attr) or ""
            if (v == label) if exact else (label.lower() in v.lower()):
                return n
    return None


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(label, exact=True, required=True, scrolls=3):
    for _ in range(scrolls + 1):
        n = find(label, exact)
        if n is not None:
            x, y = center(n)
            sh(f"input tap {x} {y}")
            log(f"tap '{label}'")
            time.sleep(1.2)
            return True
        scroll()
    msg = f"NOT FOUND '{label}'"
    log(msg)
    if required:
        shot("notfound-" + re.sub(r"\W+", "_", label))
    return False


def scroll(up=False):
    if up:
        sh("input swipe 540 700 540 1900 300")
    else:
        sh("input swipe 540 1900 540 700 300")
    time.sleep(0.8)


def back():
    sh("input keyevent KEYCODE_BACK")
    time.sleep(1.2)


def battery(**kv):
    for k, v in kv.items():
        sh(f"dumpsys battery set {k} {v}")


def crashed():
    out = adb("logcat", "-b", "crash", "-d")
    return PKG in out


def scroll_shots(prefix, n=3):
    shot(f"{prefix}-1")
    for i in range(2, n + 1):
        scroll()
        shot(f"{prefix}-{i}", wait=0.8)
    for _ in range(n):
        scroll(up=True)


def main():
    adb("logcat", "-c")
    sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    sh(f"appops set {PKG} GET_USAGE_STATS allow")
    battery(ac=0, usb=0, status=3, level=92, temp=312)

    sh(f"am start -n {PKG}/.MainActivity")
    shot("01-welcome", wait=4)
    tap("Get started")
    shot("02-today", wait=3)

    # Discharge with the app open; readings are saved at most once a minute.
    for level in (90, 88, 86, 84, 83, 81):
        battery(level=level)
        sh("input swipe 540 1500 540 1400 100")  # Keep the screen awake.
        time.sleep(62)
    shot("03-today-after-drain")
    scroll_shots("04-today-scroll", 4)

    # Plug in and charge to full, then unplug.
    battery(ac=1, status=2, level=82, temp=335)
    shot("05-today-charging", wait=3)
    for level in (86, 91, 96, 100):
        battery(level=level, temp=340)
        time.sleep(62)
    battery(status=5)
    time.sleep(62)
    battery(ac=0, status=3, level=99, temp=318)
    time.sleep(62)
    battery(level=98)
    shot("06-today-unplugged", wait=3)

    tap("History")
    shot("07-history-level")
    scroll_shots("07-history-level-scroll", 2)
    if tap("Charging"):
        shot("08-history-charging")
        if tap("82% → 100%", exact=False, required=False) or tap("% → 100%", exact=False, required=False):
            scroll_shots("09-charging-detail", 2)
            back()
    if tap("Nights"):
        shot("10-history-nights")
        if tap("→", exact=False, required=False):
            scroll_shots("11-night-detail", 2)
            back()
    if tap("Cycles"):
        shot("12-history-cycles")

    tap("Apps")
    shot("13-apps")

    tap("Lab")
    shot("14-lab-empty")
    if tap("New test"):
        shot("15-new-test-sheet")
        if tap("Test protocol / workload"):
            sh("input text Video%sloop")
            sh("input keyevent KEYCODE_BACK")
            shot("16-new-test-filled")
        tap("Start test")
        shot("17-lab-running", wait=3)
        battery(level=97)
        time.sleep(20)
        if tap("RUN IN PROGRESS", exact=False):
            scroll_shots("18-run-detail", 3)
            if tap("Finish and save run"):
                shot("19-run-finished")
            back()
        shot("20-lab-saved")

    tap("Today")
    tap("Share battery stats")
    shot("21-stats-image")
    back()

    tap("Settings")
    scroll_shots("22-settings", 4)
    for scheme in ("Sage", "Ocean", "Ember", "Iris", "Dynamic"):
        if tap(f"{scheme} color scheme"):
            shot(f"23-settings-{scheme.lower()}")
    tap("Bloom color scheme")
    tap("Dark")
    shot("24-settings-dark")
    back()
    shot("25-today-dark", wait=2)
    tap("History")
    shot("26-history-dark")
    tap("Lab")
    shot("27-lab-dark")

    with open(f"{OUT}/logcat-volta.txt", "w") as f:
        pid_lines = adb("logcat", "-d", "-v", "time")
        f.write("\n".join(l for l in pid_lines.splitlines() if "volta" in l.lower() or "AndroidRuntime" in l))
    with open(f"{OUT}/crash.txt", "w") as f:
        f.write(adb("logcat", "-b", "crash", "-d"))
    with open(f"{OUT}/steps.txt", "w") as f:
        f.write("\n".join(steps))
    if crashed():
        print("Volta crashed during the tour; see crash.txt")
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    finally:
        with open(f"{OUT}/crash.txt", "a") as f:
            f.write(adb("logcat", "-b", "crash", "-d"))
        with open(f"{OUT}/steps.txt", "w") as f:
            f.write("\n".join(steps))
