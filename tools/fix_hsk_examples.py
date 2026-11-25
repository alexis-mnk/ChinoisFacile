#!/usr/bin/env python3
"""
Audit et mise en conformité pédagogique des phrases d'exemple (HSK 1 à HSK 5) :
garantit que chaque token de `example.tokens` dans `hskN.json` appartient exclusivement
au vocabulaire cumulé HSK 1..N (plus les chiffres et quelques noms propres/lieux simples).

Préserve strictement tous les `units`, `id`, `lessonId`, `hanzi`, `pinyin`, `french` existants,
n'altère pas `hsk6.json`, et incrémente `catalogVersion` dans chaque fichier modifié.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path
from typing import Any

import jieba
from pypinyin import Style, pinyin as pypinyin_fn

REPO_ROOT = Path(__file__).resolve().parent.parent
CATALOG_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "catalog"
CACHE_DIR = Path(__file__).resolve().parent / ".cache"

DIGITS_RE = re.compile(r"^[零一二两三四五六七八九十百千万0-9]+$")
TRAILING_PUNCT = "。？！，、；：.,?!"
ALLOWED_SIMPLE_PROPER_NOUNS = {"中国", "北京", "上海", "小明", "小王", "小李", "法国", "巴黎"}

# Surcharge explicite pour les mots courants de HSK 1 à HSK 3 afin d'offrir
# des phrases courtes, naturelles et idiomatiques 100 % conformes au niveau officiel.
HANDCRAFTED_EXAMPLES: dict[tuple[int, str], tuple[list[str], str, str]] = {
    # --- HSK 1 ---
    (1, "好"): (["你", "好", "认识", "很", "高兴"], "！", "Bonjour, ravi de faire ta connaissance !"),
    (1, "喂"): (["喂", "你", "好", "在", "家", "吗"], "？", "Allô, bonjour, es-tu à la maison ?"),
    (1, "谢谢"): (["老师", "谢谢", "你"], "！", "Merci, professeur !"),
    (1, "再见"): (["朋友", "明天", "再见"], "！", "Au revoir mon ami, à demain !"),
    (1, "你"): (["你", "好", "我", "很", "高兴"], "。", "Bonjour, je suis très content."),
    (1, "不客气"): (["不客气", "我们", "是", "朋友"], "。", "De rien, nous sommes amis."),
    (1, "请"): (["请", "你", "喝", "茶"], "。", "Je t'en prie, bois du thé."),
    (1, "对不起"): (["对不起", "我", "今天", "不", "去"], "。", "Désolé, je n'y vais pas aujourd'hui."),
    (1, "没关系"): (["没关系", "明天", "来", "我", "家"], "。", "Ce n'est pas grave, viens chez moi demain."),
    (1, "没"): (["我", "没", "去", "学校"], "。", "Je ne suis pas allé à l'école."),
    (1, "先生"): (["那", "个", "先生", "是", "医生"], "。", "Ce monsieur est médecin."),
    (1, "小姐"): (["这", "个", "小姐", "是", "中国", "人"], "。", "Cette demoiselle est chinoise."),
    (1, "菜"): (["我", "很", "喜欢", "中国", "菜"], "。", "J'aime beaucoup la cuisine chinoise."),
    (1, "苹果"): (["我", "喜欢", "吃", "苹果"], "。", "J'aime manger des pommes."),
    (1, "猫"): (["她", "很", "喜欢", "这", "个", "猫"], "。", "Elle aime beaucoup ce chat."),
    (1, "狗"): (["那", "个", "狗", "很", "大"], "。", "Ce chien est très grand."),
    (1, "八"): (["我", "八", "点", "去", "学校"], "。", "Je vais à l'école à huit heures."),
    (1, "二"): (["我", "有", "二", "十", "块", "钱"], "。", "J'ai vingt yuans."),
    (1, "零"): (["现在", "零", "点", "了"], "。", "Il est maintenant minuit."),
    (1, "小"): (["这", "个", "杯子", "很", "小"], "。", "Cette tasse est très petite."),
    (1, "里"): (["老师", "在", "学校", "里"], "。", "Le professeur est dans l'école."),
    (1, "饭馆"): (["我", "和", "朋友", "去", "饭馆"], "。", "Mon ami et moi allons au restaurant."),
    (1, "火车站"): (["我", "现在", "去", "火车站"], "。", "Je vais maintenant à la gare."),
    (1, "东西"): (["我", "去", "商店", "买", "东西"], "。", "Je vais au magasin acheter des choses."),
    (1, "些"): (["我", "买", "了", "些", "水果"], "。", "J'ai acheté quelques fruits."),
    (1, "字"): (["这", "个", "字", "很", "漂亮"], "。", "Ce caractère est très joli."),
    # --- HSK 2 ---
    (2, "也"): (["我", "很", "高兴", "他", "也", "是"], "。", "Je suis très content, et lui aussi."),
    (2, "所以"): (["今天", "下雨", "所以", "我", "在", "家"], "。", "Il pleut aujourd'hui, donc je reste à la maison."),
    (2, "但是"): (["今天", "冷", "但是", "我", "很", "高兴"], "。", "Il fait froid aujourd'hui, mais je suis très content."),
    (2, "介绍"): (["请", "给", "大家", "介绍", "你", "朋友"], "。", "Veuillez présenter votre ami à tout le monde."),
    (2, "公共汽车"): (["公共汽车", "快", "来", "了"], "。", "Le bus va bientôt arriver."),
    (2, "左边"): (["医院", "在", "学校", "左边"], "。", "L'hôpital est à gauche de l'école."),
    (2, "右边"): (["商店", "在", "医院", "右边"], "。", "Le magasin est à droite de l'hôpital."),
    (2, "旅游"): (["我", "想", "去", "北京", "旅游"], "。", "Je veux voyager à Pékin."),
    (2, "身体"): (["爸爸", "的", "身体", "很", "好"], "。", "La santé de papa est très bonne."),
    (2, "第一"): (["这", "是", "我", "第一", "次", "来", "中国"], "。", "C'est la première fois que je viens en Chine."),
    (2, "元"): (["这", "本", "书", "十", "元"], "。", "Ce livre coûte dix yuans."),
    (2, "红"): (["我", "喜欢", "这", "个", "红", "苹果"], "。", "J'aime cette pomme rouge."),
    (2, "手机"): (["我", "的", "新", "手机", "很", "贵"], "。", "Mon nouveau téléphone est très cher."),
    (2, "白"): (["她", "穿", "着", "白", "衣服"], "。", "Elle porte des vêtements blancs."),
    (2, "考试"): (["明天", "上午", "有", "汉语", "考试"], "。", "Demain matin il y a un examen de chinois."),
    (2, "千"): (["这", "个", "电脑", "三", "千", "块"], "。", "Cet ordinateur coûte trois mille yuans."),
    (2, "百"): (["学校", "里", "有", "两", "百", "个", "学生"], "。", "Il y a deux cents élèves dans l'école."),
    (2, "好吃"): (["中国", "菜", "真", "好吃"], "！", "La cuisine chinoise est vraiment délicieuse !"),
    (2, "鸡蛋"): (["我", "早上", "吃", "了", "两", "个", "鸡蛋"], "。", "J'ai mangé deux œufs ce matin."),
    (2, "阴"): (["今天", "天气", "很", "阴"], "。", "Le temps est très couvert aujourd'hui."),
    (2, "公斤"): (["我", "买", "了", "两", "公斤", "苹果"], "。", "J'ai acheté deux kilos de pommes."),
    (2, "教室"): (["老师", "和", "学生", "都", "在", "教室", "里"], "。", "Le professeur et les élèves sont tous dans la salle de classe."),
    (2, "西瓜"): (["这", "个", "大", "西瓜", "很", "好吃"], "。", "Cette grosse pastèque est très bonne."),
    (2, "羊肉"): (["爸爸", "喜欢", "吃", "羊肉"], "。", "Papa aime manger du mouton."),
    (2, "服务员"): (["服务员", "请", "给", "我", "一", "个", "杯子"], "。", "Serveur, s'il vous plaît, donnez-moi une tasse."),
    # --- HSK 3 ---
    (3, "为什么"): (["你", "为什么", "喜欢", "学习", "汉语"], "？", "Pourquoi aimes-tu apprendre le chinois ?"),
}

# Gabarits à jetons explicites et strictement bornés par niveau (min_level, tokens_tpl, punct, fr_tpl).
# Chaque token fixe est vérifié au démarrage pour appartenir à HSK 1..min_level.
VERB_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (1, ["我们", "现在", "{w}"], "。", "Nous sommes en train de : {fr}."),
    (1, ["他", "明天", "去", "{w}"], "。", "Il ira demain pour : {fr}."),
    (1, ["我", "很", "想", "{w}"], "。", "J'ai très envie de : {fr}."),
    (1, ["她", "喜欢", "{w}"], "。", "Elle aime : {fr}."),
    (1, ["请", "你", "多", "{w}"], "。", "Veuillez souvent : {fr}."),
    (2, ["朋友", "也", "会", "{w}"], "。", "Mon ami sait aussi : {fr}."),
    (2, ["大家", "一起", "{w}", "吧"], "！", "Allons {fr} tous ensemble !"),
    (2, ["她", "正在", "{w}", "呢"], "。", "Elle est en train de : {fr}."),
    (2, ["我们", "开始", "{w}", "了"], "。", "Nous avons commencé à : {fr}."),
    (2, ["我", "觉得", "可以", "{w}"], "。", "Je pense qu'on peut : {fr}."),
    (2, ["明天", "再", "{w}", "吧"], "。", "Remettons à demain pour : {fr}."),
    (3, ["我们", "应该", "认真", "{w}"], "。", "Nous devons sérieusement : {fr}."),
    (3, ["我", "相信", "她", "能", "{w}"], "。", "Je crois qu'elle peut : {fr}."),
    (3, ["大家", "需要", "{w}"], "。", "Tout le monde doit : {fr}."),
]

NOUN_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (1, ["这", "个", "{w}", "很", "好"], "。", "Ce terme « {fr} » est très bien."),
    (1, ["我", "看见", "那", "个", "{w}"], "。", "J'ai vu : {fr}."),
    (1, ["老师", "有", "一", "个", "{w}"], "。", "Le professeur a : {fr}."),
    (1, ["她", "很", "喜欢", "{w}"], "。", "Elle aime beaucoup : {fr}."),
    (1, ["我们", "今天", "看", "{w}"], "。", "Aujourd'hui nous voyons : {fr}."),
    (1, ["学校", "里", "有", "{w}"], "。", "Il y a : {fr} à l'école."),
    (2, ["大家", "都", "知道", "{w}"], "。", "Tout le monde connaît : {fr}."),
    (2, ["我", "很", "喜欢", "新", "{w}"], "。", "J'aime beaucoup ce nouveau / cette nouvelle : {fr}."),
    (2, ["这", "个", "{w}", "非常", "好"], "。", "Ce/cette {fr} est extrêmement bien."),
    (2, ["老师", "正在", "介绍", "{w}"], "。", "Le professeur présente : {fr}."),
    (3, ["这", "个", "{w}", "很", "重要"], "。", "Ce point « {fr} » est très important."),
    (3, ["大家", "需要", "明白", "{w}"], "。", "Tout le monde doit comprendre : {fr}."),
]

ADJ_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (1, ["今天", "天气", "很", "{w}"], "。", "Aujourd'hui c'est très : {fr}."),
    (1, ["这", "个", "很", "{w}"], "。", "Ceci est très : {fr}."),
    (1, ["他", "现在", "很", "{w}"], "。", "Il est très {fr} maintenant."),
    (1, ["她", "的", "朋友", "很", "{w}"], "。", "Son ami est très : {fr}."),
    (1, ["中国", "菜", "很", "{w}"], "。", "La cuisine chinoise est très : {fr}."),
    (2, ["这", "件", "事情", "非常", "{w}"], "。", "Cette affaire est particulièrement : {fr}."),
    (2, ["我", "觉得", "这", "很", "{w}"], "。", "Je trouve cela très : {fr}."),
    (2, ["今天", "早上", "真", "{w}"], "！", "Ce matin est vraiment : {fr} !"),
    (3, ["这", "个", "地方", "很", "{w}"], "。", "Cet endroit est très : {fr}."),
    (3, ["我们", "的", "环境", "真", "{w}"], "！", "Notre environnement est vraiment : {fr} !"),
]

ADV_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (1, ["我们", "{w}", "去", "学校"], "。", "Nous allons à l'école ({fr})."),
    (1, ["她", "{w}", "在", "家", "学习"], "。", "Elle étudie à la maison ({fr})."),
    (1, ["我", "{w}", "看", "这", "本", "书"], "。", "Je lis ce livre ({fr})."),
    (2, ["大家", "{w}", "看", "这", "本", "书"], "。", "Tout le monde lit ce livre ({fr})."),
    (2, ["他", "{w}", "给", "朋友", "打电话"], "。", "Il téléphone à son ami ({fr})."),
    (3, ["他", "一直", "{w}", "工作"], "。", "Il travaille toujours ({fr})."),
    (3, ["她", "{w}", "准备", "去", "北京"], "。", "Elle se prépare à aller à Pékin ({fr})."),
]

IDIOM_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (2, ["大家", "一起", "学习", "{w}"], "。", "Apprenons ensemble : {fr}."),
    (2, ["大家", "都", "知道", "{w}"], "。", "Tout le monde connaît : {fr}."),
    (3, ["老师", "教", "大家", "{w}"], "。", "Le professeur nous enseigne : {fr}."),
    (3, ["老师", "经常", "讲", "{w}"], "。", "Le professeur explique souvent : {fr}."),
    (3, ["我们", "应该", "明白", "{w}"], "。", "Nous devons comprendre : {fr}."),
    (3, ["大家", "需要", "记得", "{w}"], "。", "Tout le monde doit retenir : {fr}."),
]


SANDHI_OR_PARTICLE_WORDS = {
    "一", "不", "的", "了", "吗", "呢", "吧", "着", "过", "得", "地", "啊",
}


def norm_tonal_pinyin(py: str) -> str:
    return re.sub(r"[^a-zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü]", "", py.lower().replace("v", "ü"))


def is_example_pinyin_consistent(word_hanzi: str, word_py: str, ex_py: str) -> bool:
    n_w = norm_tonal_pinyin(word_py)
    n_ex = norm_tonal_pinyin(ex_py)
    return n_w in n_ex


def is_token_allowed(tok: str, level: int, allowed_vocab: set[str], word_to_level: dict[str, int]) -> bool:
    tok_level = word_to_level.get(tok)
    if tok_level is not None and tok_level > level:
        return False
    return tok in allowed_vocab or bool(DIGITS_RE.match(tok))


def strict_segment(
    body: str,
    level: int,
    allowed_vocab: set[str],
    word_to_level: dict[str, int],
    target: str | None = None,
) -> list[str] | None:
    """Segmente `body` exclusivement en tokens présents dans `allowed_vocab` (ou chiffres)."""
    n = len(body)
    if n == 0:
        return None
    inf = 10**9
    dp: list[tuple[int, int]] = [(inf, -1)] * (n + 1)
    dp[0] = (0, -1)
    max_len = 8

    for i in range(n):
        cur_cost = dp[i][0]
        if cur_cost >= inf:
            continue
        for length in range(1, min(max_len, n - i) + 1):
            sub = body[i : i + length]
            if is_token_allowed(sub, level, allowed_vocab, word_to_level):
                step_cost = 10 - (4 if sub == target else 0) - (1 if length >= 2 else 0)
                if cur_cost + step_cost < dp[i + length][0]:
                    dp[i + length] = (cur_cost + step_cost, i)

    if dp[n][0] >= inf:
        return None

    tokens: list[str] = []
    cur = n
    while cur > 0:
        prev = dp[cur][1]
        tokens.append(body[prev:cur])
        cur = prev
    tokens.reverse()
    return tokens


def has_higher_hsk_compound(body: str, level: int, word_to_level: dict[str, int]) -> bool:
    """Empêche de découper un mot composé d'un niveau HSK > `level` en caractères simples."""
    for tok in jieba.lcut(body):
        if word_to_level.get(tok, 0) > level:
            return True
    return False


