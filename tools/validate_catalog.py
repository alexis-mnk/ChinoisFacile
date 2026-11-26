#!/usr/bin/env python3
"""Validates all HSK JSON catalog files under app/src/main/assets/catalog/hsk*.json."""

from __future__ import annotations

import json
import re
import sys
import unicodedata
from pathlib import Path

TONE_MARKS_RE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
TRAILING_PUNCT = "。？！，、；：.,?!"
NEUTRAL_TONE_PARTICLES = {"ma", "ne", "de", "le", "ba", "zhe", "guo", "a", "ya", "wa", "na", "la", "o", "en"}
DIGITS_RE = re.compile(r"^[零一二两三四五六七八九十百千万0-9]+$")
ALLOWED_SIMPLE_PROPER_NOUNS = {"中国", "北京", "上海", "小明", "小王", "小李", "法国", "巴黎", "天", "黄河"}
ALLOWED_PROPER_NOUNS = {
    "中国", "北京", "汉语", "中文", "黄河", "亚洲", "京剧", "长城", "长江",
    "元旦", "华裔", "国庆节", "礼拜天", "除夕", "互联网", "元宵节", "华侨",
    "国务院", "摄氏度", "欧洲", "正月", "端午节", "重阳节",
}
SURNAME_RE = re.compile(r"\bsurname\b|\bfamily name\b|\bclan name\b", re.IGNORECASE)
VULGAR_OR_SLANG_RE = re.compile(
    r"(\(slang\)|\(vulgar\)|\(dialect\)|\(taboo\)|\(derog\.\)|\bradical\b|"
    r"\bfuck\w*\b|\bshit\w*\b|\bbitch\w*\b|\bcunt\w*\b|\bwhore\w*\b|"
    r"\bporn\w*\b|\bpenis\b|\bvagina\b|\bbastard\w*\b|\basshole\w*\b|"
    r"\bdickhead\w*\b|\bcock\b|\bpiss\w*\b|\bcrap\b|\bboobs\b|\btits\b|\bscrotum\b)",
    re.IGNORECASE,
)
MECHANICAL_TEMPLATE_RE = re.compile(
    r"( : |: |\([^)]+\)|"
    r"currently practicing:|carefully practice:|practices:|want to learn how to:|"
    r"do this together:|continue tomorrow with:|acts this way:|"
    r'This concept "|already discussed:|knows a lot about:|remember this term:|'
    r"The book mentions:|something new:|Today's situation is very:|This place is particularly:|"
    r"even more:|Everyone's attitude is very:|environment here is really:|"
    r"In what we do, remember:|The teacher often emphasizes:|We should uphold:|Practice this word today:|"
    r"Everyone completes the task|She expressed her idea|Let us keep moving forward|Please get ready|"
    r"Our environment is truly |Everyone needs to |Let's all .+ together!|"
    r"We are going to |He will go to |She is currently doing this:|We have started to |"
    r"I think we can |Let's .+ tomorrow instead\.|We should seriously |I believe she can |"
    r"The teacher is introducing |Everyone knows about |I really like the new |"
    r"Today we are looking at |The school has |This matter is extremely |I find this very |"
    r"This morning is really |This place is very |He keeps working |She prepares to go to Beijing )"
)
# Detects accidental leftover French strings or French diacritics (ignoring legitimate English loanwords)
FRENCH_LOANWORDS_IN_EN = {"café", "cafés", "cliché", "naïve", "résumé", "fiancé", "fiancée", "façade", "entrée", "protégé", "sauté", "sautéed"}
RESIDUAL_FRENCH_WORDS_RE = re.compile(
    r"\b(Unité|Leçon|Terme HSK|nom de famille|patronyme|c'est|aujourd'hui|bonjour|merci|au revoir|s'il vous plaît)\b",
    re.IGNORECASE,
)
FRENCH_DIACRITICS_RE = re.compile(r"[éèêëàâçùûôîœæÉÈÊËÀÂÇÙÛÔÎŒÆ]")

