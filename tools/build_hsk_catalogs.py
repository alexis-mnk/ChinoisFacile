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
DATA_DIR = Path(__file__).resolve().parent / "data"

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

# Progression pédagogique curatée des 150 mots officiels Hanban 2012 HSK 1 (6 unités × 5 leçons × 5 mots)
HSK1_CURATED_UNITS: list[tuple[str, str, list[tuple[str, list[str]]]]] = [
    (
        "Unité 1 — Salutations, Pronoms & Bases",
        "Premiers échanges pour se présenter et poser des questions simples.",
        [
            ("Leçon 1 — Salutations et politesse", ["你", "好", "谢谢", "再见", "喂"]),
            ("Leçon 2 — Excuses et courtoisie", ["请", "对不起", "没关系", "不客气", "高兴"]),
            ("Leçon 3 — Pronoms et être", ["我", "他", "她", "我们", "是"]),
            ("Leçon 4 — Négation et questions", ["不", "没有", "吗", "呢", "谁"]),
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
            ("Leçon 11 — Chiffres 1 à 5", ["一", "二", "三", "四", "五"]),
            ("Leçon 12 — Chiffres 6 à 10", ["六", "七", "八", "九", "十"]),
            ("Leçon 13 — Quantités et âge", ["几", "多少", "岁", "个", "号"]),
            ("Leçon 14 — Dates et calendrier", ["年", "月", "星期", "今天", "明天"]),
            ("Leçon 15 — Heures et moments", ["昨天", "现在", "点", "分钟", "一点儿"]),
        ],
    ),
    (
        "Unité 4 — Lieux, Repères & Maison",
        "Se repérer dans l'espace et décrire son quotidien.",
        [
            ("Leçon 16 — Moments du jour", ["上午", "中午", "下午", "时候", "了"]),
            ("Leçon 17 — Pays et lieux publics", ["中国", "北京", "饭店", "商店", "医院"]),
            ("Leçon 18 — Transports et logement", ["学校", "飞机", "出租车", "电影", "住"]),
            ("Leçon 19 — Repères spatiaux", ["哪", "哪儿", "里", "前面", "后面"]),
            ("Leçon 20 — Meubles et objets", ["上", "下", "桌子", "椅子", "杯子"]),
        ],
    ),
    (
        "Unité 5 — Actions, Achats & Météo",
        "Exprimer ses actions quotidiennes, faire des achats et parler du temps.",
        [
            ("Leçon 21 — Déplacements et regard", ["去", "来", "回", "坐", "看"]),
            ("Leçon 22 — Travail et études", ["工作", "学习", "老师", "学生", "医生"]),
            ("Leçon 23 — Perception et langage", ["看见", "听", "说", "读", "写"]),
            ("Leçon 24 — Livres, sommeil et appels", ["字", "书", "睡觉", "打电话", "东西"]),
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

# Progression pédagogique douce des 151 mots officiels Hanban 2012 HSK 2 (concret -> abstrait)
HSK2_CURATED_UNITS: list[tuple[str, str, list[tuple[str, list[str]]]]] = [
    (
        "Unité 1 — Premiers échanges & Bases simples",
        "Personnes, verbes courants et adverbes usuels du quotidien.",
        [
            ("Leçon 1 — Politesse et personnes", ["您", "大家", "男", "女", "孩子"]),
            ("Leçon 2 — Verbes essentiels", ["要", "可以", "知道", "觉得", "希望"]),
            ("Leçon 3 — Adverbes simples", ["也", "真", "非常", "最", "两"]),
            ("Leçon 4 — Famille et proches", ["哥哥", "姐姐", "弟弟", "妹妹", "丈夫"]),
            ("Leçon 5 — Présentation et identité", ["妻子", "姓", "介绍", "它", "快乐"]),
        ],
    ),
    (
        "Unité 2 — Temps, Chiffres & Repères",
        "Chiffres, durées, calendrier et repères dans l'espace.",
        [
            ("Leçon 6 — Chiffres et ordre", ["零", "百", "千", "第一", "次"]),
            ("Leçon 7 — Calendrier et moments", ["日", "去年", "生日", "早上", "晚上", "时间"]),
            ("Leçon 8 — Durée et rythme", ["小时", "每", "开始", "准备", "完"]),
            ("Leçon 9 — Lieu et direction", ["旁边", "外", "左边", "右边", "往"]),
            ("Leçon 10 — Distance et position", ["近", "远", "离", "到", "进"]),
        ],
    ),
    (
        "Unité 3 — Nourriture, Objets & Achats",
        "Boissons, repas, vêtements, objets du quotidien et couleurs.",
        [
            ("Leçon 11 — Boissons et repas", ["咖啡", "牛奶", "鸡蛋", "面条", "鱼"]),
            ("Leçon 12 — Fruits, plats et restaurant", ["羊肉", "西瓜", "好吃", "服务员", "贵"]),
            ("Leçon 13 — Prix et vêtements", ["便宜", "卖", "件", "穿", "洗"]),
            ("Leçon 14 — Objets du quotidien", ["手机", "手表", "铅笔", "报纸", "新"]),
            ("Leçon 15 — Couleurs et apparence", ["颜色", "红", "白", "黑", "长"]),
        ],
    ),
    (
        "Unité 4 — Lieux, Transports & Santé",
        "Bâtiments, déplacements, météo, corps et santé.",
        [
            ("Leçon 16 — Maison et bâtiments", ["门", "房间", "教室", "公司", "宾馆"]),
            ("Leçon 17 — Transports et trajets", ["火车站", "机场", "公共汽车", "路", "票"]),
            ("Leçon 18 — Déplacements et actions", ["旅游", "走", "出", "送", "找"]),
            ("Leçon 19 — Météo et traits physiques", ["晴", "阴", "雪", "高", "眼睛"]),
            ("Leçon 20 — Corps, santé et repos", ["身体", "生病", "药", "累", "休息"]),
        ],
    ),
    (
        "Unité 5 — Activités, Études & Communication",
        "Routine, sports, loisirs, études et échanges.",
        [
            ("Leçon 21 — Routine et travail", ["起床", "上班", "忙", "笑", "玩"]),
            ("Leçon 22 — Sports et loisirs", ["运动", "跑步", "游泳", "踢足球", "打篮球"]),
            ("Leçon 23 — Musique et études", ["唱歌", "跳舞", "课", "考试", "题"]),
            ("Leçon 24 — Communication", ["说话", "问", "问题", "告诉", "懂"]),
            ("Leçon 25 — Entraide et sens", ["帮助", "意思", "事情", "对", "等"]),
        ],
    ),
    (
        "Unité 6 — Grammaire, Nuances & Connecteurs",
        "Aspects verbaux, comparaisons, nuances et connecteurs logiques.",
        [
            ("Leçon 26 — Temps et durée d'action", ["正在", "已经", "过", "着", "一下"]),
            ("Leçon 27 — Adverbes et vitesse", ["还", "再", "一起", "快", "慢"]),
            ("Leçon 28 — Structures et comparaison", ["就", "从", "比", "得", "错"]),
            ("Leçon 29 — Interaction et modalité", ["给", "让", "别", "吧", "可能"]),
            ("Leçon 30 — Cause, concession et logique", ["为什么", "因为", "所以", "虽然", "但是"]),
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
    "呢": "Particule interrogative (et toi ?)",
    "岁": "An / Âge (ans)",
    "使劲儿": "Faire un effort / Forcer",
    "系领带": "Mettre une cravate",
    "墨水儿": "Encre",
    "纽扣儿": "Bouton (vêtement)",
    "摊儿": "Étal / Stand",
    "致力于": "Se consacrer à / S'engager pour",
    "烟花爆竹": "Feux d'artifice et pétards",
    "侃侃而谈": "Parler avec assurance et aisance",
    "儒家": "École confucéenne / Confucianisme",
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


SURNAME_RE = re.compile(r"nom de famille|patronyme|prénom", re.IGNORECASE)
VULGAR_OR_SLANG_RE = re.compile(
    r"(\(argot\)|\(vulg\.?\)|\(fam\.?\)|radical\)|\bputain\b|\bmerde\b|\bbordel\b|\bsalope\b|\bconnard\w*\b|\bconnasse\b|\bencul\w*\b|\bnichons?\b|\bbranl\w*\b|\bculotte\b|\bporn\w*\b|\bpénis\b|\bvagin\b|\bcouille\w*\b|\bchatte\b|\bbite\b|\bchier\b|\bchiant\w*\b|\bfoutre\b|\bfous\b|\bfout\b|\bgueule\b|\bpisse\w*\b|\bcaca\b|\bpipi\b|\bsalaud\b|\benfoiré\w*\b|\bbâtard\w*\b|\bnique\w*\b|\bpétasse\b|\bgarce\b|\bdégueulasse\b|\bemmerd\w*\b|\bculs?\b|\bprostitu\w*\b|\bsapes\b|\bcon\b|\bcons\b|\bconne\b)",
    re.IGNORECASE,
)

# Seuls vrais noms propres HSK 1..6 autorisant une majuscule dans le pinyin
ALLOWED_PROPER_NOUNS = {
    "中国", "北京", "汉语", "中文", "黄河", "亚洲", "京剧", "长城", "长江",
    "元旦", "华裔", "国庆节", "礼拜天", "除夕", "互联网", "元宵节", "华侨",
    "国务院", "摄氏度", "欧洲", "正月", "端午节", "重阳节",
}

# Table explicite de référence pour les polyphones clés (lecture standard HSK, pinyin numéroté minuscule)
PREFERRED_PINYIN: dict[str, str] = {
    # Polyphones principaux HSK 1-4 demandés + cas audités
    "喝": "he1",
    "说": "shuo1",
    "没有": "mei2you3",
    "一点儿": "yi1dian3r5",
    "哪儿": "na3r5",
    "还": "hai2",
    "都": "dou1",
    "长": "chang2",
    "只": "zhi3",
    "没": "mei2",
    "得": "de5",
    "地": "de5",
    "了": "le5",
    "着": "zhe5",
    "和": "he2",
    "行": "xing2",
    "为": "wei4",
    "重": "zhong4",
    "干": "gan4",
    "发": "fa1",
    "会": "hui4",
    "好": "hao3",
    "看": "kan4",
    "少": "shao3",
    "分": "fen1",
    "教": "jiao1",
    "过": "guo5",
    "对": "dui4",
    "等": "deng3",
    "往": "wang3",
    "错": "cuo4",
    "一下": "yi1xia4",
    "得罪": "de2zui5",
    "假": "jia3",
    "种": "zhong3",
    "倒": "dao3",
    "数": "shu4",
    "空": "kong1",
    "要": "yao4",
    "应": "ying1",
    "相": "xiang1",
    "传": "chuan2",
    # Autres polyphones HSK 1-4 audités
    "东西": "dong1xi5",
    "多少": "duo1shao5",
    "几": "ji3",
    "吗": "ma5",
    "呢": "ne5",
    "哪": "na3",
    "喂": "wei4",
    "的": "de5",
    "读": "du2",
    "便宜": "pian2yi5",
    "别": "bie2",
    "号": "hao4",
    "吧": "ba5",
    "告诉": "gao4su5",
    "女人": "nv3ren2",
    "好吃": "hao3chi1",
    "妻子": "qi1zi5",
    "累": "lei4",
    "给": "gei3",
    "远": "yuan3",
    "张": "zhang1",
    "比": "bi3",
    "刷": "shua1",
    "啊": "a5",
    "地方": "di4fang5",
    "差": "cha4",
    "把": "ba3",
    "故事": "gu4shi5",
    "更": "geng4",
    "胖": "pang4",
    "角": "jiao3",
    "过去": "guo4qu4",
    "难": "nan2",
    "骑": "qi2",
    "与": "yu3",
    "场": "chang3",
    "大夫": "dai4fu5",
    "好处": "hao3chu5",
    "底": "di3",
    "弄": "nong4",
    "弹": "tan2",
    "当": "dang1",
    "当时": "dang1shi2",
    "汤": "tang1",
    "省": "sheng3",
    "精神": "jing1shen2",
    "结果": "jie2guo3",
    "脏": "zang1",
    "赚": "zhuan4",
    "起来": "qi3lai5",
    "趟": "tang4",
    "重点": "zhong4dian3",
    "页": "ye4",
    "孙子": "sun1zi5",
    "奶奶": "nai3nai5",
    "爷爷": "ye2ye5",
    "叔叔": "shu1shu5",
    "舒服": "shu1fu5",
    "困难": "kun4nan5",
    "麻烦": "ma2fan5",
    "味道": "wei4dao5",
    "厉害": "li4hai5",
    "肚子": "du4zi5",
    "收拾": "shou1shi5",
    "护士": "hu4shi5",
    "脾气": "pi2qi5",
    "笑话": "xiao4hua5",
    "商量": "shang1liang5",
    "亲戚": "qin1qi5",
    "热闹": "re4nao5",
    "活泼": "huo2po5",
    "凉快": "liang2kuai5",
    "消息": "xiao1xi5",
    "一会儿": "yi1hui4r5",
    # Polyphones HSK 5-6 audités
    "乙": "yi3",
    "冲": "chong1",
    "切": "qie1",
    "匹": "pi3",
    "卷": "juan3",
    "吐": "tu3",
    "吓": "xia4",
    "唉": "ai1",
    "圈": "quan1",
    "土地": "tu3di4",
    "地道": "di4dao5",
    "大方": "da4fang5",
    "尽量": "jin3liang4",
    "抢": "qiang3",
    "挡": "dang3",
    "晕": "yun1",
    "朝": "chao2",
    "横": "heng2",
    "正": "zheng4",
    "歪": "wai1",
    "浅": "qian3",
    "涨": "zhang3",
    "片": "pian4",
    "系": "xi4",
    "结实": "jie1shi5",
    "背": "bei4",
    "臭": "chou4",
    "薄": "bao2",
    "露": "lu4",
    "提": "ti2",
    "盖": "gai4",
    "看来": "kan4lai5",
    "力量": "li4liang5",
    "要是": "yao4shi5",
    "人家": "ren2jia5",
    "公道": "gong1dao5",
    "出息": "chu1xi5",
    "分量": "fen4liang5",
    "利害": "li4hai5",
    "刺": "ci4",
    "劈": "pi1",
    "口音": "kou3yin1",
    "咋": "za3",
    "哄": "hong3",
    "哇": "wa1",
    "哦": "o4",
    "啦": "la5",
    "嗯": "en4",
    "大意": "da4yi5",
    "工夫": "gong1fu5",
    "幢": "zhuang4",
    "恶心": "e3xin5",
    "扎": "zha1",
    "扒": "ba1",
    "扛": "kang2",
    "把手": "ba3shou5",
    "折": "zhe2",
    "拧": "ning2",
    "拽": "zhuai4",
    "拾": "shi2",
    "挨": "ai1",
    "搁": "ge1",
    "搂": "lou3",
    "播种": "bo1zhong3",
    "攒": "zan3",
    "本事": "ben3shi5",
    "款式": "kuan3shi4",
    "正当": "zheng4dang4",
    "淋": "lin2",
    "温和": "wen1he2",
    "澄清": "cheng2qing1",
    "照应": "zhao4ying4",
    "熨": "yun4",
    "熬": "ao2",
    "琢磨": "zuo2mo5",
    "甭": "beng2",
    "盛": "sheng4",
    "眯": "mi1",
    "翘": "qiao4",
    "蒙": "meng2",
    "裁缝": "cai2feng5",
    "跟前": "gen1qian5",
    "铺": "pu1",
    "扁": "bian3",
    "番": "fan1",
    "束": "shu4",
    "翼": "yi4",
}

# Traductions françaises explicites pour les polyphones/cas sensibles (prioritaires sur CFDICT)
FRENCH_OVERRIDES: dict[str, str] = {
    "喝": "Boire",
    "不客气": "De rien / Je vous en prie",
    "吗": "Particule interrogative (est-ce que)",
    "叫": "S'appeler / Appeler",
    "那": "Ce ...-là / Cela",
    "个": "Classificateur général",
    "点": "Heure / Point / Un peu",
    "上": "Sur / Dessus / Monter",
    "回": "Revenir / Rentrer",
    "本": "Classificateur des livres",
    "能": "Pouvoir / Être capable de",
    "想": "Vouloir / Penser",
    "猫": "Chat",
    "狗": "Chien",
    "二": "Deux",
    "飞机": "Avion",
    "没有": "Ne pas avoir / Ne pas (passé)",
    "一点儿": "Un peu",
    "哪儿": "Où",
    "说": "Dire / Parler",
    "饭店": "Restaurant / Hôtel",
    "还": "Encore / Toujours",
    "都": "Tous / Tout",
    "长": "Long",
    "晴": "Ensoleillé / Clair (météo)",
    "只": "Seulement / Uniquement",
    "没": "Ne pas (avoir / passé)",
    "得": "Particule de degré",
    "地": "Particule adverbiale",
    "了": "Particule d'action accomplie",
    "着": "Particule d'aspect continu",
    "和": "Et / Avec",
    "行": "D'accord / Marcher",
    "为": "Pour / À cause de",
    "重": "Lourd / Important",
    "干": "Faire / Travailler",
    "发": "Envoyer / Émettre",
    "会": "Savoir faire / Pouvoir",
    "好": "Bon / Bien",
    "看": "Regarder / Lire",
    "少": "Peu / Manquer",
    "分": "Diviser / Minute / Point",
    "教": "Enseigner",
    "过": "Particule d'expérience passée",
    "假": "Faux / Artificiel",
    "种": "Sorte / Espèce / Graine",
    "倒": "Tomber / S'effondrer",
    "数": "Nombre / Chiffre",
    "空": "Vide / Ciel",
    "要": "Vouloir / Devoir",
    "应": "Devoir / Répondre",
    "相": "Mutuellement / Apparence",
    "传": "Transmettre / Passer",
    "东西": "Chose / Objet",
    "多少": "Combien",
    "便宜": "Bon marché / Pas cher",
    "告诉": "Dire / Informer",
    "妻子": "Épouse / Femme",
    "地方": "Endroit / Lieu",
    "故事": "Histoire / Récit",
    "大夫": "Médecin / Docteur",
    "好处": "Avantage / Bénéfice",
    "结果": "Résultat / Conséquence",
    "弄": "Faire / Arranger",
    "胖": "Gros / Dodu",
    "角": "Angle / Coin / Jiao",
    "张": "Ouvrir / Classificateur (objets plats)",
    "比": "Comparer / Que",
    "别": "Ne pas (impératif) / Autre",
    "让": "Laisser / Faire faire",
    "穿": "Porter (un vêtement) / S'habiller",
    "累": "Fatigué",
    "鸡蛋": "Œuf",
    "踢足球": "Jouer au football",
    "一下": "Un instant / Un peu",
    "男": "Masculin / Homme",
    "女": "Féminin / Femme",
    "对": "Juste / Correct / Envers",
    "等": "Attendre / Etc.",
    "往": "Vers / En direction de",
    "面条": "Nouilles",
    "铅笔": "Crayon",
    "宾馆": "Hôtel",
    "刷牙": "Se brosser les dents",
    "刮风": "Venter / Le vent souffle",
    "只有": "Seulement si / Il n'y a que",
    "感兴趣": "S'intéresser à",
    "电子邮件": "Courriel / E-mail",
    "黄河": "Fleuve Jaune",
    "鸟": "Oiseau",
    "裤子": "Pantalon",
    "春": "Printemps",
    "玻璃": "Verre",
    "屁股": "Fesses / Postérieur",
    "犬": "Chien",
    "得罪": "Offenser / Froisser",
    "正当": "Légitime / Honnête",
    "幢": "Classificateur des immeubles / bâtiments",
    "孙子": "Petit-fils",
    "奶奶": "Grand-mère paternelle",
    "脏": "Sale",
    "弹": "Jouer d'un instrument / Pincer",
    "趟": "Classificateur (trajets, allers-retours)",
    "重点": "Point clé / Essentiel",
    "提": "Porter à la main / Mentionner / Lever",
    "吓": "Effrayer / Faire peur",
    "盖": "Couvrir / Couvercle",
    "正": "Droit / Exactement / En train de",
    "背": "Dos / Porter sur le dos",
    "大方": "Généreux / Naturel",
    "地道": "Authentique / Pur",
    "扁": "Plat / Aplati",
    "番": "Fois / Tour",
    "束": "Bouquet / Faisceau / Lier",
    "翼": "Aile",
    "哈": "Ha (rire / exclamation)",
    "现代": "Moderne / Contemporain",
    "和平": "Paix / Pacifique",
    "时代": "Époque / Ère",
    "友好": "Amical",
    "狮子": "Lion",
    "朝": "Vers / En direction de / Dynastie",
    "黄": "Jaune",
    "鱼": "Poisson",
    "阴": "Nuageux / Ombre",
    "雪": "Neige",
    "米": "Riz / Mètre",
    "克": "Gramme / Vaincre",
    "项": "Élément / Rubrique / Nuque",
    "正": "Droit / Juste / Exactement",
    "过": "Particule d'expérience passée",
    "台": "Plateforme / Classificateur (machines)",
    "休": "Cesser / Se reposer",
    "结实": "Solide / Robuste",
    "地道": "Authentique / Pur",
    "大意": "Négligent / Idée générale",
    "人家": "Les autres / Autrui",
    "公道": "Équitable / Juste",
    "利害": "Avantages et inconvénients / Redoutable",
    "本事": "Capacité / Talent",
    "照应": "Prendre soin / Faire écho",
    "跟前": "Devant / Auprès de",
    "裁缝": "Tailleur / Couturier",
    "把手": "Poignée",
    "出息": "Avenir prometteur",
    "分量": "Poids / Importance",
    "口音": "Accent (prononciation)",
    "工夫": "Temps / Moment / Effort",
    "恶心": "Écœurant / Nausée",
    "款式": "Modèle / Style",
    "琢磨": "Réfléchir / Méditer",
    "盛": "Prospère / Florissant",
    "铺": "Étendre / Déployer",
}


def norm_numbered_pinyin(raw_py: str) -> str:
    return (
        raw_py.strip()
        .lower()
        .replace(" ", "")
        .replace("u:", "v")
        .replace("ü", "v")
    )


def norm_tonal_pinyin(py: str) -> str:
    return re.sub(
        r"[^a-zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü]",
        "",
        py.lower().replace("v", "ü"),
    )


def clean_cfdict_sense(raw: str) -> str | None:
    s = raw.strip()
    if not s:
        return None
    if SURNAME_RE.search(s) or VULGAR_OR_SLANG_RE.search(s):
        return None
    # Supprimer les mentions de classificateurs ou renvois techniques
    if s.startswith(("CL:", "voir ", "Voir ", "variante ", "Variante ", "ancien ", "fig.")):
        if s.startswith("CL:"):
            return None
    s = re.sub(r"\bCL:[^;/)]+", "", s)
    s = re.sub(r"\[[^\]]+\]", "", s)
    s = re.sub(r"\([^)]*\)", lambda m: "" if len(m.group(0)) > 28 or SURNAME_RE.search(m.group(0)) or VULGAR_OR_SLANG_RE.search(m.group(0)) else m.group(0), s)
    s = re.sub(r"\s{2,}", " ", s).strip(" ;,.-")
    if not s or len(s) < 2 or SURNAME_RE.search(s) or VULGAR_OR_SLANG_RE.search(s):
        return None
    return s


def load_cfdict(
    path: Path,
) -> tuple[dict[tuple[str, str], str], dict[tuple[str, str], str]]:
    """
    Charge CFDICT indexé par couple exact (hanzi, pinyin_numéroté_minuscule) et
    par (hanzi, pinyin_sans_tons) pour repli contrôlé. Ignore les entrées noms de famille.
    """
    by_pair_raw: dict[tuple[str, str], list[str]] = defaultdict(list)
    by_toneless_raw: dict[tuple[str, str], list[str]] = defaultdict(list)

    with path.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if not line or line.startswith("#"):
                continue
            m = CEDICT_LINE_RE.match(line)
            if not m:
                continue
            trad, simp, raw_py, gloss_block = m.groups()
            first_alpha = next((ch for ch in raw_py if ch.isalpha()), "")
            if first_alpha.isupper() and simp not in ALLOWED_PROPER_NOUNS and trad not in ALLOWED_PROPER_NOUNS:
                continue

            senses = [clean_cfdict_sense(g) for g in gloss_block.split("/")]
            senses = [s for s in senses if s]
            if not senses:
                continue

            concise = " / ".join(senses[:2])
            if len(concise) > 72:
                concise = senses[0][:72].rstrip(" ,;-")
            concise = concise[0].upper() + concise[1:]

            py_key = norm_numbered_pinyin(raw_py)
            toneless_key = re.sub(r"[1-5]", "", py_key)
            for hz in (simp, trad):
                by_pair_raw[(hz, py_key)].append(concise)
                by_toneless_raw[(hz, toneless_key)].append(concise)

    by_pair: dict[tuple[str, str], str] = {}
    for k, vals in by_pair_raw.items():
        vals_sorted = sorted(vals, key=lambda v: (len(v) < 3, len(v)))
        by_pair[k] = vals_sorted[0]

    by_toneless: dict[tuple[str, str], str] = {}
    for k, vals in by_toneless_raw.items():
        vals_sorted = sorted(vals, key=lambda v: (len(v) < 3, len(v)))
        by_toneless[k] = vals_sorted[0]

    return by_pair, by_toneless


def select_best_form(
    entry: dict[str, Any],
    cfdict_by_pair: dict[tuple[str, str], str],
) -> tuple[str, str, str]:
    """
    Sélectionne la lecture standard dans `entry['f']` en excluant noms de famille et variantes.
    Retourne `(pinyin_tonal, pinyin_numéroté_normalisé, hanzi_traditionnel)`.
    """
    hanzi = entry["s"]
    forms = entry.get("f", [])
    if not forms:
        syls = pypinyin_fn(hanzi, style=Style.TONE, heteronym=False)
        pyp_tonal = "".join(x[0] for x in syls)
        syls_num = pypinyin_fn(hanzi, style=Style.TONE3, neutral_tone_with_five=True, heteronym=False)
        pyp_num = norm_numbered_pinyin("".join(x[0] for x in syls_num))
        return pyp_tonal, pyp_num, ""

    # 1. Override explicite si le mot figure dans PREFERRED_PINYIN
    preferred = PREFERRED_PINYIN.get(hanzi)
    if preferred:
        for form in forms:
            num_py = norm_numbered_pinyin(form.get("i", {}).get("n", ""))
            tonal_py = form.get("i", {}).get("y", "").strip()
            if num_py == preferred and not any(c.isupper() for c in tonal_py):
                return ensure_tonal_word_pinyin(hanzi, tonal_py), num_py, form.get("t", "")

    # 2. Filtrer les entrées noms de famille / majuscules (sauf vrais noms propres)
    is_proper = hanzi in ALLOWED_PROPER_NOUNS
    non_upper = [
        f for f in forms
        if not any(c.isupper() for c in f.get("i", {}).get("y", ""))
        and "surname" not in " ".join(f.get("m", [])).lower()
    ]
    pool = forms if is_proper else (non_upper or forms)

    non_variant = [
        f for f in pool
        if not (
            f.get("m")
            and all(
                any(kw in m.lower() for kw in ("variant of", "old variant", "archaic", "used in ", "see "))
                for m in f["m"]
            )
        )
        and not VULGAR_OR_SLANG_RE.search(" ".join(f.get("m", [])))
    ]
    if non_variant:
        pool = non_variant

    if len(pool) == 1:
        f0 = pool[0]
        tonal = f0.get("i", {}).get("y", "").strip()
        if not is_proper:
            tonal = tonal.lower()
        num_py = norm_numbered_pinyin(f0.get("i", {}).get("n", ""))
        return ensure_tonal_word_pinyin(hanzi, tonal), num_py, f0.get("t", "")

    # 3. Scorer les lectures candidates par concordance pypinyin + CFDICT + richesse sémantique
    pyp_num = norm_numbered_pinyin(
        "".join(x[0] for x in pypinyin_fn(hanzi, style=Style.TONE3, neutral_tone_with_five=True, heteronym=False))
    )
    pyp_toneless = re.sub(r"[1-5]", "", pyp_num)

    best_form = pool[0]
    best_score = -10_000
    for f in pool:
        tonal = f.get("i", {}).get("y", "").strip()
        num_py = norm_numbered_pinyin(f.get("i", {}).get("n", ""))
        m_low = " ".join(f.get("m", [])).lower()
        score = 0
        if num_py == pyp_num:
            score += 100
        elif re.sub(r"[1-5]", "", num_py) == pyp_toneless:
            score += 35
        if (hanzi, num_py) in cfdict_by_pair:
            score += 40
        if any(tag in m_low for tag in ("literary", "classical", "dialect", "onom.", "(tw)")):
            score -= 30
        if "(bound form)" in m_low:
            score -= 10
        score += min(5, len(f.get("m", [])))
        if score > best_score:
            best_score = score
            best_form = f

    tonal = best_form.get("i", {}).get("y", "").strip()
    if not is_proper:
        tonal = tonal.lower()
    num_py = norm_numbered_pinyin(best_form.get("i", {}).get("n", ""))
    return ensure_tonal_word_pinyin(hanzi, tonal), num_py, best_form.get("t", "")


def tokens_to_pinyin_sentence(
    tokens: list[str],
    punct: str,
    word_to_pinyin: dict[str, str],
) -> str:
    """Rend le pinyin d'une phrase token par token en utilisant la lecture retenue du catalogue."""
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
                if len(parts) == 2 and not VULGAR_OR_SLANG_RE.search(parts[1]):
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


# Gabarits explicites tokenisés : garantit `hanzi in tokens` sans collage jieba
VERB_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["我们", "正在", "{w}"], "。", "Nous sommes en train de : {fr}."),
    (["请", "认真", "{w}"], "。", "Veuillez sérieusement : {fr}."),
    (["他", "每天", "都", "{w}"], "。", "Il pratique chaque jour : {fr}."),
    (["我", "想", "学会", "{w}"], "。", "J'aimerais apprendre à : {fr}."),
    (["大家", "一起", "{w}", "吧"], "！", "Allons {fr} tous ensemble !"),
    (["明天", "再", "{w}", "好", "吗"], "？", "Et si on remettait à demain pour : {fr} ?"),
    (["她", "经常", "这样", "{w}"], "。", "Elle a souvent l'habitude de : {fr}."),
]

NOUN_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["这个", "{w}", "很", "重要"], "。", "Ce terme « {fr} » est très important."),
    (["我们", "讨论", "过", "{w}"], "。", "Nous avons déjà abordé : {fr}."),
    (["大家", "都", "很", "了解", "{w}"], "。", "Tout le monde connaît bien : {fr}."),
    (["请", "记住", "这个", "{w}"], "。", "Veuillez retenir ce mot : {fr}."),
    (["书", "上", "提到", "了", "{w}"], "。", "Le livre mentionne : {fr}."),
    (["这里", "需要", "新的", "{w}"], "。", "Il faut un nouvel élément ici : {fr}."),
]

ADJ_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["今天", "的", "情况", "很", "{w}"], "。", "La situation d'aujourd'hui est très : {fr}."),
    (["这个", "地方", "非常", "{w}"], "。", "Cet endroit est particulièrement : {fr}."),
    (["他", "觉得", "这样", "更", "{w}"], "。", "Il trouve cela encore plus : {fr}."),
    (["大家", "的", "态度", "很", "{w}"], "。", "L'attitude de chacun est très : {fr}."),
    (["这里", "的", "环境", "真", "{w}"], "！", "Le cadre ici est vraiment : {fr} !"),
]

