#!/usr/bin/env python3
"""
Génère les catalogues HSK 2 à HSK 6 complets (programme HSK 2.0) dans
app/src/main/assets/catalog/hsk{2..6}.json à partir de :
  - complete-hsk-vocabulary (listes officielles HSK 2.0 + fréquence + POS + pinyin)
  - CFDICT (dictionnaire chinois-français open-source)
  - Tatoeba (paires de phrases chinois-français open-source)
  - jieba + pypinyin (segmentation en tokens et pinyin tonal Unicode)

Exécuté uniquement sur PC ; l'application Android reste 100 % hors-ligne.
"""

from __future__ import annotations

import bz2
import hashlib
import json
import re
import tarfile
from collections import defaultdict
from pathlib import Path
from typing import Any

import jieba
import requests
from pypinyin import Style, pinyin as pypinyin_fn

REPO_ROOT = Path(__file__).resolve().parent.parent
CATALOG_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "catalog"
CACHE_DIR = Path(__file__).resolve().parent / ".cache"

HSK_URL = "https://raw.githubusercontent.com/drkameleon/complete-hsk-vocabulary/main/complete.min.json"
CFDICT_URL = "https://chine.in/assets/cfdict/cfdict.u8"
TATOEBA_CMN_URL = "https://downloads.tatoeba.org/exports/per_language/cmn/cmn_sentences.tsv.bz2"
TATOEBA_FRA_URL = "https://downloads.tatoeba.org/exports/per_language/fra/fra_sentences.tsv.bz2"
TATOEBA_LINKS_URL = "https://downloads.tatoeba.org/exports/links.tar.bz2"

TONE_MARKS_RE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
CEDICT_LINE_RE = re.compile(r"^(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+/(.+)/\s*$")
BAD_SENTENCE_CHARS_RE = re.compile(
    r"[A-Za-z0-9，、；：（）()\[\]「」『』《》“”\"'…—·,.;:/\\@#&*+=<>|~`]"
)
CJK_ONLY_RE = re.compile(r"^[\u4e00-\u9fff]+$")
TRAILING_PUNCT = "。？！"
NEUTRAL_TONE_PARTICLES = {"ma", "ne", "de", "le", "ba", "zhe", "guo", "a", "ya", "wa", "na"}

LESSON_SIZES = {1: 5, 2: 5, 3: 5, 4: 8, 5: 10, 6: 10}

# Progression pédagogique curatée des 150 mots officiels HSK 1 (6 unités × 5 leçons × 5 mots)
HSK1_CURATED_UNITS: list[tuple[str, str, list[tuple[str, list[str]]]]] = [
    (
        "Unité 1 — Salutations, Pronoms & Bases",
        "Premiers échanges pour se présenter et poser des questions simples.",
        [
            ("Leçon 1 — Salutations et politesse", ["你", "好", "谢谢", "再见", "喂"]),
            ("Leçon 2 — Excuses et courtoisie", ["请", "对不起", "没关系", "不客气", "高兴"]),
            ("Leçon 3 — Pronoms et être", ["我", "他", "她", "我们", "是"]),
            ("Leçon 4 — Négation et questions", ["不", "没", "吗", "呢", "谁"]),
            ("Leçon 5 — Identité et démonstratifs", ["叫", "什么", "名字", "这", "那"]),
        ],
    ),
    (
        "Unité 2 — Famille, Personnes & Goûts",
        "Parler de ses proches, des personnes et de l'alimentation.",
        [
            ("Leçon 6 — La famille", ["家", "爸爸", "妈妈", "儿子", "女儿"]),
            ("Leçon 7 — Personnes et présentations", ["人", "朋友", "先生", "小姐", "同学"]),
            ("Leçon 8 — Goûts et boissons", ["爱", "喜欢", "吃", "喝", "水"]),
            ("Leçon 9 — Repas et fruits", ["茶", "米饭", "菜", "水果", "苹果"]),
            ("Leçon 10 — Animaux et possessif", ["猫", "狗", "的", "很", "太"]),
        ],
    ),
    (
        "Unité 3 — Chiffres, Dates & Heures",
        "Compter, donner l'heure, la date et son âge.",
        [
            ("Leçon 11 — Chiffres 0 à 4", ["零", "一", "二", "三", "四"]),
            ("Leçon 12 — Chiffres 5 à 10", ["五", "六", "七", "八", "九"]),
            ("Leçon 13 — Dizaines et âge", ["十", "几", "多少", "岁", "个"]),
            ("Leçon 14 — Dates et calendrier", ["年", "月", "日", "星期", "今天"]),
            ("Leçon 15 — Heures et moments", ["明天", "昨天", "现在", "点", "分钟"]),
        ],
    ),
    (
        "Unité 4 — Lieux, Repères & Maison",
        "Se repérer dans l'espace et décrire son quotidien.",
        [
            ("Leçon 16 — Moments du jour", ["上午", "中午", "下午", "时候", "了"]),
            ("Leçon 17 — Pays et lieux publics", ["中国", "北京", "饭馆", "商店", "医院"]),
            ("Leçon 18 — Transports et déplacements", ["学校", "火车站", "飞机", "出租车", "电影"]),
            ("Leçon 19 — Repères spatiaux", ["哪", "里", "前面", "后面", "上"]),
            ("Leçon 20 — Meubles et objets", ["下", "桌子", "椅子", "杯子", "东西"]),
        ],
    ),
    (
        "Unité 5 — Actions, Achats & Météo",
        "Exprimer ses actions quotidiennes, faire des achats et parler du temps.",
        [
            ("Leçon 21 — Déplacements et métiers", ["去", "来", "回", "住", "坐"]),
            ("Leçon 22 — Travail et études", ["工作", "学习", "老师", "学生", "医生"]),
            ("Leçon 23 — Perception et lecture", ["看", "看见", "听", "说话", "读"]),
            ("Leçon 24 — Écrire, dormir et appeler", ["写", "字", "书", "睡觉", "打电话"]),
            ("Leçon 25 — Achats, argent et météo", ["买", "钱", "块", "天气", "下雨"]),
        ],
    ),
    (
        "Unité 6 — Qualités, Modaux & Connecteurs",
        "Nuancer son propos, utiliser les verbes modaux et relier ses idées.",
        [
            ("Leçon 26 — Objets et classificateurs", ["衣服", "电脑", "电视", "本", "些"]),
            ("Leçon 27 — Adjectifs courants", ["大", "小", "多", "少", "漂亮"]),
            ("Leçon 28 — Sensations et verbes d'action", ["冷", "热", "做", "开", "有"]),
            ("Leçon 29 — Capacité et connaissance", ["在", "会", "能", "想", "认识"]),
            ("Leçon 30 — Langue et connecteurs", ["汉语", "和", "都", "怎么", "怎么样"]),
        ],
    ),
]

