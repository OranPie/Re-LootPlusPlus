#!/usr/bin/env python3
import argparse
import json
import os
import re
import subprocess
import tempfile
import zipfile
from collections import Counter, defaultdict
from pathlib import Path
from typing import List, Optional, Tuple


DROP_FILES = ("drops.txt", "sword_drops.txt", "bow_drops.txt", "potion_drops.txt")
CONTINUATION_END = {"\\", "(", "[", ",", ";"}
CONTINUATION_START = {")", "]"}


def split_lines(lines: List[str]) -> List[str]:
    out = []
    combined = ""
    prev_line = None
    for line_init in lines:
        line = line_init.strip()
        if not line or line.startswith("/"):
            continue
        is_cont = False
        if prev_line is not None:
            if prev_line and prev_line[-1] in CONTINUATION_END:
                is_cont = True
            if line and line[0] in CONTINUATION_START:
                is_cont = True
        line_contents = line[:-1].strip() if line.endswith("\\") else line
        if not is_cont and combined:
            out.append(combined)
            combined = line_contents
        else:
            combined += line_contents
        prev_line = line
    if combined:
        out.append(combined)
    return out


def index_of_unnested(value: str, start: int, ch: str) -> Optional[int]:
    depth = 0
    in_quotes = False
    i = start
    while i < len(value):
        canceled = i > 0 and value[i - 1] == "\\"
        if not canceled:
            if value[i] == '"':
                in_quotes = not in_quotes
            if value[i] in "([{" and not in_quotes:
                depth += 1
            if value[i] in ")]}" and not in_quotes:
                depth -= 1
        if depth == 0 and not in_quotes and value[i] == ch:
            return i
        i += 1
    return None


def split_bracket_string(value: str, sep: str) -> List[str]:
    points = [-1]
    pos = -1
    while True:
        nxt = index_of_unnested(value, pos + 1, sep)
        if nxt is None:
            break
        points.append(nxt)
        pos = nxt
    points.append(len(value))
    return [value[points[i] + 1 : points[i + 1]].strip() for i in range(len(points) - 1)]


def check_balance(value: str) -> bool:
    depth = 0
    in_quotes = False
    for i, c in enumerate(value):
        canceled = i > 0 and value[i - 1] == "\\"
        if not canceled:
            if c == '"':
                in_quotes = not in_quotes
            if c in "([{" and not in_quotes:
                depth += 1
            if c in ")]}" and not in_quotes:
                depth -= 1
                if depth < 0:
                    return False
    return depth == 0 and not in_quotes


class ParseError(Exception):
    pass


def read_luck_chance(drop_str: str) -> Tuple[str, dict]:
    idx = index_of_unnested(drop_str, 0, "@")
    if idx is None:
        return drop_str, {}
    base = drop_str[:idx]
    attr_str = ",".join(drop_str[idx + 1 :].split("@"))
    attrs = {}
    for part in split_bracket_string(attr_str, ","):
        if "=" not in part:
            continue
        k, v = part.split("=", 1)
        attrs[k.strip()] = v.strip()
    return base, attrs


def parse_dict_props(inner: str):
    contents = split_bracket_string(inner, ",")
    for entry in contents:
        if "=" not in entry:
            raise ParseError(f"Invalid dictionary entry '{entry}'")


def parse_single(drop_str: str):
    if not check_balance(drop_str):
        raise ParseError("Unbalanced brackets/quotes")
    inner = drop_str
    if drop_str.startswith("(") and drop_str.endswith(")"):
        inner = drop_str[1:-1]
    parse_dict_props(inner)


def parse_group(group_str: str):
    if not check_balance(group_str):
        raise ParseError("Unbalanced brackets/quotes")
    split_colon = split_bracket_string(group_str, ":")
    split_comma = split_bracket_string(split_colon[-1], ",")
    head = split_comma[0]
    if head.lower().startswith("group"):
        inner = head[len("group(") : -1]
    else:
        inner = head[1:-1]
    entries = split_bracket_string(inner, ";")
    for entry in entries:
        entry = entry.strip()
        if not entry:
            continue
        if entry.lower().startswith("group"):
            parse_group(entry)
        else:
            parse_single(entry)


def parse_drop(drop_str: str):
    base, _ = read_luck_chance(drop_str)
    base = base.strip()
    if not base:
        raise ParseError("empty")
    if base.lower().startswith("group"):
        parse_group(base)
    else:
        parse_single(base)


def read_drop_lines_from_zip(path: Path) -> List[Tuple[str, str, int, str]]:
    rows = []
    with zipfile.ZipFile(path) as zf:
        for name in zf.namelist():
            base = name.rsplit("/", 1)[-1]
            if base not in DROP_FILES:
                continue
            raw = zf.read(name).decode("utf-8", errors="replace").splitlines()
            for idx, line in enumerate(split_lines(raw), 1):
                rows.append((path.stem, name, idx, line))
    return rows