ADV_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["大家", "{w}", "完成", "任务"], "。", "Chacun accomplit la tâche ({fr})."),
    (["她", "{w}", "说出", "了", "想法"], "。", "Elle a exprimé son idée ({fr})."),
    (["我们", "{w}", "继续", "前进"], "。", "Continuons d'avancer ({fr})."),
    (["请", "{w}", "准备", "一下"], "。", "Veuillez vous préparer ({fr})."),
]

IDIOM_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["大家", "做事", "要", "{w}"], "。", "Dans nos actions, il faut appliquer : {fr}."),
    (["老师", "常常", "强调", "{w}"], "。", "Le professeur souligne souvent : {fr}."),
    (["我们", "应该", "坚持", "{w}"], "。", "Nous devons nous efforcer de : {fr}."),
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

    digest = int(hashlib.md5(hanzi.encode("utf-8")).hexdigest()[:8], 16)
    start = (digest + seed_idx) % len(pool)
    for offset in range(len(pool)):
        tpl_tokens, punct, fr_tpl = pool[(start + offset) % len(pool)]
        tokens = [hanzi if t == "{w}" else t for t in tpl_tokens]
        if len(tokens) >= 3 and len(tokens) == len(set(tokens)) and hanzi in tokens:
            zh = "".join(tokens) + punct
            fr = fr_tpl.format(fr=gloss)
            return zh, fr, tokens

    for tokens, punct in [
        (["今天", "学习", hanzi, "这个", "词"], "。"),
        (["请", "多", "练习", hanzi], "。"),
        (["我们", "认识", hanzi], "。"),
    ]:
        if len(tokens) == len(set(tokens)) and hanzi in tokens:
            return "".join(tokens) + punct, f"Exercez-vous aujourd'hui avec : {gloss}.", tokens
    raise RuntimeError(f"Impossible de générer un exemple pour {hanzi}")