def tokens_to_pinyin(tokens: list[str], punct: str, word_to_pinyin: dict[str, str]) -> str:
    """Génère un pinyin tonal Unicode propre et cohérent à partir des tokens."""
    rendered: list[str] = []
    for tok in tokens:
        known = word_to_pinyin.get(tok, "").strip()
        if known:
            rendered.append(known)
        else:
            syls = pypinyin_fn(tok, style=Style.TONE, heteronym=False)
            rendered.append("".join(item[0] for item in syls))

    sentence = " ".join(rendered).strip()
    if sentence:
        sentence = sentence[0].upper() + sentence[1:]
    if punct == "。":
        sentence += "."
    elif punct == "？":
        sentence += "?"
    elif punct == "！":
        sentence += "!"
    return sentence


def short_french_gloss(french: str) -> str:
    head = french.split("/")[0].strip()
    if not head:
        return french
    return head[0].lower() + head[1:]


def verify_static_templates(word_to_level: dict[str, int]) -> None:
    """Vérifie au démarrage que tous les gabarits et phrases écrites à la main respectent leur niveau."""
    for (lv, hanzi), (tokens, punct, french) in HANDCRAFTED_EXAMPLES.items():
        allowed = {w for w, l in word_to_level.items() if l <= lv} | ALLOWED_SIMPLE_PROPER_NOUNS
        assert len(tokens) >= 3, f"HANDCRAFTED ({lv}, {hanzi}) < 3 tokens: {tokens}"
        assert len(tokens) == len(set(tokens)), f"HANDCRAFTED ({lv}, {hanzi}) doublon: {tokens}"
        assert hanzi in tokens or "".join(tokens).find(hanzi) >= 0, (
            f"HANDCRAFTED ({lv}, {hanzi}) sans mot cible: {tokens}"
        )
        for t in tokens:
            assert t in allowed or DIGITS_RE.match(t), (
                f"HANDCRAFTED ({lv}, {hanzi}) token interdit '{t}' (niveau {word_to_level.get(t)})"
            )
        assert punct in {"。", "？", "！"}
        assert french.strip()

    all_pools = [
        ("VERB", VERB_TEMPLATES),
        ("NOUN", NOUN_TEMPLATES),
        ("ADJ", ADJ_TEMPLATES),
        ("ADV", ADV_TEMPLATES),
        ("IDIOM", IDIOM_TEMPLATES),
    ]
    for pool_name, pool in all_pools:
        for min_lv, tpl_tokens, punct, _fr_tpl in pool:
            allowed = {w for w, l in word_to_level.items() if l <= min_lv} | ALLOWED_SIMPLE_PROPER_NOUNS
            fixed = [t for t in tpl_tokens if t != "{w}"]
            assert len(fixed) == len(set(fixed)), f"{pool_name} template doublon: {tpl_tokens}"
            for t in fixed:
                assert t in allowed or DIGITS_RE.match(t), (
                    f"{pool_name} template (min_lv={min_lv}) token interdit '{t}' "
                    f"(niveau={word_to_level.get(t)}): {tpl_tokens}"
                )
            assert punct in {"。", "？", "！"}


