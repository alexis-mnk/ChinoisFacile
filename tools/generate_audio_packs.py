#!/usr/bin/env python3
"""
Generate compact uncompressed Opus (.ogg) audio .pack archives for EasyMandarin
using edge-tts + ffmpeg, with local SHA-256 caching and resume support.

Usage examples:
  python tools/generate_audio_packs.py --levels 1,grammar
  python tools/generate_audio_packs.py --levels all --concurrency 4
  python tools/generate_audio_packs.py --dry-run
"""

from __future__ import annotations

import argparse
import asyncio
import glob
import hashlib
import json
import os
import random
import shutil
import struct
import subprocess
import sys
import time
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

MAGIC = b"EMAU"
FORMAT_VERSION = 1
HEADER_SIZE_BYTES = 10
INDEX_ENTRY_SIZE_BYTES = 20

DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"
DEFAULT_BITRATE = "20k"
DEFAULT_SAMPLE_RATE = "24000"
DEFAULT_CONCURRENCY = 4
MIN_TRIMMED_OGG_BYTES = 300

SILENCE_FILTER = (
    "silenceremove=start_periods=1:start_duration=0:start_threshold=-50dB:start_silence=0.04:detection=peak,"
    "areverse,"
    "silenceremove=start_periods=1:start_duration=0:start_threshold=-50dB:start_silence=0.04:detection=peak,"
    "areverse"
)


def compute_hashes(text: str) -> Tuple[str, int]:
    normalized = text.strip()
    digest = hashlib.sha256(normalized.encode("utf-8")).digest()
    key_hash64 = struct.unpack(">q", digest[:8])[0]
    return digest.hex(), key_hash64


def find_ffmpeg(explicit_path: Optional[str]) -> Optional[str]:
    if explicit_path:
        p = Path(explicit_path)
        return str(p) if p.is_file() else None

    on_path = shutil.which("ffmpeg")
    if on_path:
        return on_path

    if os.name == "nt":
        local_app_data = os.environ.get("LOCALAPPDATA", "")
        candidates: List[Path] = []
        if local_app_data:
            links_exe = Path(local_app_data) / "Microsoft" / "WinGet" / "Links" / "ffmpeg.exe"
            if links_exe.is_file():
                return str(links_exe)
            pkg_pattern = str(
                Path(local_app_data)
                / "Microsoft"
                / "WinGet"
                / "Packages"
                / "*FFmpeg*"
                / "**"
                / "ffmpeg.exe"
            )
            candidates.extend(Path(p) for p in glob.glob(pkg_pattern, recursive=True))
        for extra in (
            Path("C:/ffmpeg/bin/ffmpeg.exe"),
            Path("C:/Program Files/ffmpeg/bin/ffmpeg.exe"),
        ):
            if extra.is_file():
                candidates.append(extra)
        if candidates:
            return str(candidates[0])
    return None


def parse_levels(raw: str) -> List[str]:
    raw_clean = raw.strip().lower()
    if raw_clean == "all":
        return ["hsk1", "hsk2", "hsk3", "hsk4", "hsk5", "hsk6", "grammar"]

    selected: List[str] = []
    for part in raw_clean.split(","):
        token = part.strip()
        if not token:
            continue
        if token in {"1", "2", "3", "4", "5", "6"}:
            name = f"hsk{token}"
        elif token in {"hsk1", "hsk2", "hsk3", "hsk4", "hsk5", "hsk6", "grammar"}:
            name = token
        else:
            raise ValueError(f"Unknown level specifier: '{token}'. Use 1..6, grammar, or all.")
        if name not in selected:
            selected.append(name)
    if not selected:
        raise ValueError("No valid levels specified.")
    return selected


def load_pack_texts(catalog_dir: Path, pack_name: str) -> List[str]:
    seen = set()
    ordered: List[str] = []

    def add_text(raw: str) -> None:
        cleaned = raw.strip()
        if cleaned and cleaned not in seen:
            seen.add(cleaned)
            ordered.append(cleaned)

    if pack_name == "grammar":
        grammar_path = catalog_dir / "grammar_notes.json"
        data = json.loads(grammar_path.read_text(encoding="utf-8"))
        for note in data["notes"]:
            for example in note["examples"]:
                add_text(example["chinese"])
    else:
        hsk_path = catalog_dir / f"{pack_name}.json"
        data = json.loads(hsk_path.read_text(encoding="utf-8"))
        for word in data["words"]:
            add_text(word["hanzi"])
            add_text(word["example"]["hanzi"])

    return ordered


def cache_path_for(cache_dir: Path, voice: str, sha256_hex: str) -> Path:
    safe_voice = voice.replace("/", "_").replace("\\", "_")
    return cache_dir / safe_voice / sha256_hex[:2] / f"{sha256_hex}.ogg"