def ensure_java_runner(tmp_dir: Path, sanitizer_path: Path) -> Path:
    runner_dir = tmp_dir / "legacy_sanitize_runner"
    runner_dir.mkdir(parents=True, exist_ok=True)
    pkg_dir = runner_dir / "ie/orangep/reLootplusplus/legacy"
    diag_dir = runner_dir / "ie/orangep/reLootplusplus/diagnostic"
    pkg_dir.mkdir(parents=True, exist_ok=True)
    diag_dir.mkdir(parents=True, exist_ok=True)

    # copy sanitizer
    target = pkg_dir / "LegacyDropSanitizer.java"
    if not target.exists():
        target.write_text(sanitizer_path.read_text(encoding="utf-8"), encoding="utf-8")

    # stub LegacyWarnReporter
    warn_stub = diag_dir / "LegacyWarnReporter.java"
    if not warn_stub.exists():
        warn_stub.write_text(
            "package ie.orangep.reLootplusplus.diagnostic;\n"
            "public final class LegacyWarnReporter {\n"
            "  public void warn(String type, String detail, Object loc) {}\n"
            "  public void warnOnce(String type, String detail, Object loc) {}\n"
            "}\n",
            encoding="utf-8",
        )

    # stub Log
    log_stub = diag_dir / "Log.java"
    if not log_stub.exists():
        log_stub.write_text(
            "package ie.orangep.reLootplusplus.diagnostic;\n"
            "public final class Log {\n"
            "  public static void warn(String msg, Object... args) {}\n"
            "}\n",
            encoding="utf-8",
        )

    # runner
    runner = runner_dir / "LegacySanitizeRunner.java"
    if not runner.exists():
        runner.write_text(
            "import java.io.*;\n"
            "import ie.orangep.reLootplusplus.legacy.LegacyDropSanitizer;\n"
            "import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;\n"
            "public class LegacySanitizeRunner {\n"
            "  public static void main(String[] args) throws Exception {\n"
            "    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));\n"
            "    String line;\n"
            "    LegacyWarnReporter reporter = new LegacyWarnReporter();\n"
            "    StringBuilder out = new StringBuilder();\n"
            "    while ((line = br.readLine()) != null) {\n"
            "      String sanitized = LegacyDropSanitizer.sanitize(line, reporter);\n"
            "      out.append(sanitized).append('\\n');\n"
            "    }\n"
            "    System.out.print(out.toString());\n"
            "  }\n"
            "}\n",
            encoding="utf-8",
        )

    # compile
    class_files = list(runner_dir.rglob("*.class"))
    if not class_files:
        subprocess.run(
            ["javac", str(runner)],
            cwd=str(runner_dir),
            check=True,
        )
    return runner_dir


def sanitize_lines(lines: List[str], sanitizer_path: Path) -> List[str]:
    with tempfile.TemporaryDirectory() as tmp:
        runner_dir = ensure_java_runner(Path(tmp), sanitizer_path)
        proc = subprocess.run(
            ["java", "-cp", str(runner_dir), "LegacySanitizeRunner"],
            input="\n".join(lines).encode("utf-8"),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )
        return proc.stdout.decode("utf-8", errors="replace").splitlines()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--addons", default="run/addons/lucky", help="Addons directory")
    ap.add_argument("--out", default="run/logs/re_lpp/coverage", help="Output directory")
    ap.add_argument("--sanitizer", default="src/main/java/ie/orangep/reLootplusplus/legacy/LegacyDropSanitizer.java")
    args = ap.parse_args()

    addons_dir = Path(args.addons)
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    for path in sorted(addons_dir.iterdir()):
        if path.suffix.lower() in (".zip", ".jar"):
            rows.extend(read_drop_lines_from_zip(path))

    drop_lines = [r[3] for r in rows]
    sanitized = sanitize_lines(drop_lines, Path(args.sanitizer))

    before_ok = 0
    after_ok = 0
    before_err = Counter()
    after_err = Counter()
    per_pack = defaultdict(lambda: {"total": 0, "before_ok": 0, "after_ok": 0})

    for (pack, file, line_no, raw), san in zip(rows, sanitized):
        per_pack[pack]["total"] += 1
        try:
            parse_drop(raw)
            before_ok += 1
            per_pack[pack]["before_ok"] += 1
        except Exception as e:
            before_err[str(e).split(":")[0]] += 1
        try:
            parse_drop(san)
            after_ok += 1
            per_pack[pack]["after_ok"] += 1
        except Exception as e:
            after_err[str(e).split(":")[0]] += 1

    total = len(rows)
    summary = {
        "total": total,
        "before_ok": before_ok,
        "after_ok": after_ok,
        "before_rate": 0 if total == 0 else before_ok / total,
        "after_rate": 0 if total == 0 else after_ok / total,
        "before_errors": dict(before_err),
        "after_errors": dict(after_err),
        "per_pack": per_pack,
    }

    (out_dir / "coverage.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    with (out_dir / "per_pack.tsv").open("w", encoding="utf-8") as f:
        f.write("pack\ttotal\tbefore_ok\tafter_ok\tbefore_rate\tafter_rate\n")
        for pack, data in sorted(per_pack.items()):
            total = data["total"]
            before = data["before_ok"]
            after = data["after_ok"]
            f.write(
                f"{pack}\t{total}\t{before}\t{after}\t"
                f"{(before/total if total else 0):.4f}\t"
                f"{(after/total if total else 0):.4f}\n"
            )

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
