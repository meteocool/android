#!/usr/bin/env python3
"""Generate app/src/main/assets/licences.json, the open-source licences in Settings.

Two sources, as in the iOS app's scripts/licences.py:
- `LICENSE`, the app's own licence, and `licences/`, third-party code built
  into the app, in `NATIVE` below. Add an entry when the app takes in
  someone else's code or library.
- core's `public/third-party-licences.json`, written by core's
  `scripts/licences.mjs` from what its production build ships: the web map
  the app shows. It becomes one entry, "Web Map Libraries".

Usage:
    python3 scripts/licences.py [path/to/core]

The core checkout defaults to $MC_CORE, then ../core next to this repository.
Rerun it after the app's or core's dependencies change and commit the output.
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "licences.json"

# (name, which SettingsFragment translates; SPDX licence; files relative to the repository; what it is)
NATIVE = [
    ("meteocool", "AGPL-3.0", ["LICENSE"], "this app, https://github.com/meteocool/android"),
    ("Android Libraries", "Apache-2.0", ["licences/android-libraries.txt", "licences/Apache-2.0.txt"],
     "the libraries the app is built with"),
    ("Py-ART Colour Maps", "BSD-3-Clause", ["licences/Py-ART.txt"],
     "the StepSeq, Homeyer and Lang radar palettes (StepSeq25, HomeyerRainbow, LangRainbow12), "
     "resampled for meteocool; the colour bars in Settings (ColormapTables.kt)"),
]


def core_path() -> Path:
    if len(sys.argv) > 1:
        return Path(sys.argv[1])
    if os.environ.get("MC_CORE"):
        return Path(os.environ["MC_CORE"])
    return ROOT.parent / "core"


def notice(group: dict) -> str:
    """One licence group of core's, as its imprint shows it."""
    packages = ", ".join(f"{p['name']} {p['version']}".strip() for p in group["packages"])
    body = group["text"].strip()
    if group["copyright"]:
        body = group["copyright"].strip() + "\n\n" + body
    return f"{packages}\n({group['license']})\n\n{body}"


def main() -> None:
    entries = []
    for name, licence, files, what in NATIVE:
        text = "\n\n".join((ROOT / file).read_text().strip() for file in files)
        entries.append({"name": name, "licence": licence, "text": f"{name}: {what}.\n\n{text}\n"})

    source = core_path() / "public" / "third-party-licences.json"
    groups = json.loads(source.read_text())
    # The summary line has room for licence families, not SPDX expressions.
    spdx = " ".join(group["license"] for group in groups)
    families = [name for name, token in [("MIT", "MIT"), ("BSD", "BSD"), ("ISC", "ISC"),
                                         ("Apache 2.0", "Apache-2.0"), ("CC BY 4.0", "CC-BY-4.0")] if token in spdx]
    count = sum(len(group["packages"]) for group in groups)
    intro = (f"meteocool's map is a web page, meteocool/core, shown in the app. "
             f"It is built with these {count} pieces of open-source software.")
    rule = "\n\n" + "─" * 32 + "\n\n"
    entries.append({
        "name": "Web Map Libraries",
        "licence": f"{count} packages · " + ", ".join(families),
        "text": intro + rule + rule.join(notice(group) for group in groups) + "\n",
    })

    OUTPUT.write_text(json.dumps(entries, ensure_ascii=False, indent=1) + "\n")
    print(f"Wrote {OUTPUT.relative_to(ROOT)}: {len(NATIVE)} native entries, {count} core packages in {len(groups)} texts")


if __name__ == "__main__":
    main()