THEMATIC_UNITS = {
    2: [
        ("Premiers échanges", "Connecteurs simples, questions et bases du quotidien."),
        ("Temps & Repères", "Horaires, jours, durées et orientation."),
        ("Actions du quotidien", "Verbes courants, déplacements et loisirs."),
        ("Personnes, Objets & Vie pratique", "Famille, achats, vêtements et nourriture."),
        ("Qualités & Nuances", "Adjectifs essentiels, états et sentiments."),
        ("Structures & Expressions", "Particules grammaticales et comparaisons."),
    ],
    3: [
        ("Raisonnement & Échanges", "Opinions, questions complexes et communication."),
        ("Actions & Démarches", "Décisions, habitudes et vie pratique."),
        ("Lieux & Environnement", "Ville, nature, saisons et voyages."),
        ("Relations & Sentiments", "Comportements, émotions et rapports humains."),
        ("Descriptions & Précisions", "Adjectifs variés, degrés et qualités."),
        ("Connecteurs & Grammaire", "Articulations logiques et tournures clés."),
    ],
    4: [
        ("Études & Carrière", "Compétences, projets et vie professionnelle."),
        ("Société & Relations", "Échanges sociaux, règles et entraide."),
        ("Culture, Médias & Savoirs", "Éducation, informations et lecture."),
        ("Voyages, Nature & Quotidien", "Déplacements, santé et environnement."),
        ("Attitudes & Qualités", "Caractère, émotions et jugements."),
        ("Argumentation & Connecteurs", "Nuances logiques et expression structurée."),
    ],
    5: [
        ("Analyse & Pensée", "Raisonnement, faits et argumentation structurée."),
        ("Travail, Économie & Gestion", "Organisation, ressources et monde professionnel."),
        ("Société & Vie collective", "Institutions, règles et phénomènes sociaux."),
        ("Sciences, Médias & Techniques", "Recherche, technologies et information."),
        ("Culture, Arts & Patrimoine", "Traditions, littérature et création."),
        ("Nature, Santé & Cadre de vie", "Environnement, énergie et bien-être."),
        ("Psychologie & Comportements", "Traits de caractère, émotions et attitudes."),
        ("Style soutenu & Idiomes", "Tournures littéraires et chengyu courants."),
    ],
    6: [
        ("Discours & Méthodologie", "Verbes analytiques et vocabulaire académique."),
        ("Droit, Gouvernance & Institutions", "Politique, législation et administration."),
        ("Économie, Stratégie & Développement", "Marchés, production et innovation."),
        ("Société, Éthique & Relations", "Normes sociales, devoirs et valeurs morales."),
        ("Culture, Histoire & Philosophie", "Patrimoine, pensée et arts classiques."),
        ("Sciences, Nature & Technologie", "Phénomènes physiques, biologie et technique."),
        ("Psychologie & Introspection", "Nuances affectives, caractère et jugement."),
        ("Expressions soutenues & Chengyu", "Adjectifs littéraires et locutions quadrisyllabiques."),
    ],
}

