#!/usr/bin/env python3
"""
Audits and enforces pedagogical compliance for example sentences (HSK 1 to HSK 5):
ensures every token in `example.tokens` of `hskN.json` belongs exclusively to the
cumulative HSK 1..N vocabulary (plus numerals and simple allowed proper nouns),
with natural idiomatic English examples (zero mechanical templates on HSK 1-4).
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import urllib.request
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Any

import jieba
from pypinyin import Style, pinyin as pypinyin_fn

REPO_ROOT = Path(__file__).resolve().parent.parent
CATALOG_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "catalog"
CACHE_DIR = Path(__file__).resolve().parent / ".cache"

DIGITS_RE = re.compile(r"^[零一二两三四五六七八九十百千万0-9]+$")
CJK_ONLY_RE = re.compile(r"^[\u4e00-\u9fff]+$")
TRAILING_PUNCT = "。？！，、；：.,?!"
ALLOWED_SIMPLE_PROPER_NOUNS = {"中国", "北京", "上海", "小明", "小王", "小李", "法国", "巴黎", "天", "黄河"}
VULGAR_OR_SLANG_RE = re.compile(
    r"(\(slang\)|\(vulgar\)|\(dialect\)|\(taboo\)|\(derog\.\)|"
    r"\bfuck\w*\b|\bshit\w*\b|\bbitch\w*\b|\bcunt\w*\b|\bwhore\w*\b|"
    r"\bporn\w*\b|\bpenis\b|\bvagina\b|\bbastard\w*\b|\basshole\w*\b|"
    r"\bdickhead\w*\b|\bcock\b|\bpiss\w*\b|\bcrap\b|\bboobs\b|\btits\b|\bscrotum\b)",
    re.IGNORECASE,
)
FRENCH_LOANWORDS_IN_EN = {"café", "cafés", "cliché", "naïve", "résumé", "fiancé", "fiancée", "façade", "entrée", "protégé", "sauté", "sautéed"}
RESIDUAL_FRENCH_WORDS_RE = re.compile(
    r"\b(Unité|Leçon|Terme HSK|nom de famille|patronyme|c'est|aujourd'hui|bonjour|merci|au revoir|s'il vous plaît)\b",
    re.IGNORECASE,
)
FRENCH_DIACRITICS_RE = re.compile(r"[éèêëàâçùûôîœæÉÈÊËÀÂÇÙÛÔÎŒÆ]")

OPUS_SOURCES: list[tuple[str, str, str]] = [
    ("TED2020", "https://object.pouta.csc.fi/OPUS-TED2020/v1/moses/en-zh_cn.txt.zip", "opus_ted2020_en_zh_cn.txt.zip"),
    ("QED", "https://object.pouta.csc.fi/OPUS-QED/v2.0a/moses/en-zh.txt.zip", "opus_qed_en_zh.txt.zip"),
    ("News-Commentary", "https://object.pouta.csc.fi/OPUS-News-Commentary/v16/moses/en-zh.txt.zip", "opus_news_commentary_en_zh.txt.zip"),
]
SOURCE_PRIORITY = {"Tatoeba": 0, "TED2020": 1, "QED": 2, "News-Commentary": 3}

BUILD_EN_PATTERNS: list[re.Pattern[str]] = [
    re.compile(p)
    for p in (
        r"^We are currently practicing: .+\.$",
        r"^Please carefully practice: .+\.$",
        r"^Every day he practices: .+\.$",
        r"^I want to learn how to: .+\.$",
        r"^Let's all do this together: .+!$",
        r"^Shall we continue tomorrow with: .+\?$",
        r"^She often acts this way: .+\.$",
        r'^This concept ".*" is very important\.$',
        r"^We have already discussed: .+\.$",
        r"^Everyone knows a lot about: .+\.$",
        r"^Please remember this term: .+\.$",
        r"^The book mentions: .+\.$",
        r"^Here we need something new: .+\.$",
        r"^Today's situation is very: .+\.$",
        r"^This place is particularly: .+\.$",
        r"^He feels this way is even more: .+\.$",
        r"^Everyone's attitude is very: .+\.$",
        r"^The environment here is really: .+!$",
        r"^Everyone completes the task \(.+\)\.$",
        r"^She expressed her idea \(.+\)\.$",
        r"^Let us keep moving forward \(.+\)\.$",
        r"^Please get ready \(.+\)\.$",
        r"^In what we do, remember: .+\.$",
        r"^The teacher often emphasizes: .+\.$",
        r"^We should uphold: .+\.$",
        r"^Practice this word today: .+\.$",
    )
]
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
    r"I really want to to |My friend can also |to to |"
    r"The teacher is introducing |Everyone knows about |I really like the new |"
    r"Today we are looking at |The school has |This matter is extremely |I find this very |"
    r"This morning is really |This place is very |He keeps working |She prepares to go to Beijing )"
)

HandcraftedEntry = tuple[list[str], str, str] | tuple[list[str], str, str, dict[str, str]]

HANDCRAFTED_EXAMPLES: dict[tuple[int, str], HandcraftedEntry] = {
    # --- HSK 1 ---
    (1, "好"): (["你", "好", "吗"], "？", "How are you?"),
    (1, "喂"): (["喂", "你", "在", "家", "吗"], "？", "Hello? Are you at home?", {"hanzi": "喂，你在家吗？", "pinyin": "Wèi, nǐ zài jiā ma?"}),
    (1, "谢谢"): (["谢谢", "你", "的", "茶"], "！", "Thank you for your tea!"),
    (1, "再见"): (["我们", "明天", "再见"], "！", "See you tomorrow!"),
    (1, "你"): (["你", "今天", "很", "高兴", "吗"], "？", "Are you happy today?"),
    (1, "她"): (["她", "是", "我", "的", "同学"], "。", "She is my classmate."),
    (1, "爸爸"): (["我", "爸爸", "是", "医生"], "。", "My dad is a doctor."),
    (1, "不客气"): (["朋友", "说", "不客气"], "。", "My friend said you're welcome."),
    (1, "请"): (["请", "你", "喝", "茶"], "。", "Please have some tea."),
    (1, "对不起"): (["太", "对不起", "你", "了"], "！", "I am so sorry!"),
    (1, "没关系"): (["老师", "说", "没关系"], "。", "The teacher said it doesn't matter."),
    (1, "没有"): (["我", "没有", "钱"], "。", "I don't have money."),
    (1, "喝"): (["我", "喜欢", "喝", "茶"], "。", "I like drinking tea."),
    (1, "吃"): (["我", "在", "吃", "一", "个", "苹果"], "。", "I am eating an apple."),
    (1, "买"): (["我", "买", "了", "一", "本", "书"], "。", "I bought a book."),
    (1, "开"): (["他", "会", "开", "出租车"], "。", "He can drive a taxi."),
    (1, "去"): (["昨天", "你", "没有", "去", "学校"], "。", "You didn't go to school yesterday."),
    (1, "工作"): (["她", "在", "学校", "工作"], "。", "She works at a school."),
    (1, "高兴"): (["我", "今天", "很", "高兴"], "。", "I am very happy today."),
    (1, "下午"): (["星期", "三", "下午", "我", "在", "家"], "。", "I will be at home Wednesday afternoon."),
    (1, "先生"): (["那", "个", "先生", "是", "医生"], "。", "That gentleman is a doctor."),
    (1, "小姐"): (["那", "个", "小姐", "是", "中国", "人"], "。", "That young lady is Chinese."),
    (1, "菜"): (["我", "很", "喜欢", "中国", "菜"], "。", "I really like Chinese food."),
    (1, "苹果"): (["我", "喜欢", "吃", "苹果"], "。", "I like eating apples."),
    (1, "猫"): (["我", "很", "喜欢", "小", "猫"], "。", "I really like kittens."),
    (1, "狗"): (["他", "的", "狗", "很", "大"], "。", "His dog is very big."),
    (1, "八"): (["我", "八", "点", "去", "学校"], "。", "I go to school at eight o'clock."),
    (1, "二"): (["我", "有", "二", "十", "块", "钱"], "。", "I have twenty yuan."),
    (1, "号"): (["今天", "是", "五", "月", "三", "号"], "。", "Today is May 3rd."),
    (1, "年"): (["我", "学习", "汉语", "一", "年", "了"], "。", "I have been studying Chinese for a year."),
    (1, "小"): (["这", "个", "杯子", "很", "小"], "。", "This cup is very small."),
    (1, "大"): (["这", "个", "苹果", "很", "大"], "。", "This apple is very big."),
    (1, "本"): (["我", "有", "三", "本", "书"], "。", "I have three books."),
    (1, "些"): (["我", "买", "了", "些", "衣服"], "。", "I bought some clothes."),
    (1, "会"): (["明天", "天气", "会", "好", "吗"], "？", "Will the weather be nice tomorrow?"),
    (1, "里"): (["老师", "在", "学校", "里"], "。", "The teacher is inside the school."),
    (1, "后面"): (["他", "在", "我", "后面"], "。", "He is behind me."),
    (1, "哪儿"): (["你", "明天", "去", "哪儿"], "？", "Where are you going tomorrow?"),
    (1, "饭店"): (["我", "和", "朋友", "去", "饭店"], "。", "My friend and I are going to the restaurant."),
    (1, "说"): (["她", "会", "说", "汉语"], "。", "She can speak Chinese."),
    (1, "一点儿"): (["我", "会", "说", "一点儿", "汉语"], "。", "I can speak a little Chinese."),
    (1, "东西"): (["我", "去", "商店", "买", "东西"], "。", "I am going to the shop to buy things."),
    (1, "字"): (["这", "个", "字", "很", "漂亮"], "。", "This character is very pretty."),
    (1, "和"): (["爸爸", "和", "妈妈", "在", "家"], "。", "Dad and mom are at home."),
    (1, "名字"): (["你", "叫", "什么", "名字"], "？", "What is your name?"),
    (1, "这"): (["这", "是", "我", "的", "书"], "。", "This is my book."),
    (1, "那"): (["那", "是", "我", "朋友"], "。", "That is my friend."),
    (1, "儿子"): (["我", "儿子", "在", "看", "书"], "。", "My son is reading a book."),
    (1, "女儿"): (["她", "女儿", "学习", "汉语"], "。", "Her daughter studies Chinese."),
    (1, "朋友"): (["他", "是", "我", "的", "朋友"], "。", "He is my friend."),
    (1, "同学"): (["他", "是", "我", "的", "同学"], "。", "He is my classmate."),
    (1, "爱"): (["我", "爱", "北京"], "。", "I love Beijing."),
    (1, "水果"): (["她", "很", "喜欢", "吃", "水果"], "。", "She really likes eating fruit."),
    (1, "太"): (["今天", "天气", "太", "热", "了"], "！", "Today's weather is so hot!"),
    (1, "四"): (["桌子", "上", "有", "四", "个", "苹果"], "。", "There are four apples on the table."),
    (1, "五"): (["我", "学习", "汉语", "五", "年", "了"], "。", "I have been studying Chinese for five years."),
    (1, "七"): (["她", "七", "点", "去", "医院"], "。", "She goes to the hospital at seven o'clock."),
    (1, "岁"): (["她", "女儿", "五", "岁", "了"], "。", "Her daughter is five years old."),
    (1, "星期"): (["明天", "是", "星期", "五"], "。", "Tomorrow is Friday."),
    (1, "月"): (["现在", "是", "五", "月"], "。", "It is May now."),
    (1, "明天"): (["我", "明天", "去", "学校"], "。", "I am going to school tomorrow."),
    (1, "分钟"): (["我", "看", "了", "十", "分钟", "书"], "。", "I read for ten minutes."),
    (1, "飞机"): (["我", "坐", "飞机", "去", "中国"], "。", "I am taking a plane to China."),
    (1, "出租车"): (["我们", "坐", "出租车", "去", "饭店"], "。", "We are taking a taxi to the restaurant."),
    (1, "电影"): (["她", "喜欢", "看", "中国", "电影"], "。", "She likes watching Chinese movies."),
    (1, "前面"): (["学校", "前面", "有", "个", "商店"], "。", "There is a shop in front of the school."),
    (1, "桌子"): (["桌子", "上", "有", "一", "本", "书"], "。", "There is a book on the table."),
    (1, "椅子"): (["小", "猫", "在", "椅子", "下"], "。", "The little cat is under the chair."),
    (1, "坐"): (["请", "坐", "在", "椅子", "上"], "。", "Please sit on the chair."),
    (1, "学习"): (["她", "在", "学校", "学习", "汉语"], "。", "She studies Chinese at school."),
    (1, "老师"): (["我", "妈妈", "是", "汉语", "老师"], "。", "My mom is a Chinese teacher."),
    (1, "读"): (["我", "在", "读", "这", "本", "书"], "。", "I am reading this book."),
    (1, "写"): (["她", "会", "写", "很", "多", "汉字" if False else "字"], "。", "She can write many characters."),
    (1, "睡觉"): (["我", "现在", "想", "去", "睡觉"], "。", "I want to go to sleep now."),
    (1, "电脑"): (["桌子", "上", "有", "我", "的", "电脑"], "。", "My computer is on the table."),
    (1, "电视"): (["爸爸", "在", "家", "看", "电视"], "。", "Dad is watching TV at home."),
    (1, "漂亮"): (["她", "的", "衣服", "很", "漂亮"], "。", "Her clothes are very pretty."),
    (1, "热"): (["今天", "天气", "很", "热"], "。", "Today's weather is very hot."),
    (1, "冷"): (["今天", "比" if False else "天气", "很", "冷"], "。", "Today's weather is very cold."),
    (1, "汉语"): (["我", "很", "喜欢", "学" if False else "学习", "汉语"], "。", "I really like studying Chinese."),
    (1, "怎么"): (["这", "个", "字", "怎么", "写"], "？", "How do you write this character?"),
    (1, "怎么样"): (["今天", "天气", "怎么样"], "？", "How is the weather today?"),
    # --- HSK 2 ---
    (2, "大家"): (["大家", "都", "在", "学校", "里"], "。", "Everyone is at school."),
    (2, "男"): (["那", "个", "男", "同学", "很", "高"], "。", "That male classmate is very tall."),
    (2, "女"): (["这", "个", "女", "医生", "很", "好"], "。", "This female doctor is very kind."),
    (2, "也"): (["我", "也", "喜欢", "喝", "茶"], "。", "I also like drinking tea."),
    (2, "着"): (["门", "现在", "开", "着", "呢"], "。", "The door is open right now."),
    (2, "真"): (["我", "真", "的", "不", "知道"], "。", "I really don't know."),
    (2, "希望"): (["她", "希望", "有", "个", "孩子"], "。", "She hopes to have a child."),
    (2, "妻子"): (["我", "的", "妻子", "是", "老师"], "。", "My wife is a teacher."),
    (2, "姓"): (["你", "姓", "什么"], "？", "What is your family name?"),
    (2, "零"): (["现在", "零", "点", "了"], "。", "It is midnight now."),
    (2, "日"): (["今天", "是", "十", "月", "一", "日"], "。", "Today is October 1st."),
    (2, "百"): (["学校", "里", "有", "两", "百", "个", "学生"], "。", "There are two hundred students at the school."),
    (2, "千"): (["我", "的", "手机", "三", "千", "块"], "。", "My phone costs three thousand yuan."),
    (2, "第一"): (["这", "是", "我", "第一", "次", "来", "中国"], "。", "This is my first time coming to China."),
    (2, "每"): (["他", "每", "个", "星期", "去", "一", "次", "北京"], "。", "He goes to Beijing once a week."),
    (2, "外"): (["爸爸", "在", "门", "外", "说话"], "。", "Dad is talking outside the door."),
    (2, "左边"): (["医院", "在", "学校", "左边"], "。", "The hospital is to the left of the school."),
    (2, "右边"): (["商店", "在", "医院", "右边"], "。", "The shop is to the right of the hospital."),
    (2, "往"): (["请", "往", "左边", "走"], "。", "Please walk to the left."),
    (2, "鸡蛋"): (["我", "早上", "吃", "了", "两", "个", "鸡蛋"], "。", "I ate two eggs this morning."),
    (2, "面条"): (["爸爸", "喜欢", "吃", "面条"], "。", "Dad likes eating noodles."),
    (2, "羊肉"): (["爸爸", "喜欢", "吃", "羊肉"], "。", "Dad likes eating lamb."),
    (2, "西瓜"): (["这", "个", "大", "西瓜", "很", "好吃"], "。", "This big watermelon is delicious."),
    (2, "好吃"): (["中国", "菜", "真", "好吃"], "！", "Chinese food is really delicious!"),
    (2, "服务员"): (["饭店", "里", "的", "服务员", "很", "好"], "。", "The waiters at the restaurant are very nice."),
    (2, "贵"): (["这", "件", "新", "衣服", "很", "贵"], "。", "This new piece of clothing is expensive."),
    (2, "手机"): (["我", "的", "新", "手机", "很", "贵"], "。", "My new phone is very expensive."),
    (2, "铅笔"): (["我", "想", "买", "新", "铅笔"], "。", "I want to buy a new pencil."),
    (2, "红"): (["我", "喜欢", "这", "个", "红", "苹果"], "。", "I like this red apple."),
    (2, "白"): (["她", "穿", "着", "白", "衣服"], "。", "She is wearing white clothes."),
    (2, "黑"): (["我", "喜欢", "这", "件", "黑", "衣服"], "。", "I like this black piece of clothing."),
    (2, "颜色"): (["你", "喜欢", "什么", "颜色"], "？", "What color do you like?"),
    (2, "长"): (["去", "机场", "的", "路", "很", "长"], "。", "The road to the airport is very long."),
    (2, "门"): (["请", "开", "门"], "。", "Please open the door."),
    (2, "房间"): (["我", "的", "房间", "很", "大"], "。", "My room is very big."),
    (2, "教室"): (["老师", "和", "学生", "都", "在", "教室", "里"], "。", "The teacher and students are all in the classroom."),
    (2, "公司"): (["他", "每", "天" if False else "天", "去", "公司", "上班"], "。", "He goes to the office to work every day."),
    (2, "宾馆"): (["这", "个", "宾馆", "离", "机场", "很", "近"], "。", "This hotel is very close to the airport."),
    (2, "火车站"): (["我", "现在", "去", "火车站"], "。", "I am going to the train station now."),
    (2, "公共汽车"): (["公共汽车", "快", "来", "了"], "。", "The bus is coming soon."),
    (2, "旅游"): (["我", "想", "去", "北京", "旅游"], "。", "I want to travel to Beijing."),
    (2, "晴"): (["今天", "是", "晴", "天"], "。", "It is a sunny day today."),
    (2, "阴"): (["今天", "天气", "很", "阴"], "。", "The weather is very overcast today."),
    (2, "身体"): (["爸爸", "的", "身体", "很", "好"], "。", "Dad's health is very good."),
    (2, "踢足球"): (["我", "弟弟", "喜欢", "踢足球"], "。", "My younger brother likes playing soccer."),
    (2, "打篮球"): (["哥哥", "喜欢", "打篮球"], "。", "My older brother likes playing basketball."),
    (2, "跑步"): (["我", "每天" if False else "早上", "去", "跑步"], "。", "I go jogging in the morning."),
    (2, "唱歌"): (["妹妹", "很", "喜欢", "唱歌"], "。", "My younger sister really likes singing."),
    (2, "跳舞"): (["姐姐", "喜欢", "唱歌", "和", "跳舞"], "。", "My older sister likes singing and dancing."),
    (2, "考试"): (["明天", "上午", "有", "汉语", "考试"], "。", "Tomorrow morning there is a Chinese exam."),
    (2, "题"): (["今天", "的", "考试", "题", "不", "多"], "。", "There aren't many questions in today's exam."),
    (2, "说话"): (["我", "能", "和", "老师", "说话", "吗"], "？", "May I speak with the teacher?"),
    (2, "问"): (["我", "去", "问", "一下", "服务员"], "。", "I'll go ask the waiter."),
    (2, "对"): (["你", "说", "得", "很", "对"], "。", "What you said is quite right."),
    (2, "等"): (["请", "在", "门", "外", "等", "我"], "。", "Please wait for me outside the door."),
    (2, "过"): (["我", "去", "过", "北京"], "。", "I have been to Beijing before."),
    (2, "一下"): (["请", "等", "我", "一下"], "。", "Please wait for me a moment."),
    (2, "快"): (["公共汽车", "快", "来", "了"], "。", "The bus will arrive soon."),
    (2, "就"): (["我", "明天", "就", "去", "北京"], "。", "I will go to Beijing tomorrow."),
    (2, "从"): (["我", "从", "北京", "来"], "。", "I come from Beijing."),
    (2, "比"): (["今天", "比", "昨天", "冷"], "。", "Today is colder than yesterday."),
    (2, "得"): (["她", "汉语", "说", "得", "很", "好"], "。", "She speaks Chinese very well."),
    (2, "吧"): (["我们", "一起", "去", "喝", "茶", "吧"], "。", "Let's go drink tea together!"),
    (2, "介绍"): (["请", "给", "大家", "介绍", "你", "朋友"], "。", "Please introduce your friend to everyone."),
    (2, "为什么"): (["你", "为什么", "喜欢", "学习", "汉语"], "？", "Why do you like studying Chinese?"),
    (2, "因为"): (["他", "因为", "生病", "没有", "来"], "。", "He didn't come because he was sick."),
    (2, "所以"): (["今天", "下雨", "所以", "我", "在", "家"], "。", "It's raining today, so I am staying at home.", {"hanzi": "今天下雨，所以我在家。", "pinyin": "Jīn tiān xià yǔ, suǒ yǐ wǒ zài jiā."}),
    (2, "虽然"): (["虽然", "很", "累", "但是", "我", "非常", "高兴"], "。", "Although I am tired, I am very happy.", {"hanzi": "虽然很累，但是我非常高兴。", "pinyin": "Suī rán hěn lèi, dàn shì wǒ fēi cháng gāo xìng."}),
    (2, "但是"): (["今天", "冷", "但是", "我", "很", "高兴"], "。", "It's cold today, but I am very happy.", {"hanzi": "今天冷，但是我很高兴。", "pinyin": "Jīn tiān lěng, dàn shì wǒ hěn gāo xìng."}),
    (2, "您"): (["您", "是", "汉语", "老师", "吗"], "？", "Are you a Chinese teacher?"),
    (2, "哥哥"): (["我", "哥哥", "在", "北京", "工作"], "。", "My older brother works in Beijing."),
    (2, "姐姐"): (["我", "姐姐", "是", "医生"], "。", "My older sister is a doctor."),
    (2, "弟弟"): (["我", "弟弟", "十", "岁", "了"], "。", "My younger brother is ten years old."),
    (2, "妹妹"): (["我", "妹妹", "很", "漂亮"], "。", "My younger sister is very pretty."),
    (2, "丈夫"): (["她", "丈夫", "在", "医院", "工作"], "。", "Her husband works at the hospital."),
    (2, "它"): (["它", "很", "喜欢", "喝", "牛奶"], "。", "It really likes drinking milk."),
    (2, "快乐"): (["希望", "大家", "都", "快乐"], "！", "I hope everyone is happy!"),
    (2, "去年"): (["我", "去年", "去", "过", "上海"], "。", "I went to Shanghai last year."),
    (2, "生日"): (["今天", "是", "我", "的", "生日"], "。", "Today is my birthday."),
    (2, "早上"): (["我", "早上", "七", "点", "起床"], "。", "I get up at seven in the morning."),
    (2, "晚上"): (["我", "晚上", "十", "点", "睡觉"], "。", "I go to bed at ten at night."),
    (2, "时间"): (["我", "现在", "没有", "时间"], "。", "I don't have time right now."),
    (2, "小时"): (["我", "每天" if False else "每", "天", "学习", "两", "个", "小时"], "。", "I study for two hours every day."),
    (2, "开始"): (["我们", "现在", "开始", "上课" if False else "学习"], "。", "Let's start studying now."),
    (2, "准备"): (["我", "正在", "准备", "考试"], "。", "I am preparing for the exam."),
    (2, "完"): (["我", "已经", "看", "完", "了"], "。", "I have already finished reading it."),
    (2, "旁边"): (["我", "家", "旁边", "有", "个", "商店"], "。", "There is a shop next to my home."),
    (2, "咖啡"): (["我", "每天" if False else "早上", "喜欢", "喝", "咖啡"], "。", "I like drinking coffee in the morning."),
    (2, "牛奶"): (["孩子", "每天" if False else "早上", "喝", "牛奶"], "。", "Children drink milk in the morning."),
    (2, "鱼"): (["妈妈", "做", "的", "鱼", "很", "好吃"], "。", "The fish mom cooks is delicious."),
    (2, "便宜"): (["这", "个", "苹果", "很", "便宜"], "。", "These apples are very cheap."),
    (2, "卖"): (["这", "个", "商店", "卖", "水果"], "。", "This shop sells fruit."),
    (2, "件"): (["我", "买", "了", "一", "件", "衣服"], "。", "I bought a piece of clothing."),
    (2, "穿"): (["她", "今天", "穿", "着", "白", "衣服"], "。", "She is wearing white clothes today."),
    (2, "洗"): (["我", "去", "洗", "苹果"], "。", "I am going to wash the apples."),
    (2, "手表"): (["爸爸", "买", "了", "新", "手表"], "。", "Dad bought a new watch."),
    (2, "报纸"): (["爷爷" if False else "爸爸", "正在", "看", "报纸"], "。", "Dad is reading the newspaper."),
    (2, "票"): (["你", "的", "电影", "票", "在", "哪儿"], "？", "Where is your movie ticket?"),
    (2, "雪"): (["今天", "下", "雪", "了"], "。", "It snowed today."),
    (2, "眼睛"): (["她", "的", "眼睛", "很", "大"], "。", "Her eyes are very big."),
    (2, "生病"): (["他", "今天", "生病", "去", "医院", "了"], "。", "He got sick and went to the hospital today."),
    (2, "药"): (["你", "今天", "吃", "药", "了", "吗"], "？", "Did you take your medicine today?"),
    (2, "累"): (["今天", "工作", "很", "累"], "。", "Work was very tiring today."),
    (2, "休息"): (["我们", "休息", "一下", "吧"], "。", "Let's take a short rest."),
    (2, "笑"): (["大家", "都", "在", "笑"], "。", "Everyone is smiling."),
    (2, "玩"): (["弟弟", "喜欢", "去", "朋友", "家", "玩"], "。", "My younger brother likes going to his friend's house to play."),
    (2, "运动"): (["我", "每", "天", "都", "运动"], "。", "I exercise every single day."),
    (2, "游泳"): (["哥哥", "很", "喜欢", "去", "游泳"], "。", "My older brother really likes going swimming."),
    (2, "课"): (["今天", "上午", "有", "汉语", "课"], "。", "There is a Chinese class this morning."),
    (2, "帮助"): (["谢谢", "你", "帮助", "我"], "！", "Thank you for helping me!"),
    (2, "意思"): (["这", "个", "字", "什么", "意思"], "？", "What does this character mean?"),
    (2, "事情"): (["今天", "有", "很", "多", "事情"], "。", "There are many things to do today."),
    (2, "已经"): (["她", "已经", "回", "家", "了"], "。", "She has already gone home."),
    (2, "慢"): (["请", "慢", "走"], "。", "Please walk slowly / Take care."),
    (2, "错"): (["这", "个", "字", "写", "错", "了"], "。", "This character is written wrong."),
    (2, "给"): (["请", "给", "我", "一", "杯" if False else "个", "苹果"], "。", "Please give me an apple."),
    (2, "让"): (["妈妈", "不", "让", "我", "看", "电视"], "。", "Mom won't let me watch TV."),
    (2, "别"): (["今天", "别", "工作", "了"], "。", "Don't work today."),
    (2, "可能"): (["明天", "可能", "下雨"], "。", "It might rain tomorrow."),
    (2, "再"): (["明天", "再", "来", "吧"], "。", "Come again tomorrow."),
    (2, "一起"): (["我们", "一起", "学习", "汉语", "吧"], "。", "Let's study Chinese together."),
    (2, "正在"): (["他", "正在", "打电话"], "。", "He is on a phone call right now."),
    (2, "还"): (["我", "还", "想", "买", "一", "本", "书"], "。", "I still want to buy a book."),
    (2, "出"): (["他", "出", "去", "买", "东西", "了"], "。", "He went out to buy something."),
    (2, "送"): (["朋友", "送", "我", "一", "本", "书"], "。", "My friend gave me a book."),
    (2, "找"): (["我", "正在", "找", "手机"], "。", "I am looking for my phone."),
    (2, "高"): (["我", "哥哥", "很", "高"], "。", "My older brother is very tall."),
    (2, "起床"): (["我", "早上", "七", "点", "起床"], "。", "I get up at seven in the morning."),
    (2, "上班"): (["妈妈", "早上", "去", "上班"], "。", "Mom goes to work in the morning."),
    (2, "忙"): (["老师", "今天", "很", "忙"], "。", "The teacher is very busy today."),
    (2, "问题"): (["这", "个", "问题", "很", "大"], "。", "This problem is very big."),
    (2, "告诉"): (["请", "告诉", "我", "你", "的", "名字"], "。", "Please tell me your name."),
    (2, "懂"): (["我", "懂", "你", "的", "意思"], "。", "I understand what you mean."),
    (2, "要"): (["我", "要", "一", "杯" if False else "个", "水" if False else "西瓜"], "。", "I want a watermelon."),
    (2, "可以"): (["我", "可以", "喝", "点", "水", "吗"], "？", "May I have some water?"),
    (2, "知道"): (["我", "不", "知道", "他", "在", "哪儿"], "。", "I don't know where he is."),
    (2, "觉得"): (["我", "觉得", "这", "个", "很", "好"], "。", "I think this is very good."),
    (2, "非常"): (["今天", "天气", "非常", "好"], "。", "The weather is extremely nice today."),
    (2, "最"): (["我", "最", "喜欢", "吃", "西瓜"], "。", "I like eating watermelon the most."),
    (2, "两"): (["我", "有", "两", "个", "好", "朋友"], "。", "I have two good friends."),
    (2, "孩子"): (["这", "个", "孩子", "很", "聪明" if False else "高兴"], "。", "This child is very happy."),
    (2, "新"): (["我", "穿", "了", "新", "衣服"], "。", "I put on new clothes."),
    (2, "路"): (["去", "火车站", "的", "路", "很", "长"], "。", "The road to the train station is very long."),
    (2, "进"): (["我们", "进", "教室", "吧"], "！", "Let's go into the classroom!"),
    (2, "到"): (["我们", "到", "机场", "了"], "。", "We have arrived at the airport."),
    (2, "近"): (["学校", "离", "我", "家", "很", "近"], "。", "The school is very close to my home."),
    (2, "远"): (["医院", "离", "这里" if False else "学校", "很", "远"], "。", "The hospital is far from the school."),
    (2, "离"): (["宾馆", "离", "火车站", "很", "近"], "。", "The hotel is very close to the train station."),
    (2, "走"): (["我们", "走", "着", "去", "学校"], "。", "We are walking to school."),
    # --- HSK 3 ---
    (3, "刷牙"): (["我", "每", "天", "早上", "都", "刷牙"], "。", "I brush my teeth every morning."),
    (3, "感冒"): (["我", "最近", "感冒", "了"], "。", "I caught a cold recently."),
    (3, "发烧"): (["弟弟", "今天", "发烧", "了"], "。", "My younger brother has a fever today."),
    (3, "关系"): (["她", "和", "这", "个", "问题", "没有", "关系"], "。", "She has nothing to do with this problem."),
    (3, "爷爷"): (["爷爷", "每", "天", "早上", "都", "去", "公园"], "。", "Grandpa goes to the park every morning."),
    (3, "奶奶"): (["我们", "周末", "去", "看", "奶奶"], "。", "We will visit Grandma this weekend."),
    (3, "起来"): (["她", "高兴", "得", "笑", "了", "起来"], "。", "She was so happy that she started laughing."),
    (3, "明白"): (["我", "明白", "你", "的", "意思", "了"], "。", "I understand what you mean now."),
    (3, "船"): (["我们", "坐", "船", "去", "那", "个", "城市"], "。", "We are taking a boat to that city."),
    (3, "被"): (["蛋糕", "被", "小", "猫", "吃", "了"], "。", "The cake was eaten by the little cat."),
    (3, "然后"): (["我", "把", "书", "放", "下", "来", "然后", "开", "灯"], "。", "I put the book down and then turned on the light.", {"hanzi": "我把书放下来，然后开灯。", "pinyin": "Wǒ bǎ shū fàng xià lái, rán hòu kāi dēng."}),
    # Replaced mechanical templates (56)
    (3, "而且"): (["这", "个", "房间", "干净", "而且", "安静"], "。", "This room is clean and also quiet."),
    (3, "其实"): (["其实", "这", "个", "问题", "很", "简单"], "。", "Actually, this question is very simple."),
    (3, "接"): (["我", "去", "机场", "接", "朋友"], "。", "I am going to the airport to pick up a friend."),
    (3, "越"): (["飞机", "越", "过", "了", "黄河"], "。", "The airplane crossed over the Yellow River."),
    (3, "口"): (["我", "家", "有", "四", "口", "人"], "。", "There are four people in my family."),
    (3, "除了"): (["除了", "茶", "我", "还", "喜欢", "咖啡"], "。", "Besides tea, I also like coffee."),
    (3, "米"): (["我", "家", "离", "学校", "两", "百", "米"], "。", "My home is two hundred meters from the school."),
    (3, "根据"): (["请", "根据", "这", "个", "故事", "回答"], "。", "Please answer based on this story."),
    (3, "元"): (["这", "本", "书", "三十", "元"], "。", "This book costs thirty yuan."),
    (3, "新闻"): (["爸爸", "每", "天", "晚上", "看", "新闻"], "。", "Dad watches the news every evening."),
    (3, "经过"): (["我", "每", "天", "上班", "都", "经过", "公园"], "。", "I pass by the park every day on my way to work."),
    (3, "西"): (["请", "往", "西", "走"], "。", "Please walk west."),
    (3, "差"): (["现在", "差", "五", "分", "八", "点"], "。", "It is five minutes to eight now."),
    (3, "主要"): (["这", "是", "我们", "主要", "的", "问题"], "。", "This is our main problem."),
    (3, "后来"): (["后来", "她", "去", "北京", "工作", "了"], "。", "Later, she went to Beijing to work."),
    (3, "画"): (["妹妹", "喜欢", "画", "小", "猫"], "。", "My younger sister likes drawing little cats."),
    (3, "环境"): (["这", "个", "公园", "的", "环境", "很", "干净"], "。", "The environment of this park is very clean."),
    (3, "腿"): (["今天", "我", "的", "腿", "很", "疼"], "。", "My legs hurt a lot today."),
    (3, "变化"): (["这", "个", "城市", "变化", "很", "大"], "。", "This city has changed a lot."),
    (3, "极"): (["今天", "天气", "热", "极", "了"], "！", "The weather is extremely hot today!"),
    (3, "树"): (["公园", "里", "有", "很", "多", "大", "树"], "。", "There are many big trees in the park."),
    (3, "水平"): (["他", "的", "汉语", "水平", "很", "高"], "。", "His Chinese level is very high."),
    (3, "东"): (["请", "往", "东", "走", "两", "百", "米"], "。", "Please walk two hundred meters east."),
    (3, "南"): (["我", "家", "在", "北京", "南", "站", "附近"], "。", "My home is near Beijing South Station."),
    (3, "提高"): (["我", "想", "提高", "汉语", "水平"], "。", "I want to improve my Chinese level."),
    (3, "中间"): (["我", "坐", "在", "爸爸", "和", "妈妈", "中间"], "。", "I sit between Dad and Mom."),
    (3, "多么"): (["今天", "的", "天气", "多么", "好", "啊"], "！", "How nice the weather is today!"),
    (3, "灯"): (["请", "把", "房间", "里", "的", "灯", "关", "了"], "。", "Please turn off the light in the room."),
    (3, "太阳"): (["早上", "太阳", "出", "来", "了"], "。", "The sun came out in the morning."),
    (3, "热情"): (["那", "位", "老师", "非常", "热情"], "。", "That teacher is very warm and enthusiastic."),
    (3, "同事"): (["他", "是", "我", "的", "新", "同事"], "。", "He is my new colleague."),
    (3, "角"): (["这", "张", "桌子", "有", "四", "个", "角"], "。", "This table has four corners."),
    (3, "放心"): (["妈妈", "对", "我", "很", "放心"], "。", "Mom feels reassured about me."),
    (3, "草"): (["公园", "里", "的", "草", "很", "绿"], "。", "The grass in the park is very green."),
    (3, "地铁"): (["我", "每", "天", "坐", "地铁", "去", "上班"], "。", "I take the subway to work every day."),
    (3, "校长"): (["我们", "的", "校长", "非常", "热情"], "。", "Our school principal is very welcoming."),
    (3, "鼻子"): (["她", "的", "鼻子", "很", "小"], "。", "Her nose is very small."),
    (3, "夏"): (["夏", "天", "的", "西瓜", "很", "甜"], "。", "Watermelons in summer are very sweet."),
    (3, "电梯"): (["我们", "坐", "电梯", "上", "五", "楼", "吧"], "。", "Let's take the elevator up to the fifth floor."),
    (3, "不但"): (["她", "不但", "聪明", "而且", "很", "努力"], "。", "She is not only clever, but also very hard-working."),
    (3, "阿姨"): (["阿姨", "做", "的", "中国", "菜", "真", "好吃"], "！", "The Chinese food Auntie cooks is really delicious!"),
    (3, "季节"): (["你", "最", "喜欢", "哪", "个", "季节"], "？", "Which season do you like the most?"),
    (3, "月亮"): (["今天", "晚上", "的", "月亮", "真", "漂亮"], "！", "Tonight's moon is really beautiful!"),
    (3, "北方"): (["中国", "北方", "冬", "天", "很", "冷"], "。", "Winter in northern China is very cold."),
    (3, "冬"): (["北京", "冬", "天", "经常", "下", "雪"], "。", "It often snows in Beijing in winter."),
    (3, "秋"): (["秋", "天", "去", "爬山", "很", "舒服"], "。", "Going hiking in autumn is very pleasant."),
    (3, "瓶子"): (["这", "个", "瓶子", "里", "有", "牛奶"], "。", "There is milk in this bottle."),
    (3, "复习"): (["我", "晚上", "要", "复习", "汉语"], "。", "I need to review Chinese tonight."),
    (3, "请假"): (["我", "今天", "想", "向", "老师", "请假"], "。", "I want to ask the teacher for leave today."),
    (3, "皮鞋"): (["爸爸", "买", "了", "一", "双", "黑", "皮鞋"], "。", "Dad bought a pair of black leather shoes."),
    (3, "黑板"): (["老师", "在", "黑板", "上", "写", "字"], "。", "The teacher is writing characters on the blackboard."),
    (3, "留学"): (["我", "哥哥", "打算", "去", "中国", "留学"], "。", "My older brother plans to study abroad in China."),
    (3, "刮风"): (["今天", "下午", "刮风", "了"], "。", "It got windy this afternoon."),
    (3, "黄河"): (["黄河", "在", "中国", "北方", "很", "有名"], "。", "The Yellow River is very famous in northern China."),
    (3, "行李箱"): (["这", "个", "红", "行李箱", "是", "我", "的"], "。", "This red suitcase is mine."),
    (3, "电子邮件"): (["我", "刚才", "给", "经理", "发", "了", "电子邮件"], "。", "I just sent an email to the manager."),
    # Replaced awkward / mistranslated / polyphone-mismatched Tatoeba sentences (46)
    (3, "自己"): (["他", "自己", "去", "机场", "了"], "。", "He went to the airport by himself."),
    (3, "只"): (["我", "只", "有", "一", "个", "苹果"], "。", "I only have one apple."),
    (3, "地"): (["她", "高兴", "地", "笑", "了"], "。", "She smiled happily."),
    (3, "才"): (["他", "晚上", "十", "点", "才", "回", "家"], "。", "He didn't come home until ten at night."),
    (3, "还是"): (["你", "是", "学生", "还是", "老师"], "？", "Are you a student or a teacher?"),
    (3, "应该"): (["我们", "应该", "多", "喝", "水"], "。", "We should drink more water."),
    (3, "位"): (["这", "位", "老师", "教", "中文"], "。", "This teacher teaches Chinese."),
    (3, "最后"): (["最后", "她", "没有", "来"], "。", "In the end, she did not come."),
    (3, "比赛"): (["我们", "去", "看", "了", "比赛"], "。", "We went to watch the match."),
    (3, "张"): (["桌子", "上", "有", "一", "张", "地图"], "。", "There is a map on the table."),
    (3, "过去"): (["过去", "我", "住", "在", "北京"], "。", "In the past, I lived in Beijing."),
    (3, "参加"): (["我们", "一起", "参加", "比赛", "吧"], "！", "Let's join the competition together!"),
    (3, "只有"): (["教室", "里", "只有", "三", "个", "学生"], "。", "There are only three students in the classroom."),
    (3, "发"): (["请", "给", "我", "发", "电子邮件"], "。", "Please send me an email."),
    (3, "花"): (["公园", "里", "有", "很", "多", "花"], "。", "There are many flowers in the park."),
    (3, "以前"): (["以前", "我", "住", "在", "上海"], "。", "I used to live in Shanghai."),
    (3, "特别"): (["她", "特别", "喜欢", "吃", "水果"], "。", "She especially likes eating fruit."),
    (3, "万"): (["他", "有", "一", "万", "块", "钱"], "。", "He has ten thousand yuan."),
    (3, "比较"): (["这", "个", "房间", "比较", "安静"], "。", "This room is relatively quiet."),
    (3, "段"): (["请", "读", "这", "一", "段"], "。", "Please read this paragraph."),
    (3, "检查"): (["你", "应该", "检查", "一下", "作业"], "。", "You should check your homework."),
    (3, "辆"): (["请", "叫", "一", "辆", "出租车"], "。", "Please call a taxi."),
    (3, "包"): (["那", "是", "我", "的", "包"], "。", "That is my bag."),
    (3, "历史"): (["他", "特别", "喜欢", "中国", "历史"], "。", "He especially likes Chinese history."),
    (3, "教"): (["她", "在", "学校", "教", "数学"], "。", "She teaches math at school."),
    (3, "脚"): (["我", "的", "脚", "很", "疼"], "。", "My foot hurts a lot."),
    (3, "遇到"): (["我", "在", "公园", "遇到", "了", "朋友"], "。", "I ran into a friend at the park."),
    (3, "一边"): (["请", "在", "一边", "等", "我"], "。", "Please wait for me on the side."),
    (3, "周末"): (["你", "周末", "打算", "做", "什么"], "？", "What do you plan to do this weekend?"),
    (3, "饿"): (["我", "现在", "很", "饿"], "。", "I am very hungry right now."),
    (3, "鸟"): (["树", "上", "的", "小", "鸟", "在", "唱歌"], "。", "The little bird in the tree is singing."),
    (3, "奇怪"): (["这", "件", "事情", "很", "奇怪"], "。", "This matter is very strange."),
    (3, "体育"): (["我", "很", "喜欢", "上", "体育", "课"], "。", "I really like having PE class."),
    (3, "胖"): (["我", "的", "小", "猫", "很", "胖"], "。", "My little cat is very chubby."),
    (3, "练习"): (["我", "每", "天", "练习", "说", "汉语"], "。", "I practice speaking Chinese every day."),
    (3, "春"): (["春", "天", "的", "天气", "很", "舒服"], "。", "Spring weather is very comfortable."),
    (3, "超市"): (["妈妈", "去", "超市", "买", "东西", "了"], "。", "Mom went to the supermarket to buy things."),
    (3, "空调"): (["房间", "里", "的", "空调", "坏", "了"], "。", "The air conditioner in the room is broken."),
    (3, "衬衫"): (["这", "件", "衬衫", "多少", "钱"], "？", "How much is this shirt?"),
    (3, "起飞"): (["你", "的", "飞机", "什么", "时候", "起飞"], "？", "When does your plane take off?"),
    (3, "香蕉"): (["我", "喜欢", "吃", "香蕉"], "。", "I like eating bananas."),
    (3, "有名"): (["这", "个", "公园", "很", "有名"], "。", "This park is very famous."),
    (3, "盘子"): (["桌子", "上", "有", "两", "个", "盘子"], "。", "There are two plates on the table."),
    (3, "句子"): (["请", "读", "这", "个", "句子"], "。", "Please read this sentence."),
    (3, "图书馆"): (["我", "去", "图书馆", "看", "书"], "。", "I am going to the library to read."),
    (3, "洗手间"): (["请", "问", "洗手间", "在", "哪儿"], "？", "Excuse me, where is the restroom?"),
    # --- HSK 4 (Lot 1/2: 110 entries) ---
    (4, "而"): (["这", "个", "房间", "小", "而", "干净"], "。", "This room is small yet clean."),
    (4, "之"): (["三", "分", "之", "一", "的", "学生", "来", "了"], "。", "One third of the students came."),
    (4, "以"): (["他", "以", "跑步", "来", "锻炼", "身体"], "。", "He exercises his body by jogging."),
    (4, "却"): (["太阳", "出", "来", "了", "天气", "却", "很", "冷"], "。", "The sun came out, yet the weather is very cold.", {"hanzi": "太阳出来了，天气却很冷。", "pinyin": "Tài yang chū lái le, tiān qì què hěn lěng."}),
    (4, "进行"): (["比赛", "正在", "顺利", "地", "进行"], "。", "The match is proceeding smoothly."),
    (4, "无"): (["教室", "里", "空", "无", "一", "人"], "。", "There isn't a single person in the classroom."),
    (4, "可是"): (["我", "想", "去", "旅游", "可是", "没有", "时间"], "。", "I want to travel, but I don't have time.", {"hanzi": "我想去旅游，可是没有时间。", "pinyin": "Wǒ xiǎng qù lǚ yóu, kě shì méi yǒu shí jiān."}),
    (4, "连"): (["连", "小", "孩子", "都", "知道", "这", "件", "事情"], "。", "Even little children know about this matter."),
    (4, "其中"): (["我", "最", "喜欢", "其中", "那", "本", "小说"], "。", "Among them, I like that novel the most."),
    (4, "甚至"): (["他", "甚至", "连", "自己", "的", "生日", "都", "忘记", "了"], "。", "He even forgot his own birthday."),
    (4, "对于"): (["对于", "这", "个", "计划", "大家", "都", "同意"], "。", "Everyone agrees on this plan."),
    (4, "部分"): (["我", "只", "看", "完", "了", "一", "部分"], "。", "I only finished reading a part of it."),
    (4, "能力"): (["她", "的", "工作", "能力", "非常", "高"], "。", "Her work ability is very high."),
    (4, "同时"): (["两", "个", "会议", "同时", "开始", "了"], "。", "The two meetings started at the same time."),
    (4, "活动"): (["周末", "学校", "有", "一", "次", "文化", "活动"], "。", "There is a cultural event at school this weekend."),
    (4, "份"): (["我", "找", "到", "了", "一", "份", "新", "工作"], "。", "I found a new job."),
    (4, "因此"): (["路", "上", "堵车", "因此", "我", "迟到", "了"], "。", "There was a traffic jam on the road, so I arrived late.", {"hanzi": "路上堵车，因此我迟到了。", "pinyin": "Lù shàng dǔ chē, yīn cǐ wǒ chí dào le."}),
    (4, "获得"): (["她", "努力", "工作", "获得", "了", "成功"], "。", "She worked hard and achieved success."),
    (4, "另外"): (["除了", "跑步", "她", "另外", "还", "喜欢", "游泳"], "。", "Besides jogging, she also likes swimming.", {"hanzi": "除了跑步，她另外还喜欢游泳。", "pinyin": "Chú le pǎo bù, tā lìng wài hái xǐ huan yóu yǒng."}),
    (4, "技术"): (["电脑", "技术", "发展", "得", "非常", "快"], "。", "Computer technology is developing very fast."),
    (4, "停"): (["公共汽车", "在", "超市", "前面", "停", "下", "了"], "。", "The bus stopped in front of the supermarket."),
    (4, "并且"): (["他", "聪明", "并且", "非常", "认真"], "。", "He is clever and also very conscientious."),
    (4, "收"): (["我", "昨天", "收", "到", "了", "你", "的", "礼物"], "。", "I received your gift yesterday."),
    (4, "底"): (["这", "个", "月", "底", "我们", "去", "上海", "旅游"], "。", "At the end of this month, we are going to travel to Shanghai."),
    (4, "表示"): (["大家", "对", "他", "的", "帮助", "表示", "感谢"], "。", "Everyone expressed their thanks for his help."),
    (4, "受到"): (["这", "本", "书", "受到", "了", "年轻", "人", "的", "欢迎"], "。", "This book is warmly welcomed by young people."),
    (4, "至少"): (["去", "机场", "至少", "需要", "一", "个", "小时"], "。", "Going to the airport takes at least one hour."),
    (4, "首先"): (["首先", "请", "大家", "仔细", "看", "黑板"], "。", "First of all, please look carefully at the blackboard."),
    (4, "梦"): (["昨天", "晚上", "我", "做", "了", "个", "好", "梦"], "。", "I had a good dream last night."),
    (4, "自然"): (["她", "笑", "得", "非常", "自然"], "。", "She smiles very naturally."),
    (4, "低"): (["这", "家", "商店", "价格", "很", "低"], "。", "The prices at this shop are very low."),
    (4, "作用"): (["这", "种", "药", "对", "感冒", "很", "有", "作用"], "。", "This medicine is very effective against colds."),
    (4, "满"): (["杯子", "里", "的", "茶", "满", "了"], "。", "The tea in the cup is full."),
    (4, "由于"): (["由于", "下雨", "比赛", "推迟", "了"], "。", "Due to the rain, the match was postponed.", {"hanzi": "由于下雨，比赛推迟了。", "pinyin": "Yóu yú xià yǔ, bǐ sài tuī chí le."}),
    (4, "过程"): (["学习", "汉语", "的", "过程", "很", "有趣"], "。", "The process of learning Chinese is very interesting."),
    (4, "即使"): (["即使", "明天", "下雨", "我", "也", "要", "去", "跑步"], "。", "Even if it rains tomorrow, I will still go jogging.", {"hanzi": "即使明天下雨，我也要去跑步。", "pinyin": "Jí shǐ míng tiān xià yǔ, wǒ yě yào qù pǎo bù."}),
    (4, "秒"): (["请", "等", "我", "十", "秒"], "。", "Please wait ten seconds for me."),
    (4, "比如"): (["我", "喜欢", "吃", "水果", "比如", "苹果", "和", "香蕉"], "。", "I like eating fruit, for example apples and bananas.", {"hanzi": "我喜欢吃水果，比如苹果和香蕉。", "pinyin": "Wǒ xǐ huan chī shuǐ guǒ, bǐ rú píng guǒ hé xiāng jiāo."}),
    (4, "无论"): (["无论", "天气", "怎么样", "他", "都", "坚持", "锻炼"], "。", "No matter what the weather is like, he keeps exercising.", {"hanzi": "无论天气怎么样，他都坚持锻炼。", "pinyin": "Wú lùn tiān qì zěn me yàng, tā dōu jiān chí duàn liàn."}),
    (4, "省"): (["坐", "地铁", "上班", "可以", "省", "很", "多", "时间"], "。", "Taking the subway to work can save a lot of time."),
    (4, "挂"): (["教室", "前面", "挂", "着", "一", "张", "世界", "地图"], "。", "A world map is hanging at the front of the classroom."),
    (4, "广告"): (["电视", "上", "的", "广告", "太", "多", "了"], "。", "There are too many commercials on TV."),
    (4, "难道"): (["难道", "你", "忘记", "今天", "的", "会议", "了", "吗"], "？", "Don't tell me you forgot today's meeting?"),
    (4, "于是"): (["下雨", "了", "于是", "我们", "坐", "出租车", "回", "家"], "。", "It started raining, so we took a taxi home.", {"hanzi": "下雨了，于是我们坐出租车回家。", "pinyin": "Xià yǔ le, yú shì wǒ men zuò chū zū chē huí jiā."}),
    (4, "规定"): (["学校", "规定", "学生", "不", "能", "迟到"], "。", "The school stipulates that students cannot be late."),
    (4, "够"): (["我", "带", "的", "钱", "不", "够"], "。", "The money I brought is not enough."),
    (4, "方向"): (["我们", "好像", "走", "错", "方向", "了"], "。", "It seems we walked in the wrong direction."),
    (4, "节"): (["今天", "上午", "有", "两", "节", "数学", "课"], "。", "There are two math classes this morning."),
    (4, "管理"): (["经理", "负责", "管理", "这", "家", "新", "公司"], "。", "The manager is in charge of managing this new company."),
    (4, "正式"): (["下", "星期", "一", "开始", "正式", "上班"], "。", "Official work starts next Monday."),
    (4, "内容"): (["这", "本", "书", "的", "内容", "非常", "丰富"], "。", "The content of this book is very rich."),
    (4, "感情"): (["爸爸", "和", "妈妈", "的", "感情", "非常", "好"], "。", "Dad and Mom have a very close relationship."),
    (4, "观众"): (["很", "多", "观众", "都", "喜欢", "这", "个", "节目"], "。", "Many viewers like this program."),
    (4, "破"): (["我", "的", "裤子", "不", "小心", "弄", "破", "了"], "。", "I accidentally tore my pants."),
    (4, "推"): (["请", "往", "里", "推", "门"], "。", "Please push the door inward."),
    (4, "标准"): (["她", "说", "的", "普通话", "非常", "标准"], "。", "Her Mandarin pronunciation is very standard."),
    (4, "毛"): (["这", "只", "小", "猫", "的", "毛", "很", "白"], "。", "This little cat's fur is very white.", {"pinyin": "Zhè zhī xiǎo māo de máo hěn bái."}),
    (4, "接着"): (["我们", "休息", "一会儿", "再", "接着", "学习"], "。", "Let's rest a bit and then continue studying."),
    (4, "速度"): (["地铁", "的", "速度", "比", "公共汽车", "快"], "。", "The subway is faster than the bus."),
    (4, "左右"): (["我", "晚上", "十", "点", "左右", "睡觉"], "。", "I go to bed around ten at night."),
    (4, "经济"): (["中国", "的", "经济", "发展", "非常", "快"], "。", "China's economy is developing very fast."),
    (4, "按照"): (["我们", "按照", "计划", "开始", "工作"], "。", "We are starting work according to the plan."),
    (4, "竟然"): (["他", "竟然", "忘记", "了", "自己", "的", "生日"], "！", "He actually forgot his own birthday!"),
    (4, "重点"): (["这", "次", "考试", "的", "重点", "是", "历史"], "。", "The focus of this exam is history."),
    (4, "否则"): (["快", "出发", "吧", "否则", "会", "迟到"], "！", "Let's set off quickly, otherwise we will be late!", {"hanzi": "快出发吧，否则会迟到！", "pinyin": "Kuài chū fā ba, fǒu zé huì chí dào!"}),
    (4, "动作"): (["他", "打篮球", "的", "动作", "很", "帅"], "。", "His basketball moves are very cool."),
    (4, "增加"): (["最近", "来", "中国", "旅游", "的", "人", "增加", "了"], "。", "The number of people traveling to China has increased recently."),
    (4, "举行"): (["学校", "明天", "下午", "举行", "比赛"], "。", "The school is holding a competition tomorrow afternoon."),
    (4, "著名"): (["长城", "是", "中国", "著名", "的", "地方"], "。", "The Great Wall is a famous place in China."),
    (4, "竞争"): (["这", "份", "工作", "的", "竞争", "很", "大"], "。", "The competition for this job is very intense."),
    (4, "估计"): (["我", "估计", "他", "今天", "不", "会", "来", "了"], "。", "I reckon he won't come today."),
    (4, "吸引"): (["他", "的", "表演", "吸引", "了", "很", "多", "观众"], "。", "His performance attracted many viewers."),
    (4, "共同"): (["我们", "有", "很", "多", "共同", "的", "爱好"], "。", "We have many hobbies in common."),
    (4, "既然"): (["既然", "你", "累", "了", "就", "先", "休息", "吧"], "。", "Since you're tired, take a rest first.", {"hanzi": "既然你累了，就先休息吧。", "pinyin": "Jì rán nǐ lèi le, jiù xiān xiū xi ba."}),
    (4, "提醒"): (["谢谢", "你", "提醒", "我", "带", "伞"], "！", "Thank you for reminding me to bring an umbrella!"),
    (4, "周围"): (["公园", "周围", "有", "很", "多", "饭店"], "。", "There are many restaurants around the park."),
    (4, "距离"): (["这", "里", "距离", "火车站", "不", "远"], "。", "It is not far from here to the train station."),
    (4, "价格"): (["超市", "里", "面包", "的", "价格", "很", "便宜"], "。", "The price of bread at the supermarket is very cheap."),
    (4, "然而"): (["他", "很", "努力", "然而", "结果", "不", "理想"], "。", "He worked very hard; however, the result was not ideal.", {"hanzi": "他很努力，然而结果不理想。", "pinyin": "Tā hěn nǔ lì, rán ér jié guǒ bù lǐ xiǎng."}),
    (4, "交流"): (["我", "喜欢", "和", "其他", "国家", "的", "朋友", "交流"], "。", "I like communicating with friends from other countries."),
    (4, "地点"): (["明天", "会议", "的", "地点", "在", "三", "楼"], "。", "The venue for tomorrow's meeting is on the third floor."),
    (4, "皮肤"): (["多", "喝", "水", "对", "皮肤", "很", "好"], "。", "Drinking more water is very good for your skin."),
    (4, "语言"): (["学习", "一", "门", "新", "语言", "需要", "耐心"], "。", "Learning a new language requires patience."),
    (4, "网站"): (["这", "个", "购物", "网站", "非常", "方便"], "。", "This shopping website is very convenient."),
    (4, "回忆"): (["那", "次", "旅游", "是", "我", "最", "好", "的", "回忆"], "。", "That trip is my fondest memory."),
    (4, "材料"): (["做", "蛋糕", "的", "材料", "都", "准备", "好", "了"], "。", "All the ingredients for making the cake are ready."),
    (4, "相同"): (["我们", "对", "这", "件", "事情", "的", "看法", "完全", "相同"], "。", "Our views on this matter are completely identical."),
    (4, "赶"): (["我们", "要", "快", "点", "去", "机场", "赶", "飞机"], "。", "We need to hurry to the airport to catch the flight."),
    (4, "赚"): (["他", "每", "个", "月", "赚", "不", "少", "钱"], "。", "He earns quite a bit of money every month."),
    (4, "大约"): (["从", "学校", "到", "机场", "大约", "半", "小时"], "。", "It takes about half an hour from school to the airport."),
    (4, "暂时"): (["他", "暂时", "住", "在", "朋友", "家"], "。", "He is temporarily staying at a friend's place."),
    (4, "及时"): (["谢谢", "你", "及时", "告诉", "我", "这", "个", "消息"], "！", "Thank you for telling me this news in time!"),
    (4, "专门"): (["妈妈", "专门", "为", "我", "做", "了", "鱼"], "。", "Mom specially cooked fish for me."),
    (4, "举"): (["他", "把", "杯子", "举", "起来", "了"], "。", "He raised his glass."),
    (4, "苦"): (["这", "种", "药", "太", "苦", "了"], "！", "This medicine is way too bitter!"),
    (4, "租"): (["我", "在", "公司", "附近", "租", "了", "个", "房间"], "。", "I rented a room near the company."),
    (4, "信心"): (["她", "对", "明天", "的", "考试", "很", "有", "信心"], "。", "She is very confident about tomorrow's exam."),
    (4, "博士"): (["我", "姐姐", "正在", "读", "博士"], "。", "My older sister is currently studying for a PhD."),
    (4, "正好"): (["这", "件", "衣服", "我", "穿", "着", "正好"], "。", "This piece of clothing fits me just right."),
    (4, "理想"): (["当", "老师", "是", "她", "从", "小", "的", "理想"], "。", "Becoming a teacher has been her dream since childhood."),
    (4, "举办"): (["北京", "成功", "举办", "了", "这", "次", "活动"], "。", "Beijing successfully hosted this event."),
    (4, "流行"): (["现在", "骑", "自行车", "上班", "很", "流行"], "。", "Cycling to work is very popular nowadays."),
    (4, "整理"): (["周末", "我", "把", "房间", "整理", "干净", "了"], "。", "Over the weekend, I tidied up the room cleanly."),
    (4, "质量"): (["这", "双", "皮鞋", "的", "质量", "非常", "好"], "。", "The quality of this pair of leather shoes is very good."),
    (4, "准确"): (["这", "个", "手表", "的", "时间", "很", "准确"], "。", "This watch keeps very accurate time."),
    (4, "禁止"): (["公园", "里", "禁止", "抽烟"], "。", "Smoking is prohibited in the park."),
    (4, "数量"): (["请", "检查", "一下", "苹果", "的", "数量"], "。", "Please check the quantity of apples."),
    (4, "富"): (["他", "虽然", "不", "富", "但是", "很", "快乐"], "。", "Although he is not rich, he is very happy.", {"hanzi": "他虽然不富，但是很快乐。", "pinyin": "Tā suī rán bù fù, dàn shì hěn kuài lè."}),
    (4, "性格"): (["我", "新", "同事", "的", "性格", "非常", "热情"], "。", "My new colleague has a very warm personality."),
    (4, "降低"): (["商店", "降低", "了", "这", "件", "衣服", "的", "价格"], "。", "The store lowered the price of this piece of clothing."),
    # --- HSK 4 (Lot 2/2: 105 entries) ---
    (4, "随着"): (["随着", "经济", "的", "发展", "生活", "更", "方便", "了"], "。", "Along with economic development, life has become more convenient.", {"hanzi": "随着经济的发展，生活更方便了。", "pinyin": "Suí zhe jīng jì de fā zhǎn, shēng huó gèng fāng biàn le."}),
    (4, "只好"): (["下雨", "了", "我们", "只好", "在", "家", "看", "电影"], "。", "It rained, so we had no choice but to watch a movie at home.", {"hanzi": "下雨了，我们只好在家看电影。", "pinyin": "Xià yǔ le, wǒ men zhǐ hǎo zài jiā kàn diàn yǐng."}),
    (4, "趟"): (["下", "星期", "我", "要", "去", "一", "趟", "上海"], "。", "Next week I need to make a trip to Shanghai."),
    (4, "尝"): (["请", "尝", "一下", "妈妈", "做", "的", "饺子"], "。", "Please taste the dumplings Mom made."),
    (4, "对话"): (["请", "大家", "听", "这", "段", "中文", "对话"], "。", "Everyone, please listen to this Chinese dialogue."),
    (4, "广播"): (["爷爷", "每", "天", "早上", "喜欢", "听", "广播"], "。", "Grandpa likes listening to the radio every morning."),
    (4, "污染"): (["这", "个", "地方", "几乎", "没有", "空气", "污染"], "。", "There is almost no air pollution in this place."),
    (4, "温度"): (["今天", "晚上", "的", "温度", "很", "低"], "。", "The temperature tonight is very low."),
    (4, "例如"): (["他", "会", "很", "多", "运动", "例如", "游泳", "和", "跑步"], "。", "He can do many sports, for example swimming and jogging.", {"hanzi": "他会很多运动，例如游泳和跑步。", "pinyin": "Tā huì hěn duō yùn dòng, lì rú yóu yǒng hé pǎo bù."}),
    (4, "祝贺"): (["祝贺", "你", "找", "到", "了", "新", "工作"], "！", "Congratulations on finding a new job!"),
    (4, "重视"): (["老师", "非常", "重视", "学生", "的", "健康"], "。", "The teacher attaches great importance to students' health."),
    (4, "抬"): (["请", "和", "我", "一起", "抬", "这", "张", "桌子"], "。", "Please lift this table together with me."),
    (4, "功夫"): (["很", "多", "朋友", "对", "中国", "功夫", "非常", "感兴趣"], "。", "Many friends are very interested in Chinese kung fu."),
    (4, "民族"): (["中国", "有", "五十六", "个", "民族"], "。", "China has fifty-six ethnic groups."),
    (4, "航班"): (["今天", "去", "北京", "的", "航班", "推迟", "了"], "。", "Today's flight to Beijing has been delayed."),
    (4, "厚"): (["这", "本", "词典", "非常", "厚"], "。", "This dictionary is very thick."),
    (4, "参观"): (["我们", "周末", "去", "参观", "了", "长城"], "。", "We went to visit the Great Wall over the weekend."),
    (4, "顺便"): (["你", "去", "超市", "顺便", "买", "些", "牛奶", "吧"], "。", "While you're at the supermarket, pick up some milk too."),
    (4, "友谊"): (["这", "份", "友谊", "对", "我", "很", "重要"], "。", "This friendship is very important to me."),
    (4, "植物"): (["公园", "里", "有", "各", "种", "漂亮", "的", "植物"], "。", "There are all kinds of pretty plants in the park."),
    (4, "对面"): (["银行", "就", "在", "医院", "对面"], "。", "The bank is right across from the hospital."),
    (4, "其次"): (["首先", "检查", "质量", "其次", "比较", "价格"], "。", "First check the quality, and secondly compare the price.", {"hanzi": "首先检查质量，其次比较价格。", "pinyin": "Shǒu xiān jiǎn chá zhì liàng, qí cì bǐ jiào jià gé."}),
    (4, "敲"): (["进", "办公室", "以前", "请", "先", "敲", "门"], "。", "Please knock on the door first before entering the office."),
    (4, "特点"): (["每", "个", "季节", "都", "有", "自己", "的", "特点"], "。", "Every season has its own characteristics."),
    (4, "往往"): (["周末", "超市", "里", "往往", "人", "很", "多"], "。", "Supermarkets are usually very crowded on weekends."),
    (4, "海洋"): (["海洋", "里", "生活", "着", "很", "多", "鱼"], "。", "Many fish live in the ocean."),
    (4, "家具"): (["我们", "为", "新", "家", "买", "了", "家具"], "。", "We bought furniture for our new home."),
    (4, "棵"): (["学校", "前面", "有", "两", "棵", "大", "树"], "。", "There are two big trees in front of the school."),
    (4, "稍微"): (["今天", "比", "昨天", "稍微", "暖和", "一点儿"], "。", "Today is a little warmer than yesterday."),
    (4, "缺少"): (["他", "现在", "还", "缺少", "工作", "经验"], "。", "He still lacks work experience right now."),
    (4, "打扮"): (["姐姐", "今天", "打扮", "得", "真", "漂亮"], "！", "My older sister is dressed up really nicely today!"),
    (4, "吃惊"): (["听", "到", "这", "个", "消息", "大家", "都", "很", "吃惊"], "。", "Everyone was very surprised upon hearing this news."),
    (4, "报名"): (["我", "已经", "报名", "参加", "汉语", "比赛", "了"], "。", "I have already signed up for the Chinese competition."),
    (4, "日记"): (["妹妹", "每", "天", "晚上", "都", "写", "日记"], "。", "My younger sister writes in her diary every night."),
    (4, "总结"): (["经理", "在", "会议", "最后", "总结", "了", "计划"], "。", "The manager summarized the plan at the end of the meeting."),
    (4, "幽默"): (["我们", "的", "历史", "老师", "非常", "幽默"], "。", "Our history teacher is very humorous."),
    (4, "师傅"): (["这", "位", "师傅", "的", "普通话", "很", "标准"], "。", "This master's Mandarin is very standard."),
    (4, "镜子"): (["她", "在", "镜子", "前面", "整理", "头发"], "。", "She is fixing her hair in front of the mirror."),
    (4, "亲戚"): (["周末", "爸爸", "妈妈", "去", "看", "亲戚", "了"], "。", "Dad and Mom went to visit relatives over the weekend."),
    (4, "力气"): (["哥哥", "的", "力气", "非常", "大"], "。", "My older brother is very strong."),
    (4, "胳膊"): (["他", "的", "胳膊", "比", "我", "长"], "。", "His arms are longer than mine."),
    (4, "乘坐"): (["请", "大家", "排队", "乘坐", "电梯"], "。", "Everyone, please line up to take the elevator."),
    (4, "孙子"): (["奶奶", "非常", "喜欢", "她", "的", "小", "孙子"], "。", "Grandma loves her little grandson very much."),
    (4, "客厅"): (["客人", "都", "坐", "在", "客厅", "里", "喝", "茶"], "。", "The guests are all sitting in the living room drinking tea."),
    (4, "普遍"): (["现在", "上网", "购物", "已经", "很", "普遍", "了"], "。", "Shopping online is already very common nowadays."),
    (4, "热闹"): (["节日", "的", "街道", "非常", "热闹"], "。", "The streets during the festival are very lively."),
    (4, "顺序"): (["请", "按照", "顺序", "回答", "问题"], "。", "Please answer the questions in order."),
    (4, "得意"): (["他", "对", "自己", "的", "成绩", "非常", "得意"], "。", "He is very proud of his grades."),
    (4, "葡萄"): (["这", "些", "新鲜", "的", "葡萄", "真", "甜"], "！", "These fresh grapes are really sweet!"),
    (4, "性别"): (["请", "在", "表格", "里", "写", "上", "性别"], "。", "Please write your gender on the form."),
    (4, "毛巾"): (["请", "给", "我", "一", "条", "干净", "的", "毛巾"], "。", "Please give me a clean towel."),
    (4, "降落"): (["飞机", "马上", "就", "要", "降落", "了"], "。", "The plane is about to land right away."),
    (4, "咸"): (["今天", "的", "汤", "有", "点", "咸"], "。", "Today's soup is a bit salty."),
    (4, "节约"): (["我们", "都", "应该", "节约", "用", "水"], "。", "We should all conserve water."),
    (4, "招聘"): (["那", "家", "公司", "正在", "招聘", "经理"], "。", "That company is currently recruiting a manager."),
    (4, "活泼"): (["这", "个", "小", "孩子", "性格", "非常", "活泼"], "。", "This little child has a very lively personality."),
    (4, "打印"): (["请", "给", "我", "打印", "两", "份", "材料"], "。", "Please print two copies of the materials for me."),
    (4, "排列"): (["请", "把", "这", "些", "书", "按照", "顺序", "排列", "好"], "。", "Please arrange these books neatly in order."),
    (4, "积累"): (["学习", "语言", "需要", "每", "天", "积累", "词语"], "。", "Learning a language requires accumulating words every day."),
    (4, "景色"): (["秋", "天", "公园", "的", "景色", "非常", "漂亮"], "。", "The scenery in the park in autumn is very beautiful."),
    (4, "表格"): (["请", "把", "这", "张", "表格", "交", "给", "老师"], "。", "Please hand this form to the teacher."),
    (4, "表扬"): (["老师", "表扬", "了", "认真", "学习", "的", "同学"], "。", "The teacher praised the students who studied hard."),
    (4, "硕士"): (["他", "在", "北京", "读", "完", "了", "硕士"], "。", "He finished his master's degree in Beijing."),
    (4, "牙膏"): (["家", "里", "的", "牙膏", "用", "完", "了"], "。", "The toothpaste at home has run out."),
    (4, "房东"): (["我们", "的", "房东", "人", "非常", "热情"], "。", "Our landlord is a very warm person."),
    (4, "长城"): (["很", "多", "客人", "来", "北京", "都", "想", "去", "长城"], "。", "Many visitors who come to Beijing want to visit the Great Wall."),
    (4, "导游"): (["导游", "正在", "给", "大家", "介绍", "北京", "历史"], "。", "The tour guide is introducing Beijing's history to everyone."),
    (4, "小吃"): (["这", "条", "街道", "有", "很", "多", "好吃", "的", "小吃"], "。", "This street has many delicious snacks."),
    (4, "叶子"): (["秋", "天", "树", "上", "的", "叶子", "红", "了"], "。", "In autumn, the leaves on the trees turn red."),
    (4, "打折"): (["周末", "这", "家", "商店", "的", "衣服", "打折"], "。", "The clothes at this shop are on sale over the weekend."),
    (4, "词语"): (["我", "今天", "学习", "了", "十", "个", "新", "词语"], "。", "I learned ten new words today."),
    (4, "堵车"): (["早上", "上班", "的", "时候", "路", "上", "经常", "堵车"], "。", "There are often traffic jams on the road during the morning commute."),
    (4, "饺子"): (["妈妈", "包", "的", "饺子", "真", "好吃"], "！", "The dumplings Mom makes are really delicious!"),
    (4, "应聘"): (["哥哥", "明天", "去", "那", "家", "公司", "应聘"], "。", "My older brother is going to apply for a job at that company tomorrow."),
    (4, "打针"): (["小", "孩子", "一般", "都", "害怕", "打针"], "。", "Little children are generally afraid of getting an injection."),
    (4, "干杯"): (["为了", "我们", "的", "友谊", "干杯"], "！", "Cheers to our friendship!"),
    (4, "复印"): (["请", "给", "我", "复印", "一下", "护照"], "。", "Please photocopy my passport for me."),
    (4, "国籍"): (["请", "在", "表格", "上", "写", "清楚", "你", "的", "国籍"], "。", "Please clearly write your nationality on the form."),
    (4, "包子"): (["我", "早上", "吃", "了", "两", "个", "热", "包子"], "。", "I ate two hot steamed buns this morning."),
    (4, "流利"): (["他", "的", "中文", "说", "得", "非常", "流利"], "。", "He speaks Chinese very fluently."),
    (4, "橡皮"): (["我", "可以", "借", "你", "的", "橡皮", "用", "一下", "吗"], "？", "May I borrow your eraser for a moment?"),
    (4, "长江"): (["长江", "在", "中国", "非常", "有名"], "。", "The Yangtze River is very famous in China."),
    (4, "马虎"): (["做", "事情", "一定", "不", "能", "马虎"], "。", "You must never be careless when doing things."),
    (4, "寒假"): (["寒假", "我", "打算", "和", "朋友", "去", "上海", "玩"], "。", "During winter vacation, I plan to go visit Shanghai with friends."),
    (4, "粗心"): (["他", "工作", "的", "时候", "太", "粗心", "了"], "。", "He is too careless when he works."),
    (4, "烤鸭"): (["来", "北京", "旅游", "一定", "要", "尝", "烤鸭"], "。", "When traveling to Beijing, you must try roast duck."),
    (4, "预习"): (["我", "每", "天", "晚上", "都", "会", "预习", "明天", "的", "课"], "。", "I preview tomorrow's lesson every evening."),
    (4, "占线"): (["我", "给", "他", "打电话", "但是", "一直", "占线"], "。", "I called him, but the line has been busy the whole time.", {"hanzi": "我给他打电话，但是一直占线。", "pinyin": "Wǒ gěi tā dǎ diàn huà, dàn shì yī zhí zhàn xiàn."}),
    (4, "京剧"): (["爷爷", "最", "喜欢", "在", "电视", "上", "看", "京剧"], "。", "Grandpa likes watching Beijing opera on TV the most."),
    (4, "填空"): (["这", "几", "个", "填空", "题", "不", "难"], "。", "These fill-in-the-blank questions are not hard."),
    (4, "小伙子"): (["这", "个", "小伙子", "工作", "非常", "努力"], "。", "This young man works very hard."),
    (4, "互联网"): (["互联网", "让", "大家", "交流", "更", "方便", "了"], "。", "The internet makes it more convenient for everyone to communicate."),
    (4, "来得及"): (["现在", "出发", "还", "来得及", "赶", "飞机"], "。", "If we leave now, there is still enough time to catch the flight."),
    (4, "加油站"): (["前面", "右边", "就", "有", "一", "个", "加油站"], "。", "There is a gas station right ahead on the right."),
    (4, "乒乓球"): (["叔叔", "周末", "经常", "看", "乒乓球", "比赛"], "。", "My uncle often watches table tennis matches on weekends."),
    (4, "西红柿"): (["妈妈", "买", "了", "西红柿", "和", "鸡蛋"], "。", "Mom bought tomatoes and eggs."),
    (4, "大使馆"): (["他", "上午", "去", "大使馆", "拿", "护照", "了"], "。", "He went to the embassy this morning to pick up his passport."),
    (4, "塑料袋"): (["去", "超市", "购物", "请", "少", "用", "塑料袋"], "。", "Please use fewer plastic bags when shopping at the supermarket."),
    (4, "矿泉水"): (["这", "个", "瓶子", "里", "是", "矿泉水"], "。", "There is mineral water in this bottle."),
    (4, "羽毛球"): (["姐姐", "最", "喜欢", "的", "运动", "是", "羽毛球"], "。", "My older sister's favorite sport is badminton."),
    (4, "售货员"): (["这", "家", "商店", "的", "售货员", "非常", "热情"], "。", "The salespersons at this store are very welcoming."),
    (4, "登机牌"): (["请", "提前", "准备", "好", "护照", "和", "登机牌"], "。", "Please have your passport and boarding pass ready in advance."),
    (4, "礼拜天"): (["礼拜天", "我", "打算", "在", "家", "休息"], "。", "On Sunday, I plan to rest at home."),
    (4, "百分之"): (["我们", "班", "百分之", "八十", "的", "学生", "喜欢", "音乐"], "。", "Eighty percent of the students in our class like music."),
    (4, "高速公路"): (["这", "条", "高速公路", "直接", "到", "机场"], "。", "This highway goes directly to the airport."),
    (4, "不仅"): (["她", "不仅", "友好", "还", "很", "诚实"], "。", "She is not only kind, but also very honest.", {"hanzi": "她不仅友好，还很诚实。", "pinyin": "Tā bù jǐn yǒu hǎo, hái hěn chéng shí."}),
    (4, "厕所"): (["餐厅", "旁边", "有", "个", "厕所"], "。", "There is a restroom next to the restaurant."),
    (4, "扔"): (["不", "要", "往", "地", "上", "扔", "任何", "东西"], "。", "Don't throw anything on the floor.", {"pinyin": "Bù yào wǎng dì shàng rēng rèn hé dōng xi."}),
    (4, "重"): (["我", "得", "了", "重", "感冒"], "。", "I caught a bad cold.", {"pinyin": "Wǒ dé le zhòng gǎn mào."}),
    (4, "商量"): (["我", "得", "找", "人", "商量", "一下"], "。", "I need to find someone to talk it over with.", {"pinyin": "Wǒ děi zhǎo rén shāng liang yíxià."}),
    (4, "眼镜"): (["他", "每", "天", "都", "戴", "着", "眼镜"], "。", "He wears glasses every day."),
    (4, "熟悉"): (["他", "非常", "熟悉", "中国", "文化"], "。", "He is very familiar with Chinese culture."),
    (4, "内"): (["一", "个", "小时", "内", "就", "能", "到", "机场"], "。", "We can arrive at the airport within an hour."),
    (4, "作者"): (["这", "本", "书", "的", "作者", "非常", "有名"], "。", "The author of this book is very famous."),
    (4, "通知"): (["请", "通知", "大家", "明天", "不", "上班"], "。", "Please inform everyone that there is no work tomorrow."),
    (4, "感觉"): (["我", "今天", "感觉", "很", "舒服"], "。", "I feel very comfortable today."),
    (4, "好像"): (["他", "今天", "好像", "生病", "了"], "。", "It seems he got sick today."),
    (4, "死"): (["公园", "里", "的", "那", "棵", "树", "死", "了"], "。", "That tree in the park died."),
    (4, "调查"): (["警察", "正在", "调查", "这", "件", "事情"], "。", "The police are investigating this matter."),
    (4, "台"): (["我", "买", "了", "一", "台", "新", "电脑"], "。", "I bought a new computer."),
    (4, "打招呼"): (["邻居", "每", "次", "见面", "都", "和", "我", "打招呼"], "。", "My neighbor greets me every time we meet."),
    (4, "出现"): (["问题", "是", "怎么", "出现", "的"], "？", "How did the problem occur?"),
    (4, "警察"): (["这", "位", "警察", "在", "做", "什么"], "？", "What is this police officer doing?"),
    (4, "笨"): (["这", "只", "小", "狗", "一点儿", "也", "不", "笨"], "。", "This little dog is not stupid at all.", {"pinyin": "Zhè zhī xiǎo gǒu yī diǎnr yě bù bèn."}),
    (4, "躺"): (["小", "猫", "躺", "在", "沙发", "上"], "。", "The little cat is lying on the sofa."),
    (4, "顾客"): (["最近", "他", "的", "顾客", "减少", "了"], "。", "Recently his customers have decreased."),
    (4, "减少"): (["这", "个", "城市", "的", "空气", "污染", "减少", "了", "很", "多"], "。", "Air pollution in this city has decreased a lot."),
    (4, "散步"): (["爷爷", "每", "天", "晚上", "去", "公园", "散步"], "。", "Grandpa goes for a walk in the park every evening."),
    (4, "提供"): (["这", "家", "宾馆", "提供", "免费", "饮料"], "。", "This hotel provides free drinks."),
    # --- HSK 5 ---
    (5, "滑"): (["小心", "地", "滑"], "！", "Watch out, slippery floor!", {"pinyin": "Xiǎo xīn dì huá!"}),
    (5, "踩"): (["不", "要", "踩", "草", "地"], "。", "Don't step on the grass.", {"pinyin": "Bù yào cǎi cǎo dì."}),
    (5, "抓"): (["这", "只", "猫", "不", "抓", "老鼠"], "。", "This cat doesn't catch mice.", {"pinyin": "Zhè zhī māo bù zhuā lǎo shǔ."}),
    (5, "老鼠"): (["这", "只", "猫", "不", "抓", "老鼠"], "。", "This cat doesn't catch mice.", {"pinyin": "Zhè zhī māo bù zhuā lǎo shǔ."}),
    (5, "确认"): (["但是", "所有", "这", "些", "数据", "都", "没有", "得", "到", "确认"], "。", "However, none of this data has been confirmed.", {"pinyin": "Dàn shì suǒ yǒu zhè xiē shù jù dōu méi yǒu dé dào què rèn."}),
    (5, "奋斗"): (["我们", "要", "为", "梦想", "努力", "奋斗"], "。", "We must strive hard for our dreams."),
    (5, "观念"): (["我们", "得", "有", "现代", "观念", "来", "理解", "这", "数据"], "。", "We must have a modern mindset to understand this data.", {"pinyin": "Wǒ men děi yǒu xiàn dài guān niàn lái lǐ jiě zhè shù jù."}),
    (5, "成语"): (["我", "把", "成语", "全部", "还", "给", "老师", "了"], "。", "I forgot all the idioms I learned from the teacher.", {"pinyin": "Wǒ bǎ chéng yǔ quán bù huán gěi lǎo shī le."}),
    (5, "鼠标"): (["这", "个", "鼠标", "突然", "坏", "了"], "。", "This mouse suddenly broke."),
    (5, "点心"): (["我", "买", "了", "一", "些", "中国", "点心"], "。", "I bought some Chinese pastries."),
    # --- HSK 6 ---
    (6, "屁股"): (["小", "孩子", "摔", "到", "了", "屁股"], "。", "The little child fell on their bottom."),
    (6, "把手"): (["这", "个", "门", "的", "把手", "坏", "了"], "。", "The handle of this door is broken."),
    (6, "部位"): (["医生", "仔细", "检查", "了", "受伤", "的", "部位"], "。", "The doctor carefully examined the injured area."),
    (6, "处境"): (["我", "完全", "理解", "你", "的", "处境"], "。", "I completely understand your situation."),
    (6, "熬"): (["妈妈", "正在", "厨房", "熬", "汤"], "。", "Mom is simmering soup in the kitchen."),
    (6, "愈"): (["天气", "愈", "加", "寒冷", "了"], "。", "The weather has grown increasingly cold.", {"pinyin": "Tiān qì yù jiā hán lěng le."}),
    (6, "委员"): (["他", "是", "这", "个", "组织", "的", "委员"], "。", "He is a committee member of this organization."),
    (6, "挪"): (["请", "把", "这", "张", "桌子", "往", "旁边", "挪", "一下"], "。", "Please move this table aside a bit."),
    (6, "依靠"): (["我们", "得", "依靠", "自己"], "。", "We're on our own.", {"pinyin": "Wǒ men děi yī kào zì jǐ."}),
    (6, "过渡"): (["它", "得", "是", "一个", "过渡"], "。", "It has to be about transition.", {"pinyin": "Tā děi shì yígè guò dù."}),
    (6, "效益"): (["我", "得", "有", "成本", "效益", "和", "文化", "敏感性"], "。", "I have to be cost-effective and culturally sensitive.", {"pinyin": "Wǒ děi yǒu chéng běn xiào yì hé wén huà mǐngǎnxìng."}),
    (6, "抚摸"): (["他", "在", "抚摸", "那", "只", "狗"], "。", "He was petting the dog.", {"pinyin": "Tā zài fǔ mō nà zhī gǒu."}),
    (6, "体积"): (["那", "只", "鸟", "的", "体积", "有", "半只", "老鹰", "那么", "大"], "。", "The bird was half the size of a hawk.", {"pinyin": "Nà zhī niǎo de tǐ jī yǒu bàn zhī lǎoyīng nàme dà."}),
    (6, "公认"): (["鲸", "是", "被", "公认", "为", "最大", "的", "哺乳动物"], "。", "The whale is well known to be the largest mammal.", {"pinyin": "Jīng shì bèi gōng rèn wéi zuìdà de bǔrǔdòngwù."}),
}

VERB_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (1, ["我们", "现在", "{w}"], "。", "We are going to {en}."),
    (1, ["他", "明天", "去", "{w}"], "。", "He will go to {en} tomorrow."),
    (1, ["我", "很", "想", "{w}"], "。", "I really want to {en}."),
    (1, ["她", "喜欢", "{w}"], "。", "She likes to {en}."),
    (1, ["请", "你", "多", "{w}"], "。", "Please {en} more often."),
    (2, ["朋友", "也", "会", "{w}"], "。", "My friend can also {en}."),
    (2, ["大家", "一起", "{w}", "吧"], "！", "Let's all {en} together!"),
    (2, ["她", "正在", "{w}", "呢"], "。", "She is currently doing this: {en}."),
    (2, ["我们", "开始", "{w}", "了"], "。", "We have started to {en}."),
    (2, ["我", "觉得", "可以", "{w}"], "。", "I think we can {en}."),
    (2, ["明天", "再", "{w}", "吧"], "。", "Let's {en} tomorrow instead."),
    (3, ["我们", "应该", "认真", "{w}"], "。", "We should seriously {en}."),
    (3, ["我", "相信", "她", "能", "{w}"], "。", "I believe she can {en}."),
    (3, ["大家", "需要", "{w}"], "。", "Everyone needs to {en}."),
]

NOUN_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (1, ["这", "个", "{w}", "很", "好"], "。", "This ({en}) is very good."),
    (1, ["我", "看见", "那", "个", "{w}"], "。", "I saw that ({en})."),
    (1, ["老师", "有", "一", "个", "{w}"], "。", "The teacher has one ({en})."),
    (1, ["她", "很", "喜欢", "{w}"], "。", "She really likes ({en})."),
    (1, ["我们", "今天", "看", "{w}"], "。", "Today we are looking at ({en})."),
    (1, ["学校", "里", "有", "{w}"], "。", "The school has ({en})."),
    (2, ["大家", "都", "知道", "{w}"], "。", "Everyone knows about ({en})."),
    (2, ["我", "很", "喜欢", "新", "{w}"], "。", "I really like the new ({en})."),
    (2, ["这", "个", "{w}", "非常", "好"], "。", "This ({en}) is extremely good."),
    (2, ["老师", "正在", "介绍", "{w}"], "。", "The teacher is introducing ({en})."),
    (3, ["这", "个", "{w}", "很", "重要"], "。", "This ({en}) is very important."),
    (3, ["大家", "需要", "明白", "{w}"], "。", "Everyone needs to understand ({en})."),
]

ADJ_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (1, ["今天", "天气", "很", "{w}"], "。", "The weather is very {en} today."),
    (1, ["这", "个", "很", "{w}"], "。", "This is very {en}."),
    (1, ["他", "现在", "很", "{w}"], "。", "He is very {en} right now."),
    (1, ["她", "的", "朋友", "很", "{w}"], "。", "Her friend is very {en}."),
    (1, ["中国", "菜", "很", "{w}"], "。", "Chinese food is very {en}."),
    (2, ["这", "件", "事情", "非常", "{w}"], "。", "This matter is extremely {en}."),
    (2, ["我", "觉得", "这", "很", "{w}"], "。", "I find this very {en}."),
    (2, ["今天", "早上", "真", "{w}"], "！", "This morning is really {en}!"),
    (3, ["这", "个", "地方", "很", "{w}"], "。", "This place is very {en}."),
    (3, ["我们", "的", "环境", "真", "{w}"], "！", "Our environment is truly {en}!"),
]

ADV_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (1, ["我们", "{w}", "去", "学校"], "。", "We go to school ({en})."),
    (1, ["她", "{w}", "在", "家", "学习"], "。", "She studies at home ({en})."),
    (1, ["我", "{w}", "看", "这", "本", "书"], "。", "I read this book ({en})."),
    (2, ["大家", "{w}", "看", "这", "本", "书"], "。", "Everyone reads this book ({en})."),
    (2, ["他", "{w}", "给", "朋友", "打电话"], "。", "He calls his friend ({en})."),
    (3, ["他", "一直", "{w}", "工作"], "。", "He keeps working ({en})."),
    (3, ["她", "{w}", "准备", "去", "北京"], "。", "She prepares to go to Beijing ({en})."),
]

IDIOM_TEMPLATES: list[tuple[int, list[str], str, str]] = [
    (2, ["大家", "一起", "学习", "{w}"], "。", "Let's learn together: {en}."),
    (2, ["大家", "都", "知道", "{w}"], "。", "Everyone knows: {en}."),
    (3, ["老师", "教", "大家", "{w}"], "。", "The teacher teaches everyone: {en}."),
    (3, ["老师", "经常", "讲", "{w}"], "。", "The teacher often explains: {en}."),
    (3, ["我们", "应该", "明白", "{w}"], "。", "We should understand: {en}."),
    (3, ["大家", "需要", "记得", "{w}"], "。", "Everyone should remember: {en}."),
]

SANDHI_OR_PARTICLE_WORDS = {
    "一", "不", "的", "了", "吗", "呢", "吧", "着", "过", "得", "地", "啊",
}


def norm_tonal_pinyin(py: str) -> str:
    return re.sub(r"[^a-zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü]", "", py.lower().replace("v", "ü"))


def is_example_pinyin_consistent(word_hanzi: str, word_py: str, ex_py: str) -> bool:
    n_w = norm_tonal_pinyin(word_py)
    n_ex = norm_tonal_pinyin(ex_py)
    if n_w in n_ex:
        return True
    if word_hanzi in SANDHI_OR_PARTICLE_WORDS:
        return True
    return False


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
    for tok in jieba.lcut(body):
        if word_to_level.get(tok, 0) > level:
            return True
    return False


def tokens_to_pinyin(tokens: list[str], punct: str, word_to_pinyin: dict[str, str]) -> str:
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


def short_english_gloss(english: str) -> str:
    head = english.split("/")[0].strip()
    if not head:
        return english
    return head[0].lower() + head[1:]


def build_handcrafted_example(
    entry: HandcraftedEntry,
    word_to_pinyin: dict[str, str],
) -> dict[str, Any]:
    if len(entry) == 4:
        tokens, punct, english, overrides = entry
    else:
        tokens, punct, english = entry
        overrides = {}
    hanzi = overrides.get("hanzi") or ("".join(tokens) + punct)
    pinyin = overrides.get("pinyin") or tokens_to_pinyin(tokens, punct, word_to_pinyin)
    return {
        "hanzi": hanzi,
        "pinyin": pinyin,
        "english": english,
        "tokens": tokens,
    }


def verify_static_templates(word_to_level: dict[str, int]) -> None:
    for (lv, hanzi), entry in HANDCRAFTED_EXAMPLES.items():
        if len(entry) == 4:
            tokens, punct, english, overrides = entry
        else:
            tokens, punct, english = entry
            overrides = {}
        allowed = {w for w, l in word_to_level.items() if l <= lv} | ALLOWED_SIMPLE_PROPER_NOUNS
        assert len(tokens) >= 3, f"HANDCRAFTED ({lv}, {hanzi}) < 3 tokens: {tokens}"
        assert all("，" not in t for t in tokens), f"HANDCRAFTED ({lv}, {hanzi}) comma in tokens: {tokens}"
        assert len(tokens) == len(set(tokens)), f"HANDCRAFTED ({lv}, {hanzi}) duplicate token: {tokens}"
        assert hanzi in tokens, f"HANDCRAFTED ({lv}, {hanzi}) missing target: {tokens}"
        ex_hanzi = overrides.get("hanzi") or ("".join(tokens) + punct)
        assert "".join(tokens) == ex_hanzi.rstrip(TRAILING_PUNCT).replace("，", ""), (
            f"HANDCRAFTED ({lv}, {hanzi}) mismatched hanzi {ex_hanzi!r} vs {tokens}"
        )
        assert ex_hanzi.count(hanzi) == 1, f"HANDCRAFTED ({lv}, {hanzi}) Cloze count != 1 in {ex_hanzi!r}"
        if lv <= 5:
            for t in tokens:
                assert t in allowed or DIGITS_RE.match(t), (
                    f"HANDCRAFTED ({lv}, {hanzi}) forbidden token '{t}' (level {word_to_level.get(t)})"
                )
        assert punct in {"。", "？", "！"}
        assert english.strip()

    all_pools = [
        ("VERB", VERB_TEMPLATES),
        ("NOUN", NOUN_TEMPLATES),
        ("ADJ", ADJ_TEMPLATES),
        ("ADV", ADV_TEMPLATES),
        ("IDIOM", IDIOM_TEMPLATES),
    ]
    for pool_name, pool in all_pools:
        for min_lv, tpl_tokens, punct, _en_tpl in pool:
            allowed = {w for w, l in word_to_level.items() if l <= min_lv} | ALLOWED_SIMPLE_PROPER_NOUNS
            fixed = [t for t in tpl_tokens if t != "{w}"]
            assert len(fixed) == len(set(fixed)), f"{pool_name} duplicate fixed token: {tpl_tokens}"
            for t in fixed:
                assert t in allowed or DIGITS_RE.match(t), (
                    f"{pool_name} template (min_lv={min_lv}) forbidden token '{t}' "
                    f"(level={word_to_level.get(t)}): {tpl_tokens}"
                )
            assert punct in {"。", "？", "！"}


def generate_level_safe_example(
    hanzi: str,
    english: str,
    level: int,
    pos_tags: list[str],
    allowed_vocab: set[str],
    word_to_level: dict[str, int],
    word_to_pinyin: dict[str, str],
) -> tuple[str, str, str, list[str]]:
    gloss = short_english_gloss(english)
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

    pool = [item for item in raw_pool if item[0] <= level]
    if not pool:
        pool = [item for item in (VERB_TEMPLATES + NOUN_TEMPLATES) if item[0] <= level]

    digest = int(hashlib.md5(hanzi.encode("utf-8")).hexdigest()[:8], 16)
    start = digest % len(pool)
    for offset in range(len(pool)):
        _min_lv, tpl_tokens, punct, en_tpl = pool[(start + offset) % len(pool)]
        tokens = [hanzi if t == "{w}" else t for t in tpl_tokens]
        if (
            len(tokens) >= 3
            and len(tokens) == len(set(tokens))
            and all(is_token_allowed(t, level, allowed_vocab, word_to_level) for t in tokens)
        ):
            zh = "".join(tokens) + punct
            py = tokens_to_pinyin(tokens, punct, word_to_pinyin)
            en = en_tpl.format(en=gloss)
            return zh, py, en, tokens

    fallbacks = [
        (["我", "今天", "学习", hanzi], "。", f"Today I am learning: {gloss}."),
        (["请", "看", "这", "个", hanzi], "。", f"Please look at: {gloss}."),
        (["他们" if "他们" in allowed_vocab else "我们", "学习", hanzi, "了"], "。", f"We studied: {gloss}."),
        (["爸爸", "妈妈", "知道" if "知道" in allowed_vocab else "看见", hanzi], "。", f"Mom and dad see: {gloss}."),
    ]
    for tokens, punct, en in fallbacks:
        if (
            len(tokens) >= 3
            and len(tokens) == len(set(tokens))
            and all(is_token_allowed(t, level, allowed_vocab, word_to_level) for t in tokens)
        ):
            zh = "".join(tokens) + punct
            py = tokens_to_pinyin(tokens, punct, word_to_pinyin)
            return zh, py, en, tokens

    raise RuntimeError(f"Unable to generate compliant example for ({level}, {hanzi})")


def has_residual_french(text: str) -> bool:
    if RESIDUAL_FRENCH_WORDS_RE.search(text):
        return True
    cleaned = text
    for loan in FRENCH_LOANWORDS_IN_EN:
        cleaned = re.sub(rf"\b{re.escape(loan)}\b", "", cleaned, flags=re.IGNORECASE)
    return bool(FRENCH_DIACRITICS_RE.search(cleaned))


def _make_exact_en_regex(en_tpl: str) -> re.Pattern[str]:
    parts = en_tpl.split("{en}")
    return re.compile("^" + ".+".join(re.escape(p) for p in parts) + "$")


FIX_SKELETON_SPECS: list[tuple[tuple[str, ...] | None, re.Pattern[str]]] = [
    (tuple(toks), _make_exact_en_regex(en_tpl))
    for pool in (VERB_TEMPLATES, NOUN_TEMPLATES, ADJ_TEMPLATES, ADV_TEMPLATES, IDIOM_TEMPLATES)
    for _lv, toks, _punct, en_tpl in pool
] + [
    (None, re.compile(p))
    for p in (
        r"^Today I am learning: .+\.$",
        r"^Please look at: .+\.$",
        r"^We studied: .+\.$",
        r"^Mom and dad see: .+\.$",
    )
]


def is_mechanical_example(word: dict[str, Any]) -> bool:
    ex = word.get("example", {})
    en = ex.get("english", "")
    if any(p.match(en) for p in BUILD_EN_PATTERNS):
        return True
    hz = word.get("hanzi", "")
    toks = tuple("{w}" if t == hz else t for t in ex.get("tokens", []))
    return any(en_re.match(en) and (s_toks is None or s_toks == toks) for s_toks, en_re in FIX_SKELETON_SPECS)


TED_BAD_EN_START_RE = re.compile(r'^"?(And|But|So|Or|Because|Which|That)\s')
TED_BAD_ZH_START_RE = re.compile(r"^(还有|以及|但是|不过|而且|所以)")
TED_BAD_ZH_NP_RE = re.compile(r"^对.+的[^。？！]+。$")
TED_FINITE_VERB_RE = re.compile(
    r"(\b(am|is|are|was|were|be|been|have|has|had|do|does|did|will|would|can|could|shall|should|may|might|must|"
    r"go|goes|went|make|makes|made|take|takes|took|get|gets|got|give|gives|gave|know|knows|knew|think|thinks|thought|"
    r"see|sees|saw|come|comes|came|want|wants|use|uses|find|finds|found|tell|tells|told|ask|asks|work|works|"
    r"seem|seems|feel|feels|felt|try|tries|leave|leaves|left|call|calls|need|needs|become|becomes|became|mean|means|meant|"
    r"keep|keeps|kept|let|lets|begin|begins|began|help|helps|talk|talks|turn|turns|start|starts|show|shows|"
    r"hear|hears|heard|play|plays|run|runs|ran|move|moves|live|lives|believe|believes|hold|holds|held|bring|brings|brought|"
    r"happen|happens|write|writes|wrote|provide|provides|sit|sits|sat|stand|stands|stood|lose|loses|lost|pay|pays|paid|"
    r"meet|meets|met|include|includes|continue|continues|set|sets|learn|learns|change|changes|lead|leads|led|"
    r"understand|understands|understood|watch|watches|follow|follows|stop|stops|create|creates|speak|speaks|spoke|"
    r"read|reads|allow|allows|add|adds|spend|spends|spent|grow|grows|grew|open|opens|walk|walks|win|wins|won|offer|offers|"
    r"remember|remembers|love|loves|like|likes|consider|considers|appear|appears|buy|buys|bought|wait|waits|serve|serves|"
    r"die|dies|send|sends|sent|expect|expects|build|builds|built|stay|stays|fall|falls|fell|cut|cuts|reach|reaches|"
    r"remain|remains|suggest|suggests|raise|raises|pass|passes|sell|sells|sold|require|requires|report|reports|"
    r"decide|decides|affect|affects|cause|causes|produce|produces|exist|exists|explain|explains|protect|protects|"
    r"prevent|prevents|achieve|achieves|improve|improves|reduce|reduces|increase|increases|develop|develops|"
    r"support|supports|avoid|avoids|solve|solves|control|controls|save|saves|share|shares|enjoy|enjoys|hope|hopes|"
    r"eat|eats|ate|drink|drinks|drank|sleep|sleeps|slept|drive|drives|drove|wear|wears|wore|sing|sings|sang|"
    r"fly|flies|flew|draw|draws|drew|break|breaks|broke|choose|chooses|chose|forget|forgets|forgot|teach|teaches|taught|"
    r"catch|catches|caught|check|checks|finish|finishes|accept|accepts|refuse|refuses|agree|agrees|promise|promises|"
    r"trust|trusts|respect|respects|depend|depends|belong|belongs|matter|matters|cost|costs|look|looks|sound|sounds)\b"
    r"|'(m|re|s|ve|ll|d)\b|n't\b|\b[a-z]{3,}ed\b)",
    re.IGNORECASE,
)

EN_STOPWORDS = {
    "the", "and", "for", "are", "was", "were", "been", "being", "have", "has", "had",
    "this", "that", "these", "those", "with", "from", "not", "but", "they", "their",
    "them", "you", "your", "his", "her", "hers", "she", "our", "ours", "its", "can",
    "could", "will", "would", "shall", "should", "may", "might", "must", "just",
    "into", "over", "under", "also", "more", "most", "some", "such", "what", "when",
    "where", "which", "who", "whom", "why", "how", "one", "two", "three", "very",
    "too", "out", "off", "about", "after", "before", "between", "through", "during",
    "without", "within", "against", "than", "then", "there", "here", "all", "any",
    "each", "every", "both", "few", "many", "much", "other", "another", "only",
    "own", "same", "now", "well", "way", "even", "still", "yet", "already",
    "classifier", "measure", "word", "particle", "marker", "variant", "used",
    "etc", "sth", "sb", "lit", "fig", "coll", "abbr", "esp", "derog",
}

IRREGULAR_EN_STEMS = {
    "went": "go", "gone": "go", "goes": "go", "going": "go",
    "came": "come", "comes": "come", "coming": "come",
    "saw": "see", "seen": "see", "sees": "see",
    "made": "make", "makes": "make", "making": "make",
    "took": "take", "taken": "take", "takes": "take",
    "gave": "give", "given": "give", "gives": "give",
    "knew": "know", "known": "know", "knows": "know",
    "thought": "think", "thinks": "think", "thinking": "think",
    "found": "find", "finds": "find",
    "told": "tell", "tells": "tell",
    "became": "become", "becomes": "become",
    "kept": "keep", "keeps": "keep",
    "began": "begin", "begun": "begin", "begins": "begin",
    "held": "hold", "holds": "hold",
    "brought": "bring", "brings": "bring",
    "wrote": "write", "written": "write", "writes": "write",
    "stood": "stand", "stands": "stand",
    "lost": "lose", "loses": "lose",
    "paid": "pay", "pays": "pay",
    "met": "meet", "meets": "meet",
    "led": "lead", "leads": "lead",
    "understood": "understand", "understands": "understand",
    "spoke": "speak", "spoken": "speak", "speaks": "speak",
    "ran": "run", "runs": "run", "running": "run",
    "grew": "grow", "grown": "grow", "grows": "grow",
    "won": "win", "wins": "win",
    "bought": "buy", "buys": "buy",
    "sent": "send", "sends": "send",
    "built": "build", "builds": "build",
    "fell": "fall", "fallen": "fall", "falls": "fall",
    "sold": "sell", "sells": "sell",
    "ate": "eat", "eaten": "eat", "eats": "eat",
    "drank": "drink", "drunk": "drink", "drinks": "drink",
    "slept": "sleep", "sleeps": "sleep",
    "drove": "drive", "driven": "drive", "drives": "drive",
    "wore": "wear", "worn": "wear", "wears": "wear",
    "sang": "sing", "sung": "sing", "sings": "sing",
    "flew": "fly", "flown": "fly", "flies": "fly",
    "drew": "draw", "drawn": "draw", "draws": "draw",
    "broke": "break", "broken": "break", "breaks": "break",
    "chose": "choose", "chosen": "choose", "chooses": "choose",
    "forgot": "forget", "forgotten": "forget", "forgets": "forget",
    "taught": "teach", "teaches": "teach",
    "caught": "catch", "catches": "catch",
    "better": "good", "best": "good",
    "worse": "bad", "worst": "bad",
    "children": "child", "people": "person", "persons": "person",
    "men": "man", "women": "woman", "feet": "foot", "teeth": "tooth",
    "lives": "life", "living": "live", "lived": "live",
    "kids": "kid", "kid": "child", "development": "grow",
}

TRIVIAL_ZH_TOKENS = {
    "我", "你", "您", "他", "她", "它", "我们", "你们", "他们", "她们", "它们",
    "自己", "大家", "别人", "人家", "这", "那", "哪", "哪儿", "这里", "那里",
    "谁", "什么", "怎么", "怎么样", "为什么", "几", "多少", "吗", "呢", "吧", "啊", "呀",
    "的", "了", "着", "过", "得", "地", "很", "太", "真", "都", "也", "就", "才", "再",
    "又", "还", "更", "最", "不", "没", "没有", "在", "是", "有", "和", "跟", "对",
    "把", "被", "给", "让", "向", "往", "从", "离", "比", "为", "因为", "所以",
    "虽然", "但是", "如果", "而且", "或者", "并", "却", "而", "之", "可以", "能", "会", "要",
    "个", "位", "件", "条", "张", "本", "次", "种", "点", "些", "一下", "一点儿",
    "一", "二", "两", "三", "四", "五", "六", "七", "八", "九", "十", "百", "千", "万",
}


def extract_en_stems(text: str) -> set[str]:
    stems: set[str] = set()
    for word in re.findall(r"[a-z]{3,}", text.lower()):
        if word in EN_STOPWORDS:
            continue
        mapped = IRREGULAR_EN_STEMS.get(word, word)
        stems.add(mapped)
        if word != mapped:
            stems.add(word)
        stem = mapped
        if len(stem) > 5 and stem.endswith(("ies", "ied")):
            stem = stem[:-3] + "y"
        else:
            for suf in ("ation", "ition", "ment", "ness", "able", "ible", "ical", "tion", "sion", "ing", "ous", "ful", "less", "ive", "ity", "ize", "ise", "al", "ly", "ed", "es", "er", "or"):
                if len(stem) - len(suf) >= 4 and stem.endswith(suf):
                    stem = stem[: -len(suf)]
                    break
            else:
                if len(stem) >= 5 and stem.endswith("s") and not stem.endswith("ss"):
                    stem = stem[:-1]
        stems.add(stem)
    return stems


def build_cedict_stems(
    complete_entries: list[dict[str, Any]],
    catalogs: dict[int, dict[str, Any]],
) -> dict[str, set[str]]:
    cedict_stems: dict[str, set[str]] = defaultdict(set)
    for entry in complete_entries:
        hz = entry.get("s", "")
        if not hz:
            continue
        for form in entry.get("f", []):
            for raw_gloss in form.get("m", []):
                low = raw_gloss.strip().lower()
                if (
                    not low
                    or low.startswith(("surname ", "variant of ", "old variant ", "cl:", "see ", "abbr. "))
                ):
                    continue
                cleaned = re.sub(r"\([^)]*\)", " ", raw_gloss)
                cedict_stems[hz].update(extract_en_stems(cleaned))
    for lv in range(1, 7):
        for w in catalogs[lv]["words"]:
            cedict_stems[w["hanzi"]].update(extract_en_stems(w["english"]))
    return cedict_stems


def ted_alignment_score(
    target_hz: str,
    tokens: list[str],
    eng_text: str,
    cedict_stems: dict[str, set[str]],
) -> float | None:
    eng_stems = extract_en_stems(eng_text)
    if not eng_stems:
        return None

    def _matches(zh_tok: str) -> bool:
        t_stems = cedict_stems.get(zh_tok)
        if not t_stems:
            return False
        if t_stems & eng_stems:
            return True
        return any(
            len(s) >= 5 and len(e) >= 5 and s[:5] == e[:5]
            for s in t_stems
            for e in eng_stems
        )

    if not _matches(target_hz):
        return None

    other_content = [
        t
        for t in tokens
        if t != target_hz and t not in TRIVIAL_ZH_TOKENS and cedict_stems.get(t)
    ]
    if not other_content:
        return 1.0
    aligned = sum(1 for t in other_content if _matches(t))
    ratio = aligned / len(other_content)
    if aligned < 1 or ratio < 0.35:
        return None
    return ratio


def clean_opus_pair(
    source_name: str,
    zh_raw: str,
    en_raw: str,
    traditional_only_chars: set[str],
) -> tuple[str, str] | None:
    if re.search(r"[A-Za-z0-9]", zh_raw):
        return None
    cmn = re.sub(r"\s+", "", html.unescape(zh_raw)).strip()
    if not (6 <= len(cmn) <= 26) or cmn[-1] not in "。？！":
        return None
    body = cmn[:-1]
    if not CJK_ONLY_RE.match(body):
        return None
    if any(ch in traditional_only_chars for ch in body):
        return None

    eng = html.unescape(en_raw)
    eng = eng.replace("“", '"').replace("”", '"').replace("’", "'").replace("‘", "'")
    eng = re.sub(r"\s+", " ", eng).strip()
    if not (10 <= len(eng) <= 130):
        return None
    words = eng.split()
    if not (3 <= len(words) <= 26):
        return None
    if "..." in eng or "…" in eng:
        return None
    if any(ch in eng for ch in "()[]{}<>|★♪•\t\r"):
        return None
    if ": " in eng or " : " in eng:
        return None
    if re.search(r"(https?://|www\.|\.[a-z]{2,4}/)", eng, re.IGNORECASE):
        return None
    if re.search(r"\s+[.,?!;:]", eng):
        return None
    if eng.count('"') not in (0, 2):
        return None
    if eng.startswith("'") or re.search(r"(^|\s)'", eng):
        return None
    if not re.match(r'^"?[A-Z]', eng):
        return None
    if not (re.search(r"[.?!]$", eng) or (eng.startswith('"') and re.search(r'[.?!]"$', eng))):
        return None
    if (cmn[-1] == "？") != eng.rstrip('"').endswith("?"):
        return None
    ratio = len(eng) / len(body)
    if not (1.5 <= ratio <= 8.5):
        return None
    if source_name == "TED2020":
        if (
            TED_BAD_EN_START_RE.match(eng)
            or TED_BAD_ZH_START_RE.match(cmn)
            or TED_BAD_ZH_NP_RE.match(cmn)
            or not TED_FINITE_VERB_RE.search(eng)
            or not (2.2 <= ratio <= 6.8)
            or len(words) < max(4, round(len(body) * 0.45))
        ):
            return None
    if VULGAR_OR_SLANG_RE.search(eng) or has_residual_french(eng) or MECHANICAL_TEMPLATE_RE.search(eng):
        return None
    return cmn, eng


def load_opus_pairs(complete_entries: list[dict[str, Any]]) -> list[tuple[str, str, str]]:
    """Loads clean Simplified Chinese -> English pairs from OPUS corpora (cached locally)."""
    cached_path = CACHE_DIR / "opus_zh_en_clean_v2.tsv"
    if cached_path.exists() and cached_path.stat().st_size > 0:
        pairs: list[tuple[str, str, str]] = []
        with cached_path.open("r", encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) == 3:
                    src, cmn, eng = parts
                    if not VULGAR_OR_SLANG_RE.search(eng) and not has_residual_french(eng):
                        pairs.append((src, cmn, eng))
        return pairs

    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    simp_chars: set[str] = set()
    trad_chars: set[str] = set()
    for entry in complete_entries:
        s = entry.get("s", "")
        simp_chars.update(s)
        for form in entry.get("f", []):
            trad_chars.update(form.get("t", ""))
    traditional_only_chars = {
        ch for ch in (trad_chars - simp_chars) if "\u4e00" <= ch <= "\u9fff"
    }

    dedup_by_cmn: dict[str, tuple[str, str, str]] = {}
    source_counts: dict[str, int] = defaultdict(int)

    for source_name, url, zip_name in OPUS_SOURCES:
        zip_path = CACHE_DIR / zip_name
        if not zip_path.exists() or zip_path.stat().st_size == 0:
            print(f"Downloading OPUS {source_name} ({url})...")
            req = urllib.request.Request(url, headers={"User-Agent": "EasyMandarin/1.0"})
            with urllib.request.urlopen(req, timeout=120) as resp:
                zip_path.write_bytes(resp.read())

        print(f"Filtering OPUS {source_name} from {zip_path.name}...")
        with zipfile.ZipFile(zip_path, "r") as zf:
            names = [n for n in zf.namelist() if not n.endswith("/") and ".ids" not in n]
            en_name = next(n for n in names if n.endswith(".en"))
            zh_name = next(n for n in names if n.endswith(".zh_cn") or n.endswith(".zh"))
            with zf.open(en_name, "r") as fen, zf.open(zh_name, "r") as fzh:
                for raw_en_b, raw_zh_b in zip(fen, fzh):
                    en_raw = raw_en_b.decode("utf-8", errors="ignore").strip()
                    zh_raw = raw_zh_b.decode("utf-8", errors="ignore").strip()
                    cleaned = clean_opus_pair(source_name, zh_raw, en_raw, traditional_only_chars)
                    if cleaned is None:
                        continue
                    cmn, eng = cleaned
                    if cmn not in dedup_by_cmn:
                        dedup_by_cmn[cmn] = (source_name, cmn, eng)
                        source_counts[source_name] += 1

    pairs = list(dedup_by_cmn.values())
    with cached_path.open("w", encoding="utf-8") as out:
        for src, cmn, eng in pairs:
            out.write(f"{src}\t{cmn}\t{eng}\n")
    print(f"Cached {len(pairs)} clean OPUS pairs (v2): {dict(source_counts)}")
    return pairs


def build_trad_to_simp_maps(
    complete_entries: list[dict[str, Any]],
) -> tuple[set[str], dict[str, str], dict[str, str]]:
    """Builds word-first and char-fallback Traditional->Simplified maps from CC-CEDICT."""
    simp_chars: set[str] = set()
    trad_chars: set[str] = set()
    t2s_word: dict[str, str] = {}
    t2s_char_counts: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))

    for entry in complete_entries:
        s = entry.get("s", "")
        if not s:
            continue
        simp_chars.update(s)
        for form in entry.get("f", []):
            t = form.get("t", "")
            if not t:
                continue
            trad_chars.update(t)
            if len(t) == len(s):
                if len(t) >= 2 and t not in t2s_word:
                    t2s_word[t] = s
                for tc, sc in zip(t, s):
                    if tc != sc:
                        t2s_char_counts[tc][sc] += 1

    # Preserve multi-character simplified words where traditional text shares characters
    for entry in complete_entries:
        s = entry.get("s", "")
        if len(s) >= 2 and s not in t2s_word:
            t2s_word[s] = s

    trad_only_chars = {
        ch for ch in (trad_chars - simp_chars) if "\u4e00" <= ch <= "\u9fff"
    }
    t2s_char: dict[str, str] = {
        tc: max(sc_map.items(), key=lambda item: item[1])[0]
        for tc, sc_map in t2s_char_counts.items()
        if tc in trad_only_chars
    }
    # Disambiguated single-char fallbacks for shared-codepoint Traditional forms
    t2s_char.update(
        {
            "著": "着",
            "汙": "污",
            "裡": "里",
            "裏": "里",
            "為": "为",
            "後": "后",
            "麼": "么",
            "髒": "脏",
            "臟": "脏",
            "發": "发",
            "髮": "发",
            "鐘": "钟",
            "鍾": "钟",
            "製": "制",
            "臺": "台",
            "隻": "只",
            "乾": "干",
            "覆": "复",
            "範": "范",
            "鬆": "松",
            "餘": "余",
            "衝": "冲",
            "採": "采",
            "夥": "伙",
            "颳": "刮",
            "穀": "谷",
            "醜": "丑",
            "鬥": "斗",
            "薑": "姜",
            "儘": "尽",
            "漣": "涟",
            "瑪": "玛",
            "鮑": "鲍",
            "驢": "驴",
        }
    )
    return trad_only_chars, t2s_word, t2s_char


def convert_trad_example_to_simp(
    ex_hanzi: str,
    tokens: list[str],
    t2s_word: dict[str, str],
    t2s_char: dict[str, str],
) -> tuple[str, list[str]]:
    """Converts a Traditional Chinese sentence and its tokens via CC-CEDICT word-first longest match."""
    n = len(ex_hanzi)
    simp_chars = list(ex_hanzi)
    i = 0
    while i < n:
        matched = False
        for length in range(min(8, n - i), 1, -1):
            chunk = ex_hanzi[i : i + length]
            repl = t2s_word.get(chunk)
            if repl is not None and len(repl) == length:
                simp_chars[i : i + length] = list(repl)
                i += length
                matched = True
                break
        if not matched:
            ch = ex_hanzi[i]
            simp_chars[i] = t2s_char.get(ch, ch)
            i += 1

    new_hanzi = "".join(simp_chars)
    new_tokens: list[str] = []
    cursor = 0
    for tok in tokens:
        idx = ex_hanzi.find(tok, cursor)
        if idx == -1:
            # Fallback direct conversion if token offset cannot be located
            conv_tok, _ = convert_trad_example_to_simp(tok, [], t2s_word, t2s_char) if tok else (tok, [])
            new_tokens.append(conv_tok)
        else:
            new_tokens.append("".join(simp_chars[idx : idx + len(tok)]))
            cursor = idx + len(tok)
    return new_hanzi, new_tokens


def enrich_hsk5_and_hsk6(
    catalogs: dict[int, dict[str, Any]],
    word_to_level: dict[str, int],
    word_to_pinyin: dict[str, str],
    tatoeba_pairs: list[tuple[str, str]],
    complete_entries: list[dict[str, Any]],
    no_bump_version: bool,
) -> None:
    """Enriches mechanical templates in HSK 5 and HSK 6 using Tatoeba + OPUS pairs."""
    opus_pairs = load_opus_pairs(complete_entries)
    cedict_stems = build_cedict_stems(complete_entries, catalogs)
    trad_only_chars, t2s_word, t2s_char = build_trad_to_simp_maps(complete_entries)
    all_pairs: list[tuple[str, str, str]] = [
        ("Tatoeba", cmn, eng) for cmn, eng in tatoeba_pairs
    ] + opus_pairs

    hsk5_data = catalogs[5]
    hsk6_data = catalogs[6]
    hsk5_allowed = {hz for hz, lv in word_to_level.items() if lv <= 5} | ALLOWED_SIMPLE_PROPER_NOUNS

    hsk5_modified = False
    hsk5_needed: set[str] = set()
    for w in hsk5_data["words"]:
        hz = w["hanzi"]
        if (5, hz) in HANDCRAFTED_EXAMPLES:
            new_ex = build_handcrafted_example(HANDCRAFTED_EXAMPLES[(5, hz)], word_to_pinyin)
            if w["example"] != new_ex:
                w["example"] = new_ex
                hsk5_modified = True
            continue
        if "to to " in w["example"]["english"].lower():
            fixed_en = re.sub(r"\bto to ", "to ", w["example"]["english"], flags=re.IGNORECASE)
            if fixed_en != w["example"]["english"]:
                w["example"]["english"] = fixed_en
                hsk5_modified = True
        toks = w["example"]["tokens"]
        compliant = (
            not is_mechanical_example(w)
            and not VULGAR_OR_SLANG_RE.search(w["example"]["english"])
            and not has_residual_french(w["example"]["english"])
            and hz in toks
            and all(is_token_allowed(t, 5, hsk5_allowed, word_to_level) for t in toks)
            and is_example_pinyin_consistent(hz, w["pinyin"], w["example"]["pinyin"])
        )
        if not compliant:
            hsk5_needed.add(hz)

    # Apply handcrafted overrides + Traditional->Simplified conversion on HSK 6 first
    hsk6_modified = False
    hsk6_needed: set[str] = set()
    for w in hsk6_data["words"]:
        hz = w["hanzi"]
        if (6, hz) in HANDCRAFTED_EXAMPLES:
            new_ex = build_handcrafted_example(HANDCRAFTED_EXAMPLES[(6, hz)], word_to_pinyin)
            if w["example"] != new_ex:
                w["example"] = new_ex
                hsk6_modified = True
            continue
        ex_hz = w["example"]["hanzi"]
        if (
            any(ch in trad_only_chars or ch in {"漣", "瑪", "鮑", "驢"} for ch in ex_hz)
            or "穿著" in ex_hz
        ):
            new_hz, new_toks = convert_trad_example_to_simp(
                ex_hz,
                w["example"]["tokens"],
                t2s_word,
                t2s_char,
            )
            if new_hz != ex_hz or new_toks != w["example"]["tokens"]:
                w["example"]["hanzi"] = new_hz
                w["example"]["tokens"] = new_toks
                hsk6_modified = True
        toks = w["example"]["tokens"]
        compliant = (
            not is_mechanical_example(w)
            and not VULGAR_OR_SLANG_RE.search(w["example"]["english"])
            and not has_residual_french(w["example"]["english"])
            and hz in toks
            and len(toks) >= 3
            and len(toks) == len(set(toks))
            and is_example_pinyin_consistent(hz, w["pinyin"], w["example"]["pinyin"])
        )
        if not compliant:
            hsk6_needed.add(hz)

    print(f"Indexing {len(all_pairs)} sentences for HSK 5 ({len(hsk5_needed)} needed) and HSK 6 ({len(hsk6_needed)} needed)...")
    cands_5: dict[str, list[tuple[tuple[int, int, int, int], str, str, str, list[str], str]]] = defaultdict(list)
    cands_6: dict[str, list[tuple[tuple[int, int, int, int], str, str, str, list[str], str]]] = defaultdict(list)

    for src, cmn_text, eng_text in all_pairs:
        if has_residual_french(eng_text) or VULGAR_OR_SLANG_RE.search(eng_text):
            continue
        body = cmn_text.rstrip("。？！")
        punct = cmn_text[len(body) :] or "。"
        if not CJK_ONLY_RE.match(body) or not (6 <= len(cmn_text) <= 26 or src == "Tatoeba"):
            continue
        j_toks = jieba.lcut(body)
        if not j_toks or "".join(j_toks) != body:
            continue
        j_set = set(j_toks)

        # Match HSK 5 candidates under strict HSK 1..5 ceiling
        hits_5 = j_set & hsk5_needed
        if hits_5 and not any(word_to_level.get(t, 0) > 5 for t in j_toks):
            for hz in hits_5:
                if len(cands_5[hz]) >= 60:
                    continue
                seg = strict_segment(body, 5, hsk5_allowed, word_to_level, target=hz)
                if (
                    seg
                    and hz in seg
                    and 3 <= len(seg) <= 11
                    and len(seg) == len(set(seg))
                    and "".join(seg) == body
                    and all(is_token_allowed(t, 5, hsk5_allowed, word_to_level) for t in seg)
                ):
                    align_bonus = 0
                    if src == "TED2020":
                        align = ted_alignment_score(hz, seg, eng_text, cedict_stems)
                        if align is None:
                            continue
                        align_bonus = -round(align * 10)
                    py = tokens_to_pinyin(seg, punct, word_to_pinyin)
                    if is_example_pinyin_consistent(hz, word_to_pinyin[hz], py):
                        score = (
                            SOURCE_PRIORITY[src],
                            align_bonus,
                            abs(len(seg) - 6) * 2 + abs(len(body) - 12),
                            len(eng_text),
                        )
                        cands_5[hz].append((score, src, cmn_text, eng_text, seg, py))

        # Match HSK 6 candidates (preferring HSK-known coverage, no OOV cutoff)
        hits_6 = j_set & hsk6_needed
        if hits_6 and 3 <= len(j_toks) <= 11 and len(j_toks) == len(set(j_toks)):
            if all(CJK_ONLY_RE.match(t) for t in j_toks):
                py6 = tokens_to_pinyin(j_toks, punct, word_to_pinyin)
                known_ratio = sum(
                    1
                    for t in j_toks
                    if t in word_to_level or t in ALLOWED_SIMPLE_PROPER_NOUNS or DIGITS_RE.match(t)
                ) / len(j_toks)
                for hz in hits_6:
                    if len(cands_6[hz]) >= 60:
                        continue
                    align_bonus = 0
                    if src == "TED2020":
                        align = ted_alignment_score(hz, j_toks, eng_text, cedict_stems)
                        if align is None:
                            continue
                        align_bonus = -round(align * 10)
                    if is_example_pinyin_consistent(hz, word_to_pinyin[hz], py6):
                        score = (
                            SOURCE_PRIORITY[src],
                            align_bonus,
                            round((1.0 - known_ratio) * 20) + abs(len(j_toks) - 6) * 2 + abs(len(body) - 13),
                            len(eng_text),
                        )
                        cands_6[hz].append((score, src, cmn_text, eng_text, list(j_toks), py6))

    report: dict[str, Any] = {"hsk5": [], "hsk6": []}

    for lv, data, needed, cands_map, init_mod in (
        (5, hsk5_data, hsk5_needed, cands_5, hsk5_modified),
        (6, hsk6_data, hsk6_needed, cands_6, hsk6_modified),
    ):
        words_by_hz = {w["hanzi"]: w for w in data["words"]}
        used_cmn = {
            w["example"]["hanzi"]
            for w in data["words"]
            if w["hanzi"] not in needed
        }
        src_stats: dict[str, int] = defaultdict(int)
        for w in data["words"]:
            if w["hanzi"] not in needed:
                src_stats["handcrafted" if (lv, w["hanzi"]) in HANDCRAFTED_EXAMPLES else "kept"] += 1

        modified = init_mod
        # Assign rarest candidates first to minimize duplicate sentences across words
        for hz in sorted(needed, key=lambda item: (len(cands_map.get(item, [])), item)):
            cands = sorted(cands_map.get(hz, []), key=lambda x: x[0])
            if not cands:
                src_stats["remaining_template"] += 1
                continue
            chosen = next((c for c in cands if c[2] not in used_cmn), cands[0])
            _score, src, cmn_text, eng_text, tokens, py = chosen
            used_cmn.add(cmn_text)
            w = words_by_hz[hz]
            w["example"] = {
                "hanzi": cmn_text,
                "pinyin": py,
                "english": eng_text,
                "tokens": tokens,
            }
            modified = True
            src_stats[src] += 1
            report[f"hsk{lv}"].append(
                {
                    "id": w["id"],
                    "hanzi": hz,
                    "pinyin": w["pinyin"],
                    "english": w["english"],
                    "source": src,
                    "example": w["example"],
                }
            )

        if modified:
            if not no_bump_version:
                data["catalogVersion"] = int(data["catalogVersion"]) + 1
            out_path = CATALOG_DIR / f"hsk{lv}.json"
            out_path.write_text(
                json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )

        print(
            f"HSK {lv} (v{data['catalogVersion']}) — "
            f"kept: {src_stats['kept']}, "
            f"handcrafted: {src_stats['handcrafted']}, "
            f"Tatoeba: {src_stats['Tatoeba']}, "
            f"TED2020: {src_stats['TED2020']}, "
            f"QED: {src_stats['QED']}, "
            f"News-Commentary: {src_stats['News-Commentary']}, "
            f"remaining templates: {src_stats['remaining_template']}"
        )

    (CACHE_DIR / "opus_enrichment_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fix and verify HSK 1 to 6 example sentences.")
    parser.add_argument(
        "--no-bump-version",
        action="store_true",
        help="Do not increment catalogVersion in modified files.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    catalogs: dict[int, dict[str, Any]] = {}
    word_to_level: dict[str, int] = {}
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
        data = json.loads((CATALOG_DIR / f"hsk{lv}.json").read_text(encoding="utf-8"))
        catalogs[lv] = data
        for w in data["words"]:
            hz = w["hanzi"]
            word_to_level[hz] = lv
            word_to_pinyin[hz] = w["pinyin"]
            jieba.add_word(hz, freq=100_000)

    verify_static_templates(word_to_level)

    pos_map: dict[str, list[str]] = {}
    complete_entries: list[dict[str, Any]] = []
    comp_path = CACHE_DIR / "complete.min.json"
    if comp_path.exists():
        complete_entries = json.loads(comp_path.read_text(encoding="utf-8"))
        for entry in complete_entries:
            pos_map[entry["s"]] = entry.get("p", [])

    tatoeba_pairs: list[tuple[str, str]] = []
    tat_path = CACHE_DIR / "tatoeba_cmn_eng.tsv"
    if tat_path.exists():
        with tat_path.open("r", encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) == 2 and not VULGAR_OR_SLANG_RE.search(parts[1]):
                    tatoeba_pairs.append((parts[0], parts[1]))

    for level in range(1, 5):
        data = catalogs[level]
        allowed_vocab = {hz for hz, lv in word_to_level.items() if lv <= level} | ALLOWED_SIMPLE_PROPER_NOUNS
        level_words_set = {w["hanzi"] for w in data["words"]}
        lesson_order_map = {
            lesson["id"]: lesson["orderIndex"]
            for unit in data["units"]
            for lesson in unit["lessons"]
        }

        tat_candidates: dict[str, list[tuple[int, str, str, list[str]]]] = defaultdict(list)
        for cmn_text, eng_text in tatoeba_pairs:
            body = cmn_text.rstrip("。？！")
            if has_higher_hsk_compound(body, level, word_to_level):
                continue
            tokens = strict_segment(body, level, allowed_vocab, word_to_level)
            if not tokens or not (3 <= len(tokens) <= 9) or len(tokens) != len(set(tokens)):
                continue
            if "".join(tokens) != body:
                continue
            for tok in tokens:
                if tok in level_words_set and len(tat_candidates[tok]) < 40:
                    score = abs(len(tokens) - 5) * 2 + abs(len(body) - 9)
                    tat_candidates[tok].append((score, cmn_text, eng_text, tokens))

        stats = {"kept": 0, "handcrafted": 0, "resegmented": 0, "tatoeba": 0, "template": 0}
        modified = False

        for w in data["words"]:
            hz = w["hanzi"]
            ex = w["example"]
            old_tokens = ex["tokens"]
            old_hanzi = ex["hanzi"]
            old_body = old_hanzi.rstrip(TRAILING_PUNCT)
            old_punct = old_hanzi[len(old_body) :] or "。"

            if (level, hz) in HANDCRAFTED_EXAMPLES:
                new_ex = build_handcrafted_example(HANDCRAFTED_EXAMPLES[(level, hz)], word_to_pinyin)
                if ex != new_ex:
                    w["example"] = new_ex
                    modified = True
                stats["handcrafted"] += 1
                continue

            is_tpl_or_vulgar = bool(VULGAR_OR_SLANG_RE.search(ex["english"])) or bool(
                MECHANICAL_TEMPLATE_RE.search(ex["english"])
            )
            already_valid = (
                not is_tpl_or_vulgar
                and hz in old_tokens
                and all(is_token_allowed(t, level, allowed_vocab, word_to_level) for t in old_tokens)
                and is_example_pinyin_consistent(hz, w["pinyin"], ex["pinyin"])
            )
            if already_valid:
                stats["kept"] += 1
                continue

            modified = True

            has_higher_existing = any(word_to_level.get(t, 0) > level for t in old_tokens)
            if (
                not is_tpl_or_vulgar
                and not has_higher_existing
                and not has_higher_hsk_compound(old_body, level, word_to_level)
                and "，" not in old_body
            ):
                reseg = strict_segment(old_body, level, allowed_vocab, word_to_level, target=hz)
                if (
                    reseg
                    and hz in reseg
                    and 3 <= len(reseg) <= 10
                    and len(reseg) == len(set(reseg))
                    and "".join(reseg) == old_body
                    and all(is_token_allowed(t, level, allowed_vocab, word_to_level) for t in reseg)
                ):
                    new_py = tokens_to_pinyin(reseg, old_punct, word_to_pinyin)
                    if is_example_pinyin_consistent(hz, w["pinyin"], new_py):
                        ex["tokens"] = reseg
                        ex["pinyin"] = new_py
                        stats["resegmented"] += 1
                        continue

            cands = tat_candidates.get(hz, [])
            if cands:
                cur_l_ord = lesson_order_map[w["lessonId"]]
                word_l_ord = {
                    w2["hanzi"]: lesson_order_map[w2["lessonId"]]
                    for w2 in data["words"]
                }
                cands_sorted = sorted(
                    cands,
                    key=lambda item: (
                        sum(1 for t in item[3] if word_l_ord.get(t, 0) > cur_l_ord),
                        item[0],
                    ),
                )
                _score, cmn_text, eng_text, tat_tokens = cands_sorted[0]
                body = cmn_text.rstrip("。？！")
                punct = cmn_text[len(body) :] or "。"
                w["example"] = {
                    "hanzi": cmn_text,
                    "pinyin": tokens_to_pinyin(tat_tokens, punct, word_to_pinyin),
                    "english": eng_text,
                    "tokens": tat_tokens,
                }
                stats["tatoeba"] += 1
                continue

            new_zh, new_py, new_en, new_tokens = generate_level_safe_example(
                hanzi=hz,
                english=w["english"],
                level=level,
                pos_tags=pos_map.get(hz, []),
                allowed_vocab=allowed_vocab,
                word_to_level=word_to_level,
                word_to_pinyin=word_to_pinyin,
            )
            w["example"] = {
                "hanzi": new_zh,
                "pinyin": new_py,
                "english": new_en,
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
            f"kept: {stats['kept']}, "
            f"resegmented: {stats['resegmented']}, "
            f"handcrafted: {stats['handcrafted']}, "
            f"Tatoeba strict: {stats['tatoeba']}, "
            f"safe templates: {stats['template']}"
        )

    enrich_hsk5_and_hsk6(
        catalogs=catalogs,
        word_to_level=word_to_level,
        word_to_pinyin=word_to_pinyin,
        tatoeba_pairs=tatoeba_pairs,
        complete_entries=complete_entries,
        no_bump_version=args.no_bump_version,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