OFFICIAL_TAG_RE = re.compile(r"^o([1-6])$")


def resolve_word_metadata(
    level: int,
    entry: dict[str, Any],
    cfdict_by_pair: dict[tuple[str, str], str],
    cfdict_toneless: dict[tuple[str, str], str],
    missing_french: list[tuple[int, str, str]],
) -> tuple[str, str, str]:
    """Résout (pinyin_tonal, norm_num_py, french) de manière 100 % exacte sans nom de famille."""
    hanzi = entry["s"]
    pinyin_val, norm_num_py, trad = select_best_form(entry, cfdict_by_pair)
    toneless_py = re.sub(r"[1-5]", "", norm_num_py)

    french = FRENCH_OVERRIDES.get(hanzi) or FRENCH_FALLBACK.get(hanzi)
    if not french:
        french = (
            cfdict_by_pair.get((hanzi, norm_num_py))
            or cfdict_by_pair.get((hanzi.rstrip("儿"), norm_num_py))
            or (cfdict_by_pair.get((trad, norm_num_py)) if trad else None)
            or cfdict_toneless.get((hanzi, toneless_py))
            or (cfdict_toneless.get((trad, toneless_py)) if trad else None)
        )
    if not french:
        forms = entry.get("f", [])
        eng = ", ".join(forms[0].get("m", [])[:2]) if forms else ""
        missing_french.append((level, hanzi, eng))
        french = f"Terme HSK {level} ({hanzi})"
    return pinyin_val, norm_num_py, french