# Repli français pour d'éventuelles entrées absentes de CFDICT (ex. variantes/expressions composées)
FRENCH_FALLBACK: dict[str, str] = {
    "一下": "Un instant / Un peu",
    "一点儿": "Un peu",
    "一块儿": "Ensemble",
    "一起": "Ensemble",
    "一直": "Toujours / Tout droit",
    "一般": "Ordinaire / En général",
    "一边": "D'un côté / En même temps",
    "一会儿": "Un moment",
    "一样": "Pareil / Identique",
    "非常": "Très / Extrêmement",
    "大家": "Tout le monde",
    "别的": "Autre",
    "男": "Masculin / Homme",
    "女": "Féminin / Femme",
    "号": "Numéro / Jour du mois",
    "吧": "Particule de suggestion",
    "着": "Particule d'aspect continu",
    "过": "Particule d'expérience passée",
    "得": "Particule de degré / Obtenir",
    "地": "Particule adverbiale",
    "把": "Particule de disposition (objet)",
    "被": "Marque du passif",
    "比": "Comparer / Que",
    "更": "Encore plus",
    "最": "Le plus",
    "还": "Encore / Toujours",
    "就": "Alors / Tout de suite",
    "才": "Seulement alors / À peine",
    "再": "Encore / De nouveau",
    "又": "De nouveau (passé)",
    "别": "Ne pas (impératif) / Autre",
    "真": "Vraiment / Vrai",
    "正在": "Être en train de",
    "干什么": "Que faire / Pourquoi",
    "为什么": "Pourquoi",
    "怎么": "Comment",
    "怎么样": "Comment est / Qu'en penses-tu",
    "哪儿": "Où",
    "这儿": "Ici",
    "那儿": "Là-bas",
    "一会儿见": "À tout à l'heure",
    "没问题": "Pas de problème",
    "差不多": "À peu près",
    "越来越": "De plus en plus",
    "不得不": "Être obligé de",
    "说不定": "Peut-être / Qui sait",
    "怪不得": "Pas étonnant que",
    "来不及": "Ne pas avoir le temps de",
    "来得及": "Avoir le temps de",
    "舍不得": "Avoir du mal à quitter",
    "恨不得": "Mourir d'envie de",
    "巴不得": "Souhaiter ardemment",
    "好容易": "Avec beaucoup de peine",
    "好不容易": "Non sans mal",
    "看样子": "À en juger par l'apparence",
    "要不然": "Sinon / Autrement",
    "总而言之": "En un mot / En somme",
    "换句话说": "Autrement dit",
    "众所周知": "Comme chacun sait",
    "实事求是": "Chercher la vérité dans les faits",
    "无微不至": "Avec un soin minutieux",
    "画蛇添足": "Ajouter du superflu",
    "半途而废": "Abandonner à mi-chemin",
    "一举两得": "Faire d'une pierre deux coups",
    "雪中送炭": "Apporter une aide au moment critique",
    "锦上添花": "Ajouter la cerise sur le gâteau",
    "自相矛盾": "Se contredire soi-même",
    "不可思议": "Inconcevable / Incroyable",
    "理所当然": "Qui va de soi",
    "莫名其妙": "Inexplicable / Déconcertant",
    "兴高采烈": "Transporté de joie",
    "千方百计": "Par tous les moyens",
    "想方设法": "Chercher par tous les moyens",
    "津津有味": "Avec grand appétit / Passionnément",
    "微不足道": "Insignifiant / Négligeable",
    "急于求成": "Impatient de réussir",
    "循序渐进": "Progresser étape par étape",
    "潜移默化": "Influencer imperceptiblement",
    "精打细算": "Calculer au plus juste",
    "名副其实": "Digne de son nom",
    "当务之急": "Tâche la plus urgente",
    "举足轻重": "D'un poids décisif",
    "见义勇为": "Agir avec courage pour la justice",
    "实话实说": "Dire franchement la vérité",
    "东张西望": "Regarder de tous côtés",
    "七嘴八舌": "Parler tous à la fois",
    "三心二意": "Hésitant / Indécis",
    "十全十美": "Parfait à tous égards",
    "百花齐放": "Laisser s'épanouir cent fleurs",
    "川流不息": "Couler sans interruption",
    "朝气蓬勃": "Débordant de vitalité",
    "专心致志": "Se concentrer pleinement",
    "再接再厉": "Redoubler d'efforts",
    "坚持不懈": "Persévérer sans relâche",
    "异口同声": "D'une seule voix",
    "不约而同": "Spontanément d'accord",
    "心甘情愿": "De plein gré",
    "迫不及待": "Impatient / Sans pouvoir attendre",
    "忍无可忍": "À bout de patience",
    "无可奈何": "Impuissant / Sans recours",
    "无精打采": "Abattu / Sans énergie",
    "精神抖擞": "Plein d'entrain",
    "兴致勃勃": "Plein d'enthousiasme",
    "热泪盈眶": "Les larmes aux yeux",
    "喜出望外": "Fou de joie par surprise",
    "大吃一惊": "Être stupéfait",
    "目瞪口呆": "Bouche bée",
    "鸦雀无声": "Silence absolu",
    "人山人海": "Une foule immense",
    "水落石出": "La vérité finit par éclater",
    "胸有成竹": "Avoir un plan bien arrêté",
    "一帆风顺": "Bon vent / Sans encombre",
    "事半功倍": "Doubler l'efficacité avec moitié moins d'effort",
    "得不偿失": "Le jeu n'en vaut pas la chandelle",
    "因小失大": "Perdre gros pour un petit gain",
    "顾全大局": "Tenir compte de l'intérêt général",
    "以身作则": "Donner l'exemple",
    "克勤克俭": "Travailleur et économe",
    "勤俭节约": "Économe et frugal",
    "实事求是": "Réaliste et pragmatique",
    "脚踏实地": "Les pieds sur terre",
    "高瞻远瞩": "Voir loin / Prévoyant",
    "深思熟虑": "Mûrement réfléchi",
    "果断": "Décidé / Résolu",
    "不言而喻": "Cela va sans dire",
    "不屑一顾": "Ne pas daigner regarder",
    "不在乎": "Peu importe / Ne pas se soucier",
    "免不了": "Inévitablement",
    "怪不得": "Pas étonnant",
    "来不及": "Trop tard pour",
    "顾不上": "Ne pas avoir le temps de s'occuper de",
    "巴不得": "Ne demander qu'à",
    "恨不得": "Avoir très envie de",
    "忍不住": "Ne pas pouvoir s'empêcher de",
    "靠不住": "Peu fiable",
    "靠得住": "Fiable",
    "对得起": "Être digne de",
    "对不起": "Désolé / Pardon",
    "看不惯": "Ne pas supporter de voir",
    "看不起": "Mépriser",
    "看得起": "Estimer / Respecter",
    "听不懂": "Ne pas comprendre à l'écoute",
    "说不定": "Peut-être",
    "想不到": "Ne pas s'attendre à",
    "想起来": "Se souvenir de",
    "找不着": "Ne pas trouver",
    "受不了": "Insupportable / Ne plus supporter",
    "吃不消": "Ne pas tenir le coup",
    "吃得消": "Pouvoir supporter",
    "划得来": "Valoir la peine",
    "划不来": "Ne pas valoir la peine",
    "了不起": "Extraordinaire / Formidable",
    "不得了": "Extrêmement / Terrible",
    "谈不上": "On ne peut pas parler de",
    "说不过去": "Injustifiable",
    "过不去": "Ne pas pouvoir passer / En vouloir à",
    "打交道": "Avoir affaire à",
    "开玩笑": "Plaisanter",
    "闹笑话": "Faire rire de soi / Gaffe",
    "占便宜": "Tirer profit",
    "吃亏": "Subir une perte",
    "帮忙": "Donner un coup de main",
    "操心": "Se faire du souci",
    "放心": "Être rassuré",
    "小心": "Faire attention",
    "用心": "Avec application",
    "灰心": "Se décourager",
    "专心": "Concentré",
    "粗心": "Négligent / Étourdi",
    "细心": "Attentionné / Minutieux",
    "热心": "Chaleureux / Serviable",
    "虚心": "Modeste / Ouvert aux conseils",
    "野心": "Ambition",
    "决心": "Détermination",
    "耐心": "Patience",
    "信心": "Confiance en soi",
    "开心": "Joyeux / Content",
    "伤心": "Avoir le cœur brisé",
    "关心": "Se soucier de",
    "担心": "S'inquiéter",
    "恶心": "Écœurant / Nausée",
    "点心": "Pâtisserie / En-cas",
    "核心": "Noyau / Cœur",
    "重心": "Centre de gravité",
    "中心": "Centre",
    "姓": "Nom de famille / S'appeler",
    "辆": "Classificateur des véhicules",
    "棵": "Classificateur des arbres et plantes",
    "放暑假": "Être en vacances d'été",
    "池子": "Étang / Bassin",
    "解说员": "Commentateur",
    "嘛": "Particule d'évidence / Donc",
    "艘": "Classificateur des navires",
    "枚": "Classificateur (pièces, médailles)",
    "没辙": "À court d'idées / Impuissant",
    "公婆": "Beaux-parents (côté mari)",
    "滋长": "Se développer / Croître",
    "斑纹": "Rayure / Tache",
    "做东": "Inviter / Payer la tournée",
    "通货": "Monnaie / Devise en circulation",
    "砖瓦": "Briques et tuiles",
    "乘务员": "Hôtesse / Agent de bord",
    "螺丝钉": "Vis",
    "出洋相": "Se couvrir de ridicule",
    "糖葫芦": "Brochette d'aubépines caramélisées",
    "重阳节": "Fête du Double Neuf",
    "无可奉告": "Sans commentaire",
    "举世闻名": "Célèbre dans le monde entier",
    "继往开来": "Poursuivre l'œuvre passée vers l'avenir",
    "锦绣前程": "Avenir radieux",
    "东西": "Chose / Objet",
    "饭馆": "Restaurant",
    "里": "Dedans / À l'intérieur",
    "零": "Zéro",
    "没": "Ne pas (avoir / passé)",
    "喂": "Allô / Hé",
    "些": "Quelques / Un peu",
    "字": "Caractère / Mot écrit",
}