def generate_level_safe_example(
    hanzi: str,
    french: str,
    level: int,
    pos_tags: list[str],
    allowed_vocab: set[str],
    word_to_level: dict[str, int],
    word_to_pinyin: dict[str, str],
) -> tuple[str, str, str, list[str]]:
    gloss = short_french_gloss(french)
    pos_joined = " ".join(pos_tags).lower()

    if len(hanzi) == 4 or "i" in pos_tags:
        raw_pool = IDIOM_TEMPLATES
    elif any(p.startswith("v") for p in pos_tags):
        raw_pool = VERB_TEMPLATES
    elif any(p.startswith("a") for p in pos_tags):
        raw_pool = ADJ_TEMPLATES
    elif any(p.startswith("d") for p in pos_tags) or "adv" in pos_joined:
        raw_pool = ADV_TEMPLATES
    else:
        raw_pool = NOUN_TEMPLATES

    # Filtrer les gabarits compatibles avec `level`, puis compléter avec VERB/NOUN génériques au besoin
    pool = [item for item in raw_pool if item[0] <= level]
    if not pool:
        pool = [item for item in (VERB_TEMPLATES + NOUN_TEMPLATES) if item[0] <= level]

    digest = int(hashlib.md5(hanzi.encode("utf-8")).hexdigest()[:8], 16)
    start = digest % len(pool)
    for offset in range(len(pool)):
        _min_lv, tpl_tokens, punct, fr_tpl = pool[(start + offset) % len(pool)]
        tokens = [hanzi if t == "{w}" else t for t in tpl_tokens]
        if (
            len(tokens) >= 3
            and len(tokens) == len(set(tokens))
            and all(is_token_allowed(t, level, allowed_vocab, word_to_level) for t in tokens)
        ):
            zh = "".join(tokens) + punct
            py = tokens_to_pinyin(tokens, punct, word_to_pinyin)
            fr = fr_tpl.format(fr=gloss)
            return zh, py, fr, tokens

    # Repli ultime 100 % HSK 1 garanti sans doublon
    fallbacks = [
        (["我", "今天", "学习", hanzi], "。", f"Aujourd'hui j'étudie : {gloss}."),
        (["请", "看", "这", "个", hanzi], "。", f"Veuillez observer : {gloss}."),
        (["他们" if "他们" in allowed_vocab else "我们", "学习", hanzi, "了"], "。", f"Nous avons étudié : {gloss}."),
        (["爸爸", "妈妈", "知道" if "知道" in allowed_vocab else "看见", hanzi], "。", f"Papa et maman voient : {gloss}."),
    ]
    for tokens, punct, fr in fallbacks:
        if (
            len(tokens) >= 3
            and len(tokens) == len(set(tokens))
            and all(is_token_allowed(t, level, allowed_vocab, word_to_level) for t in tokens)
        ):
            zh = "".join(tokens) + punct
            py = tokens_to_pinyin(tokens, punct, word_to_pinyin)
            return zh, py, fr, tokens

    raise RuntimeError(f"Impossible de générer un exemple conforme pour ({level}, {hanzi})")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Met en conformité les phrases d'exemple HSK 1 à 5.")
    parser.add_argument(
        "--no-bump-version",
        action="store_true",
        help="Ne pas incrémenter catalogVersion dans les fichiers modifiés.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    catalogs: dict[int, dict[str, Any]] = {}
    word_to_level: dict[str, int] = {}
    word_to_pinyin: dict[str, str] = {}

    for lv in range(1, 7):
        data = json.loads((CATALOG_DIR / f"hsk{lv}.json").read_text(encoding="utf-8"))
        catalogs[lv] = data
        for w in data["words"]:
            hz = w["hanzi"]
            word_to_level[hz] = lv
            word_to_pinyin[hz] = w["pinyin"]
            jieba.add_word(hz, freq=100_000)

    # Ajouter quelques noms propres simples dans word_to_pinyin / jieba
    proper_pinyin = {
        "中国": "Zhōngguó",
        "北京": "Běijīng",
        "上海": "Shànghǎi",
        "小明": "Xiǎomíng",
        "小王": "Xiǎowáng",
        "小李": "Xiǎolǐ",
        "法国": "Fǎguó",
        "巴黎": "Bālí",
    }
    for k, v in proper_pinyin.items():
        word_to_pinyin.setdefault(k, v)
        jieba.add_word(k, freq=100_000)

    verify_static_templates(word_to_level)

    # Charger POS tags si complete.min.json est en cache
    pos_map: dict[str, list[str]] = {}
    comp_path = CACHE_DIR / "complete.min.json"
    if comp_path.exists():
        for entry in json.loads(comp_path.read_text(encoding="utf-8")):
            pos_map[entry["s"]] = entry.get("p", [])

    # Charger Tatoeba
    tatoeba_pairs: list[tuple[str, str]] = []
    tat_path = CACHE_DIR / "tatoeba_cmn_fra.tsv"
    if tat_path.exists():
        with tat_path.open("r", encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) == 2:
                    tatoeba_pairs.append((parts[0], parts[1]))

    for level in range(1, 6):
        data = catalogs[level]
        allowed_vocab = {hz for hz, lv in word_to_level.items() if lv <= level} | ALLOWED_SIMPLE_PROPER_NOUNS
        level_words_set = {w["hanzi"] for w in data["words"]}

        # Pré-indexer les phrases Tatoeba 100 % conformes à HSK 1..level
        tat_candidates: dict[str, list[tuple[int, str, str, list[str]]]] = defaultdict(list)
        for cmn_text, fra_text in tatoeba_pairs:
            body = cmn_text.rstrip("。？！")
            punct = cmn_text[len(body) :] or "。"
            if has_higher_hsk_compound(body, level, word_to_level):
                continue
            tokens = strict_segment(body, level, allowed_vocab, word_to_level)
            if not tokens or not (3 <= len(tokens) <= 9) or len(tokens) != len(set(tokens)):
                continue
            if "".join(tokens) != body:
                continue
            # S'assurer qu'aucun caractère d'un mot multi-caractères n'est artificiellement éclaté si jieba le reconnaît autrement
            for tok in tokens:
                if tok in level_words_set and len(tat_candidates[tok]) < 25:
                    score = abs(len(tokens) - 5) * 2 + abs(len(body) - 9)
                    tat_candidates[tok].append((score, cmn_text, fra_text, tokens))

        stats = {"kept": 0, "handcrafted": 0, "resegmented": 0, "tatoeba": 0, "template": 0}
        modified = False

        for w in data["words"]:
            hz = w["hanzi"]
            ex = w["example"]
            old_tokens = ex["tokens"]
            old_hanzi = ex["hanzi"]
            old_body = old_hanzi.rstrip(TRAILING_PUNCT)
            old_punct = old_hanzi[len(old_body) :] or "。"

            already_valid = (
                hz in old_tokens
                and all(is_token_allowed(t, level, allowed_vocab, word_to_level) for t in old_tokens)
                and is_example_pinyin_consistent(hz, w["pinyin"], ex["pinyin"])
            )
            if already_valid:
                stats["kept"] += 1
                continue

            modified = True

            # 1. Phrase rédigée sur mesure si disponible
            if (level, hz) in HANDCRAFTED_EXAMPLES:
                hc_tokens, hc_punct, hc_fr = HANDCRAFTED_EXAMPLES[(level, hz)]
                w["example"] = {
                    "hanzi": "".join(hc_tokens) + hc_punct,
                    "pinyin": tokens_to_pinyin(hc_tokens, hc_punct, word_to_pinyin),
                    "french": hc_fr,
                    "tokens": hc_tokens,
                }
                stats["handcrafted"] += 1
                continue

            # 2. Re-segmentation stricte si la phrase existante n'utilise aucun mot > level
            has_higher_existing = any(word_to_level.get(t, 0) > level for t in old_tokens)
            if (
                not has_higher_existing
                and not has_higher_hsk_compound(old_body, level, word_to_level)
                and "，" not in old_body
            ):
                reseg = strict_segment(old_body, level, allowed_vocab, word_to_level, target=hz)
                if (
                    reseg
                    and 3 <= len(reseg) <= 10
                    and len(reseg) == len(set(reseg))
                    and "".join(reseg) == old_body
                    and all(
                        is_token_allowed(t, level, allowed_vocab, word_to_level) for t in reseg
                    )
                ):
                    ex["tokens"] = reseg
                    ex["pinyin"] = tokens_to_pinyin(reseg, old_punct, word_to_pinyin)
                    stats["resegmented"] += 1
                    continue

            # 3. Phrase Tatoeba 100 % conforme au plafond HSK 1..level
            cands = tat_candidates.get(hz, [])
            if cands:
                cands.sort(key=lambda item: item[0])
                _score, cmn_text, fra_text, tat_tokens = cands[0]
                body = cmn_text.rstrip("。？！")
                punct = cmn_text[len(body) :] or "。"
                w["example"] = {
                    "hanzi": cmn_text,
                    "pinyin": tokens_to_pinyin(tat_tokens, punct, word_to_pinyin),
                    "french": fra_text,
                    "tokens": tat_tokens,
                }
                stats["tatoeba"] += 1
                continue

            # 4. Gabarit naturel strictement borné à HSK 1..level
            new_zh, new_py, new_fr, new_tokens = generate_level_safe_example(
                hanzi=hz,
                french=w["french"],
                level=level,
                pos_tags=pos_map.get(hz, []),
                allowed_vocab=allowed_vocab,
                word_to_level=word_to_level,
                word_to_pinyin=word_to_pinyin,
            )
            w["example"] = {
                "hanzi": new_zh,
                "pinyin": new_py,
                "french": new_fr,
                "tokens": new_tokens,
            }
            stats["template"] += 1

        if modified:
            if not args.no_bump_version:
                data["catalogVersion"] = int(data["catalogVersion"]) + 1
            out_path = CATALOG_DIR / f"hsk{level}.json"
            out_path.write_text(
                json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )

        print(
            f"HSK {level} (v{data['catalogVersion']}) — "
            f"conservées: {stats['kept']}, "
            f"resegmentées: {stats['resegmented']}, "
            f"sur mesure: {stats['handcrafted']}, "
            f"Tatoeba strict: {stats['tatoeba']}, "
            f"gabarits sûrs: {stats['template']}"
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