def build_word_dict(
    level: int,
    word_num: int,
    id_width: int,
    lesson_id: str,
    entry: dict[str, Any],
    resolved_meta: dict[str, tuple[str, str, str]],
    word_to_pinyin: dict[str, str],
    existing_words: dict[str, dict[str, Any]],
    token_sentences: dict[str, list[tuple[str, str, list[str]]]],
    word_level_map: dict[str, int],
) -> tuple[dict[str, Any], bool]:
    hanzi = entry["s"]
    pinyin_val, _norm_num_py, french = resolved_meta[hanzi]

    prev_word = existing_words.get(hanzi)
    if prev_word is not None and hanzi != "屁股":
        reused_ex = prev_word.get("example", {})
        toks = reused_ex.get("tokens", [])
        ex_hz = reused_ex.get("hanzi", "")
        ex_fr = reused_ex.get("french", "")
        body = ex_hz.rstrip("。？！，、；：.,?!")
        punct = ex_hz[len(body) :] or "。"
        same_pinyin = norm_tonal_pinyin(prev_word.get("pinyin", "")) == norm_tonal_pinyin(pinyin_val)
        same_french = prev_word.get("french", "") == french
        bad_prev_french = bool(SURNAME_RE.search(prev_word.get("french", "")))
        bad_ex_french = bool(VULGAR_OR_SLANG_RE.search(ex_fr)) or ("terme hsk" in ex_fr.lower())
        if (
            same_pinyin
            and same_french
            and not bad_prev_french
            and not bad_ex_french
            and isinstance(toks, list)
            and len(toks) >= 3
            and len(toks) == len(set(toks))
            and hanzi in toks
            and "".join(toks) == body
            and "，" not in body
        ):
            updated_ex = {
                "hanzi": ex_hz,
                "pinyin": tokens_to_pinyin_sentence(toks, punct, word_to_pinyin),
                "french": ex_fr,
                "tokens": toks,
            }
            w_id = f"hsk{level}_{word_num:0{id_width}d}"
            return (
                {
                    "id": w_id,
                    "lessonId": lesson_id,
                    "hanzi": hanzi,
                    "pinyin": pinyin_val,
                    "french": french,
                    "example": updated_ex,
                },
                False,
            )

    if hanzi == "屁股":
        hc_toks = ["小", "孩子", "摔", "到", "了", "屁股"]
        return (
            {
                "id": f"hsk{level}_{word_num:0{id_width}d}",
                "lessonId": lesson_id,
                "hanzi": hanzi,
                "pinyin": pinyin_val,
                "french": french,
                "example": {
                    "hanzi": "".join(hc_toks) + "。",
                    "pinyin": tokens_to_pinyin_sentence(hc_toks, "。", word_to_pinyin),
                    "french": "Le petit enfant est tombé sur les fesses.",
                    "tokens": hc_toks,
                },
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

    ex_body = ex_hanzi.rstrip("。？！")
    ex_punct = ex_hanzi[len(ex_body) :] or "。"
    ex_pinyin = tokens_to_pinyin_sentence(ex_tokens, ex_punct, word_to_pinyin)
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


def load_hanban_2012_levels(
    complete_by_hanzi: dict[str, dict[str, Any]],
) -> tuple[dict[str, int], dict[int, list[dict[str, Any]]], dict[str, dict[str, Any]]]:
    """
    Charge les 6 listes officielles Hanban HSK 2.0 (2012) sauvegardées dans tools/data/hsk2012_l1..l6.txt
    et associe chaque mot à son entrée complete.min.json (ou synthétise depuis le TSV pour les 51 absents).
    """
    word_level_map: dict[str, int] = {}
    entries_by_level: dict[int, list[dict[str, Any]]] = {lv: [] for lv in range(1, 7)}
    entry_by_hanzi: dict[str, dict[str, Any]] = {}

    for lv in range(1, 7):
        path = DATA_DIR / f"hsk2012_l{lv}.txt"
        if not path.exists():
            raise FileNotFoundError(f"Liste officielle Hanban 2012 introuvable : {path}")
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            clean = raw_line.lstrip("\ufeff").strip()
            if not clean or clean.startswith("#"):
                continue
            parts = clean.split("\t")
            hanzi = parts[0].strip()
            if not hanzi or hanzi in word_level_map:
                continue

            word_level_map[hanzi] = lv
            jieba.add_word(hanzi, freq=100_000)

            base_entry = complete_by_hanzi.get(hanzi)
            if base_entry is not None:
                entry = base_entry
            else:
                py_num = parts[2].strip() if len(parts) > 2 else ""
                py_tonal = parts[3].strip() if len(parts) > 3 else ""
                en_def = parts[4].strip() if len(parts) > 4 else ""
                entry = {
                    "s": hanzi,
                    "q": 50_000,
                    "p": ["i"] if len(hanzi) == 4 else ["v"] if en_def.startswith("to ") else [],
                    "f": [
                        {
                            "t": hanzi,
                            "i": {"y": py_tonal, "n": py_num},
                            "m": [x.strip() for x in en_def.split(";") if x.strip()],
                        }
                    ],
                }

            entries_by_level[lv].append(entry)
            entry_by_hanzi[hanzi] = entry

    return word_level_map, entries_by_level, entry_by_hanzi


def write_curated_catalog(
    level: int,
    curated_units: list[tuple[str, str, list[tuple[str, list[str]]]]],
    existing_version: int,
    official_entries: list[dict[str, Any]],
    entry_by_hanzi: dict[str, dict[str, Any]],
    resolved_meta: dict[str, tuple[str, str, str]],
    word_to_pinyin: dict[str, str],
    existing_words: dict[str, dict[str, Any]],
    token_sentences: dict[str, list[tuple[str, str, list[str]]]],
    word_level_map: dict[str, int],
) -> None:
    curated_words = [
        hanzi
        for _u_title, _u_desc, lessons in curated_units
        for _l_title, words_list in lessons
        for hanzi in words_list
    ]
    official_set = {e["s"] for e in official_entries}
    assert len(curated_words) == len(official_set), (
        f"HSK {level} curated count {len(curated_words)} != official {len(official_set)}"
    )
    assert len(set(curated_words)) == len(official_set), f"Doublon dans HSK{level}_CURATED_UNITS"
    assert set(curated_words) == official_set, (
        f"Écart HSK {level} curated vs officiel : "
        f"manquants={official_set - set(curated_words)}, "
        f"en trop={set(curated_words) - official_set}"
    )

    units_out: list[dict[str, Any]] = []
    words_out: list[dict[str, Any]] = []
    l_order = 1
    w_num = 1
    for u_idx, (u_title, u_desc, lesson_specs) in enumerate(curated_units, start=1):
        u_id = f"hsk{level}_u{u_idx}"
        u_lessons: list[dict[str, Any]] = []
        for l_title, lesson_hanzi_list in lesson_specs:
            l_id = f"hsk{level}_u{u_idx}_l{l_order}"
            u_lessons.append({
                "id": l_id,
                "orderIndex": l_order,
                "title": l_title,
            })
            l_order += 1
            for hz in lesson_hanzi_list:
                word_dict, _tat_used = build_word_dict(
                    level=level,
                    word_num=w_num,
                    id_width=3,
                    lesson_id=l_id,
                    entry=entry_by_hanzi[hz],
                    resolved_meta=resolved_meta,
                    word_to_pinyin=word_to_pinyin,
                    existing_words=existing_words,
                    token_sentences=token_sentences,
                    word_level_map=word_level_map,
                )
                words_out.append(word_dict)
                w_num += 1
        units_out.append({
            "id": u_id,
            "orderIndex": u_idx,
            "title": u_title,
            "description": u_desc,
            "lessons": u_lessons,
        })

    catalog_obj = {
        "hskLevel": level,
        "catalogVersion": existing_version + 1,
        "units": units_out,
        "words": words_out,
    }
    (CATALOG_DIR / f"hsk{level}.json").write_text(
        json.dumps(catalog_obj, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"HSK {level} (v{catalog_obj['catalogVersion']}) : "
        f"{len(units_out)} unités, {l_order - 1} leçons, {len(words_out)} mots"
    )


def main() -> int:
    hsk_path = download_cached(HSK_URL, "complete.min.json")
    cfdict_path = download_cached(CFDICT_URL, "cfdict.u8")
    cmn_path = download_cached(TATOEBA_CMN_URL, "cmn_sentences.tsv.bz2")
    fra_path = download_cached(TATOEBA_FRA_URL, "fra_sentences.tsv.bz2")
    links_path = download_cached(TATOEBA_LINKS_URL, "links.tar.bz2")

    hsk_entries: list[dict[str, Any]] = json.loads(hsk_path.read_text(encoding="utf-8"))
    complete_by_hanzi: dict[str, dict[str, Any]] = {}
    for entry in hsk_entries:
        complete_by_hanzi.setdefault(entry["s"], entry)

    cfdict_by_pair, cfdict_toneless = load_cfdict(cfdict_path)
    tatoeba_pairs = load_tatoeba_pairs(cmn_path, fra_path, links_path)

    # Capturer uniquement les versions et anciennes entrées (pour réutiliser les phrases déjà conformes)
    existing_versions: dict[int, int] = {}
    existing_words: dict[str, dict[str, Any]] = {}
    for lv in range(1, 7):
        cat_path = CATALOG_DIR / f"hsk{lv}.json"
        if cat_path.exists():
            cat_data = json.loads(cat_path.read_text(encoding="utf-8"))
            existing_versions[lv] = int(cat_data.get("catalogVersion", 1))
            for w in cat_data.get("words", []):
                existing_words.setdefault(w["hanzi"], w)
        else:
            existing_versions[lv] = 1

    # Charger la liste officielle Hanban HSK 2.0 (2012) stricte (4 995 mots uniques)
    word_level_map, official_entries_by_level, official_entry_by_hanzi = load_hanban_2012_levels(
        complete_by_hanzi
    )

    # Passe 1 : résoudre les pinyins et traductions autoritaires pour les 4 995 mots officiels
    missing_french: list[tuple[int, str, str]] = []
    resolved_meta: dict[str, tuple[str, str, str]] = {}
    word_to_pinyin: dict[str, str] = {
        "小明": "Xiǎomíng",
        "小王": "Xiǎowáng",
        "小李": "Xiǎolǐ",
        "上海": "Shànghǎi",
        "法国": "Fǎguó",
        "巴黎": "Bālí",
        "天": "tiān",
    }
    for lv in range(1, 7):
        for entry in official_entries_by_level[lv]:
            hz = entry["s"]
            meta = resolve_word_metadata(lv, entry, cfdict_by_pair, cfdict_toneless, missing_french)
            resolved_meta[hz] = meta
            word_to_pinyin[hz] = meta[0]

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

    # --- HSK 1 (150) & HSK 2 (151) : progressions curatées ---
    write_curated_catalog(
        level=1,
        curated_units=HSK1_CURATED_UNITS,
        existing_version=existing_versions[1],
        official_entries=official_entries_by_level[1],
        entry_by_hanzi=official_entry_by_hanzi,
        resolved_meta=resolved_meta,
        word_to_pinyin=word_to_pinyin,
        existing_words=existing_words,
        token_sentences=token_sentences,
        word_level_map=word_level_map,
    )
    write_curated_catalog(
        level=2,
        curated_units=HSK2_CURATED_UNITS,
        existing_version=existing_versions[2],
        official_entries=official_entries_by_level[2],
        entry_by_hanzi=official_entry_by_hanzi,
        resolved_meta=resolved_meta,
        word_to_pinyin=word_to_pinyin,
        existing_words=existing_words,
        token_sentences=token_sentences,
        word_level_map=word_level_map,
    )

    # --- HSK 3 à HSK 6 : 100 % officiel Hanban 2012 avec tri incrémental et IDs propres ---
    for level in range(3, 7):
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
                        resolved_meta=resolved_meta,
                        word_to_pinyin=word_to_pinyin,
                        existing_words=existing_words,
                        token_sentences=token_sentences,
                        word_level_map=word_level_map,
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
