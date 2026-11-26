#!/usr/bin/env python3
"""
Generates HSK 1 to HSK 6 catalogs (strict official Hanban HSK 2.0 2012 wordlists) in
app/src/main/assets/catalog/hsk{1..6}.json from:
  - tools/data/hsk2012_l1..l6.txt (4,995 unique HSK 2.0 words)
  - complete-hsk-vocabulary (English CC-CEDICT glosses + frequency + POS + pinyin)
  - Tatoeba (open-source Chinese-English sentence pairs: cmn-eng)
  - jieba + pypinyin (token segmentation and Unicode tone-marked pinyin)

Runs offline on PC; the Android app remains 100% offline.
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
TATOEBA_CMN_URL = "https://downloads.tatoeba.org/exports/per_language/cmn/cmn_sentences.tsv.bz2"
TATOEBA_ENG_URL = "https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2"
TATOEBA_LINKS_URL = "https://downloads.tatoeba.org/exports/links.tar.bz2"

TONE_MARKS_RE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
BAD_SENTENCE_CHARS_RE = re.compile(
    r"[A-Za-z0-9，、；：（）()\[\]「」『』《》“”\"'…—·,.;:/\\@#&*+=<>|~`]"
)
CJK_ONLY_RE = re.compile(r"^[\u4e00-\u9fff]+$")
TRAILING_PUNCT = "。？！"
NEUTRAL_TONE_PARTICLES = {"ma", "ne", "de", "le", "ba", "zhe", "guo", "a", "ya", "wa", "na", "la", "o", "en"}

LESSON_SIZES = {1: 5, 2: 5, 3: 5, 4: 8, 5: 10, 6: 10}

# Curated progression for all 150 official Hanban 2012 HSK 1 words (6 units x 5 quizzes x 5 words)
HSK1_CURATED_UNITS: list[tuple[str, str, list[tuple[str, list[str]]]]] = [
    (
        "Unit 1 — Greetings, Pronouns & Basics",
        "First exchanges to introduce yourself and ask simple questions.",
        [
            ("Quiz 1 — Greetings & Politeness", ["你", "好", "谢谢", "再见", "喂"]),
            ("Quiz 2 — Apologies & Courtesy", ["请", "对不起", "没关系", "不客气", "高兴"]),
            ("Quiz 3 — Pronouns & To Be", ["我", "他", "她", "我们", "是"]),
            ("Quiz 4 — Negation & Questions", ["不", "没有", "吗", "呢", "谁"]),
            ("Quiz 5 — Identity & Demonstratives", ["叫", "什么", "名字", "这", "那"]),
        ],
    ),
    (
        "Unit 2 — Family, People & Food",
        "Talking about family, people around you, and everyday food.",
        [
            ("Quiz 6 — Family", ["家", "爸爸", "妈妈", "儿子", "女儿"]),
            ("Quiz 7 — People & Introductions", ["人", "朋友", "先生", "小姐", "同学"]),
            ("Quiz 8 — Likes & Drinks", ["爱", "喜欢", "吃", "喝", "水"]),
            ("Quiz 9 — Meals & Fruit", ["茶", "米饭", "菜", "水果", "苹果"]),
            ("Quiz 10 — Animals & Possessives", ["猫", "狗", "的", "很", "太"]),
        ],
    ),
    (
        "Unit 3 — Numbers, Dates & Time",
        "Counting, telling the time, dates, and age.",
        [
            ("Quiz 11 — Numbers 1 to 5", ["一", "二", "三", "四", "五"]),
            ("Quiz 12 — Numbers 6 to 10", ["六", "七", "八", "九", "十"]),
            ("Quiz 13 — Quantities & Age", ["几", "多少", "岁", "个", "号"]),
            ("Quiz 14 — Dates & Calendar", ["年", "月", "星期", "今天", "明天"]),
            ("Quiz 15 — Time & Moments", ["昨天", "现在", "点", "分钟", "一点儿"]),
        ],
    ),
    (
        "Unit 4 — Places, Directions & Home",
        "Getting around and describing everyday spaces.",
        [
            ("Quiz 16 — Parts of the Day", ["上午", "中午", "下午", "时候", "了"]),
            ("Quiz 17 — Countries & Public Places", ["中国", "北京", "饭店", "商店", "医院"]),
            ("Quiz 18 — Transport & Housing", ["学校", "飞机", "出租车", "电影", "住"]),
            ("Quiz 19 — Spatial Words", ["哪", "哪儿", "里", "前面", "后面"]),
            ("Quiz 20 — Furniture & Objects", ["上", "下", "桌子", "椅子", "杯子"]),
        ],
    ),
    (
        "Unit 5 — Actions, Shopping & Weather",
        "Expressing daily actions, shopping, and talking about the weather.",
        [
            ("Quiz 21 — Movement & Looking", ["去", "来", "回", "坐", "看"]),
            ("Quiz 22 — Work & Study", ["工作", "学习", "老师", "学生", "医生"]),
            ("Quiz 23 — Senses & Language", ["看见", "听", "说", "读", "写"]),
            ("Quiz 24 — Books, Sleep & Calls", ["字", "书", "睡觉", "打电话", "东西"]),
            ("Quiz 25 — Shopping, Money & Weather", ["买", "钱", "块", "天气", "下雨"]),
        ],
    ),
    (
        "Unit 6 — Qualities, Modals & Connectors",
        "Describing things, using modal verbs, and linking ideas.",
        [
            ("Quiz 26 — Everyday Items & Measure Words", ["衣服", "电脑", "电视", "本", "些"]),
            ("Quiz 27 — Common Adjectives", ["大", "小", "多", "少", "漂亮"]),
            ("Quiz 28 — Feelings & Core Verbs", ["冷", "热", "做", "开", "有"]),
            ("Quiz 29 — Ability & Knowing", ["在", "会", "能", "想", "认识"]),
            ("Quiz 30 — Language & Connectors", ["汉语", "和", "都", "怎么", "怎么样"]),
        ],
    ),
]

# Curated progression for all 151 official Hanban 2012 HSK 2 words (concrete -> abstract)
HSK2_CURATED_UNITS: list[tuple[str, str, list[tuple[str, list[str]]]]] = [
    (
        "Unit 1 — Everyday Essentials & People",
        "People, essential verbs, and high-frequency adverbs.",
        [
            ("Quiz 1 — Politeness & People", ["您", "大家", "男", "女", "孩子"]),
            ("Quiz 2 — Essential Verbs", ["要", "可以", "知道", "觉得", "希望"]),
            ("Quiz 3 — Simple Adverbs", ["也", "真", "非常", "最", "两"]),
            ("Quiz 4 — Extended Family", ["哥哥", "姐姐", "弟弟", "妹妹", "丈夫"]),
            ("Quiz 5 — Introductions & Identity", ["妻子", "姓", "介绍", "它", "快乐"]),
        ],
    ),
    (
        "Unit 2 — Time, Numbers & Location",
        "Numbers, durations, calendar words, and spatial orientation.",
        [
            ("Quiz 6 — Numbers & Order", ["零", "百", "千", "第一", "次"]),
            ("Quiz 7 — Calendar & Time of Day", ["日", "去年", "生日", "早上", "晚上", "时间"]),
            ("Quiz 8 — Duration & Pace", ["小时", "每", "开始", "准备", "完"]),
            ("Quiz 9 — Places & Directions", ["旁边", "外", "左边", "右边", "往"]),
            ("Quiz 10 — Distance & Position", ["近", "远", "离", "到", "进"]),
        ],
    ),
    (
        "Unit 3 — Food, Objects & Shopping",
        "Drinks, meals, clothing, daily items, and colors.",
        [
            ("Quiz 11 — Drinks & Meals", ["咖啡", "牛奶", "鸡蛋", "面条", "鱼"]),
            ("Quiz 12 — Dishes & Dining Out", ["羊肉", "西瓜", "好吃", "服务员", "贵"]),
            ("Quiz 13 — Prices & Clothing", ["便宜", "卖", "件", "穿", "洗"]),
            ("Quiz 14 — Everyday Items", ["手机", "手表", "铅笔", "报纸", "新"]),
            ("Quiz 15 — Colors & Appearance", ["颜色", "红", "白", "黑", "长"]),
        ],
    ),
    (
        "Unit 4 — Places, Travel & Health",
        "Buildings, getting around, weather, body, and health.",
        [
            ("Quiz 16 — Home & Buildings", ["门", "房间", "教室", "公司", "宾馆"]),
            ("Quiz 17 — Transit & Trips", ["火车站", "机场", "公共汽车", "路", "票"]),
            ("Quiz 18 — Travel & Actions", ["旅游", "走", "出", "送", "找"]),
            ("Quiz 19 — Weather & Physical Traits", ["晴", "阴", "雪", "高", "眼睛"]),
            ("Quiz 20 — Body, Health & Rest", ["身体", "生病", "药", "累", "休息"]),
        ],
    ),
    (
        "Unit 5 — Routines, Study & Communication",
        "Daily routines, sports, hobbies, school, and conversation.",
        [
            ("Quiz 21 — Routine & Work", ["起床", "上班", "忙", "笑", "玩"]),
            ("Quiz 22 — Sports & Leisure", ["运动", "跑步", "游泳", "踢足球", "打篮球"]),
            ("Quiz 23 — Music & Study", ["唱歌", "跳舞", "课", "考试", "题"]),
            ("Quiz 24 — Communication", ["说话", "问", "问题", "告诉", "懂"]),
            ("Quiz 25 — Help & Meaning", ["帮助", "意思", "事情", "对", "等"]),
        ],
    ),
    (
        "Unit 6 — Grammar, Nuance & Connectors",
        "Aspect particles, comparisons, nuances, and logical connectors.",
        [
            ("Quiz 26 — Time & Verb Aspect", ["正在", "已经", "过", "着", "一下"]),
            ("Quiz 27 — Adverbs & Speed", ["还", "再", "一起", "快", "慢"]),
            ("Quiz 28 — Structures & Comparison", ["就", "从", "比", "得", "错"]),
            ("Quiz 29 — Interaction & Modality", ["给", "让", "别", "吧", "可能"]),
            ("Quiz 30 — Cause & Logic", ["为什么", "因为", "所以", "虽然", "但是"]),
        ],
    ),
]

THEMATIC_UNITS = {
    3: [
        ("Reasoning & Exchanges", "Opinions, complex questions, and everyday communication."),
        ("Actions & Habits", "Decisions, routines, and practical daily life."),
        ("Places & Nature", "City life, environment, seasons, and travel."),
        ("Relationships & Feelings", "Behaviors, emotions, and interpersonal ties."),
        ("Descriptions & Details", "Rich adjectives, degrees, and qualities."),
        ("Connectors & Grammar", "Logical links and key sentence structures."),
    ],
    4: [
        ("Studies & Career", "Skills, goals, and professional life."),
        ("Society & Relationships", "Social interactions, rules, and mutual support."),
        ("Culture, Media & Knowledge", "Education, news, and reading."),
        ("Travel, Nature & Daily Life", "Journeys, health, and the world around you."),
        ("Attitudes & Character", "Personality traits, feelings, and judgments."),
        ("Argumentation & Connectors", "Logical nuances and structured expression."),
    ],
    5: [
        ("Analysis & Thought", "Reasoning, facts, and structured arguments."),
        ("Work, Economy & Management", "Organization, resources, and the workplace."),
        ("Society & Public Life", "Institutions, rules, and social trends."),
        ("Science, Media & Tech", "Research, technology, and information."),
        ("Culture, Arts & Heritage", "Traditions, literature, and creativity."),
        ("Nature, Health & Living", "Environment, energy, and well-being."),
        ("Psychology & Behavior", "Personality, emotions, and attitudes."),
        ("Formal Style & Idioms", "Literary expressions and common chengyu."),
    ],
    6: [
        ("Discourse & Methodology", "Analytical verbs and academic vocabulary."),
        ("Law, Governance & Institutions", "Politics, legislation, and public administration."),
        ("Economy, Strategy & Growth", "Markets, production, and innovation."),
        ("Society, Ethics & Values", "Social norms, duties, and moral values."),
        ("Culture, History & Philosophy", "Heritage, thought, and classical arts."),
        ("Science, Nature & Technology", "Natural phenomena, biology, and engineering."),
        ("Psychology & Reflection", "Emotional nuance, character, and judgment."),
        ("Literary Expressions & Chengyu", "Formal adjectives and four-character idioms."),
    ],
}

ENGLISH_FALLBACK: dict[str, str] = {
    "一下": "For a moment / A bit",
    "一点儿": "A little bit",
    "一块儿": "Together",
    "一起": "Together",
    "一直": "Always / Straight ahead",
    "一般": "Ordinary / In general",
    "一边": "On one hand / At the same time",
    "一会儿": "A little while",
    "一样": "Same / Alike",
    "非常": "Very / Extremely",
    "大家": "Everyone / Everybody",
    "别的": "Other / Else",
    "干什么": "What are you doing / Why",
    "一会儿见": "See you soon",
    "没问题": "No problem",
    "放暑假": "To be on summer vacation",
    "系领带": "To wear a tie",
    "烟花爆竹": "Fireworks and firecrackers",
}

SURNAME_RE = re.compile(r"\bsurname\b|\bfamily name\b|\bclan name\b", re.IGNORECASE)
RADICAL_OR_TECH_RE = re.compile(
    r"(\(slang\)|\(vulgar\)|\(dialect\)|\(taboo\)|\(derog\.\)|\(euphem\.\)|\(jocular\)|"
    r"\bKangxi radical\b|\bvariant of\b|\bold variant\b|\berhua variant\b|"
    r"\barchaic variant\b|\bJapanese surname\b|\bKorean surname\b|"
    r"\bsee also\b|\bTaiwan pr\.|\balso pr\.|\babbr\. for\b|\babbr\. to\b)",
    re.IGNORECASE,
)
VULGAR_OR_SLANG_RE = re.compile(
    r"(\(slang\)|\(vulgar\)|\(dialect\)|\(taboo\)|\(derog\.\)|"
    r"\bfuck\w*\b|\bshit\w*\b|\bbitch\w*\b|\bcunt\w*\b|\bwhore\w*\b|"
    r"\bporn\w*\b|\bpenis\b|\bvagina\b|\bbastard\w*\b|\basshole\w*\b|"
    r"\bdickhead\w*\b|\bcock\b|\bpiss\w*\b|\bcrap\b|\bboobs\b|\btits\b|\bscrotum\b)",
    re.IGNORECASE,
)

# Only genuine proper nouns in HSK 1..6 allowed to start with an uppercase letter in pinyin
ALLOWED_PROPER_NOUNS = {
    "中国", "北京", "汉语", "中文", "黄河", "亚洲", "京剧", "长城", "长江",
    "元旦", "华裔", "国庆节", "礼拜天", "除夕", "互联网", "元宵节", "华侨",
    "国务院", "摄氏度", "欧洲", "正月", "端午节", "重阳节",
}

# Authoritative HSK polyphone readings (lowercase numbered pinyin)
PREFERRED_PINYIN: dict[str, str] = {
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

# Short, natural English glosses for core HSK 1-3 words and sensitive polyphones
ENGLISH_OVERRIDES: dict[str, str] = {
    "你": "You",
    "好": "Good / Well",
    "谢谢": "Thank you",
    "再见": "Goodbye",
    "喂": "Hello (on the phone)",
    "请": "Please / To invite",
    "对不起": "Sorry / Excuse me",
    "没关系": "No problem / It's okay",
    "不客气": "You're welcome",
    "高兴": "Happy / Glad",
    "我": "I / Me",
    "他": "He / Him",
    "她": "She / Her",
    "我们": "We / Us",
    "是": "To be",
    "不": "No / Not",
    "没有": "Don't have / Did not",
    "吗": "Question particle",
    "呢": "Question particle (and you?)",
    "谁": "Who",
    "叫": "To be called / To call",
    "什么": "What",
    "名字": "Name",
    "这": "This",
    "那": "That",
    "家": "Home / Family",
    "爸爸": "Dad / Father",
    "妈妈": "Mom / Mother",
    "儿子": "Son",
    "女儿": "Daughter",
    "人": "Person / People",
    "朋友": "Friend",
    "先生": "Mister / Sir",
    "小姐": "Miss / Young lady",
    "同学": "Classmate",
    "爱": "To love",
    "喜欢": "To like",
    "吃": "To eat",
    "喝": "To drink",
    "水": "Water",
    "茶": "Tea",
    "米饭": "Cooked rice",
    "菜": "Dish / Vegetable",
    "水果": "Fruit",
    "苹果": "Apple",
    "猫": "Cat",
    "狗": "Dog",
    "的": "Possessive particle",
    "很": "Very",
    "太": "Too (excessively)",
    "一": "One",
    "二": "Two",
    "三": "Three",
    "四": "Four",
    "五": "Five",
    "六": "Six",
    "七": "Seven",
    "八": "Eight",
    "九": "Nine",
    "十": "Ten",
    "几": "How many (a few)",
    "多少": "How many / How much",
    "岁": "Years old (age)",
    "个": "General measure word",
    "号": "Number / Day of month",
    "年": "Year",
    "月": "Month / Moon",
    "星期": "Week",
    "今天": "Today",
    "明天": "Tomorrow",
    "昨天": "Yesterday",
    "现在": "Now",
    "点": "O'clock / Dot / A little",
    "分钟": "Minute",
    "一点儿": "A little bit",
    "上午": "Morning",
    "中午": "Noon",
    "下午": "Afternoon",
    "时候": "Time / Moment",
    "了": "Completed action particle",
    "中国": "China",
    "北京": "Beijing",
    "饭店": "Restaurant / Hotel",
    "商店": "Shop / Store",
    "医院": "Hospital",
    "学校": "School",
    "飞机": "Airplane",
    "出租车": "Taxi",
    "电影": "Movie",
    "住": "To live / To stay",
    "哪": "Which",
    "哪儿": "Where",
    "里": "Inside / In",
    "前面": "In front",
    "后面": "Behind",
    "上": "On / Above / To go up",
    "下": "Below / Under / To go down",
    "桌子": "Table / Desk",
    "椅子": "Chair",
    "杯子": "Cup / Glass",
    "去": "To go",
    "来": "To come",
    "回": "To return / Go back",
    "坐": "To sit / Take (transport)",
    "看": "To look / Watch / Read",
    "工作": "Work / To work",
    "学习": "To study / Learn",
    "老师": "Teacher",
    "学生": "Student",
    "医生": "Doctor",
    "看见": "To see",
    "听": "To listen / Hear",
    "说": "To speak / Say",
    "读": "To read",
    "写": "To write",
    "字": "Character / Word",
    "书": "Book",
    "睡觉": "To sleep",
    "打电话": "To make a phone call",
    "东西": "Thing / Object",
    "买": "To buy",
    "钱": "Money",
    "块": "Yuan (currency) / Piece",
    "天气": "Weather",
    "下雨": "To rain",
    "衣服": "Clothes",
    "电脑": "Computer",
    "电视": "Television",
    "本": "Measure word for books",
    "些": "Some / A few",
    "大": "Big / Large",
    "小": "Small / Little",
    "多": "Many / Much",
    "少": "Few / Little",
    "漂亮": "Pretty / Beautiful",
    "冷": "Cold",
    "热": "Hot",
    "做": "To do / Make",
    "开": "To open / Drive / Turn on",
    "有": "To have / There is",
    "在": "At / In / Currently",
    "会": "Can (learned skill) / Will",
    "能": "Can / Be able to",
    "想": "To want / Think / Miss",
    "认识": "To know (a person) / Recognize",
    "汉语": "Chinese language",
    "和": "And / With",
    "都": "All / Both",
    "怎么": "How / Why",
    "怎么样": "How about / How is it",
    # Key HSK 2-6 overrides & polyphones
    "姓": "Surname / Family name",
    "还": "Still / Also / Yet",
    "长": "Long",
    "晴": "Sunny / Clear (weather)",
    "只": "Only / Just",
    "没": "Not have / Did not",
    "得": "Degree particle",
    "地": "Adverbial particle",
    "着": "Continuous aspect particle",
    "行": "Okay / All right / To walk",
    "为": "For / Because of",
    "重": "Heavy / Important",
    "干": "To do / Work",
    "发": "To send / Emit",
    "分": "Minute / Point / Divide",
    "教": "To teach",
    "过": "Past experience particle",
    "假": "Fake / False",
    "种": "Kind / Type / Seed",
    "倒": "To fall / Collapse",
    "数": "Number / Figure",
    "空": "Empty / Sky",
    "要": "To want / Must / Will",
    "应": "Should / To answer",
    "相": "Mutually / Appearance",
    "传": "To pass on / Spread",
    "便宜": "Cheap / Inexpensive",
    "告诉": "To tell / Inform",
    "妻子": "Wife",
    "地方": "Place / Location",
    "故事": "Story",
    "大夫": "Doctor / Physician",
    "好处": "Benefit / Advantage",
    "结果": "Result / Outcome",
    "弄": "To do / Handle",
    "胖": "Fat / Plump",
    "角": "Corner / Angle / Jiao",
    "张": "To open / Measure word for flat objects",
    "比": "To compare / Than",
    "别": "Don't (imperative) / Other",
    "让": "To let / Allow / Make",
    "穿": "To wear / Put on",
    "累": "Tired",
    "鸡蛋": "Egg",
    "踢足球": "To play soccer",
    "男孩": "Boy",
    "男": "Male / Man",
    "女": "Female / Woman",
    "对": "Correct / Right / Toward",
    "等": "To wait / Etc.",
    "往": "Toward",
    "面条": "Noodles",
    "铅笔": "Pencil",
    "宾馆": "Hotel",
    "刷牙": "To brush teeth",
    "刮风": "To be windy",
    "只有": "Only if",
    "感兴趣": "To be interested in",
    "电子邮件": "Email",
    "黄河": "Yellow River",
    "鸟": "Bird",
    "裤子": "Pants / Trousers",
    "春": "Spring (season)",
    "玻璃": "Glass (material)",
    "屁股": "Bottom / Rear",
    "犬": "Dog",
    "得罪": "To offend",
    "正当": "Proper / Legitimate",
    "幢": "Measure word for buildings",
    "孙子": "Grandson",
    "奶奶": "Paternal grandmother",
    "脏": "Dirty",
    "弹": "To play (a plucked instrument)",
    "趟": "Measure word for trips",
    "重点": "Key point / Focus",
    "提": "To carry / Mention / Raise",
    "吓": "To scare / Frighten",
    "盖": "To cover / Lid",
    "正": "Straight / Right / In the middle of",
    "背": "Back / Carry on one's back",
    "大方": "Generous / Natural",
    "地道": "Authentic / Genuine",
    "扁": "Flat",
    "番": "Time / Turn",
    "束": "Bundle / Bouquet / To tie",
    "翼": "Wing",
    "吧": "Suggestion particle",
    "把": "Object marker (ba)",
    "被": "Passive marker (bei)",
    "更": "Even more",
    "最": "Most",
    "就": "Then / Right away",
    "才": "Only then / Just",
    "再": "Again (future)",
    "又": "Again (past)",
    "真": "Really / True",
    "正在": "In the middle of (doing)",
    "为什么": "Why",
    "您": "You (polite)",
    "它": "It",
    "两": "Two (before measure words)",
    "哥哥": "Older brother",
    "姐姐": "Older sister",
    "弟弟": "Younger brother",
    "妹妹": "Younger sister",
    "丈夫": "Husband",
    "介绍": "To introduce",
    "快乐": "Happy / Joyful",
    "零": "Zero",
    "百": "Hundred",
    "千": "Thousand",
    "第一": "First",
    "次": "Time (occurrence)",
    "日": "Day / Sun",
    "去年": "Last year",
    "生日": "Birthday",
    "早上": "Morning",
    "晚上": "Evening",
    "时间": "Time",
    "小时": "Hour",
    "每": "Every / Each",
    "开始": "To start / Begin",
    "准备": "To prepare",
    "完": "To finish / Complete",
    "旁边": "Beside / Next to",
    "外": "Outside",
    "左边": "Left side",
    "右边": "Right side",
    "近": "Near / Close",
    "远": "Far",
    "离": "Away from",
    "到": "To arrive",
    "进": "To enter",
    "咖啡": "Coffee",
    "牛奶": "Milk",
    "鱼": "Fish",
    "羊肉": "Lamb / Mutton",
    "西瓜": "Watermelon",
    "好吃": "Delicious",
    "服务员": "Waiter / Attendant",
    "贵": "Expensive",
    "卖": "To sell",
    "件": "Measure word for clothes",
    "洗": "To wash",
    "手机": "Mobile phone",
    "手表": "Wristwatch",
    "报纸": "Newspaper",
    "新": "New",
    "颜色": "Color",
    "红": "Red",
    "白": "White",
    "黑": "Black",
    "门": "Door",
    "房间": "Room",
    "教室": "Classroom",
    "公司": "Company / Office",
    "火车站": "Train station",
    "机场": "Airport",
    "公共汽车": "Bus",
    "路": "Road / Way",
    "票": "Ticket",
    "旅游": "To travel",
    "走": "To walk / Go",
    "出": "To go out",
    "送": "To give / Deliver / See off",
    "找": "To look for / Find",
    "阴": "Cloudy / Overcast",
    "雪": "Snow",
    "高": "Tall / High",
    "眼睛": "Eye",
    "身体": "Body / Health",
    "生病": "To get sick",
    "药": "Medicine",
    "休息": "To rest",
    "起床": "To get up",
    "上班": "To go to work",
    "忙": "Busy",
    "笑": "To smile / Laugh",
    "玩": "To play / Have fun",
    "运动": "Exercise / Sport",
    "跑步": "To run / Jog",
    "游泳": "To swim",
    "打篮球": "To play basketball",
    "唱歌": "To sing",
    "跳舞": "To dance",
    "课": "Class / Lesson",
    "考试": "Exam / Test",
    "题": "Question / Topic",
    "说话": "To speak / Talk",
    "问": "To ask",
    "问题": "Question / Problem",
    "懂": "To understand",
    "帮助": "To help",
    "意思": "Meaning / Idea",
    "事情": "Matter / Thing / Affair",
    "已经": "Already",
    "快": "Fast / Soon",
    "慢": "Slow",
    "从": "From",
    "错": "Wrong / Mistake",
    "可能": "Maybe / Possible",
    "因为": "Because",
    "所以": "Therefore / So",
    "虽然": "Although",
    "但是": "But / However",
    "可以": "Can / May",
    "知道": "To know",
    "觉得": "To feel / Think",
    "希望": "To hope",
    "孩子": "Child",
    "乙": "Second (in a sequence) / B",
    "幢": "Measure word for buildings",
    "啊": "Ah (interjection)",
    "哇": "Wow (interjection)",
    "哦": "Oh (interjection)",
    "啦": "Sentence-final particle",
    "嗯": "Uh-huh / Mhm",
    "呀": "Particle expressing surprise or doubt",
    "纪录": "Record / Minutes / Notes",
    "啰唆": "Long-winded / Wordy",
    "贤惠": "Virtuous and kindhearted",
    "做主": "To decide / Take charge",
}


def download_cached(url: str, filename: str) -> Path:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    target = CACHE_DIR / filename
    if target.exists() and target.stat().st_size > 0:
        return target
    print(f"Downloading: {url} -> {target.name} ...")
    with requests.get(url, stream=True, timeout=120) as resp:
        resp.raise_for_status()
        with target.open("wb") as f:
            for chunk in resp.iter_content(chunk_size=1024 * 256):
                if chunk:
                    f.write(chunk)
    return target


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


def clean_english_sense(raw: str, hanzi: str, strict: bool = True) -> str | None:
    s = raw.strip()
    if not s:
        return None
    if hanzi != "姓" and SURNAME_RE.search(s):
        return None
    if VULGAR_OR_SLANG_RE.search(s):
        return None
    if strict and RADICAL_OR_TECH_RE.search(s):
        return None
    if not strict and re.search(r"(\bKangxi radical\b|\bvariant of\b|\bold variant\b|\barchaic variant\b|\bJapanese surname\b)", s, re.IGNORECASE):
        return None
    if s.startswith(("CL:", "see ", "See ", "Taiwan pr.", "also pr.")):
        return None

    s = re.sub(r"\bCL:[^;/)]+", "", s)
    s = re.sub(r"\[[^\]]+\]", "", s)
    if not strict:
        s = re.sub(r"\((?:coll\.|fig\.|lit\.|dialect|archaic|onom\.|euphem\.|slang|tw)\)", "", s, flags=re.IGNORECASE)
    s = re.sub(
        r"\([^)]*\)",
        lambda m: ""
        if len(m.group(0)) > 28
        or SURNAME_RE.search(m.group(0))
        or VULGAR_OR_SLANG_RE.search(m.group(0))
        or (strict and RADICAL_OR_TECH_RE.search(m.group(0)))
        else m.group(0),
        s,
    )
    s = re.sub(r"\s{2,}", " ", s).strip(" ;,.-")
    if not s or len(s) < 2:
        return None
    if hanzi != "姓" and SURNAME_RE.search(s):
        return None
    if VULGAR_OR_SLANG_RE.search(s):
        return None
    return s


def clean_form_glosses(hanzi: str, meanings: list[str]) -> str | None:
    cleaned = [clean_english_sense(m, hanzi, strict=True) for m in meanings]
    cleaned = [c for c in cleaned if c]
    if not cleaned:
        cleaned = [clean_english_sense(m, hanzi, strict=False) for m in meanings]
        cleaned = [c for c in cleaned if c]
    if not cleaned:
        return None

    # Deduplicate preserving order
    unique: list[str] = []
    seen_low: set[str] = set()
    for s in cleaned:
        low = s.lower()
        if low not in seen_low:
            seen_low.add(low)
            unique.append(s)

    concise = " / ".join(unique[:2])
    if len(concise) > 72:
        first = unique[0]
        if len(first) > 72:
            concise = first.split(";")[0].split(",")[0].strip()[:72].rstrip(" ,;-")
        else:
            concise = first
    if not concise:
        return None
    return concise[0].upper() + concise[1:]


def select_best_form(entry: dict[str, Any]) -> tuple[str, str, list[str]]:
    """
    Selects the standard reading in `entry['f']` excluding surnames and obscure variants.
    Returns `(tonal_pinyin, normalized_numbered_pinyin, meanings_list)`.
    """
    hanzi = entry["s"]
    forms = entry.get("f", [])
    if not forms:
        syls = pypinyin_fn(hanzi, style=Style.TONE, heteronym=False)
        pyp_tonal = "".join(x[0] for x in syls)
        syls_num = pypinyin_fn(hanzi, style=Style.TONE3, neutral_tone_with_five=True, heteronym=False)
        pyp_num = norm_numbered_pinyin("".join(x[0] for x in syls_num))
        return pyp_tonal, pyp_num, []

    preferred = PREFERRED_PINYIN.get(hanzi)
    if preferred:
        for form in forms:
            num_py = norm_numbered_pinyin(form.get("i", {}).get("n", ""))
            tonal_py = form.get("i", {}).get("y", "").strip()
            if num_py == preferred and not any(c.isupper() for c in tonal_py):
                return ensure_tonal_word_pinyin(hanzi, tonal_py), num_py, form.get("m", [])

    is_proper = hanzi in ALLOWED_PROPER_NOUNS
    non_upper = [
        f
        for f in forms
        if not any(c.isupper() for c in f.get("i", {}).get("y", ""))
        and "surname" not in " ".join(f.get("m", [])).lower()
    ]
    pool = forms if is_proper else (non_upper or forms)

    non_variant = [
        f
        for f in pool
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
        return ensure_tonal_word_pinyin(hanzi, tonal), num_py, f0.get("m", [])

    pyp_num = norm_numbered_pinyin(
        "".join(x[0] for x in pypinyin_fn(hanzi, style=Style.TONE3, neutral_tone_with_five=True, heteronym=False))
    )
    pyp_toneless = re.sub(r"[1-5]", "", pyp_num)

    best_form = pool[0]
    best_score = -10_000
    for f in pool:
        tonal = f.get("i", {}).get("y", "").strip()
        num_py = norm_numbered_pinyin(f.get("i", {}).get("n", ""))
        meanings = f.get("m", [])
        m_low = " ".join(meanings).lower()
        score = 0
        if num_py == pyp_num:
            score += 100
        elif re.sub(r"[1-5]", "", num_py) == pyp_toneless:
            score += 35
        if clean_form_glosses(hanzi, meanings):
            score += 40
        if any(tag in m_low for tag in ("literary", "classical", "dialect", "onom.", "(tw)")):
            score -= 30
        if "(bound form)" in m_low:
            score -= 10
        score += min(5, len(meanings))
        if score > best_score:
            best_score = score
            best_form = f

    tonal = best_form.get("i", {}).get("y", "").strip()
    if not is_proper:
        tonal = tonal.lower()
    num_py = norm_numbered_pinyin(best_form.get("i", {}).get("n", ""))
    return ensure_tonal_word_pinyin(hanzi, tonal), num_py, best_form.get("m", [])


def tokens_to_pinyin_sentence(
    tokens: list[str],
    punct: str,
    word_to_pinyin: dict[str, str],
) -> str:
    """Renders sentence pinyin token by token using catalog word readings."""
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
    eng_bz2: Path,
    links_tar_bz2: Path,
) -> list[tuple[str, str]]:
    cached_tsv = CACHE_DIR / "tatoeba_cmn_eng.tsv"
    if cached_tsv.exists() and cached_tsv.stat().st_size > 0:
        pairs: list[tuple[str, str]] = []
        with cached_tsv.open("r", encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) == 2 and not VULGAR_OR_SLANG_RE.search(parts[1]):
                    pairs.append((parts[0], parts[1]))
        return pairs

    print("Extracting Tatoeba Chinese-English sentence pairs...")
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

    eng_map: dict[str, str] = {}
    with bz2.open(eng_bz2, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            sid, lang, text = parts[0], parts[1], parts[2].strip()
            if lang == "eng" and 4 <= len(text) <= 120 and not VULGAR_OR_SLANG_RE.search(text):
                eng_map[sid] = text

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
            if src_id in cmn_map and tgt_id in eng_map:
                if src_id not in pairs_map or len(eng_map[tgt_id]) < len(pairs_map[src_id][1]):
                    pairs_map[src_id] = (cmn_map[src_id], eng_map[tgt_id])

    pairs = list(pairs_map.values())
    with cached_tsv.open("w", encoding="utf-8") as out:
        for cmn_text, eng_text in pairs:
            out.write(f"{cmn_text}\t{eng_text}\n")
    print(f"Tatoeba: {len(pairs)} clean Chinese-English pairs extracted.")
    return pairs


def ensure_tonal_word_pinyin(hanzi: str, candidate_pinyin: str) -> str:
    p = candidate_pinyin.strip()
    if p and (TONE_MARKS_RE.search(p) or p.lower() in NEUTRAL_TONE_PARTICLES):
        return p
    syls = pypinyin_fn(hanzi, style=Style.TONE, heteronym=False)
    generated = "".join(item[0] for item in syls).strip()
    return generated or p


VERB_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["我们", "正在", "{w}"], "。", "We are currently practicing: {en}."),
    (["请", "认真", "{w}"], "。", "Please carefully practice: {en}."),
    (["他", "每天", "都", "{w}"], "。", "Every day he practices: {en}."),
    (["我", "想", "学会", "{w}"], "。", "I want to learn how to: {en}."),
    (["大家", "一起", "{w}", "吧"], "！", "Let's all do this together: {en}!"),
    (["明天", "再", "{w}", "好", "吗"], "？", "Shall we continue tomorrow with: {en}?"),
    (["她", "经常", "这样", "{w}"], "。", "She often acts this way: {en}."),
]

NOUN_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["这个", "{w}", "很", "重要"], "。", "This concept \"{en}\" is very important."),
    (["我们", "讨论", "过", "{w}"], "。", "We have already discussed: {en}."),
    (["大家", "都", "很", "了解", "{w}"], "。", "Everyone knows a lot about: {en}."),
    (["请", "记住", "这个", "{w}"], "。", "Please remember this term: {en}."),
    (["书", "上", "提到", "了", "{w}"], "。", "The book mentions: {en}."),
    (["这里", "需要", "新的", "{w}"], "。", "Here we need something new: {en}."),
]

ADJ_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["今天", "的", "情况", "很", "{w}"], "。", "Today's situation is very: {en}."),
    (["这个", "地方", "非常", "{w}"], "。", "This place is particularly: {en}."),
    (["他", "觉得", "这样", "更", "{w}"], "。", "He feels this way is even more: {en}."),
    (["大家", "的", "态度", "很", "{w}"], "。", "Everyone's attitude is very: {en}."),
    (["这里", "的", "环境", "真", "{w}"], "！", "The environment here is really: {en}!"),
]

ADV_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["大家", "{w}", "完成", "任务"], "。", "Everyone completes the task ({en})."),
    (["她", "{w}", "说出", "了", "想法"], "。", "She expressed her idea ({en})."),
    (["我们", "{w}", "继续", "前进"], "。", "Let us keep moving forward ({en})."),
    (["请", "{w}", "准备", "一下"], "。", "Please get ready ({en})."),
]

IDIOM_TEMPLATES: list[tuple[list[str], str, str]] = [
    (["大家", "做事", "要", "{w}"], "。", "In what we do, remember: {en}."),
    (["老师", "常常", "强调", "{w}"], "。", "The teacher often emphasizes: {en}."),
    (["我们", "应该", "坚持", "{w}"], "。", "We should uphold: {en}."),
]


def short_english_gloss(english: str) -> str:
    head = english.split("/")[0].strip()
    if not head:
        return english
    return head[0].lower() + head[1:]


def generate_natural_example(
    hanzi: str,
    english: str,
    pos_tags: list[str],
    seed_idx: int,
) -> tuple[str, str, list[str]]:
    gloss = short_english_gloss(english)
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
        tpl_tokens, punct, en_tpl = pool[(start + offset) % len(pool)]
        tokens = [hanzi if t == "{w}" else t for t in tpl_tokens]
        if len(tokens) >= 3 and len(tokens) == len(set(tokens)) and hanzi in tokens:
            zh = "".join(tokens) + punct
            en = en_tpl.format(en=gloss)
            return zh, en, tokens

    for tokens, punct in [
        (["今天", "学习", hanzi, "这个", "词"], "。"),
        (["请", "多", "练习", hanzi], "。"),
        (["我们", "认识", hanzi], "。"),
    ]:
        if len(tokens) == len(set(tokens)) and hanzi in tokens:
            return "".join(tokens) + punct, f"Practice this word today: {gloss}.", tokens
    raise RuntimeError(f"Unable to generate example for {hanzi}")


def resolve_word_metadata(
    level: int,
    entry: dict[str, Any],
    missing_english: list[tuple[int, str, list[str]]],
) -> tuple[str, str, str]:
    """Resolves authoritative (tonal_pinyin, norm_num_py, english) without surnames or slang."""
    hanzi = entry["s"]
    pinyin_val, norm_num_py, form_meanings = select_best_form(entry)

    english = ENGLISH_OVERRIDES.get(hanzi) or ENGLISH_FALLBACK.get(hanzi)
    if not english:
        english = clean_form_glosses(hanzi, form_meanings)
    if not english:
        for alt_form in entry.get("f", []):
            alt_m = clean_form_glosses(hanzi, alt_form.get("m", []))
            if alt_m:
                english = alt_m
                break
    if not english:
        missing_english.append((level, hanzi, form_meanings))
        english = f"HSK {level} term ({hanzi})"
    return pinyin_val, norm_num_py, english


def build_word_dict(
    level: int,
    word_num: int,
    id_width: int,
    lesson_id: str,
    entry: dict[str, Any],
    resolved_meta: dict[str, tuple[str, str, str]],
    word_to_pinyin: dict[str, str],
    token_sentences: dict[str, list[tuple[str, str, list[str]]]],
    word_level_map: dict[str, int],
) -> tuple[dict[str, Any], bool]:
    hanzi = entry["s"]
    pinyin_val, _norm_num_py, english = resolved_meta[hanzi]

    best_tatoeba = None
    best_score = -10_000.0
    for cmn_text, eng_text, tokens in token_sentences.get(hanzi, []):
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
            best_tatoeba = (cmn_text, eng_text, tokens)

    tatoeba_used = False
    if best_tatoeba is not None and best_score >= 30.0:
        ex_hanzi, ex_english, ex_tokens = best_tatoeba
        tatoeba_used = True
    else:
        ex_hanzi, ex_english, ex_tokens = generate_natural_example(
            hanzi=hanzi,
            english=english,
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
            "english": english,
            "example": {
                "hanzi": ex_hanzi,
                "pinyin": ex_pinyin,
                "english": ex_english,
                "tokens": ex_tokens,
            },
        },
        tatoeba_used,
    )


def load_hanban_2012_levels(
    complete_by_hanzi: dict[str, dict[str, Any]],
) -> tuple[dict[str, int], dict[int, list[dict[str, Any]]], dict[str, dict[str, Any]]]:
    """Loads official Hanban HSK 2.0 (2012) wordlists from tools/data/hsk2012_l1..l6.txt."""
    word_level_map: dict[str, int] = {}
    entries_by_level: dict[int, list[dict[str, Any]]] = {lv: [] for lv in range(1, 7)}
    entry_by_hanzi: dict[str, dict[str, Any]] = {}

    for lv in range(1, 7):
        path = DATA_DIR / f"hsk2012_l{lv}.txt"
        if not path.exists():
            raise FileNotFoundError(f"Official Hanban 2012 list not found: {path}")
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
    assert len(set(curated_words)) == len(official_set), f"Duplicate in HSK{level}_CURATED_UNITS"
    assert set(curated_words) == official_set, (
        f"HSK {level} mismatch vs official: "
        f"missing={official_set - set(curated_words)}, "
        f"extra={set(curated_words) - official_set}"
    )

    units_out: list[dict[str, Any]] = []
    words_out: list[dict[str, Any]] = []
    l_order = 1
    w_num = 1
    tatoeba_hits = 0
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
                word_dict, tat_used = build_word_dict(
                    level=level,
                    word_num=w_num,
                    id_width=3,
                    lesson_id=l_id,
                    entry=entry_by_hanzi[hz],
                    resolved_meta=resolved_meta,
                    word_to_pinyin=word_to_pinyin,
                    token_sentences=token_sentences,
                    word_level_map=word_level_map,
                )
                if tat_used:
                    tatoeba_hits += 1
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
        f"HSK {level} (v{catalog_obj['catalogVersion']}): "
        f"{len(units_out)} units, {l_order - 1} quizzes, {len(words_out)} words, {tatoeba_hits} Tatoeba"
    )


def main() -> int:
    hsk_path = download_cached(HSK_URL, "complete.min.json")
    cmn_path = download_cached(TATOEBA_CMN_URL, "cmn_sentences.tsv.bz2")
    eng_path = download_cached(TATOEBA_ENG_URL, "eng_sentences.tsv.bz2")
    links_path = download_cached(TATOEBA_LINKS_URL, "links.tar.bz2")

    hsk_entries: list[dict[str, Any]] = json.loads(hsk_path.read_text(encoding="utf-8"))
    complete_by_hanzi: dict[str, dict[str, Any]] = {}
    for entry in hsk_entries:
        complete_by_hanzi.setdefault(entry["s"], entry)

    tatoeba_pairs = load_tatoeba_pairs(cmn_path, eng_path, links_path)

    existing_versions: dict[int, int] = {}
    for lv in range(1, 7):
        cat_path = CATALOG_DIR / f"hsk{lv}.json"
        if cat_path.exists():
            cat_data = json.loads(cat_path.read_text(encoding="utf-8"))
            existing_versions[lv] = int(cat_data.get("catalogVersion", 1))
        else:
            existing_versions[lv] = 1

    word_level_map, official_entries_by_level, official_entry_by_hanzi = load_hanban_2012_levels(
        complete_by_hanzi
    )

    missing_english: list[tuple[int, str, list[str]]] = []
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
            meta = resolve_word_metadata(lv, entry, missing_english)
            resolved_meta[hz] = meta
            word_to_pinyin[hz] = meta[0]

    print("Indexing Tatoeba Chinese-English sentences...")
    token_sentences: dict[str, list[tuple[str, str, list[str]]]] = defaultdict(list)
    for cmn_text, eng_text in tatoeba_pairs:
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
                token_sentences[tok].append((cmn_text, eng_text, tokens))

    write_curated_catalog(
        level=1,
        curated_units=HSK1_CURATED_UNITS,
        existing_version=existing_versions[1],
        official_entries=official_entries_by_level[1],
        entry_by_hanzi=official_entry_by_hanzi,
        resolved_meta=resolved_meta,
        word_to_pinyin=word_to_pinyin,
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
        token_sentences=token_sentences,
        word_level_map=word_level_map,
    )

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
                    "title": f"Quiz {l_order} — {u_title_short}",
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
                    "title": f"Unit {u_idx} — {u_title_short}",
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
            f"HSK {level} (v{updated_catalog['catalogVersion']}): "
            f"{len(all_units)} units, {total_lessons} quizzes, {len(all_words)} words, {tatoeba_hits} Tatoeba"
        )

    if missing_english:
        print(f"\nWARNING: {len(missing_english)} word(s) fell back to placeholder English:")
        for lv, hz, raw_m in missing_english[:40]:
            print(f"  HSK {lv}: {hz} -> {raw_m}")
        (CACHE_DIR / "missing_english.json").write_text(
            json.dumps(missing_english, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