def download_cached(url: str, filename: str) -> Path:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    target = CACHE_DIR / filename
    if target.exists() and target.stat().st_size > 0:
        return target
    print(f"Téléchargement : {url} -> {target.name} ...")
    with requests.get(url, stream=True, timeout=60) as resp:
        resp.raise_for_status()
        with target.open("wb") as f:
            for chunk in resp.iter_content(chunk_size=1024 * 256):
                if chunk:
                    f.write(chunk)
    return target


def clean_cfdict_sense(raw: str) -> str | None:
    s = raw.strip()
    if not s:
        return None
    # Supprimer les mentions de classificateurs ou renvois techniques
    if s.startswith(("CL:", "voir ", "Voir ", "variante ", "Variante ", "ancien ", "fig.")):
        if s.startswith("CL:"):
            return None
    s = re.sub(r"\bCL:[^;/)]+", "", s)
    s = re.sub(r"\[[^\]]+\]", "", s)
    s = re.sub(r"\([^)]*\)", lambda m: "" if len(m.group(0)) > 28 else m.group(0), s)
    s = re.sub(r"\s{2,}", " ", s).strip(" ;,.-")
    if not s or len(s) < 2:
        return None
    # Éviter les entrées purement de noms propres ou de phonétique
    low = s.lower()
    if low.startswith(("nom de famille", "prénom", "patronyme")):
        return None
    return s


def load_cfdict(path: Path) -> dict[str, str]:
    by_simp: dict[str, list[str]] = defaultdict(list)
    by_trad: dict[str, list[str]] = defaultdict(list)

    with path.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if not line or line.startswith("#"):
                continue
            m = CEDICT_LINE_RE.match(line)
            if not m:
                continue
            trad, simp, _pinyin, gloss_block = m.groups()
            senses = [clean_cfdict_sense(g) for g in gloss_block.split("/")]
            senses = [s for s in senses if s]
            if not senses:
                continue
            # Garder 1 à 2 sens concis
            concise = " / ".join(senses[:2])
            if len(concise) > 72:
                concise = senses[0][:72].rstrip(" ,;-")
            concise = concise[0].upper() + concise[1:]
            by_simp[simp].append(concise)
            by_trad[trad].append(concise)

    result: dict[str, str] = {}
    for k, vals in by_simp.items():
        # Préférer une définition ni trop courte ni trop longue
        vals_sorted = sorted(vals, key=lambda v: (len(v) < 3, len(v)))
        result[k] = vals_sorted[0]
    for k, vals in by_trad.items():
        if k not in result:
            vals_sorted = sorted(vals, key=lambda v: (len(v) < 3, len(v)))
            result[k] = vals_sorted[0]
    return result