def encode_mp3_to_opus(
    ffmpeg_bin: str,
    mp3_path: Path,
    ogg_tmp_path: Path,
    bitrate: str,
    use_silence_trim: bool,
) -> Tuple[bool, str]:
    cmd = [
        ffmpeg_bin,
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        str(mp3_path),
    ]
    if use_silence_trim:
        cmd.extend(["-af", SILENCE_FILTER])
    cmd.extend([
        "-c:a",
        "libopus",
        "-b:a",
        bitrate,
        "-ar",
        DEFAULT_SAMPLE_RATE,
        "-ac",
        "1",
        "-vbr",
        "on",
        "-application",
        "voip",
        str(ogg_tmp_path),
    ])
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        return False, proc.stderr.strip()
    if not ogg_tmp_path.is_file() or ogg_tmp_path.stat().st_size == 0:
        return False, "empty output"
    return True, ""


async def generate_single_clip(
    text: str,
    voice: str,
    bitrate: str,
    ffmpeg_bin: str,
    cache_dir: Path,
    semaphore: asyncio.Semaphore,
    edge_tts_module: object,
) -> Path:
    sha_hex, _ = compute_hashes(text)
    ogg_path = cache_path_for(cache_dir, voice, sha_hex)
    if ogg_path.is_file() and ogg_path.stat().st_size > 0:
        return ogg_path

    ogg_path.parent.mkdir(parents=True, exist_ok=True)
    mp3_tmp = ogg_path.with_suffix(".tmp.mp3")
    ogg_tmp = ogg_path.with_suffix(".tmp.ogg")

    async with semaphore:
        if ogg_path.is_file() and ogg_path.stat().st_size > 0:
            return ogg_path

        last_error: Optional[Exception] = None
        max_attempts = 5
        for attempt in range(max_attempts):
            try:
                communicate = edge_tts_module.Communicate(text=text, voice=voice)
                await communicate.save(str(mp3_tmp))
                if not mp3_tmp.is_file() or mp3_tmp.stat().st_size == 0:
                    raise RuntimeError(f"edge-tts produced empty audio for '{text}'")

                ok, err = await asyncio.to_thread(
                    encode_mp3_to_opus, ffmpeg_bin, mp3_tmp, ogg_tmp, bitrate, True
                )
                if not ok or ogg_tmp.stat().st_size < MIN_TRIMMED_OGG_BYTES:
                    ok_fallback, err_fallback = await asyncio.to_thread(
                        encode_mp3_to_opus, ffmpeg_bin, mp3_tmp, ogg_tmp, bitrate, False
                    )
                    if not ok_fallback:
                        raise RuntimeError(
                            f"ffmpeg failed for '{text}': trimmed=({err}), untrimmed=({err_fallback})"
                        )

                os.replace(ogg_tmp, ogg_path)
                return ogg_path
            except Exception as exc:
                last_error = exc
                if attempt + 1 < max_attempts:
                    delay = (2 ** attempt) + random.uniform(0.15, 0.75)
                    await asyncio.sleep(delay)
            finally:
                for tmp in (mp3_tmp, ogg_tmp):
                    if tmp.exists():
                        try:
                            tmp.unlink()
                        except OSError:
                            pass

        raise RuntimeError(f"Failed to synthesize '{text}' after {max_attempts} attempts: {last_error}")


async def ensure_clips_generated(
    texts: Sequence[str],
    voice: str,
    bitrate: str,
    ffmpeg_bin: str,
    cache_dir: Path,
    concurrency: int,
    edge_tts_module: object,
) -> Tuple[int, int]:
    pending: List[str] = []
    cached = 0
    for text in texts:
        sha_hex, _ = compute_hashes(text)
        p = cache_path_for(cache_dir, voice, sha_hex)
        if p.is_file() and p.stat().st_size > 0:
            cached += 1
        else:
            pending.append(text)

    if not pending:
        return cached, 0

    semaphore = asyncio.Semaphore(concurrency)
    total = len(pending)
    done = 0
    started = time.monotonic()

    tasks = [
        asyncio.create_task(
            generate_single_clip(
                text=text,
                voice=voice,
                bitrate=bitrate,
                ffmpeg_bin=ffmpeg_bin,
                cache_dir=cache_dir,
                semaphore=semaphore,
                edge_tts_module=edge_tts_module,
            )
        )
        for text in pending
    ]

    for coro in asyncio.as_completed(tasks):
        await coro
        done += 1
        if done % 25 == 0 or done == total:
            elapsed = max(0.01, time.monotonic() - started)
            rate = done / elapsed
            print(f"    Synthesized {done}/{total} ({rate:.1f} clips/s)", flush=True)

    return cached, total


