#!/usr/bin/env python3
"""Read-only workspace size audit; never follows links or removes files."""

from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
import heapq
import json
import os
from pathlib import Path
import shutil
import stat
import time


@dataclass
class Directory:
    parent: str | None
    own_bytes: int = 0
    own_files: int = 0
    total_bytes: int = 0
    total_files: int = 0
    children: int = 0
    has_level_dat: bool = False
    has_server_properties: bool = False


def size(value: int) -> str:
    return f"{value / (1024 ** 3):,.2f} GiB"


def json_write(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.root.resolve(strict=True)
    output = (args.output or root / "output" / "workspace-disk-audit-20261029").resolve()
    if not output.is_relative_to(root):
        raise SystemExit("The audit output directory must remain inside the workspace.")
    started = time.monotonic()
    disk_before = shutil.disk_usage(root)
    nodes: dict[str, Directory] = {".": Directory(None)}
    extensions: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    largest: list[tuple[int, str]] = []
    skipped: dict[str, int] = defaultdict(int)
    errors: list[dict[str, str]] = []
    hardlinks_seen: set[tuple[int, int]] = set()
    unique_bytes = 0
    hardlink_duplicate_bytes = 0
    scanned_files = 0
    stack = [(root, ".")]
    last_progress = started

    def failure(path: Path | str, error: OSError) -> None:
        skipped["errors"] += 1
        if len(errors) < 100:
            errors.append({"path": str(path), "error": str(error)})

    while stack:
        directory_path, relative = stack.pop()
        try:
            directory_stat = directory_path.stat(follow_symlinks=False)
            if stat.S_ISLNK(directory_stat.st_mode) or (
                getattr(directory_stat, "st_file_attributes", 0)
                & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 1024)
            ):
                skipped["link_or_reparse_directory"] += 1
                continue
            with os.scandir(directory_path) as entries:
                for entry in entries:
                    path = Path(entry.path)
                    child_relative = entry.name if relative == "." else relative + "/" + entry.name
                    if path == output:
                        skipped["audit_output_directory"] += 1
                        continue
                    try:
                        metadata = entry.stat(follow_symlinks=False)
                        if stat.S_ISLNK(metadata.st_mode) or (
                            getattr(metadata, "st_file_attributes", 0)
                            & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 1024)
                        ):
                            skipped["links_or_reparse_entries"] += 1
                            continue
                        if stat.S_ISDIR(metadata.st_mode):
                            nodes[child_relative] = Directory(relative)
                            nodes[relative].children += 1
                            stack.append((path, child_relative))
                        elif stat.S_ISREG(metadata.st_mode):
                            byte_count = metadata.st_size
                            node = nodes[relative]
                            node.own_bytes += byte_count
                            node.own_files += 1
                            node.has_level_dat |= entry.name.lower() == "level.dat"
                            node.has_server_properties |= entry.name.lower() == "server.properties"
                            scanned_files += 1
                            extension = path.suffix.lower() or "[no extension]"
                            extensions[extension][0] += byte_count
                            extensions[extension][1] += 1
                            item = (byte_count, child_relative)
                            if len(largest) < 40:
                                heapq.heappush(largest, item)
                            elif item > largest[0]:
                                heapq.heapreplace(largest, item)
                            if metadata.st_nlink > 1 and metadata.st_ino:
                                identity = (metadata.st_dev, metadata.st_ino)
                                if identity in hardlinks_seen:
                                    hardlink_duplicate_bytes += byte_count
                                else:
                                    hardlinks_seen.add(identity)
                                    unique_bytes += byte_count
                            else:
                                unique_bytes += byte_count
                        else:
                            skipped["non_regular_entries"] += 1
                    except OSError as error:
                        failure(child_relative, error)
        except OSError as error:
            failure(relative, error)
        now = time.monotonic()
        if now - last_progress >= 10:
            print(f"Scanned {scanned_files:,} files across {len(nodes):,} directories...", flush=True)
            last_progress = now

    # Reverse depth order gives each directory its complete recursive size.
    for relative in sorted(nodes, key=lambda value: (value.count("/"), value != "."), reverse=True):
        node = nodes[relative]
        node.total_bytes += node.own_bytes
        node.total_files += node.own_files
        if node.parent is not None:
            nodes[node.parent].total_bytes += node.total_bytes
            nodes[node.parent].total_files += node.total_files

    categories: dict[str, list[str]] = defaultdict(list)
    fixture_roots: list[str] = []
    for relative, node in nodes.items():
        if relative == ".":
            continue
        parts = relative.lower().split("/")
        name = parts[-1]
        if name == "libraries":
            categories["libraries"].append(relative)
        if name == "versions":
            categories["minecraft_versions"].append(relative)
        if name in {"cache", ".cache"}:
            categories["caches"].append(relative)
        if name == ".paper-remapped":
            categories["paper_remapped"].append(relative)
        if name == "modelengine":
            categories["modelengine_directories"].append(relative)
        if name == "plugins" and parts[0] == "output":
            categories["fixture_plugins"].append(relative)
        if node.has_level_dat:
            categories["minecraft_worlds_with_level_dat"].append(relative)
        if name in {"target", "build", ".gradle", "node_modules", ".venv", "__pycache__"}:
            categories["build_and_tool_dependencies"].append(relative)
        if name == "logs":
            categories["logs"].append(relative)
        if node.has_server_properties and parts[0] == "output":
            fixture_roots.append(relative)

    def prune_descendants(paths: list[str]) -> list[str]:
        selected: list[str] = []
        for candidate in sorted(paths, key=lambda value: (value.count("/"), value)):
            if not any(candidate == ancestor or candidate.startswith(ancestor + "/") for ancestor in selected):
                selected.append(candidate)
        return selected

    def row(path: str) -> dict[str, object]:
        node = nodes[path]
        return {"path": path, "bytes": node.total_bytes, "files": node.total_files,
                "direct_bytes": node.own_bytes, "direct_files": node.own_files,
                "child_directories": node.children}

    category_rows = {}
    for name, paths in sorted(categories.items()):
        paths = prune_descendants(paths)
        category_rows[name] = {
            "bytes": sum(nodes[path].total_bytes for path in paths),
            "directories": len(paths),
            "largest_roots": [row(path) for path in sorted(paths, key=lambda p: nodes[p].total_bytes, reverse=True)[:30]],
        }
    fixtures = prune_descendants(fixture_roots)
    candidate_roots = prune_descendants([path for paths in categories.values() for path in paths])
    top_level = sorted((row(path) for path, node in nodes.items() if node.parent == "."),
                       key=lambda value: value["bytes"], reverse=True)
    second_level = sorted((row(path) for path, node in nodes.items()
                           if node.parent not in (None, ".") and path.count("/") == 1),
                          key=lambda value: value["bytes"], reverse=True)
    output_children = sorted((row(path) for path, node in nodes.items() if node.parent == "output"),
                             key=lambda value: value["bytes"], reverse=True)
    disk_after = shutil.disk_usage(root)
    summary = {
        "root": str(root), "created_utc": datetime.now(timezone.utc).isoformat(),
        "elapsed_seconds": round(time.monotonic() - started, 2),
        "measurement": "Logical file bytes, not NTFS allocated/compressed bytes; no links/reparse entries followed. Files may change during scan.",
        "total_bytes": nodes["."].total_bytes,
        "unique_file_identity_bytes": unique_bytes,
        "duplicate_hardlink_bytes": hardlink_duplicate_bytes,
        "files": scanned_files, "directories": len(nodes),
        "free_bytes_before": disk_before.free, "free_bytes_after": disk_after.free,
        "root_direct_files": {"bytes": nodes["."].own_bytes, "files": nodes["."].own_files},
        "top_level": top_level, "second_level": second_level,
        "output_children": output_children,
        "fixture_roots": [row(path) for path in sorted(fixtures, key=lambda p: nodes[p].total_bytes, reverse=True)],
        "fixture_total_bytes": sum(nodes[path].total_bytes for path in fixtures),
        "categories": category_rows,
        "category_union_bytes": sum(nodes[path].total_bytes for path in candidate_roots),
        "category_note": "Different categories can overlap. category_union_bytes counts overlapping directory subtrees once. Categories identify contents, not permission to delete them.",
        "extensions": [{"extension": suffix, "bytes": counts[0], "files": counts[1]}
                       for suffix, counts in sorted(extensions.items(), key=lambda item: item[1][0], reverse=True)],
        "largest_files": [{"path": path, "bytes": byte_count} for byte_count, path in sorted(largest, reverse=True)],
        "skipped": dict(skipped), "first_100_errors": errors,
    }
    output.mkdir(parents=True, exist_ok=True)
    json_write(output / "summary.json", summary)
    with (output / "directory-inventory.jsonl").open("w", encoding="utf-8", newline="\n") as inventory:
        for path in sorted(nodes):
            inventory.write(json.dumps(row(path), ensure_ascii=False, separators=(",", ":")) + "\n")

    lines = ["# Evergarden2 workspace disk audit", "", f"Root: `{root}`", "",
             f"Logical size: **{size(nodes['.'].total_bytes)}** ({nodes['.'].total_bytes:,} bytes).",
             f"Files: {scanned_files:,}; directories: {len(nodes):,}; free disk: {size(disk_after.free)}.",
             f"Hardlink duplicate logical bytes: {size(hardlink_duplicate_bytes)}.", "",
             "Sizes are logical file lengths, not NTFS allocated sizes. Links and reparse entries were skipped. Files may change during this read-only scan.",
             "", "## Top-level directories", "", "| Directory | Size | Files |", "|---|---:|---:|"]
    for item in top_level:
        lines.append(f"| `{item['path']}` | {size(item['bytes'])} | {item['files']:,} |")
    lines += ["", "## Largest output directories", "", "| Directory | Size | Files |", "|---|---:|---:|"]
    for item in output_children[:40]:
        lines.append(f"| `{item['path']}` | {size(item['bytes'])} | {item['files']:,} |")
    lines += ["", "## Generated content and server data", "",
              "These categories can overlap; do not add category totals together. Some contain worlds, plugin data or retained evidence that must be reviewed before cleanup.", "",
              "| Category | Size | Directory roots |", "|---|---:|---:|"]
    for name, item in category_rows.items():
        lines.append(f"| `{name}` | {size(item['bytes'])} | {item['directories']:,} |")
    lines += ["", f"Combined category size without overlapping subtrees: **{size(summary['category_union_bytes'])}**.",
              f"Isolated server fixture size (directories with server.properties below output): **{size(summary['fixture_total_bytes'])}**, {len(fixtures):,} fixtures.",
              "", "## Largest files", "", "| File | Size |", "|---|---:|"]
    for item in summary["largest_files"]:
        lines.append(f"| `{item['path']}` | {size(item['bytes'])} |")
    lines += ["", "## Scan limits", "", f"Skipped entries: `{json.dumps(dict(skipped), ensure_ascii=False)}`.",
              f"Errors: {skipped.get('errors', 0):,}; first 100 recorded in summary.json.",
              "", "The audit performed no cleanup, hashing, plugin installation, world modification or server launch.",
              "Detailed directory inventory: directory-inventory.jsonl. Exact byte counts and extension totals: summary.json.", ""]
    (output / "report.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"report": str(output / "report.md"), "total_bytes": nodes["."].total_bytes,
                      "files": scanned_files, "directories": len(nodes),
                      "free_bytes": disk_after.free, "skipped": dict(skipped)}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
