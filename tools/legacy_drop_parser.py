#!/usr/bin/env python3
import argparse
import json
import re
from dataclasses import dataclass
from typing import List, Optional


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


def read_luck_chance(drop_str: str):
    idx = index_of_unnested(drop_str, 0, "@")
    if idx is None:
        return drop_str, {}
    base = drop_str[:idx]
    attr_str = drop_str[idx + 1 :].split("@")
    attr_str = ",".join(attr_str)
    attrs = {}
    for part in split_bracket_string(attr_str, ","):
        if "=" not in part:
            continue
        k, v = part.split("=", 1)
        attrs[k.strip()] = v.strip()
    return base, attrs


@dataclass
class SingleDrop:
    raw: str
    type: str
    props: dict


@dataclass
class GroupDrop:
    drops: list
    amount_expr: Optional[str]
    shuffle: bool


def parse_single(drop_str: str) -> SingleDrop:
    inner = drop_str
    if drop_str.startswith("(") and drop_str.endswith(")"):
        inner = drop_str[1:-1]
    props = {}
    for part in split_bracket_string(inner, ","):
        if "=" not in part:
            # legacy bare token
            props.setdefault("ID", part.strip())
            continue
        k, v = part.split("=", 1)
        props[k.strip()] = v.strip()
    drop_type = str(props.get("type", "item"))
    return SingleDrop(raw=drop_str, type=drop_type, props=props)


def parse_group(group_str: str) -> GroupDrop:
    split_colon = split_bracket_string(group_str, ":")
    split_comma = split_bracket_string(split_colon[-1], ",")
    if split_comma[0].lower().startswith("group"):
        inner = split_comma[0][len("group(") : -1]
    else:
        inner = split_comma[0][1:-1]
    common_attr = ",".join(split_comma[1:]) if len(split_comma) > 1 else None
    entries = split_bracket_string(inner, ";")
    drops = []
    for entry in entries:
        entry = entry.strip()
        if not entry:
            continue
        if common_attr:
            entry = f"{entry},{common_attr}"
        if entry.lower().startswith("group"):
            drops.append(parse_group(entry))
        else:
            drops.append(parse_single(entry))
    amount_expr = split_colon[1] if len(split_colon) > 1 else None
    return GroupDrop(drops=drops, amount_expr=amount_expr, shuffle=amount_expr is not None)


def parse_drop(drop_str: str):
    base, attrs = read_luck_chance(drop_str)
    base = base.strip()
    if base.lower().startswith("group"):
        return {"kind": "group", "group": parse_group(base), "attrs": attrs}
    return {"kind": "single", "drop": parse_single(base), "attrs": attrs}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("drop", nargs="*", help="Drop string(s)")
    ap.add_argument("--file", help="Read drops from file")
    args = ap.parse_args()

    drops = []
    if args.file:
        with open(args.file, "r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("/") or line.startswith("#"):
                    continue
                drops.append(line)
    drops.extend(args.drop)

    parsed = [parse_drop(d) for d in drops]
    print(json.dumps(parsed, ensure_ascii=False, indent=2, default=lambda o: o.__dict__))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