def write_pack_archive(
    pack_path: Path,
    texts: Sequence[str],
    voice: str,
    cache_dir: Path,
) -> Tuple[int, int]:
    entries: List[Tuple[int, str, bytes]] = []
    seen_hashes: Dict[int, str] = {}

    for text in texts:
        sha_hex, key_hash64 = compute_hashes(text)
        prev = seen_hashes.get(key_hash64)
        if prev is not None and prev != text:
            raise RuntimeError(f"64-bit hash collision between '{prev}' and '{text}'")
        seen_hashes[key_hash64] = text

        ogg_file = cache_path_for(cache_dir, voice, sha_hex)
        payload = ogg_file.read_bytes()
        if not payload:
            raise RuntimeError(f"Empty cached clip for '{text}' at {ogg_file}")
        entries.append((key_hash64, text, payload))

    entries.sort(key=lambda item: item[0])

    pack_path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = pack_path.with_suffix(".pack.tmp")
    count = len(entries)
    current_offset = HEADER_SIZE_BYTES + count * INDEX_ENTRY_SIZE_BYTES

    with open(tmp_path, "wb") as f:
        f.write(MAGIC)
        f.write(struct.pack(">HI", FORMAT_VERSION, count))
        for key_hash64, _, payload in entries:
            f.write(struct.pack(">qqi", key_hash64, current_offset, len(payload)))
            current_offset += len(payload)
        for _, _, payload in entries:
            f.write(payload)

    os.replace(tmp_path, pack_path)
    return count, pack_path.stat().st_size


def format_size(size_bytes: int) -> str:
    if size_bytes >= 1024 * 1024:
        return f"{size_bytes / (1024 * 1024):.2f} MB"
    return f"{size_bytes / 1024:.1f} KB"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate Opus .pack audio archives for EasyMandarin.")
    parser.add_argument(
        "--levels",
        default="all",
        help="Comma-separated packs to build: 1..6, grammar, or all (default: all)",
    )
    parser.add_argument(
        "--voice",
        default=DEFAULT_VOICE,
        help=f"edge-tts voice name (default: {DEFAULT_VOICE})",
    )
    parser.add_argument(
        "--concurrency",
        type=int,
        default=DEFAULT_CONCURRENCY,
        help=f"Max parallel TTS requests (default: {DEFAULT_CONCURRENCY})",
    )
    parser.add_argument(
        "--bitrate",
        default=DEFAULT_BITRATE,
        help=f"Opus bitrate (default: {DEFAULT_BITRATE})",
    )
    parser.add_argument(
        "--catalog-dir",
        default="app/src/main/assets/catalog",
        help="Catalog JSON directory",
    )
    parser.add_argument(
        "--cache-dir",
        default="tools/.audio_cache",
        help="Cache directory for per-clip .ogg files",
    )
    parser.add_argument(
        "--output-dir",
        default="app/src/main/assets/audio",
        help="Output directory for .pack archives",
    )
    parser.add_argument(
        "--ffmpeg",
        default=None,
        help="Optional explicit path to ffmpeg executable",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show pack counts without downloading or encoding audio",
    )
    args = parser.parse_args()

    try:
        pack_names = parse_levels(args.levels)
    except ValueError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 2

    catalog_dir = Path(args.catalog_dir)
    cache_dir = Path(args.cache_dir)
    output_dir = Path(args.output_dir)

    if not catalog_dir.is_dir():
        print(f"Error: Catalog directory not found: {catalog_dir}", file=sys.stderr)
        return 2

    pack_texts: Dict[str, List[str]] = {
        name: load_pack_texts(catalog_dir, name) for name in pack_names
    }
    unique_texts = list(dict.fromkeys(t for name in pack_names for t in pack_texts[name]))

    print(f"Voice: {args.voice} | Packs: {', '.join(pack_names)} | Unique clips: {len(unique_texts)}")
    for name in pack_names:
        print(f"  - {name}.pack: {len(pack_texts[name])} clips")

    if args.dry_run:
        return 0

    try:
        import edge_tts  # type: ignore
    except ImportError:
        print(
            "Error: Python package 'edge-tts' is not installed.\n"
            "Install it with: pip install edge-tts",
            file=sys.stderr,
        )
        return 1

    ffmpeg_bin = find_ffmpeg(args.ffmpeg)
    if not ffmpeg_bin:
        print(
            "Error: 'ffmpeg' was not found on PATH or standard WinGet directories.\n"
            "Install it on Windows with: winget install --id Gyan.FFmpeg -e",
            file=sys.stderr,
        )
        return 1
    print(f"Using ffmpeg: {ffmpeg_bin}")

    started = time.monotonic()
    cached_count, synthesized_count = asyncio.run(
        ensure_clips_generated(
            texts=unique_texts,
            voice=args.voice,
            bitrate=args.bitrate,
            ffmpeg_bin=ffmpeg_bin,
            cache_dir=cache_dir,
            concurrency=max(1, args.concurrency),
            edge_tts_module=edge_tts,
        )
    )
    print(
        f"Clips ready in {time.monotonic() - started:.1f}s "
        f"(cached: {cached_count}, newly synthesized: {synthesized_count})."
    )

    print("Writing .pack archives:")
    total_size = 0
    for name in pack_names:
        out_path = output_dir / f"{name}.pack"
        count, size_bytes = write_pack_archive(
            pack_path=out_path,
            texts=pack_texts[name],
            voice=args.voice,
            cache_dir=cache_dir,
        )
        total_size += size_bytes
        print(f"  -> {out_path} ({count} clips, {format_size(size_bytes)})")

    print(f"Total packed size: {format_size(total_size)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
