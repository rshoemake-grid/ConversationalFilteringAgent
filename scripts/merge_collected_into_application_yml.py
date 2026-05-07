#!/usr/bin/env python3
"""
Merge collected conversational filtering observations into application.yml.

- Loads backend/src/main/resources/application.yml with ruamel.yaml (preserves comments).
- Reads JSON/NDJSON from given paths (default: scripts/fixtures/*.{json,ndjson}).
- Adds attribute-display-mapping entries only when (target_key, value) is missing.
- Routes unnamed productAttributeValue (no name) to storageType for S/R/D/F/C, else sizes for
  size-like tokens (e.g. 12oz); other unnamed values are skipped (cannot be resolved by BrandDisplayResolver).

Requires: pip install ruamel.yaml
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

_SCRIPTS = Path(__file__).resolve().parent
_REPO = _SCRIPTS.parent
_DEFAULT_YML = _REPO / "backend/src/main/resources/application.yml"

if str(_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS))

from collect_conversational_filtering_attributes import (  # noqa: E402
    AttributeObservation,
    collect_from_files,
)

try:
    from ruamel.yaml import YAML
except ImportError as e:
    raise SystemExit("Install ruamel.yaml: pip install ruamel.yaml") from e

SIZE_LIKE = re.compile(r"^\d", re.I)
STORAGE_CODES = frozenset("SRDFC")


def _find_mapping_key(mapping: dict, logical: str) -> str | None:
    if not logical:
        return None
    for k in mapping:
        if k and k.lower() == logical.lower():
            return k
    return None


def _display_text(target_key: str, value: str) -> str:
    """Human label for UI; keep readable defaults."""
    if target_key in ("brands", "brandId"):
        if "/" in value or "\\" in value:
            return value
        if value.isascii() and value.replace("_", "").isalnum():
            if value.isupper() and len(value) > 1:
                return value.title().replace("_", " ")
    return value


def _target_key_for_unnamed(value: str) -> str | None:
    v = value.strip()
    if v in STORAGE_CODES:
        return "storageType"
    if "oz" in v.lower() or "gal" in v.lower() or v.endswith("L") or SIZE_LIKE.match(v):
        return "sizes"
    return None


def _route_observation(o: AttributeObservation) -> tuple[str, str] | None:
    """Return (yaml_attribute_key, value) or None to skip."""
    nk = (o.name_key or "").strip()
    if nk:
        return nk, o.value
    t = _target_key_for_unnamed(o.value)
    if t:
        return t, o.value
    return None


def merge_observations_into_display_mapping(
    mapping: dict,
    observations: list[AttributeObservation],
) -> int:
    """Mutate mapping in place. Returns number of new entries added."""
    added = 0
    for o in observations:
        routed = _route_observation(o)
        if not routed:
            continue
        logical_key, value = routed
        yaml_key = _find_mapping_key(mapping, logical_key)
        if yaml_key is None:
            # Create new top-level facet bucket using logical key casing from observation
            yaml_key = logical_key
            mapping[yaml_key] = {}
        sub = mapping[yaml_key]
        if not isinstance(sub, dict):
            continue
        if value in sub:
            continue
        sub[value] = _display_text(logical_key, value)
        added += 1
    return added


def default_fixture_paths() -> list[Path]:
    fix = _SCRIPTS / "fixtures"
    if not fix.is_dir():
        return []
    out: list[Path] = []
    for p in sorted(fix.iterdir()):
        if p.suffix.lower() in (".json", ".ndjson"):
            out.append(p)
    return out


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument(
        "inputs",
        nargs="*",
        help="JSON / NDJSON files (default: scripts/fixtures/*.json and *.ndjson)",
    )
    ap.add_argument(
        "--yml",
        type=Path,
        default=_DEFAULT_YML,
        help=f"application.yml path (default: {_DEFAULT_YML})",
    )
    ap.add_argument("--dry-run", action="store_true", help="Print added count only; do not write")
    args = ap.parse_args(argv)

    paths = [Path(p) for p in args.inputs] if args.inputs else default_fixture_paths()
    if not paths:
        print("No input files.", file=sys.stderr)
        return 1

    obs = collect_from_files(paths)
    yml_path: Path = args.yml
    yaml = YAML()
    yaml.preserve_quotes = True
    yaml.indent(mapping=2, sequence=4, offset=2)
    with open(yml_path) as f:
        data = yaml.load(f)

    cc = data.get("conversational-commerce") or {}
    display = cc.get("attribute-display-mapping")
    if not isinstance(display, dict):
        print("Missing conversational-commerce.attribute-display-mapping", file=sys.stderr)
        return 1

    n = merge_observations_into_display_mapping(display, obs)
    print(f"Added {n} new attribute-display-mapping entries from {len(obs)} observations in {len(paths)} file(s).")

    if args.dry_run or n == 0:
        return 0

    with open(yml_path, "w") as f:
        yaml.dump(data, f)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