def load_tatoeba_pairs(
    cmn_bz2: Path,
    fra_bz2: Path,
    links_tar_bz2: Path,
) -> list[tuple[str, str]]:
    cached_tsv = CACHE_DIR / "tatoeba_cmn_fra.tsv"
    if cached_tsv.exists() and cached_tsv.stat().st_size > 0:
        pairs: list[tuple[str, str]] = []
        with cached_tsv.open("r", encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) == 2:
                    pairs.append((parts[0], parts[1]))
        return pairs

    print("Extraction des phrases Tatoeba chinois-français...")
    cmn_map: dict[str, str] = {}
    with bz2.open(cmn_bz2, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            sid, lang, text = parts[0], parts[1], parts[2].strip()
            if lang != "cmn":
                continue
            if not (4 <= len(text) <= 28):
                continue
            if text[-1] not in TRAILING_PUNCT:
                continue
            body = text[:-1]
            if BAD_SENTENCE_CHARS_RE.search(body):
                continue
            if not CJK_ONLY_RE.match(body):
                continue
            cmn_map[sid] = text

    fra_map: dict[str, str] = {}
    with bz2.open(fra_bz2, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            sid, lang, text = parts[0], parts[1], parts[2].strip()
            if lang == "fra" and 4 <= len(text) <= 120:
                fra_map[sid] = text

    pairs_map: dict[str, tuple[str, str]] = {}
    with tarfile.open(links_tar_bz2, "r:bz2") as tar:
        member = next(m for m in tar.getmembers() if m.isfile())
        extracted = tar.extractfile(member)
        assert extracted is not None
        for raw_line in extracted:
            line = raw_line.decode("utf-8", errors="ignore").rstrip("\n")
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            src_id, tgt_id = parts[0], parts[1]
            if src_id in cmn_map and tgt_id in fra_map:
                if src_id not in pairs_map or len(fra_map[tgt_id]) < len(pairs_map[src_id][1]):
                    pairs_map[src_id] = (cmn_map[src_id], fra_map[tgt_id])

    pairs = list(pairs_map.values())
    with cached_tsv.open("w", encoding="utf-8") as out:
        for cmn_text, fra_text in pairs:
            out.write(f"{cmn_text}\t{fra_text}\n")
    print(f"Tatoeba : {len(pairs)} paires chinois-français propres extraites.")
    return pairs


def to_tonal_pinyin_sentence(chinese_text: str) -> str:
    """Convertit une phrase chinoise en pinyin tonal Unicode lisible."""
    body = chinese_text.rstrip(TRAILING_PUNCT)
    punct = chinese_text[len(body) :]
    tokens = jieba.lcut(body)
    rendered_tokens: list[str] = []
    for tok in tokens:
        syls = pypinyin_fn(tok, style=Style.TONE, heteronym=False)
        rendered_tokens.append("".join(item[0] for item in syls))
    sentence = " ".join(rendered_tokens).strip()
    if sentence:
        sentence = sentence[0].upper() + sentence[1:]
    if punct == "。":
        sentence += "."
    elif punct == "？":
        sentence += "?"
    elif punct == "！":
        sentence += "!"
    return sentence


def ensure_tonal_word_pinyin(hanzi: str, candidate_pinyin: str) -> str:
    p = candidate_pinyin.strip()
    if p and (TONE_MARKS_RE.search(p) or p.lower() in NEUTRAL_TONE_PARTICLES):
        return p
    syls = pypinyin_fn(hanzi, style=Style.TONE, heteronym=False)
    generated = "".join(item[0] for item in syls).strip()
    return generated or p


# Gabarits variés pour les mots sans phrase Tatoeba adaptée (jamais un modèle unique répétitif)
VERB_TEMPLATES = [
    ("我们正在{w}。", "Nous sommes en train de {fr}."),
    ("请认真{w}。", "Veuillez {fr} sérieusement."),
    ("他每天都{w}。", "Il faut {fr} chaque jour."),
    ("我想学会{w}。", "J'aimerais apprendre à {fr}."),
    ("大家一起{w}吧！", "Allons {fr} tous ensemble !"),
    ("明天再{w}好吗？", "Et si on remettait à demain pour {fr} ?"),
    ("她经常这样{w}。", "Elle a souvent l'habitude de {fr} ainsi."),
]

NOUN_TEMPLATES = [
    ("这个{w}很重要。", "Ce terme « {fr} » est très important."),
    ("我们讨论过{w}。", "Nous avons déjà abordé : {fr}."),
    ("大家都很关注{w}。", "Tout le monde s'intéresse à : {fr}."),
    ("请记住这个{w}。", "Veuillez retenir ce mot : {fr}."),
    ("书上提到了{w}。", "Le livre mentionne : {fr}."),
    ("这里需要新的{w}。", "Il faut un nouvel élément ici : {fr}."),
]

ADJ_TEMPLATES = [
    ("今天的情况很{w}。", "La situation d'aujourd'hui est très : {fr}."),
    ("这个地方非常{w}。", "Cet endroit est particulièrement : {fr}."),
    ("他觉得这样更{w}。", "Il trouve cela encore plus : {fr}."),
    ("大家的态度很{w}。", "L'attitude de chacun est très : {fr}."),
    ("这里的环境真{w}！", "Le cadre ici est vraiment : {fr} !"),
]

ADV_TEMPLATES = [
    ("大家{w}完成任务。", "Chacun accomplit la tâche ({fr})."),
    ("她{w}说出了想法。", "Elle a exprimé son idée ({fr})."),
    ("我们{w}继续前进。", "Continuons d'avancer ({fr})."),
    ("请{w}准备一下。", "Veuillez vous préparer ({fr})."),
]

IDIOM_TEMPLATES = [
    ("大家做事要{w}。", "Dans nos actions, il faut appliquer : {fr}."),
    ("老师常常强调{w}。", "Le professeur souligne souvent : {fr}."),
    ("我们应该坚持{w}。", "Nous devons nous efforcer de : {fr}."),
]


def short_french_gloss(french: str) -> str:
    head = french.split("/")[0].strip()
    if not head:
        return french
    return head[0].lower() + head[1:]


def generate_natural_example(
    hanzi: str,
    french: str,
    pos_tags: list[str],
    seed_idx: int,
) -> tuple[str, str, list[str]]:
    gloss = short_french_gloss(french)
    pos_joined = " ".join(pos_tags).lower()

    if len(hanzi) == 4 or "i" in pos_tags:
        pool = IDIOM_TEMPLATES
    elif any(p.startswith("v") for p in pos_tags):
        pool = VERB_TEMPLATES
    elif any(p.startswith("a") for p in pos_tags):
        pool = ADJ_TEMPLATES
    elif any(p.startswith("d") for p in pos_tags) or "adv" in pos_joined:
        pool = ADV_TEMPLATES
    else:
        pool = NOUN_TEMPLATES

    # Essayer les variantes dans un ordre déterministe jusqu'à obtenir >= 3 tokens sans doublons
    digest = int(hashlib.md5(hanzi.encode("utf-8")).hexdigest()[:8], 16)
    start = (digest + seed_idx) % len(pool)
    for offset in range(len(pool)):
        zh_tpl, fr_tpl = pool[(start + offset) % len(pool)]
        zh = zh_tpl.format(w=hanzi)
        body = zh.rstrip(TRAILING_PUNCT)
        tokens = jieba.lcut(body)
        if len(tokens) >= 3 and len(tokens) == len(set(tokens)) and "".join(tokens) == body:
            fr = fr_tpl.format(fr=gloss)
            return zh, fr, tokens

    # Repli ultime garanti sans doublons
    zh = f"今天学习{hanzi}这个词。"
    body = zh[:-1]
    tokens = ["今天", "学习", hanzi, "这个", "词"]
    if len(tokens) != len(set(tokens)) or hanzi in {"今天", "学习", "这个", "词"}:
        zh = f"请多练习{hanzi}。"
        tokens = ["请", "多", "练习", hanzi]
    return zh, f"Exercez-vous aujourd'hui avec : {gloss}.", tokens


OFFICIAL_TAG_RE = re.compile(r"^o([1-6])$")


def build_word_dict(
    level: int,
    word_num: int,
    id_width: int,
    lesson_id: str,
    entry: dict[str, Any],
    cfdict: dict[str, str],
    existing_pinyin: dict[str, str],
    existing_french: dict[str, str],
    existing_examples: dict[str, dict[str, Any]],
    token_sentences: dict[str, list[tuple[str, str, list[str]]]],
    word_level_map: dict[str, int],
    missing_french: list[tuple[int, str, str]],
) -> tuple[dict[str, Any], bool]:
    hanzi = entry["s"]
    forms = entry.get("f", [])
    raw_pinyin = forms[0]["i"]["y"] if forms and "i" in forms[0] else ""
    pinyin_val = existing_pinyin.get(hanzi) or ensure_tonal_word_pinyin(hanzi, raw_pinyin)

    trad = forms[0].get("t", "") if forms else ""
    french = (
        existing_french.get(hanzi)
        or cfdict.get(hanzi)
        or cfdict.get(hanzi.rstrip("儿"))
        or (cfdict.get(trad) if trad else None)
        or FRENCH_FALLBACK.get(hanzi)
    )
    if not french:
        eng = ", ".join(forms[0].get("m", [])[:2]) if forms else ""
        missing_french.append((level, hanzi, eng))
        french = f"Terme HSK {level} ({hanzi})"

    reused_ex = existing_examples.get(hanzi)
    if reused_ex is not None:
        toks = reused_ex.get("tokens", [])
        body = reused_ex.get("hanzi", "").rstrip("。？！，、；：.,?!")
        if (
            isinstance(toks, list)
            and len(toks) >= 3
            and len(toks) == len(set(toks))
            and "".join(toks) == body
            and "，" not in body
        ):
            w_id = f"hsk{level}_{word_num:0{id_width}d}"
            return (
                {
                    "id": w_id,
                    "lessonId": lesson_id,
                    "hanzi": hanzi,
                    "pinyin": pinyin_val,
                    "french": french,
                    "example": reused_ex,
                },
                False,
            )

    best_tatoeba = None
    best_score = -10_000.0
    for cmn_text, fra_text, tokens in token_sentences.get(hanzi, []):
        known_count = sum(1 for t in tokens if word_level_map.get(t, 99) <= level)
        max_tok_lv = max(word_level_map.get(t, 7) for t in tokens)
        ratio = known_count / len(tokens)
        score = (
            ratio * 100.0
            - max(0, max_tok_lv - level) * 35.0
            - abs(len(cmn_text) - 10) * 1.5
            - abs(len(tokens) - 5) * 2.0
        )
        if score > best_score:
            best_score = score
            best_tatoeba = (cmn_text, fra_text, tokens)

    tatoeba_used = False
    if best_tatoeba is not None and best_score >= 30.0:
        ex_hanzi, ex_french, ex_tokens = best_tatoeba
        tatoeba_used = True
    else:
        ex_hanzi, ex_french, ex_tokens = generate_natural_example(
            hanzi=hanzi,
            french=french,
            pos_tags=entry.get("p", []),
            seed_idx=word_num,
        )

    ex_pinyin = to_tonal_pinyin_sentence(ex_hanzi)
    w_id = f"hsk{level}_{word_num:0{id_width}d}"
    return (
        {
            "id": w_id,
            "lessonId": lesson_id,
            "hanzi": hanzi,
            "pinyin": pinyin_val,
            "french": french,
            "example": {
                "hanzi": ex_hanzi,
                "pinyin": ex_pinyin,
                "french": ex_french,
                "tokens": ex_tokens,
            },
        },
        tatoeba_used,
    )


def main() -> int:
    hsk_path = download_cached(HSK_URL, "complete.min.json")
    cfdict_path = download_cached(CFDICT_URL, "cfdict.u8")
    cmn_path = download_cached(TATOEBA_CMN_URL, "cmn_sentences.tsv.bz2")
    fra_path = download_cached(TATOEBA_FRA_URL, "fra_sentences.tsv.bz2")
    links_path = download_cached(TATOEBA_LINKS_URL, "links.tar.bz2")

    hsk_entries: list[dict[str, Any]] = json.loads(hsk_path.read_text(encoding="utf-8"))
    cfdict = load_cfdict(cfdict_path)
    tatoeba_pairs = load_tatoeba_pairs(cmn_path, fra_path, links_path)

    # Capturer les traductions, pinyins, exemples et versions existants avant reconstruction
    existing_versions: dict[int, int] = {}
    existing_pinyin: dict[str, str] = {}
    existing_french: dict[str, str] = {}
    existing_examples: dict[str, dict[str, Any]] = {}
    for lv in range(1, 7):
        cat_path = CATALOG_DIR / f"hsk{lv}.json"
        if cat_path.exists():
            cat_data = json.loads(cat_path.read_text(encoding="utf-8"))
            existing_versions[lv] = int(cat_data.get("catalogVersion", 1))
            for w in cat_data.get("words", []):
                hz = w["hanzi"]
                existing_pinyin.setdefault(hz, w["pinyin"])
                existing_french.setdefault(hz, w["french"])
                existing_examples.setdefault(hz, w["example"])
        else:
            existing_versions[lv] = 1

    # Extraire la liste officielle HSK 2.0 100 % exacte (o1..o6) sans doublon inter-niveaux
    word_level_map: dict[str, int] = {}
    official_entries_by_level: dict[int, list[dict[str, Any]]] = {lv: [] for lv in range(1, 7)}
    official_entry_by_hanzi: dict[str, dict[str, Any]] = {}

    for entry in hsk_entries:
        levels = []
        for tag in entry.get("l", []):
            match = OFFICIAL_TAG_RE.match(tag)
            if match:
                levels.append(int(match.group(1)))
        if not levels:
            continue
        hanzi = entry["s"]
        if hanzi in word_level_map:
            continue
        lv = min(levels)
        word_level_map[hanzi] = lv
        official_entries_by_level[lv].append(entry)
        official_entry_by_hanzi[hanzi] = entry
        jieba.add_word(hanzi, freq=100_000)

    # Pré-indexer les phrases Tatoeba par token
    print("Indexation des phrases Tatoeba...")
    token_sentences: dict[str, list[tuple[str, str, list[str]]]] = defaultdict(list)
    for cmn_text, fra_text in tatoeba_pairs:
        body = cmn_text.rstrip(TRAILING_PUNCT)
        tokens = jieba.lcut(body)
        if not (3 <= len(tokens) <= 9):
            continue
        if len(tokens) != len(set(tokens)):
            continue
        if "".join(tokens) != body:
            continue
        for tok in tokens:
            if tok in word_level_map and len(token_sentences[tok]) < 40:
                token_sentences[tok].append((cmn_text, fra_text, tokens))

    missing_french: list[tuple[int, str, str]] = []

    # --- HSK 1 : 6 unités curatées couvrant exactement les 150 mots officiels o1 ---
    curated_hsk1_words = [
        hanzi
        for _u_title, _u_desc, lessons in HSK1_CURATED_UNITS
        for _l_title, words_list in lessons
        for hanzi in words_list
    ]
    official_o1_set = {e["s"] for e in official_entries_by_level[1]}
    assert len(curated_hsk1_words) == len(official_o1_set), (
        f"HSK 1 curated count {len(curated_hsk1_words)} != official {len(official_o1_set)}"
    )
    assert len(set(curated_hsk1_words)) == len(official_o1_set), "Doublon dans HSK1_CURATED_UNITS"
    assert set(curated_hsk1_words) == official_o1_set, (
        f"Écart HSK 1 curated vs officiel : "
        f"manquants={official_o1_set - set(curated_hsk1_words)}, "
        f"en trop={set(curated_hsk1_words) - official_o1_set}"
    )

    hsk1_units: list[dict[str, Any]] = []
    hsk1_words: list[dict[str, Any]] = []
    l_order = 1
    w_num = 1
    tatoeba_hits_hsk1 = 0
    for u_idx, (u_title, u_desc, lesson_specs) in enumerate(HSK1_CURATED_UNITS, start=1):
        u_id = f"hsk1_u{u_idx}"
        u_lessons: list[dict[str, Any]] = []
        for l_title, lesson_hanzi_list in lesson_specs:
            l_id = f"hsk1_u{u_idx}_l{l_order}"
            u_lessons.append({
                "id": l_id,
                "orderIndex": l_order,
                "title": l_title,
            })
            l_order += 1
            for hz in lesson_hanzi_list:
                word_dict, tat_used = build_word_dict(
                    level=1,
                    word_num=w_num,
                    id_width=3,
                    lesson_id=l_id,
                    entry=official_entry_by_hanzi[hz],
                    cfdict=cfdict,
                    existing_pinyin=existing_pinyin,
                    existing_french=existing_french,
                    existing_examples=existing_examples,
                    token_sentences=token_sentences,
                    word_level_map=word_level_map,
                    missing_french=missing_french,
                )
                if tat_used:
                    tatoeba_hits_hsk1 += 1
                hsk1_words.append(word_dict)
                w_num += 1
        hsk1_units.append({
            "id": u_id,
            "orderIndex": u_idx,
            "title": u_title,
            "description": u_desc,
            "lessons": u_lessons,
        })

    hsk1_catalog = {
        "hskLevel": 1,
        "catalogVersion": existing_versions[1] + 1,
        "units": hsk1_units,
        "words": hsk1_words,
    }
    (CATALOG_DIR / "hsk1.json").write_text(
        json.dumps(hsk1_catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"HSK 1 (v{hsk1_catalog['catalogVersion']}) : "
        f"{len(hsk1_units)} unités, {l_order - 1} leçons, {len(hsk1_words)} mots"
    )

    # --- HSK 2 à HSK 6 : 100 % officiel o2..o6 avec tri incrémental et IDs propres ---
    for level in range(2, 7):
        candidates = list(official_entries_by_level[level])
        candidates.sort(
            key=lambda e: (
                0 if len(e["s"]) <= 2 else (1 if len(e["s"]) == 3 else 2),
                e.get("q") or 999999,
                len(e["s"]),
                e["s"],
            )
        )

        lesson_size = LESSON_SIZES[level]
        chunks: list[list[dict[str, Any]]] = [
            candidates[i : i + lesson_size] for i in range(0, len(candidates), lesson_size)
        ]
        if len(chunks) >= 2 and len(chunks[-1]) < 3:
            chunks[-2].extend(chunks[-1])
            chunks.pop()

        thematic_specs = THEMATIC_UNITS[level]
        num_units = len(thematic_specs)
        lessons_per_unit = max(1, (len(chunks) + num_units - 1) // num_units)
        id_width = 3 if level <= 4 else 4

        all_units: list[dict[str, Any]] = []
        all_words: list[dict[str, Any]] = []
        next_lesson_order = 1
        next_word_num = 1
        tatoeba_hits = 0
        chunk_idx = 0

        for u_idx, (u_title_short, u_desc) in enumerate(thematic_specs, start=1):
            u_id = f"hsk{level}_u{u_idx}"
            u_lessons: list[dict[str, Any]] = []

            end_chunk = len(chunks) if u_idx == num_units else min(len(chunks), chunk_idx + lessons_per_unit)
            while chunk_idx < end_chunk:
                chunk = chunks[chunk_idx]
                l_order = next_lesson_order
                next_lesson_order += 1
                l_id = f"hsk{level}_u{u_idx}_l{l_order}"
                u_lessons.append({
                    "id": l_id,
                    "orderIndex": l_order,
                    "title": f"Leçon {l_order} — {u_title_short}",
                })

                for entry in chunk:
                    word_dict, tat_used = build_word_dict(
                        level=level,
                        word_num=next_word_num,
                        id_width=id_width,
                        lesson_id=l_id,
                        entry=entry,
                        cfdict=cfdict,
                        existing_pinyin=existing_pinyin,
                        existing_french=existing_french,
                        existing_examples=existing_examples,
                        token_sentences=token_sentences,
                        word_level_map=word_level_map,
                        missing_french=missing_french,
                    )
                    if tat_used:
                        tatoeba_hits += 1
                    all_words.append(word_dict)
                    next_word_num += 1

                chunk_idx += 1

            if u_lessons:
                all_units.append({
                    "id": u_id,
                    "orderIndex": u_idx,
                    "title": f"Unité {u_idx} — {u_title_short}",
                    "description": u_desc,
                    "lessons": u_lessons,
                })

        updated_catalog = {
            "hskLevel": level,
            "catalogVersion": existing_versions[level] + 1,
            "units": all_units,
            "words": all_words,
        }
        (CATALOG_DIR / f"hsk{level}.json").write_text(
            json.dumps(updated_catalog, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        total_lessons = sum(len(u["lessons"]) for u in all_units)
        print(
            f"HSK {level} (v{updated_catalog['catalogVersion']}) : "
            f"{len(all_units)} unités, {total_lessons} leçons, {len(all_words)} mots"
        )

    if missing_french:
        print(f"\nATTENTION : {len(missing_french)} mots sans traduction CFDICT/fallback :")
        for lv, hz, eng in missing_french[:40]:
            print(f"  HSK {lv}: {hz} -> {eng}")
        if len(missing_french) > 40:
            print(f"  ... et {len(missing_french) - 40} autres")
        (CACHE_DIR / "missing_french.json").write_text(
            json.dumps(missing_french, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
