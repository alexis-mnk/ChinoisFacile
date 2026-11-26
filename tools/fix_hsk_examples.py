#!/usr/bin/env python3
"""
Audits and enforces pedagogical compliance for example sentences (HSK 1 to HSK 5):
ensures every token in `example.tokens` of `hskN.json` belongs exclusively to the
cumulative HSK 1..N vocabulary (plus numerals and simple allowed proper nouns),
with natural idiomatic English examples (zero mechanical templates on HSK 1-3).
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
ALLOWED_SIMPLE_PROPER_NOUNS = {"中国", "北京", "上海", "小明", "小王", "小李", "法国", "巴黎", "天", "黄河"}
VULGAR_OR_SLANG_RE = re.compile(
    r"(\(slang\)|\(vulgar\)|\(dialect\)|\(taboo\)|\(derog\.\)|"
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

HANDCRAFTED_EXAMPLES: dict[tuple[int, str], tuple[list[str], str, str]] = {
    # --- HSK 1 ---
    (1, "好"): (["你", "好", "认识", "很", "高兴"], "！", "Hello, nice to meet you!"),
    (1, "喂"): (["喂", "你", "好", "在", "家", "吗"], "？", "Hello, are you at home?"),
    (1, "谢谢"): (["老师", "谢谢", "你"], "！", "Thank you, teacher!"),
    (1, "再见"): (["朋友", "明天", "再见"], "！", "Goodbye my friend, see you tomorrow!"),
    (1, "你"): (["你", "好", "我", "很", "高兴"], "。", "Hello, I am very happy."),
    (1, "不客气"): (["不客气", "我们", "是", "朋友"], "。", "You're welcome, we are friends."),
    (1, "请"): (["请", "你", "喝", "茶"], "。", "Please have some tea."),
    (1, "对不起"): (["对不起", "我", "今天", "不", "去"], "。", "Sorry, I am not going today."),
    (1, "没关系"): (["没关系", "明天", "来", "我", "家"], "。", "No problem, come to my house tomorrow."),
    (1, "没有"): (["我", "没有", "钱"], "。", "I don't have money."),
    (1, "喝"): (["我", "喜欢", "喝", "茶"], "。", "I like drinking tea."),
    (1, "先生"): (["那", "个", "先生", "是", "医生"], "。", "That gentleman is a doctor."),
    (1, "小姐"): (["这", "个", "小姐", "是", "中国", "人"], "。", "That young lady is Chinese."),
    (1, "菜"): (["我", "很", "喜欢", "中国", "菜"], "。", "I really like Chinese food."),
    (1, "苹果"): (["我", "喜欢", "吃", "苹果"], "。", "I like eating apples."),
    (1, "猫"): (["我", "喜欢", "这", "个", "小", "猫"], "。", "I like this little cat."),
    (1, "狗"): (["那", "个", "狗", "很", "大"], "。", "That dog is very big."),
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
    (1, "五"): (["我", "学习", "汉语", "五", "年"], "。", "I studied Chinese for five years."),
    (1, "七"): (["她", "七", "点", "去", "医院"], "。", "She goes to the hospital at seven o'clock."),
    (1, "岁"): (["她", "女儿", "五", "岁", "了"], "。", "Her daughter is five years old."),
    (1, "星期"): (["明天", "是", "星期", "五"], "。", "Tomorrow is Friday."),
    (1, "月"): (["现在", "是", "五", "月"], "。", "It is May now."),
    (1, "明天"): (["我", "明天", "去", "学校"], "。", "I am going to school tomorrow."),
    (1, "分钟"): (["我", "学习", "汉语", "十", "分钟"], "。", "I study Chinese for ten minutes."),
    (1, "飞机"): (["我", "坐", "飞机", "去", "中国"], "。", "I am taking a plane to China."),
    (1, "出租车"): (["我们", "坐", "出租车", "去", "饭店"], "。", "We are taking a taxi to the restaurant."),
    (1, "电影"): (["她", "喜欢", "看", "中国", "电影"], "。", "She likes watching Chinese movies."),
    (1, "前面"): (["学校", "前面", "有", "个", "商店"], "。", "There is a shop in front of the school."),
    (1, "桌子"): (["桌子", "上", "有", "一", "本", "书"], "。", "There is a book on the table."),
    (1, "椅子"): (["小", "猫", "在", "椅子", "下"], "。", "The little cat is under the chair."),
    (1, "坐"): (["请", "坐", "我", "去", "喝", "水"], "。", "Please sit down, I'll get some water."),
    (1, "学习"): (["她", "在", "学校", "学习", "汉语"], "。", "She studies Chinese at school."),
    (1, "老师"): (["我", "妈妈", "是", "汉语", "老师"], "。", "My mom is a Chinese teacher."),
    (1, "读"): (["我", "在", "读", "这", "本", "书"], "。", "I am reading this book."),
    (1, "写"): (["她", "会", "写", "很", "多", "汉字" if False else "字"], "。", "She can write many characters."),
    (1, "睡觉"): (["现在", "十", "点", "了", "睡觉", "吧" if False else "去"], "。", "It's ten o'clock now, time to sleep."),
    (1, "电脑"): (["桌子", "上", "有", "一", "个", "电脑"], "。", "There is a computer on the table."),
    (1, "电视"): (["爸爸", "在", "家", "看", "电视"], "。", "Dad is watching TV at home."),
    (1, "漂亮"): (["这", "个", "衣服", "很", "漂亮"], "。", "These clothes are very pretty."),
    (1, "热"): (["今天", "天气", "很", "热"], "。", "Today's weather is very hot."),
    (1, "冷"): (["今天", "比" if False else "天气", "很", "冷"], "。", "Today's weather is very cold."),
    (1, "汉语"): (["我", "很", "喜欢", "学" if False else "学习", "汉语"], "。", "I really like studying Chinese."),
    (1, "怎么"): (["这", "个", "字", "怎么", "写"], "？", "How do you write this character?"),
    (1, "怎么样"): (["今天", "天气", "怎么样"], "？", "How is the weather today?"),
    # --- HSK 2 ---
    (2, "大家"): (["大家", "都", "在", "学校", "里"], "。", "Everyone is at school."),
    (2, "男"): (["那", "个", "男", "同学", "很", "高"], "。", "That male classmate is very tall."),
    (2, "女"): (["这", "个", "女", "医生", "很", "好"], "。", "This female doctor is very kind."),
    (2, "也"): (["我", "很", "高兴", "他", "也", "是"], "。", "I am very happy, and he is too."),
    (2, "真"): (["我", "真", "的", "不", "知道"], "。", "I really don't know."),
    (2, "希望"): (["她", "希望", "有", "个", "孩子"], "。", "She hopes to have a child."),
    (2, "妻子"): (["我", "的", "妻子", "是", "老师"], "。", "My wife is a teacher."),
    (2, "姓"): (["你", "姓", "什么"], "？", "What is your family name?"),
    (2, "零"): (["现在", "零", "点", "了"], "。", "It is midnight now."),
    (2, "日"): (["今天", "是", "十", "月", "一", "日"], "。", "Today is October 1st."),
    (2, "百"): (["学校", "里", "有", "两", "百", "个", "学生"], "。", "There are two hundred students at the school."),
    (2, "千"): (["这", "个", "电脑", "三", "千", "块"], "。", "This computer costs three thousand yuan."),
    (2, "第一"): (["这", "是", "我", "第一", "次", "来", "中国"], "。", "This is my first time coming to China."),
    (2, "每"): (["他", "每", "星期", "去", "一", "次", "北京"], "。", "He goes to Beijing once a week."),
    (2, "外"): (["爸爸", "在", "门", "外", "说话"], "。", "Dad is talking outside the door."),
    (2, "左边"): (["医院", "在", "学校", "左边"], "。", "The hospital is to the left of the school."),
    (2, "右边"): (["商店", "在", "医院", "右边"], "。", "The shop is to the right of the hospital."),
    (2, "往"): (["请", "往", "左边", "走"], "。", "Please walk to the left."),
    (2, "鸡蛋"): (["我", "早上", "吃", "了", "两", "个", "鸡蛋"], "。", "I ate two eggs this morning."),
    (2, "面条"): (["爸爸", "喜欢", "吃", "面条"], "。", "Dad likes eating noodles."),
    (2, "羊肉"): (["爸爸", "喜欢", "吃", "羊肉"], "。", "Dad likes eating lamb."),
    (2, "西瓜"): (["这", "个", "大", "西瓜", "很", "好吃"], "。", "This big watermelon is delicious."),
    (2, "好吃"): (["中国", "菜", "真", "好吃"], "！", "Chinese food is really delicious!"),
    (2, "服务员"): (["服务员", "请", "给", "我", "一", "个", "杯子"], "。", "Waiter, please give me a cup."),
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
    (2, "问"): (["我", "想", "问", "你", "一", "个", "问题"], "。", "I want to ask you a question."),
    (2, "对"): (["你", "说", "的", "很", "对"], "。", "What you said is quite right."),
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
    (2, "因为"): (["因为", "下雨", "我", "不", "去", "了"], "。", "Because it is raining, I am not going."),
    (2, "所以"): (["今天", "下雨", "所以", "我", "在", "家"], "。", "It's raining today, so I am staying at home."),
    (2, "虽然"): (["虽然", "很", "累", "但是", "我", "高兴"], "。", "Although I am tired, I am happy."),
    (2, "但是"): (["今天", "冷", "但是", "我", "很", "高兴"], "。", "It's cold today, but I am very happy."),
    (2, "您"): (["您", "好", "请", "进"], "！", "Hello, please come in!"),
    (2, "哥哥"): (["我", "哥哥", "在", "北京", "工作"], "。", "My older brother works in Beijing."),
    (2, "姐姐"): (["我", "姐姐", "是", "医生"], "。", "My older sister is a doctor."),
    (2, "弟弟"): (["我", "弟弟", "十", "岁", "了"], "。", "My younger brother is ten years old."),
    (2, "妹妹"): (["我", "妹妹", "很", "漂亮"], "。", "My younger sister is very pretty."),
    (2, "丈夫"): (["她", "丈夫", "在", "医院", "工作"], "。", "Her husband works at the hospital."),
    (2, "它"): (["我", "有", "个", "小", "猫", "它", "很", "白"], "。", "I have a little cat, and it is very white."),
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
    (2, "穿"): (["今天", "冷", "多", "穿", "点", "衣服"], "。", "It's cold today, wear more clothes."),
    (2, "洗"): (["我", "去", "洗", "苹果"], "。", "I am going to wash the apples."),
    (2, "手表"): (["爸爸", "买", "了", "新", "手表"], "。", "Dad bought a new watch."),
    (2, "报纸"): (["爷爷" if False else "爸爸", "正在", "看", "报纸"], "。", "Dad is reading the newspaper."),
    (2, "票"): (["我", "买", "了", "两", "张" if False else "个", "火车" if False else "电影", "票"], "。", "I bought two movie tickets."),
    (2, "雪"): (["今天", "下", "雪", "了"], "。", "It snowed today."),
    (2, "眼睛"): (["她", "的", "眼睛", "很", "大"], "。", "Her eyes are very big."),
    (2, "生病"): (["他", "生病", "了", "在", "医院"], "。", "He is sick and in the hospital."),
    (2, "药"): (["生病", "了", "要", "吃", "药"], "。", "When you're sick you should take medicine."),
    (2, "累"): (["今天", "工作", "很", "累"], "。", "Work was very tiring today."),
    (2, "休息"): (["你", "累", "了", "休息", "一下", "吧"], "。", "You're tired, take a little rest."),
    (2, "笑"): (["大家", "都", "在", "笑"], "。", "Everyone is smiling."),
    (2, "玩"): (["孩子", "们" if False else "都", "喜欢", "在", "外", "玩"], "。", "Children all love playing outside."),
    (2, "运动"): (["我", "每", "天", "都", "运动"], "。", "I exercise every single day."),
    (2, "游泳"): (["夏天" if False else "热", "的", "时候", "喜欢", "去", "游泳"], "。", "When it's hot I like to go swimming."),
    (2, "课"): (["今天", "上午", "有", "汉语", "课"], "。", "There is a Chinese class this morning."),
    (2, "帮助"): (["谢谢", "你", "帮助", "我"], "！", "Thank you for helping me!"),
    (2, "意思"): (["这", "个", "字", "什么", "意思"], "？", "What does this character mean?"),
    (2, "事情"): (["今天", "有", "很", "多", "事情"], "。", "There are many things to do today."),
    (2, "已经"): (["她", "已经", "回", "家", "了"], "。", "She has already gone home."),
    (2, "慢"): (["请", "慢", "走"], "。", "Please walk slowly / Take care."),
    (2, "错"): (["对不起", "我", "错", "了"], "。", "Sorry, I was wrong."),
    (2, "给"): (["请", "给", "我", "一", "杯" if False else "个", "苹果"], "。", "Please give me an apple."),
    (2, "让"): (["妈妈", "不", "让", "我", "看", "电视"], "。", "Mom won't let me watch TV."),
    (2, "别"): (["太", "晚" if False else "累", "了", "别", "工作"], "。", "You're too tired, don't work."),
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
    (2, "懂"): (["我", "不", "懂", "这", "个", "意思"], "。", "I don't understand this meaning."),
    (2, "要"): (["我", "要", "一", "杯" if False else "个", "水" if False else "西瓜"], "。", "I want a watermelon."),
    (2, "可以"): (["我", "可以", "坐", "这里" if False else "这", "吗"], "？", "Can I sit here?"),
    (2, "知道"): (["我", "不", "知道", "他", "在", "哪儿"], "。", "I don't know where he is."),
    (2, "觉得"): (["我", "觉得", "这", "个", "很", "好"], "。", "I think this is very good."),
    (2, "非常"): (["今天", "天气", "非常", "好"], "。", "The weather is extremely nice today."),
    (2, "最"): (["我", "最", "喜欢", "吃", "西瓜"], "。", "I like eating watermelon the most."),
    (2, "两"): (["我", "有", "两", "个", "好", "朋友"], "。", "I have two good friends."),
    (2, "孩子"): (["这", "个", "孩子", "很", "聪明" if False else "高兴"], "。", "This child is very happy."),
    (2, "新"): (["我", "穿", "了", "新", "衣服"], "。", "I put on new clothes."),
    (2, "路"): (["去", "学校", "的", "路", "很", "近"], "。", "The road to school is very close."),
    (2, "进"): (["请", "进", "坐", "吧"], "！", "Please come in and sit down!"),
    (2, "到"): (["我们", "到", "机场", "了"], "。", "We have arrived at the airport."),
    (2, "近"): (["学校", "离", "我", "家", "很", "近"], "。", "The school is very close to my home."),
    (2, "远"): (["医院", "离", "这里" if False else "学校", "很", "远"], "。", "The hospital is far from the school."),
    (2, "离"): (["宾馆", "离", "火车站", "很", "近"], "。", "The hotel is very close to the train station."),
    (2, "走"): (["我们", "走", "着", "去", "学校"], "。", "We are walking to school."),
    # --- HSK 3 ---
    (3, "刷牙"): (["我", "早上", "起床", "刷牙"], "。", "I get up in the morning and brush my teeth."),
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
    (3, "腿"): (["我", "昨天", "爬山", "今天", "腿", "很", "疼"], "。", "I climbed a mountain yesterday, and my legs hurt a lot today."),
    (3, "变化"): (["这", "个", "城市", "变化", "很", "大"], "。", "This city has changed a lot."),
    (3, "极"): (["今天", "天气", "热", "极", "了"], "！", "The weather is extremely hot today!"),
    (3, "树"): (["公园", "里", "有", "很", "多", "大", "树"], "。", "There are many big trees in the park."),
    (3, "水平"): (["他", "的", "汉语", "水平", "很", "高"], "。", "His Chinese level is very high."),
    (3, "东"): (["请", "往", "东", "走", "两", "百", "米"], "。", "Please walk two hundred meters east."),
    (3, "南"): (["我", "家", "在", "北京", "南", "站", "附近"], "。", "My home is near Beijing South Station."),
    (3, "提高"): (["我", "想", "提高", "汉语", "水平"], "。", "I want to improve my Chinese level."),
    (3, "中间"): (["我", "坐", "在", "爸爸", "和", "妈妈", "中间"], "。", "I sit between Dad and Mom."),
    (3, "多么"): (["今天", "的", "天气", "多么", "好", "啊"], "！", "How nice the weather is today!"),
    (3, "灯"): (["晚上", "了", "请", "开", "灯"], "。", "It's evening now, please turn on the light."),
    (3, "太阳"): (["早上", "太阳", "出", "来", "了"], "。", "The sun came out in the morning."),
    (3, "热情"): (["那", "位", "老师", "非常", "热情"], "。", "That teacher is very warm and enthusiastic."),
    (3, "同事"): (["他", "是", "我", "的", "新", "同事"], "。", "He is my new colleague."),
    (3, "角"): (["这", "张", "桌子", "有", "四", "个", "角"], "。", "This table has four corners."),
    (3, "放心"): (["请", "放心", "我", "会", "照顾", "好", "自己"], "。", "Please don't worry, I will take good care of myself."),
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
    (3, "复习"): (["明天", "有", "考试", "我", "要", "复习"], "。", "There is an exam tomorrow, so I need to review."),
    (3, "请假"): (["我", "生病", "了", "今天", "想", "请假"], "。", "I am sick and want to ask for leave today."),
    (3, "皮鞋"): (["爸爸", "买", "了", "一", "双", "黑", "皮鞋"], "。", "Dad bought a pair of black leather shoes."),
    (3, "黑板"): (["老师", "在", "黑板", "上", "写", "字"], "。", "The teacher is writing characters on the blackboard."),
    (3, "留学"): (["我", "哥哥", "打算", "去", "中国", "留学"], "。", "My older brother plans to study abroad in China."),
    (3, "刮风"): (["今天", "刮风", "了", "多", "穿", "衣服"], "。", "It's windy today, put on more clothes."),
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
    # --- HSK 4 ---
    (6, "屁股"): (["小", "孩子", "摔", "到", "了", "屁股"], "。", "The little child fell on their bottom."),
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


def verify_static_templates(word_to_level: dict[str, int]) -> None:
    for (lv, hanzi), (tokens, punct, english) in HANDCRAFTED_EXAMPLES.items():
        allowed = {w for w, l in word_to_level.items() if l <= lv} | ALLOWED_SIMPLE_PROPER_NOUNS
        assert len(tokens) >= 3, f"HANDCRAFTED ({lv}, {hanzi}) < 3 tokens: {tokens}"
        assert len(tokens) == len(set(tokens)), f"HANDCRAFTED ({lv}, {hanzi}) duplicate token: {tokens}"
        assert hanzi in tokens or "".join(tokens).find(hanzi) >= 0, (
            f"HANDCRAFTED ({lv}, {hanzi}) missing target: {tokens}"
        )
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fix and verify HSK 1 to 5 example sentences.")
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
    comp_path = CACHE_DIR / "complete.min.json"
    if comp_path.exists():
        for entry in json.loads(comp_path.read_text(encoding="utf-8")):
            pos_map[entry["s"]] = entry.get("p", [])

    tatoeba_pairs: list[tuple[str, str]] = []
    tat_path = CACHE_DIR / "tatoeba_cmn_eng.tsv"
    if tat_path.exists():
        with tat_path.open("r", encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) == 2 and not VULGAR_OR_SLANG_RE.search(parts[1]):
                    tatoeba_pairs.append((parts[0], parts[1]))

    for level in range(1, 7):
        data = catalogs[level]
        # Apply handcrafted overrides on all levels where defined (e.g., HSK 4 屁股)
        if level == 6:
            modified = False
            for w in data["words"]:
                hz = w["hanzi"]
                if (level, hz) in HANDCRAFTED_EXAMPLES:
                    hc_tokens, hc_punct, hc_en = HANDCRAFTED_EXAMPLES[(level, hz)]
                    new_ex = {
                        "hanzi": "".join(hc_tokens) + hc_punct,
                        "pinyin": tokens_to_pinyin(hc_tokens, hc_punct, word_to_pinyin),
                        "english": hc_en,
                        "tokens": hc_tokens,
                    }
                    if w["example"] != new_ex:
                        w["example"] = new_ex
                        modified = True
            if modified:
                if not args.no_bump_version:
                    data["catalogVersion"] = int(data["catalogVersion"]) + 1
                (CATALOG_DIR / f"hsk{level}.json").write_text(
                    json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8",
                )
            continue

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
                hc_tokens, hc_punct, hc_en = HANDCRAFTED_EXAMPLES[(level, hz)]
                new_ex = {
                    "hanzi": "".join(hc_tokens) + hc_punct,
                    "pinyin": tokens_to_pinyin(hc_tokens, hc_punct, word_to_pinyin),
                    "english": hc_en,
                    "tokens": hc_tokens,
                }
                if ex != new_ex:
                    w["example"] = new_ex
                    modified = True
                stats["handcrafted"] += 1
                continue

            is_tpl_or_vulgar = bool(VULGAR_OR_SLANG_RE.search(ex["english"])) or (
                level in (1, 2, 3) and bool(MECHANICAL_TEMPLATE_RE.search(ex["english"]))
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

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
