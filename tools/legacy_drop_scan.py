#!/usr/bin/env python3
import argparse
import json
import re
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

DROP_FILE_RE = re.compile(r"(^|/)([A-Za-z0-9_]*drops)\.txt$")
TYPE_RE = re.compile(r"\btype=([A-Za-z0-9_]+)")


def is_comment(line: str) -> bool:
    stripped = line.strip()
    if not stripped:
        return True
    return stripped.startswith("/") or stripped.startswith("#")


def scan_zip(path: Path, out_rows, stats, type_counts, token_counts):
    pack_id = path.stem
    try:
        with zipfile.ZipFile(path) as zf:
            for info in zf.infolist():
                if info.is_dir():
                    continue
                name = info.filename
                if not DROP_FILE_RE.search(name):
                    continue
                stats[pack_id]["files"].add(name)
                with zf.open(info) as fh:
                    raw = fh.read().decode("utf-8", errors="replace")
                for idx, line in enumerate(raw.splitlines(), 1):
                    stats[pack_id]["total_lines"] += 1
                    if is_comment(line):
                        stats[pack_id]["comment_lines"] += 1
                        continue
                    stats[pack_id]["data_lines"] += 1
                    out_rows.append((pack_id, name, idx, line))
                    for match in TYPE_RE.finditer(line):
                        type_counts[match.group(1)] += 1
                    token_counts["group("] += line.count("group(")
                    token_counts["type="] += line.count("type=")
                    token_counts["ID="] += line.count("ID=")
                    token_counts["NBTTag="] += line.count("NBTTag=")
                    token_counts["posOffset="] += line.count("posOffset=")
                    token_counts["posY="] += line.count("posY=")
                    token_counts["amount="] += line.count("amount=")
                    token_counts["damage="] += line.count("damage=")
    except zipfile.BadZipFile:
        stats[pack_id]["bad_zip"] = True


def main() -> int:
    parser = argparse.ArgumentParser(description="Scan legacy Lucky Block drops in addon zips/jars")
    parser.add_argument("--addons", default="run/addons/lucky", help="Addons directory")
    parser.add_argument("--out", default="run/logs/re_lpp/Latest/legacy_drop_scan", help="Output directory")
    args = parser.parse_args()

    addons_dir = Path(args.addons)
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    out_rows = []
    stats = defaultdict(lambda: {"files": set(), "total_lines": 0, "comment_lines": 0, "data_lines": 0})
    type_counts = Counter()
    token_counts = Counter()

    if addons_dir.exists():
        for path in sorted(addons_dir.iterdir()):
            if path.suffix.lower() in (".zip", ".jar"):
                scan_zip(path, out_rows, stats, type_counts, token_counts)

    tsv_path = out_dir / "legacy_drops.tsv"
    with tsv_path.open("w", encoding="utf-8") as f:
        f.write("pack\tfile\tline\traw\n")
        for pack_id, name, idx, line in out_rows:
            f.write(f"{pack_id}\t{name}\t{idx}\t{line}\n")

    summary = {
        "packs": {},
        "type_counts": dict(type_counts.most_common()),
        "token_counts": dict(token_counts.most_common()),
        "total_rows": len(out_rows),
    }
    for pack_id, data in stats.items():
        summary["packs"][pack_id] = {
            "files": sorted(data["files"]),
            "total_lines": data["total_lines"],
            "comment_lines": data["comment_lines"],
            "data_lines": data["data_lines"],
            "bad_zip": bool(data.get("bad_zip")),
        }

    with (out_dir / "legacy_drops_summary.json").open("w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    with (out_dir / "legacy_drops_types.tsv").open("w", encoding="utf-8") as f:
        f.write("type\tcount\n")
        for key, count in type_counts.most_common():
            f.write(f"{key}\t{count}\n")

    with (out_dir / "legacy_drops_tokens.tsv").open("w", encoding="utf-8") as f:
        f.write("token\tcount\n")
        for key, count in token_counts.most_common():
            f.write(f"{key}\t{count}\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
