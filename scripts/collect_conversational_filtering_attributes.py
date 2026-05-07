#!/usr/bin/env python3
"""
Extract conversational filtering attribute names and values from raw GCP Retail JSON.

Walks the entire JSON tree and records every suggestedAnswers[] entry:
  - productAttributeValue.name (e.g. attributes.brands) and .value
  - string-only suggested answers (name empty)

Usage:
  python scripts/collect_conversational_filtering_attributes.py response.json
  python scripts/collect_conversational_filtering_attributes.py *.json
  curl -s ... | python scripts/collect_conversational_filtering_attributes.py -

Outputs CSV (default) or JSON with unique (name_key, value) pairs and occurrence counts.

Align YAML keys in application.yml with name_key (strip 'attributes.' from name).
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


@dataclass(frozen=True)
class AttributeObservation:
    """One productAttributeValue (or string suggestion) seen in suggestedAnswers."""

    name_raw: str | None
    value: str

    @property
    def name_key(self) -> str:
        """Key for attribute-display-mapping: strip attributes. prefix; empty if unnamed."""
        if not self.name_raw:
            return ""
        n = self.name_raw.strip()
        if n.lower().startswith("attributes."):
            return n[len("attributes.") :]
        return n


def _extract_suggested_item(item: Any) -> AttributeObservation | None:
    if isinstance(item, str):
        v = item.strip()
        if v:
            return AttributeObservation(None, v)
        return None
    if not isinstance(item, dict):
        return None
    pav = item.get("productAttributeValue") or item.get("product_attribute_value")
    if isinstance(pav, dict):
        raw_name = pav.get("name")
        name = str(raw_name).strip() if raw_name is not None else None
        if name == "":
            name = None
        val = pav.get("value")
        if val is not None:
            s = str(val).strip()
            if s:
                return AttributeObservation(name, s)
    for k in ("text", "answer"):
        t = item.get(k)
        if t is not None:
            s = str(t).strip()
            if s:
                return AttributeObservation(None, s)
    return None


def _walk_collect_suggested_answers(obj: Any, out: list[AttributeObservation]) -> None:
    """Recurse through JSON; any dict with suggestedAnswers/suggested_answers is processed."""
    if isinstance(obj, dict):
        sa = obj.get("suggestedAnswers") or obj.get("suggested_answers")
        if isinstance(sa, list):
            for item in sa:
                obs = _extract_suggested_item(item)
                if obs:
                    out.append(obs)
        for v in obj.values():
            _walk_collect_suggested_answers(v, out)
    elif isinstance(obj, list):
        for el in obj:
            _walk_collect_suggested_answers(el, out)


def collect_from_parsed_json(parsed: Any) -> list[AttributeObservation]:
    out: list[AttributeObservation] = []
    _walk_collect_suggested_answers(parsed, out)
    return out


def _load_json_text(raw: str) -> Any:
    raw = raw.strip()
    if not raw:
        raise ValueError("empty input")
    return json.loads(raw)


def _try_load_ndjson(raw: str) -> list[Any]:
    rows: list[Any] = []
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            rows.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return rows


def parse_input_text(raw: str) -> list[Any]:
    """
    Return one or more JSON roots to scan.
    Supports: single object, array, or NDJSON (multiple lines).
    """
    raw = raw.strip()
    if not raw:
        return []
    try:
        parsed = _load_json_text(raw)
    except json.JSONDecodeError:
        nd = _try_load_ndjson(raw)
        if nd:
            return nd
        raise
    if isinstance(parsed, list):
        return parsed
    return [parsed]


def load_file(path: Path) -> str:
    if str(path) == "-" or path.name == "-":
        return sys.stdin.read()
    return path.read_text(encoding="utf-8", errors="replace")


def collect_from_files(paths: Iterable[Path]) -> list[AttributeObservation]:
    all_obs: list[AttributeObservation] = []
    for p in paths:
        text = load_file(p)
        if not text.strip():
            continue
        for root in parse_input_text(text):
            all_obs.extend(collect_from_parsed_json(root))
    return all_obs


def aggregate(obs: list[AttributeObservation]) -> Counter[tuple[str, str, str]]:
    """
    Count unique (name_raw or '', name_key, value).
    name_raw normalized: use '' when None for grouping.
    """
    c: Counter[tuple[str, str, str]] = Counter()
    for o in obs:
        nr = o.name_raw or ""
        c[(nr, o.name_key, o.value)] += 1
    return c


def emit_csv(counter: Counter[tuple[str, str, str]], stream) -> None:
    w = csv.writer(stream)
    w.writerow(["name_raw", "name_key", "value", "count"])
    for (name_raw, name_key, value), cnt in sorted(counter.items(), key=lambda x: (x[0][1], x[0][2], x[0][0])):
        w.writerow([name_raw, name_key, value, cnt])


def emit_json(counter: Counter[tuple[str, str, str]], stream) -> None:
    rows = []
    for (name_raw, name_key, value), cnt in sorted(counter.items(), key=lambda x: (x[0][1], x[0][2], x[0][0])):
        rows.append(
            {
                "name_raw": name_raw or None,
                "name_key": name_key or None,
                "value": value,
                "count": cnt,
            }
        )
    json.dump({"observations": rows, "distinct_pairs": len(counter), "total_rows": sum(counter.values())}, stream, indent=2)
    stream.write("\n")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument(
        "files",
        nargs="*",
        default=["-"],
        help="JSON files (or - for stdin). Default: stdin.",
    )
    p.add_argument("--format", choices=("csv", "json"), default="csv", help="output format")
    p.add_argument(
        "--keys-only",
        action="store_true",
        help="print unique name_key values only (one per line); empty line means unnamed attribute",
    )
    args = p.parse_args(argv)

    paths = [Path(f) for f in args.files]
    obs = collect_from_files(paths)
    counter = aggregate(obs)

    if args.keys_only:
        keys = sorted({k for (_, k, _) in counter.keys()})
        for k in keys:
            print(k)
        return 0

    if args.format == "csv":
        emit_csv(counter, sys.stdout)
    else:
        emit_json(counter, sys.stdout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
