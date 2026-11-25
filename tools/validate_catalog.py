#!/usr/bin/env python3
"""Valide tous les fichiers catalogue JSON sous app/src/main/assets/catalog/hsk*.json."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

TONE_MARKS_RE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
TRAILING_PUNCT = "。？！，、；：.,?!"
NEUTRAL_TONE_PARTICLES = {"ma", "ne", "de", "le", "ba", "zhe", "guo", "a", "ya", "wa", "na"}


def validate_catalog_files(catalog_dir: Path) -> int:
    files = sorted(catalog_dir.glob("hsk*.json"))
    if not files:
        print(f"ERREUR : aucun fichier hsk*.json trouvé dans {catalog_dir}")
        return 1

    errors: list[str] = []
    global_unit_ids: set[str] = set()
    global_lesson_ids: set[str] = set()
    global_word_ids: set[str] = set()
    global_hanzi: dict[str, str] = {}

    for path in files:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"[{path.name}] JSON invalide : {exc}")
            continue

        hsk_level = data.get("hskLevel")
        catalog_version = data.get("catalogVersion")
        units = data.get("units", [])
        words = data.get("words", [])

        if not isinstance(hsk_level, int) or not (1 <= hsk_level <= 6):
            errors.append(f"[{path.name}] hskLevel invalide : {hsk_level!r}")
        if not isinstance(catalog_version, int) or catalog_version < 1:
            errors.append(f"[{path.name}] catalogVersion invalide : {catalog_version!r}")

        unit_orders: list[int] = []
        lesson_orders: list[int] = []
        level_lesson_ids: set[str] = set()

        for unit in units:
            uid = unit.get("id", "")
            if not uid:
                errors.append(f"[{path.name}] unit.id vide")
            elif uid in global_unit_ids:
                errors.append(f"[{path.name}] doublon d'unitId : {uid}")
            else:
                global_unit_ids.add(uid)

            unit_orders.append(unit.get("orderIndex", -1))
            for lesson in unit.get("lessons", []):
                lid = lesson.get("id", "")
                if not lid:
                    errors.append(f"[{path.name}] lesson.id vide dans {uid}")
                elif lid in global_lesson_ids:
                    errors.append(f"[{path.name}] doublon de lessonId : {lid}")
                else:
                    global_lesson_ids.add(lid)
                    level_lesson_ids.add(lid)

                lesson_orders.append(lesson.get("orderIndex", -1))

        if sorted(unit_orders) != list(range(1, len(units) + 1)):
            errors.append(
                f"[{path.name}] orderIndex des unités non consécutifs : {unit_orders}"
            )
        if sorted(lesson_orders) != list(range(1, len(level_lesson_ids) + 1)):
            errors.append(
                f"[{path.name}] orderIndex des leçons non consécutifs : {lesson_orders}"
            )

        local_hanzi: set[str] = set()
        for word in words:
            wid = word.get("id", "")
            lesson_id = word.get("lessonId", "")
            hanzi = word.get("hanzi", "")
            pinyin = word.get("pinyin", "")
            french = word.get("french", "")
            example = word.get("example", {})

            if not wid:
                errors.append(f"[{path.name}] word.id vide")
            elif wid in global_word_ids:
                errors.append(f"[{path.name}] doublon de wordId : {wid}")
            else:
                global_word_ids.add(wid)

            if lesson_id not in level_lesson_ids:
                errors.append(f"[{path.name}] {wid} référence un lessonId inconnu : {lesson_id!r}")

            if not hanzi.strip():
                errors.append(f"[{path.name}] {wid} a un hanzi vide")
            else:
                if hanzi in local_hanzi:
                    errors.append(f"[{path.name}] doublon de hanzi dans le fichier : {hanzi} ({wid})")
                local_hanzi.add(hanzi)

                if hanzi in global_hanzi:
                    errors.append(
                        f"[{path.name}] doublon de hanzi inter-niveaux : {hanzi} ({wid} vs {global_hanzi[hanzi]})"
                    )
                else:
                    global_hanzi[hanzi] = f"{path.name}:{wid}"

            if not pinyin.strip():
                errors.append(f"[{path.name}] {wid} a un pinyin vide")
            else:
                has_tone = bool(TONE_MARKS_RE.search(pinyin))
                is_neutral_particle = pinyin.strip().lower() in NEUTRAL_TONE_PARTICLES
                if not has_tone and not is_neutral_particle:
                    errors.append(
                        f"[{path.name}] {wid} ({hanzi}) n'a pas de marque de ton Unicode valide : {pinyin!r}"
                    )

            if not french.strip():
                errors.append(f"[{path.name}] {wid} a une traduction française vide")

            ex_hanzi = example.get("hanzi", "")
            ex_pinyin = example.get("pinyin", "")
            ex_french = example.get("french", "")
            tokens = example.get("tokens", [])

            if not ex_hanzi.strip() or not ex_pinyin.strip() or not ex_french.strip():
                errors.append(f"[{path.name}] {wid} a une phrase d'exemple incomplète")

            if not isinstance(tokens, list) or len(tokens) < 3:
                errors.append(f"[{path.name}] {wid} doit avoir au moins 3 tokens : {tokens!r}")
            else:
                if len(tokens) != len(set(tokens)):
                    errors.append(f"[{path.name}] {wid} a des tokens dupliqués : {tokens!r}")
                cleaned_hanzi = ex_hanzi.rstrip(TRAILING_PUNCT)
                joined = "".join(tokens)
                if joined != cleaned_hanzi:
                    errors.append(
                        f"[{path.name}] {wid} tokens incohérents : {joined!r} != {cleaned_hanzi!r}"
                    )

        print(
            f"OK {path.name} : HSK {hsk_level} (v{catalog_version}) — "
            f"{len(units)} unités, {len(level_lesson_ids)} leçons, {len(words)} mots"
        )

    if errors:
        print("\nERREURS DÉTECTÉES :")
        for err in errors:
            print(f"  - {err}")
        return 1

    print(f"\nValidation réussie : {len(files)} fichiers, {len(global_word_ids)} mots uniques.")
    return 0


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    catalog_dir = repo_root / "app" / "src" / "main" / "assets" / "catalog"
    return validate_catalog_files(catalog_dir)


if __name__ == "__main__":
    sys.exit(main())