SANDHI_OR_PARTICLE_WORDS = {
    "一", "不", "的", "了", "吗", "呢", "吧", "着", "过", "得", "地", "啊",
    "嘛", "哇", "啦", "哦", "嗯",
}


def has_residual_french(text: str) -> bool:
    if RESIDUAL_FRENCH_WORDS_RE.search(text):
        return True
    cleaned = text
    for loan in FRENCH_LOANWORDS_IN_EN:
        cleaned = re.sub(rf"\b{re.escape(loan)}\b", "", cleaned, flags=re.IGNORECASE)
    return bool(FRENCH_DIACRITICS_RE.search(cleaned))


def norm_pinyin(py: str) -> str:
    return re.sub(
        r"[^a-zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü]",
        "",
        py.lower().replace("v", "ü"),
    )


def strip_tones(py: str) -> str:
    nfd = unicodedata.normalize("NFD", py)
    return "".join(ch for ch in nfd if unicodedata.category(ch) != "Mn")


def load_official_hsk_map(repo_root: Path) -> tuple[dict[str, int], dict[int, set[str]]]:
    data_dir = repo_root / "tools" / "data"
    official_word_level: dict[str, int] = {}
    official_by_level: dict[int, set[str]] = {lv: set() for lv in range(1, 7)}

    for lv in range(1, 7):
        path = data_dir / f"hsk2012_l{lv}.txt"
        if not path.exists():
            raise FileNotFoundError(f"Official Hanban HSK 2.0 (2012) list not found: {path}")
        for line in path.read_text(encoding="utf-8").splitlines():
            clean = line.lstrip("\ufeff").strip()
            if not clean or clean.startswith("#"):
                continue
            hanzi = clean.split("\t")[0].strip()
            if not hanzi or hanzi in official_word_level:
                continue
            official_word_level[hanzi] = lv
            official_by_level[lv].add(hanzi)

    return official_word_level, official_by_level


