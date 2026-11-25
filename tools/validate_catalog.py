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
DIGITS_RE = re.compile(r"^[零一二两三四五六七八九十百千万0-9]+$")
ALLOWED_SIMPLE_PROPER_NOUNS = {"中国", "北京", "上海", "小明", "小王", "小李", "法国", "巴黎"}
OFFICIAL_TAG_RE = re.compile(r"^o([1-6])$")


def load_official_hsk_map(repo_root: Path) -> tuple[dict[str, int], dict[int, set[str]]]:
    """Charge la correspondance officielle HSK 2.0 (o1..o6) depuis tools/.cache/complete.min.json."""
    official_path = repo_root / "tools" / ".cache" / "complete.min.json"
    if not official_path.exists():
        raise FileNotFoundError(
            f"Référence officielle HSK 2.0 introuvable : {official_path}"
        )

    entries = json.loads(official_path.read_text(encoding="utf-8"))
    official_word_level: dict[str, int] = {}
    official_by_level: dict[int, set[str]] = {lv: set() for lv in range(1, 7)}

    for entry in entries:
        levels = []
        for tag in entry.get("l", []):
            match = OFFICIAL_TAG_RE.match(tag)
            if match:
                levels.append(int(match.group(1)))
        if not levels:
            continue
        hanzi = entry["s"]
        if hanzi in official_word_level:
            continue
        official_lv = min(levels)
        official_word_level[hanzi] = official_lv
        official_by_level[official_lv].add(hanzi)

    return official_word_level, official_by_level


def validate_catalog_files(catalog_dir: Path, repo_root: Path) -> int:
    files = sorted(catalog_dir.glob("hsk*.json"))
    if not files:
        print(f"ERREUR : aucun fichier hsk*.json trouvé dans {catalog_dir}")
        return 1

    try:
        official_word_level, official_by_level = load_official_hsk_map(repo_root)
    except Exception as exc:
        print(f"ERREUR : {exc}")
        return 1

    errors: list[str] = []
    global_unit_ids: set[str] = set()
    global_lesson_ids: set[str] = set()
    global_word_ids: set[str] = set()
    global_hanzi: dict[str, str] = {}
    parsed_catalogs: list[tuple[Path, int | None, dict]] = []
    word_to_level: dict[str, int] = {}

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
        parsed_catalogs.append((path, hsk_level if isinstance(hsk_level, int) else None, data))

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
                    if isinstance(hsk_level, int) and 1 <= hsk_level <= 6:
                        word_to_level[hanzi] = hsk_level

                if isinstance(hsk_level, int) and 1 <= hsk_level <= 6:
                    official_lv = official_word_level.get(hanzi)
                    if official_lv is None:
                        errors.append(
                            f"[{path.name}] {wid} ({hanzi}) absent de la liste officielle HSK 2.0"
                        )
                    elif official_lv != hsk_level:
                        errors.append(
                            f"[{path.name}] {wid} ({hanzi}) appartient au niveau officiel HSK {official_lv}, pas HSK {hsk_level}"
                        )

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

        if isinstance(hsk_level, int) and 1 <= hsk_level <= 6:
            missing_official = sorted(official_by_level[hsk_level] - local_hanzi)
            if missing_official:
                errors.append(
                    f"[{path.name}] {len(missing_official)} mot(s) officiel(s) HSK {hsk_level} "
                    f"manquant(s) : {missing_official[:10]}"
                )

        print(
            f"OK {path.name} : HSK {hsk_level} (v{catalog_version}) — "
            f"{len(units)} unités, {len(level_lesson_ids)} leçons, {len(words)} mots"
        )

    # Passe 2 : vérification stricte du plafond de vocabulaire cumulé HSK 1..N (pour N = 1 à 5)
    print("\nVérification du plafond de vocabulaire (HSK 1..N) :")
    for path, hsk_level, data in parsed_catalogs:
        if hsk_level is None or not (1 <= hsk_level <= 5):
            continue
        allowed_vocab = {hz for hz, lv in word_to_level.items() if lv <= hsk_level} | ALLOWED_SIMPLE_PROPER_NOUNS
        ceiling_violations_in_level = 0
        for word in data.get("words", []):
            wid = word.get("id", "")
            hanzi = word.get("hanzi", "")
            tokens = word.get("example", {}).get("tokens", [])
            if not isinstance(tokens, list):
                continue
            has_violation = False
            for tok in tokens:
                tok_level = word_to_level.get(tok)
                if tok_level is not None and tok_level > hsk_level:
                    has_violation = True
                    errors.append(
                        f"[{path.name}] {wid} ({hanzi}) utilise le token '{tok}' "
                        f"de niveau HSK {tok_level} > {hsk_level}"
                    )
                elif tok not in allowed_vocab and not DIGITS_RE.match(tok):
                    has_violation = True
                    errors.append(
                        f"[{path.name}] {wid} ({hanzi}) utilise le token hors-catalogue '{tok}'"
                    )
            if has_violation:
                ceiling_violations_in_level += 1
        print(
            f"  Plafond vocabulaire HSK {hsk_level} : "
            f"{ceiling_violations_in_level} phrase(s) non conforme(s)"
        )

    if errors:
        print(f"\nERREURS DÉTECTÉES ({len(errors)}) :")
        max_display = 50
        for err in errors[:max_display]:
            print(f"  - {err}")
        if len(errors) > max_display:
            print(f"  ... et {len(errors) - max_display} autre(s) erreur(s)")
        return 1

    print(f"\nValidation réussie : {len(files)} fichiers, {len(global_word_ids)} mots uniques.")
    return 0


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    catalog_dir = repo_root / "app" / "src" / "main" / "assets" / "catalog"
    return validate_catalog_files(catalog_dir, repo_root)


if __name__ == "__main__":
    sys.exit(main())