def validate_catalog_files(catalog_dir: Path, repo_root: Path) -> int:
    files = sorted(catalog_dir.glob("hsk*.json"))
    if not files:
        print(f"ERROR: no hsk*.json files found in {catalog_dir}")
        return 1

    try:
        official_word_level, official_by_level = load_official_hsk_map(repo_root)
    except Exception as exc:
        print(f"ERROR: {exc}")
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
            errors.append(f"[{path.name}] invalid JSON: {exc}")
            continue

        hsk_level = data.get("hskLevel")
        catalog_version = data.get("catalogVersion")
        units = data.get("units", [])
        words = data.get("words", [])
        parsed_catalogs.append((path, hsk_level if isinstance(hsk_level, int) else None, data))

        if not isinstance(hsk_level, int) or not (1 <= hsk_level <= 6):
            errors.append(f"[{path.name}] invalid hskLevel: {hsk_level!r}")
        if not isinstance(catalog_version, int) or catalog_version < 1:
            errors.append(f"[{path.name}] invalid catalogVersion: {catalog_version!r}")

        unit_orders: list[int] = []
        lesson_orders: list[int] = []
        level_lesson_ids: set[str] = set()

        for unit in units:
            uid = unit.get("id", "")
            u_title = unit.get("title", "")
            u_desc = unit.get("description", "")
            if not uid:
                errors.append(f"[{path.name}] empty unit.id")
            elif uid in global_unit_ids:
                errors.append(f"[{path.name}] duplicate unitId: {uid}")
            else:
                global_unit_ids.add(uid)

            if not u_title.startswith("Unit ") or has_residual_french(u_title) or has_residual_french(u_desc):
                errors.append(f"[{path.name}] non-English unit metadata ({uid}): {u_title!r}")

            unit_orders.append(unit.get("orderIndex", -1))
            for lesson in unit.get("lessons", []):
                lid = lesson.get("id", "")
                l_title = lesson.get("title", "")
                if not lid:
                    errors.append(f"[{path.name}] empty lesson.id in {uid}")
                elif lid in global_lesson_ids:
                    errors.append(f"[{path.name}] duplicate lessonId: {lid}")
                else:
                    global_lesson_ids.add(lid)
                    level_lesson_ids.add(lid)

                if not l_title.startswith("Quiz ") or has_residual_french(l_title):
                    errors.append(f"[{path.name}] non-English quiz title ({lid}): {l_title!r}")

                lesson_orders.append(lesson.get("orderIndex", -1))

        if sorted(unit_orders) != list(range(1, len(units) + 1)):
            errors.append(f"[{path.name}] non-consecutive unit orderIndex: {unit_orders}")
        if sorted(lesson_orders) != list(range(1, len(level_lesson_ids) + 1)):
            errors.append(f"[{path.name}] non-consecutive lesson orderIndex: {lesson_orders}")

        local_hanzi: set[str] = set()
        for word in words:
            wid = word.get("id", "")
            lesson_id = word.get("lessonId", "")
            hanzi = word.get("hanzi", "")
            pinyin = word.get("pinyin", "")
            if "french" in word:
                errors.append(f"[{path.name}] {wid} still contains deprecated 'french' field")
            english = word.get("english", "")
            example = word.get("example", {})

            if not wid:
                errors.append(f"[{path.name}] empty word.id")
            elif wid in global_word_ids:
                errors.append(f"[{path.name}] duplicate wordId: {wid}")
            else:
                global_word_ids.add(wid)

            if lesson_id not in level_lesson_ids:
                errors.append(f"[{path.name}] {wid} references unknown lessonId: {lesson_id!r}")

            if not hanzi.strip():
                errors.append(f"[{path.name}] {wid} has empty hanzi")
            else:
                if hanzi in local_hanzi:
                    errors.append(f"[{path.name}] duplicate hanzi in file: {hanzi} ({wid})")
                local_hanzi.add(hanzi)

                if hanzi in global_hanzi:
                    errors.append(f"[{path.name}] cross-level duplicate hanzi: {hanzi} ({wid} vs {global_hanzi[hanzi]})")
                else:
                    global_hanzi[hanzi] = f"{path.name}:{wid}"
                    if isinstance(hsk_level, int) and 1 <= hsk_level <= 6:
                        word_to_level[hanzi] = hsk_level

                if isinstance(hsk_level, int) and 1 <= hsk_level <= 6:
                    official_lv = official_word_level.get(hanzi)
                    if official_lv is None:
                        errors.append(f"[{path.name}] {wid} ({hanzi}) absent from official HSK 2.0 list")
                    elif official_lv != hsk_level:
                        errors.append(
                            f"[{path.name}] {wid} ({hanzi}) belongs to HSK {official_lv}, not HSK {hsk_level}"
                        )

            if not pinyin.strip():
                errors.append(f"[{path.name}] {wid} has empty pinyin")
            else:
                has_tone = bool(TONE_MARKS_RE.search(pinyin))
                is_neutral_particle = pinyin.strip().lower() in NEUTRAL_TONE_PARTICLES
                if not has_tone and not is_neutral_particle:
                    errors.append(f"[{path.name}] {wid} ({hanzi}) missing Unicode tone mark: {pinyin!r}")
                if any(ch.isupper() for ch in pinyin) and hanzi not in ALLOWED_PROPER_NOUNS:
                    errors.append(f"[{path.name}] {wid} ({hanzi}) uppercase pinyin (surname?): {pinyin!r}")

            if not english.strip():
                errors.append(f"[{path.name}] {wid} has empty english translation")
            elif SURNAME_RE.search(english) and hanzi != "姓":
                errors.append(f"[{path.name}] {wid} ({hanzi}) has surname translation: {english!r}")
            elif VULGAR_OR_SLANG_RE.search(english):
                errors.append(f"[{path.name}] {wid} ({hanzi}) has vulgar/slang/radical translation: {english!r}")
            elif has_residual_french(english):
                errors.append(f"[{path.name}] {wid} ({hanzi}) has residual French translation: {english!r}")

            if "french" in example:
                errors.append(f"[{path.name}] {wid} example still contains deprecated 'french' field")
            ex_hanzi = example.get("hanzi", "")
            ex_pinyin = example.get("pinyin", "")
            ex_english = example.get("english", "")
            tokens = example.get("tokens", [])

            if not ex_hanzi.strip() or not ex_pinyin.strip() or not ex_english.strip():
                errors.append(f"[{path.name}] {wid} has incomplete example sentence")
            elif VULGAR_OR_SLANG_RE.search(ex_english):
                errors.append(f"[{path.name}] {wid} ({hanzi}) has vulgar/slang example: {ex_english!r}")
            elif has_residual_french(ex_english):
                errors.append(f"[{path.name}] {wid} ({hanzi}) has residual French example: {ex_english!r}")
            elif isinstance(hsk_level, int) and hsk_level in (1, 2, 3) and MECHANICAL_TEMPLATE_RE.search(ex_english):
                errors.append(f"[{path.name}] {wid} ({hanzi}) still uses mechanical template: {ex_english!r}")

            if not isinstance(tokens, list) or len(tokens) < 3:
                errors.append(f"[{path.name}] {wid} must have at least 3 tokens: {tokens!r}")
            else:
                if len(tokens) != len(set(tokens)):
                    errors.append(f"[{path.name}] {wid} has duplicate tokens: {tokens!r}")
                if hanzi not in tokens:
                    errors.append(f"[{path.name}] {wid} ({hanzi}) not present in example.tokens: {tokens!r}")
                cleaned_hanzi = ex_hanzi.rstrip(TRAILING_PUNCT)
                joined = "".join(tokens)
                if joined != cleaned_hanzi:
                    errors.append(f"[{path.name}] {wid} mismatched tokens: {joined!r} != {cleaned_hanzi!r}")

            if pinyin.strip() and ex_pinyin.strip():
                n_wpy = norm_pinyin(pinyin)
                n_expy = norm_pinyin(ex_pinyin)
                if n_wpy and n_wpy not in n_expy:
                    is_sandhi_ok = (
                        hanzi in SANDHI_OR_PARTICLE_WORDS
                        and strip_tones(n_wpy) in strip_tones(n_expy)
                    )
                    if not is_sandhi_ok:
                        errors.append(
                            f"[{path.name}] {wid} ({hanzi}) pinyin {pinyin!r} missing from example.pinyin {ex_pinyin!r}"
                        )

        if isinstance(hsk_level, int) and 1 <= hsk_level <= 6:
            missing_official = sorted(official_by_level[hsk_level] - local_hanzi)
            if missing_official:
                errors.append(
                    f"[{path.name}] {len(missing_official)} official HSK {hsk_level} word(s) missing: {missing_official[:10]}"
                )

        print(
            f"OK {path.name}: HSK {hsk_level} (v{catalog_version}) — "
            f"{len(units)} units, {len(level_lesson_ids)} quizzes, {len(words)} words"
        )

    # Pass 2: strict cumulative vocabulary ceiling HSK 1..N (for N = 1..5)
    print("\nCumulative vocabulary ceiling check (HSK 1..N):")
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
                        f"[{path.name}] {wid} ({hanzi}) uses token '{tok}' from HSK {tok_level} > {hsk_level}"
                    )
                elif tok not in allowed_vocab and not DIGITS_RE.match(tok):
                    has_violation = True
                    errors.append(
                        f"[{path.name}] {wid} ({hanzi}) uses out-of-catalog token '{tok}'"
                    )
            if has_violation:
                ceiling_violations_in_level += 1
        print(f"  HSK {hsk_level} ceiling: {ceiling_violations_in_level} non-compliant sentence(s)")

    if errors:
        print(f"\nERRORS DETECTED ({len(errors)}):")
        max_display = 50
        for err in errors[:max_display]:
            print(f"  - {err}")
        if len(errors) > max_display:
            print(f"  ... and {len(errors) - max_display} more error(s)")
        return 1

    print(f"\nValidation passed: {len(files)} files, {len(global_word_ids)} unique words.")
    return 0


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    catalog_dir = repo_root / "app" / "src" / "main" / "assets" / "catalog"
    return validate_catalog_files(catalog_dir, repo_root)


if __name__ == "__main__":
    sys.exit(main())
