#!/usr/bin/env python3
"""Write en/ko string resources and the verified L10n tables."""
from pathlib import Path
import xml.sax.saxutils as xml

ROOT = Path("/workspace")

# (key, english, korean)
ROWS = []

def add(key, en, ko):
    ROWS.append((key, en, ko))

add("app_name", "Dingrow", "딩그로")
add("pet_default_name", "Mongi", "몽이")
add("species_base", "Mongle", "몽글")
add("species_base_blurb", "A Mongle.", "몽글.")

# tabs and chrome
pairs = {
    "tab_home": ("Home", "홈"),
    "tab_schedule": ("Schedule", "일정"),
    "tab_room": ("Decorate", "방 꾸미기"),
    "tab_my": ("Dex", "마이"),
    "screen_schedule": ("Schedule", "일정"),
    "screen_room": ("Decorate", "방 꾸미기"),
    "screen_dex": ("Dex", "도감"),
    "onb_title": ("A day with Mongi", "몽이와 함께하는 하루"),
    "onb_body": ("Finish a task and Mongi goes out, then brings a gift to decorate the room.", "일정을 완수하면 몽이가 외출하고, 선물을 가져와 방을 꾸며요."),
    "onb_next": ("Next", "다음"),
    "onb_gift_title": ("Sell gifts to decorate the room", "선물을 팔아 방을 꾸며요"),
    "onb_tip": ("Press and hold a widget to resize it.", "위젯을 길게 눌러 크기를 바꿀 수 있어요"),
    "onb_start": ("Start", "시작하기"),
    "add_widget": ("Add a widget", "위젯 추가하기"),
    "set_wallpaper": ("Set the wallpaper", "배경화면 설정하기"),
    "stat_xp": ("Growth XP   %d / %d", "성장 XP   %d / %d"),
    "stat_hunger": ("Fullness %d%%", "포만감 %d%%"),
    "stat_happy": ("Happiness %d%%", "행복 %d%%"),
    "next_task": ("Next task", "다음 일정"),
    "empty_time": ("Nothing yet", "비어 있어요"),
    "empty_hint": ("Add a task", "일정을 추가해 보세요"),
    "sparkle": ("sparkle", "반짝"),
    "gift_arrived_named": ("%s arrived", "%s이 도착했어요"),
    "coins_arrived": ("%d coins arrived", "%d코인이 도착했어요"),
    "keep": ("Keep", "보관"),
    "claim": ("Claim", "받기"),
    "sell_short": ("Sell", "판매"),
    "feed": ("Feed", "밥주기"),
    "outing": ("Walk", "산책"),
    "gift": ("Gift", "선물"),
    "settings": ("Settings", "설정"),
    "allow_exact": ("Allow exact alarms", "정확한 알람 허용"),
    "today_tasks": ("Today's tasks · %d", "오늘의 할 일 · %d"),
    "empty_schedule_cd": ("No tasks", "빈 일정"),
    "no_tasks_yet": ("No tasks yet", "아직 일정이 없어요"),
    "add": ("Add", "추가"),
    "snooze_5": ("5 min", "5분 뒤"),
    "skip": ("Skip", "건너뛰기"),
    "edit": ("Edit", "고치기"),
    "delete": ("Delete", "삭제"),
    "wallpaper_change": ("Change background", "배경 변경"),
    "place": ("Place", "배치"),
    "done_place": ("Done", "완료"),
    "view": ("View", "보기"),
    "free_place_hint": ("Furniture you own is placed without coins.", "보유한 가구는 코인 없이 배치돼요"),
    "coin_shop": ("Coin shop", "코인 상점"),
    "sell_named": ("Sell %s", "%s 판매"),
    "rotate": ("Rotate", "돌리기"),
    "remove": ("Remove", "삭제"),
    "pane_furniture": ("Furniture", "가구"),
    "pane_outfit": ("Clothes", "의상"),
    "friends_count": ("Friends %d / %d", "친구 %d / %d"),
    "empty_gifts_cd": ("No gifts", "선물 없음"),
    "collection": ("Collection", "수집"),
    "owned_short": ("Owned", "보유"),
    "owned": ("Owned", "보유 중"),
    "add_schedule": ("Add a task", "일정 추가"),
    "task_hint": ("Task", "할 일"),
    "date_row": ("Date  %s", "날짜  %s"),
    "am": ("AM", "오전"),
    "pm": ("PM", "오후"),
    "repeat": ("Repeat", "반복"),
    "repeat_off": ("None", "안 함"),
    "repeat_daily": ("Every day", "매일"),
    "repeat_weekdays": ("Weekdays", "평일"),
    "repeat_pick_days": ("Choose days", "요일 선택"),
    "repeat_weekends": ("Weekends", "주말"),
    "repeat_biweekly": ("Every other week", "격주"),
    "repeat_monthly": ("Monthly", "매월"),
    "repeat_yearly": ("Yearly", "매년"),
    "repeat_n_days": ("Every N days", "N일마다"),
    "by_date": ("Date", "날짜"),
    "nth_1": ("1st", "1번째"),
    "nth_2": ("2nd", "2번째"),
    "nth_3": ("3rd", "3번째"),
    "nth_4": ("4th", "4번째"),
    "nth_last": ("Last", "마지막"),
    "end": ("End", "끝"),
    "end_never": ("Never", "없음"),
    "end_until": ("Until a date", "날짜까지"),
    "end_count": ("N times", "N회"),
    "times_5": ("5 times", "5회"),
    "times_10": ("10 times", "10회"),
    "times_30": ("30 times", "30회"),
    "lead": ("Reminder", "미리 알림"),
    "lead_0": ("On time", "정시"),
    "lead_5": ("5 min before", "5분 전"),
    "lead_10": ("10 min before", "10분 전"),
    "lead_30": ("30 min before", "30분 전"),
    "lead_60": ("1 hour before", "1시간 전"),
    "lead_1440": ("1 day before", "1일 전"),
    "alert_mode": ("Alert", "알림 방식"),
    "alert_silent": ("Notification", "알림"),
    "alert_sound": ("Alarm sound", "알람 소리"),
    "alert_vibrate": ("Vibrate", "진동"),
    "category": ("Category", "카테고리"),
    "cat_sport": ("Exercise", "운동"),
    "cat_meal": ("Meal", "식사"),
    "cat_study": ("Study", "공부"),
    "cat_pill": ("Medicine", "약"),
    "cat_task": ("Other", "기타"),
    "edit_scope": ("Edit", "수정 범위"),
    "scope_one": ("This one", "이 일정만"),
    "scope_following": ("This and future", "이후 모두"),
    "scope_all": ("All", "전체"),
    "save": ("Save", "저장"),
    "alarm_feed_hint": ("♥ Checking in feeds Mongi", "♥ 확인하면 몽이가 밥을 먹어요"),
    "ack": ("Check", "확인"),
    "snooze_again": ("In 5 minutes", "5분 뒤 다시"),
    "done": ("Done", "완료"),
    "due_suffix": ("%s time", "%s 시간이에요"),
    "settings_lead": ("Default reminder", "기본 미리 알림"),
    "lead_minutes": ("%d min before", "%d분 전"),
    "sound": ("Sound", "소리"),
    "vibrate": ("Vibrate", "진동"),
    "quiet": ("Quiet hours 23:00–07:00", "방해 금지 23:00 ~ 07:00"),
    "alarm_sound": ("Alarm sound", "알람 소리"),
    "sound_soft": ("Soft", "부드럽게"),
    "sound_chime": ("Chime", "차임"),
    "sound_bell": ("Dingrow bell", "딩그로 벨"),
    "perm_notifications": ("Notifications", "알림"),
    "perm_on": ("Notifications allowed", "알림 허용됨"),
    "perm_off": ("Notifications are off", "알림이 꺼져 있어요"),
    "perm_exact": ("Exact alarms", "정확한 알람"),
    "exact_on": ("Exact alarms allowed", "정확한 알람 허용됨"),
    "exact_off": ("Exact alarms are needed", "정확한 알람이 필요해요"),
    "perm_battery": ("Battery optimization", "배터리 최적화 해제"),
    "battery_on": ("Battery optimization off", "배터리 최적화 해제됨"),
    "battery_off": ("A battery exception is needed", "절전 예외가 필요해요"),
    "tips_toggle": ("Show tips", "팁 보기"),
    "tip_one": ("Show one tip", "팁 하나 보기"),
    "pin_fallback": ("If adding fails, press and hold the home screen and look for Dingrow.", "추가가 안 되면 홈 화면을 길게 눌러 딩그로를 찾으세요"),
    "shop_done_title": ("Purchase complete!", "구매 완료!"),
    "iap_confirm": ("Buy %s?", "%s를 구매할까요?"),
    "pay": ("Pay", "결제하기"),
    "popular": ("Popular", "인기"),
    "restore": ("Restore purchases", "구매 복원"),
    "short_coins": ("Not enough coins", "코인이 부족해요"),
    "earn_loop": ("Finish tasks and Mongi brings gifts from walks. Sell the gifts to save coins.", "일정을 완수하면 몽이가 외출해서 선물을 가져와요. 선물을 팔아 코인을 모아요!"),
    "buy": ("Buy", "구매하기"),
    "resell_title": ("Sell", "판매하기"),
    "rarity_epic_badge": ("Legendary", "전설"),
    "resell_common": ("Price %d → sell %d (%d%%)", "원가 %d → 판매가 %d (%d%%)"),
    "resell_fixed": ("Sells for %d", "판매가 %d"),
    "close": ("Close", "닫기"),
    "reactions_title": ("Mongi tap reactions", "몽이 터치 반응"),
    "lang_label": ("Language", "언어"),
    "lang_system": ("System", "System"),
    "lang_ko": ("한국어", "한국어"),
    "lang_en": ("English", "English"),
    "walk_fallback": ("a walk", "산책"),
    "speech_together": ("Let's %s together!", "같이 %s 가요!"),
    "coins_suffix": ("coins", "코인"),
}
for k, (en, ko) in pairs.items():
    add(k, en, ko)

# weekdays 0=Sun .. 6=Sat
for i, (en, ko) in enumerate([
    ("Sun", "일"), ("Mon", "월"), ("Tue", "화"), ("Wed", "수"), ("Thu", "목"), ("Fri", "금"), ("Sat", "토"),
]):
    add(f"wd_{i}", en, ko)
for i, (en, ko) in enumerate([
    ("Mon", "월"), ("Tue", "화"), ("Wed", "수"), ("Thu", "목"), ("Fri", "금"), ("Sat", "토"), ("Sun", "일"),
]):
    add(f"wd_iso_{i}", en, ko)
for i, en in enumerate(["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]):
    add(f"month_{i+1}", en, f"{i+1}월")

# stages, moods, rarity, time
for row in [
    ("stage_egg", "Egg", "알"),
    ("stage_baby", "Baby", "아기"),
    ("stage_child", "Child", "어린이"),
    ("stage_adult", "Adult", "어른"),
    ("mood_egg", "Being kept warm", "따듯하게 품는 중"),
    ("mood_happy", "Happy", "행복해요"),
    ("mood_calm", "Calm", "평온해요"),
    ("mood_sad", "Down", "시무룩해요"),
    ("mood_hungry", "Hungry", "배고파요"),
    ("mood_sleepy", "Sleeping", "자는 중"),
    ("mood_away", "Out", "외출 중"),
    ("rarity_common", "Common", "흔함"),
    ("rarity_uncommon", "Uncommon", "조금 특별"),
    ("rarity_rare", "Rare", "희귀"),
    ("rarity_epic", "Legendary", "아주 희귀"),
    ("drop_coins", "Coins", "코인"),
    ("drop_common", "Common", "흔함"),
    ("drop_rare", "Rare", "희귀"),
    ("drop_legendary", "Legendary", "전설"),
    ("time_morning", "Morning", "아침"),
    ("time_day", "Day", "낮"),
    ("time_evening", "Evening", "저녁"),
    ("time_night", "Night", "밤"),
    ("preset_wood", "Cozy room", "포근한 방"),
    ("preset_mint", "Mint room", "민트 방"),
    ("preset_starry", "Night-sky room", "밤하늘 방"),
    ("progress_child", "XP to child %d/%d", "어린이까지 경험치 %d/%d"),
    ("progress_adult", "XP to adult %d/%d", "어른까지 경험치 %d/%d"),
    ("progress_done", "All grown", "다 자랐어요"),
    ("outing_soon", "Back soon", "곧 돌아와요"),
    ("outing_later", "Back in about %d min", "약 %d분 뒤 돌아와요"),
    ("confirm_buy", "Buy %s for %d coins?", "%s을 %d코인에 살까요?"),
    ("greet_morning", "Good morning. %s stretches.", "좋은 아침이에요. %s 기지개를 켜요."),
    ("greet_day", "A lazy afternoon. Looking out the window.", "나른한 오후예요. 창밖을 보고 있어요."),
    ("greet_evening", "Evening is settling in.", "저녁이 내려앉았어요."),
    ("greet_night", "When the stars come out, %s closes their eyes.", "별이 뜨면 %s 눈을 감아요."),
    ("rule_once", ("Once", "한 번")),
    ("rule_daily", ("Every day", "매일")),
    ("rule_every_n_days", ("Every %d days", "%d일마다")),
    ("rule_weekdays", ("Weekdays", "평일")),
    ("rule_weekends", ("Weekends", "주말")),
    ("rule_weekly", ("Weekly", "매주")),
    ("rule_weekly_days", ("Weekly %s", "매주 %s")),
    ("rule_n_weeks", ("Every %d weeks", "%d주마다")),
    ("rule_n_weeks_days", ("Every %d weeks %s", "%d주마다 %s")),
    ("rule_biweekly", ("Every other week", "격주")),
    ("rule_monthly", ("Monthly", "매월")),
    ("rule_n_months", ("Every %d months", "%d달마다")),
    ("rule_month_last", ("Monthly, last", "매월 마지막")),
    ("rule_month_nth", ("Monthly, %d", "매월 %d번째")),
    ("rule_yearly", ("Yearly", "매년")),
    ("rule_n_years", ("Every %d years", "%d년마다")),
    ("rule_end_count", (" · %d times", " · %d회")),
    ("rule_end_until", (" · until a date", " · 날짜까지")),
    ("date_ko", "%d월 %d일 (%s)", "%d월 %d일 (%s)"),
    ("date_en", "%s %d (%s)", "%s %d (%s)"),
]:
    if len(row) == 2:
        add(row[0], row[1][0], row[1][1])
    else:
        add(*row)

# service messages
for k, en, ko in [
    ("msg_need_name", "Write a name", "이름을 적어 주세요"),
    ("msg_name_len", "Names can be 12 characters", "이름은 12자까지예요"),
    ("msg_named", "I'll call you %s", "%s라고 부를게요"),
    ("msg_hello", "Nice to meet you, %s", "잘 부탁해, %s"),
    ("msg_missing_task", "Couldn't find that task", "일정을 찾지 못했어요"),
    ("msg_need_task", "Write the task", "할 일을 적어 주세요"),
    ("msg_edited_one", "Updated this one", "이 일정만 고쳤어요"),
    ("msg_edited_following", "Made a new series from here", "이후 일정을 새로 만들었어요"),
    ("msg_edited_all", "Updated the whole series", "전체 일정을 고쳤어요"),
    ("msg_skipped", "Skipped this one", "이번 회차는 건너뛰었어요"),
    ("msg_deleted", "Deleted the task", "일정을 지웠어요"),
    ("msg_snoozed", "Snoozed for 5 minutes", "5분 뒤로 미뤘어요"),
    ("msg_ack_full", "Already full, so XP +%d", "배가 불러서 경험치 +%d"),
    ("msg_ack_feed", "You checked in, so Mongi ate +%d", "알람을 확인해서 몽이가 밥을 먹었어요 +%d"),
    ("msg_settings_saved", "Settings saved", "설정을 저장했어요"),
    ("msg_tips_off", "Tips are off", "팁이 꺼져 있어요"),
    ("msg_tip_wait", "Let's look again in a bit", "조금 뒤에 다시 볼게요"),
    ("msg_buy_first", "Buy it in the shop first", "먼저 상점에서 사 주세요"),
    ("msg_already_placed", "It's already placed", "이미 놓여 있어요"),
    ("msg_placed_free", "Placed without coins", "코인 없이 배치했어요"),
    ("msg_rotated", "Turned it", "방향을 돌렸어요"),
    ("msg_cleared", "Put it away", "치웠어요"),
    ("msg_not_owned", "You don't have that", "가지고 있지 않아요"),
    ("msg_nothing_restore", "Nothing to restore", "복원할 구매가 없어요"),
    ("msg_restored", "Purchases restored", "구매를 복원했어요"),
    ("msg_unknown_product", "Unknown product", "없는 상품이에요"),
    ("msg_no_receipt", "No payment info", "결제 정보가 없어요"),
    ("msg_already_granted", "That purchase is already applied", "이미 반영된 구매예요"),
    ("msg_coins_in", "%d coins arrived", "코인 %d개가 들어왔어요"),
    ("msg_no_tasks_left", "No tasks left", "남은 일정이 없어요"),
    ("msg_full", "Already full", "배가 불러요"),
    ("msg_no_food", "No food. Buy some in the shop", "먹이가 없어요. 상점에서 사 주세요"),
    ("msg_grew", "Grew into a %s", "%s로 자랐어요"),
    ("msg_fed", "Fed the %s. Fullness +%d", "%s을 먹였어요. 포만감 +%d"),
    ("msg_outing_left", "Mongi went out for a walk", "몽이가 산책을 떠났어요"),
    ("msg_outing_left_short", "Mongi headed out", "몽이가 외출을 떠났어요"),
    ("msg_no_gift", "No gift has arrived", "도착한 선물이 없어요"),
    ("msg_kept", "Kept the %s", "%s을 보관했어요"),
    ("msg_no_sell", "No gift to sell", "팔 선물이 없어요"),
    ("msg_gift_missing", "Couldn't find the gift", "선물을 찾지 못했어요"),
    ("msg_confirm_timeout", "The confirm window passed", "구매 확인 시간이 지났어요"),
    ("msg_buy_cancelled", "Purchase cancelled", "구매를 취소했어요"),
    ("msg_not_outfit", "That isn't an outfit you own", "가지고 있는 옷이 아니에요"),
    ("msg_outfit_off", "Took the outfit off", "옷을 벗었어요"),
    ("msg_outfit_on", "Put on the %s", "%s을 입었어요"),
    ("msg_unknown_friend", "Unknown friend", "모르는 친구예요"),
    ("msg_not_met", "You haven't met this friend yet", "아직 만나지 못한 친구예요"),
    ("msg_brought", "Brought %s home", "%s을 데려왔어요"),
    ("msg_bad_slot", "That doesn't go in this spot", "이 칸에 놓을 수 없어요"),
    ("msg_slot_cleared", "Cleared", "치웠어요"),
    ("msg_slot_set", "Placed the %s", "%s을 두었어요"),
    ("msg_no_room", "No such room", "방이 없어요"),
    ("msg_room_changed", "Changed to %s", "%s 으로 바꿨어요"),
    ("msg_already_done", "That task is already done", "이미 완료한 일정이에요"),
    ("msg_just_done", "Just finished that", "방금 완료했어요"),
    ("msg_hatched", "The egg hatched! Meet %s", "알이 부화했어요! %s를 만나요"),
    ("msg_complete", "Done! XP +%d, coins +%d", "완료! 경험치 +%d, 코인 +%d"),
    ("msg_no_coins_in", "No coins to collect", "받을 코인이 없어요"),
    ("msg_coins_got", "Received %d coins", "%d코인을 받았어요"),
    ("msg_no_item", "No such item", "없는 물건이에요"),
    ("msg_unsellable", "That can't be bought", "살 수 없는 물건이에요"),
    ("msg_bought", "Bought the %s", "%s을 샀어요"),
    ("msg_sold", "Sold the %s for %d coins", "%s을 %d코인에 팔았어요"),
    ("msg_gift_here", "A gift arrived", "선물이 도착했어요"),
    ("msg_brought_gift", "%s brought %s. %s", "%s %s을 가져왔어요. %s"),
    ("msg_title_len", "Titles can be 40 characters", "제목은 40자까지예요"),
    ("msg_too_many", "You can keep %d tasks", "일정은 %d개까지예요"),
    ("msg_will_remind", "I'll remind you about \"%s\"", "\"%s\" 알려줄게요"),
    ("msg_block_stage", "They can go out once they are a child", "어린이가 되면 밖에 나갈 수 있어요"),
    ("msg_block_out", "Already out", "이미 외출 중이에요"),
    ("msg_block_gift", "Sort the gift first", "선물을 먼저 정리해 주세요"),
    ("msg_block_hunger", "Too hungry to go out", "배가 고파서 나가지 않아요"),
    ("msg_block_energy", "Too tired, resting", "피곤해서 쉬고 있어요"),
    ("msg_block_cool", "Rest a little, then you can go out again", "조금 쉬었다가 다시 나갈 수 있어요"),
    ("msg_deny_coins", "Not enough coins. Finish tasks and Mongi brings gifts from walks. Sell those gifts to save coins.", "코인이 부족해요. 일정을 완수하면 몽이가 외출해서 선물을 가져오고, 그 선물을 팔아 코인을 모을 수 있어요"),
    ("msg_legendary", "A sparkle — a legendary gift landed in your hands", "반짝, 전설이 손 안에 내려왔어요"),
    ("notice_title", "%s is calling", "%s 불러요"),
    ("notice_body", "%s %s time", "%s %s 시간이에요"),
    ("widget_room", "%s's cozy room", "%s의 아늑한 방"),
    ("template_water", "Morning water", "아침 물 마시기"),
    ("template_pill", "Take medicine", "약 먹기"),
    ("template_sport", "Exercise", "운동"),
    ("template_recycle", "Recycling", "분리수거"),
]:
    add(k, en, ko)

# reactions: id -> (en line, ko line, en name, ko name) for showcase names separately
REACTIONS = [
    ("bounce", "Boing!", "통통!", "Bounce", "통통"),
    ("squish", "Squish~", "말랑~", "Squish", "말랑"),
    ("spin", "Spinning", "빙글빙글", "Spin", "빙글"),
    ("jump", "Yap!", "얍!", "Jump", "점프"),
    ("heart", "Love you!", "좋아해!", "Hearts", "하트"),
    ("blush", "Shy…", "부고…", "Blush", "부끄"),
    ("yawn", "Sleepy…", "졸려…", "Yawn", "하품"),
    ("surprise", "Whoa!", "깜짝이야!", "Surprise", "깜짝"),
    ("dance", "La la~", "랄랄라~", "Dance", "춤"),
    ("bow", "Hello!", "안녕!", "Hello", "인사"),
    ("pat", "That feels nice~", "기분 좋아~", "Pat", "쓰담"),
    ("tickle", "Hee hee, that tickles!", "히히 간지러워!", "Tickle", "간질"),
]
for rid, en, ko, en_name, ko_name in REACTIONS:
    add(f"react_{rid}", en, ko)
    add(f"react_{rid}_name", en_name, ko_name)

EXTRA = [
    ("t01", "That tickles", "간지러워요"),
    ("t02", "Tap me again", "또 눌러 주세요"),
    ("t03", "Hehe, thinking of snacks", "헤헤, 간식 생각나요"),
    ("t04", "Let's do today together", "오늘도 같이 해요"),
    ("t05", "My cheeks got warm", "볼이 따뜻해졌어요"),
    ("t06", "Whoa!", "깜짝이야!"),
    ("t07", "Tail's wagging", "꼬리가 흔들려요"),
    ("t08", "I'm right here", "나 여기 있어요"),
    ("t09", "Pats feel nice", "쓰담쓰담 좋아요"),
    ("t10", "Eyes sparkling", "눈이 반짝"),
    ("t11", "I'm hungry… kidding", "배고파요… 농담이에요"),
    ("t12", "Let's do just one task", "일정 하나만 같이 해요"),
    ("t13", "So fluffy", "포슬포슬해요"),
    ("t14", "A yawn slipped out", "하품이 나와요"),
    ("t15", "Shall we dance?", "춤출까요?"),
    ("t16", "Waving", "손 흔들어요"),
    ("t17", "Heart's thumping", "심장이 두근"),
    ("t18", "A star fell", "별이 떨어졌어요"),
    ("t19", "I'm shy", "부끄러워요"),
    ("t20", "Hug me", "안아 주세요"),
    ("t21", "Today feels lucky", "오늘은 운이 좋아요"),
    ("t22", "How about a glass of water", "물 한 잔 어때요"),
    ("t23", "We made eye contact", "나랑 눈 마주쳤어요"),
    ("t24", "Tiptoeing", "살금살금"),
    ("t25", "Pop! I bounced", "팡! 하고 튀었어요"),
    ("t26", "Feeling great", "기분 최고예요"),
    ("t27", "A little sleepy", "조금 졸려요"),
    ("t28", "I like the sun", "해가 좋아요"),
    ("t29", "The moon is watching", "달이 보고 있어요"),
    ("t30", "I want to level up", "레벨업 하고 싶어요"),
    ("t31", "I smell cookies", "쿠키 냄새나요"),
    ("t32", "I'll sit right here", "여기 앉아 있을게요"),
    ("t33", "You can pet me again", "또 만져도 돼요"),
    ("t34", "Love is floating", "사랑이 둥실"),
    ("l01", "Thanks for holding on", "오래 눌러 줘서 고마워요"),
    ("l02", "I feel safe", "안심이 돼요"),
    ("l03", "Feels like a tight hug", "꼭 안긴 기분이에요"),
    ("l04", "I don't want to hide", "숨고 싶지 않아요"),
    ("l05", "My heart is slowing down", "마음이 느긋해져요"),
    ("l06", "Stay like this a moment", "이대로 잠깐만요"),
    ("l07", "Your palm is warm", "손바닥이 포근해요"),
    ("l08", "Drifting off", "잠이 솔솔"),
    ("l09", "Cheering myself on", "힘내는 중이에요"),
    ("l10", "I like that you stayed", "오래 있어 줘서 좋아"),
    ("l11", "I'll hold on tight", "꼭 잡고 있을게요"),
    ("l12", "Quiet is nice", "고요해서 좋아요"),
    ("s01", "Couldn't hold the tickle", "간지럼 참기 실패"),
    ("s02", "Hehehe", "까르르"),
    ("s03", "Belly tickles", "배가 간지러워요"),
    ("s04", "Should I run or stay", "도망갈까 말까"),
    ("s05", "Tickle more", "더 간지럽혀요"),
    ("s06", "A little kick", "발로 살짝 밀어요"),
    ("s07", "A laugh leaked out", "웃음이 새요"),
    ("s08", "Smooth, that feels good", "스르륵, 기분 좋아요"),
    ("s09", "Fur stood up, then settled", "털이 곤두섰다가 풀렸어요"),
    ("s10", "One more stroke", "한 번 더 스윽"),
    ("s11", "Tickle attack", "간지럼 공격이에요"),
    ("s12", "Rolling around", "데굴데굴"),
]
for rid, en, ko in EXTRA:
    add(f"react_{rid}", en, ko)

TIPS_KO = [
    "일정을 등록하고 완수하면 선물을 얻어요",
    "완료한 일정마다 경험치와 코인이 조금 쌓여요",
    "배가 고프면 산책을 나가지 않아요",
    "외출에서 돌아오면 선물을 보관하거나 팔 수 있어요",
    "선물을 팔면 코인이 되고, 코인으로 가구를 사요",
    "이미 가진 가구는 코인 없이 옮겨 둘 수 있어요",
    "쿠션 침대, 민트 러그, 스탠드 조명은 기본으로 놓을 수 있어요",
    "되팔기는 상점 가격의 약 70%예요",
    "아주 가끔 상점에 없는 희귀 선물이 따라와요",
    "알림을 확인하면 몽이가 밥을 먹어요",
    "배가 부르면 밥 대신 경험치가 조금 올라요",
    "정시, 5분 전, 하루 전처럼 알림을 여러 개 둘 수 있어요",
    "평일만 반복하거나 요일을 직접 고를 수 있어요",
    "조용한 시간은 설정에서 켜요. 기본은 꺼져 있어요",
    "알람 소리, 진동, 무음 중에 고를 수 있어요",
    "정확한 알람을 허용하면 제시간에 더 잘 울려요",
    "홈 화면을 길게 눌러도 위젯을 찾을 수 있어요",
    "위젯을 누르면 몽이가 반응해요",
    "배경화면을 터치해도 몽이가 반응해요",
    "펫을 짧게 누르고, 길게 누르고, 문질러 보세요",
    "기분, 시간, 배고픔에 따라 대사가 달라져요",
    "도감의 친구는 만나면 데려올 수 있어요",
    "오늘의 상품은 하루에 한 번 바뀌어요",
    "같은 일정은 너무 빨리 다시 완료할 수 없어요",
    "반복 일정은 끝낸 뒤 다음 회로 넘어가요",
    "코인이 부족하면 일정을 끝내고 선물을 팔아 보세요",
    "프리미엄은 광고를 끄고, 코인 꾸러미는 상점을 도와요",
    "구매를 복원하면 이 기기의 결제 기록을 다시 반영해요",
    "팁은 설정에서 끌 수 있어요",
    "밤에는 몽이가 졸려 해요",
    "행복이 높으면 방을 더 자주 걸어 다녀요",
    "이름을 부르듯 머리를 톡톡 눌러 주세요",
]
TIPS_EN = [
    "Add a task and finish it to get a gift",
    "Each finished task adds a little XP and a few coins",
    "A hungry pet will not go out for a walk",
    "When the walk ends, keep the gift or sell it",
    "Selling a gift makes coins, and coins buy furniture",
    "Furniture you already own can be moved without coins",
    "The cushion bed, mint rug, and floor lamp can be placed for free",
    "Selling a common item returns about 70% of the shop price",
    "Once in a while a rare gift that isn't in the shop comes along",
    "Checking an alarm feeds Mongi",
    "If Mongi is already full, you get a little XP instead of food",
    "You can set several reminders, like on time, 5 minutes before, or a day before",
    "Repeat on weekdays only, or pick the days yourself",
    "Turn quiet hours on in Settings. They start off",
    "Choose an alarm sound, vibration, or silence",
    "Allowing exact alarms makes them fire closer to the minute",
    "You can also press and hold the home screen to find the widget",
    "Tap the widget and Mongi reacts",
    "Touching the wallpaper makes Mongi react too",
    "Try a short tap, a long press, and a rub",
    "The line changes with mood, time, and hunger",
    "Friends in the dex can come home once you meet them",
    "Today's item changes once a day",
    "The same task can't be completed again too quickly",
    "A repeating task moves to the next time after you finish it",
    "If you're short on coins, finish tasks and sell gifts",
    "Premium turns ads off, and coin packs help the shop",
    "Restore purchases applies this device's payment history again",
    "You can turn tips off in Settings",
    "Mongi gets sleepy at night",
    "High happiness means more walking around the room",
    "Tap the top of the head, like saying the name",
]
assert len(TIPS_KO) == len(TIPS_EN)
for i, (en, ko) in enumerate(zip(TIPS_EN, TIPS_KO)):
    add(f"tip_{i}", en, ko)

ITEMS = [
    ("strawberry_cake", "Strawberry cake", "딸기 케이크"),
    ("rice_ball", "Rice ball", "주먹밥"),
    ("milk_bottle", "Milk bottle", "우유병"),
    ("cookie", "Cookie", "쿠키"),
    ("ribbon", "Ribbon", "리본"),
    ("beret", "Beret", "베레모"),
    ("knit_scarf", "Knit scarf", "니트 목도리"),
    ("raincoat", "Raincoat", "우비"),
    ("crown", "Crown", "왕관"),
    ("sunglasses", "Sunglasses", "선글라스"),
    ("sprout_hat", "Sprout hat", "새싹 모자"),
    ("hoodie", "Hoodie", "후드티"),
    ("straw_hat", "Straw hat", "밀짚모자"),
    ("bunny_ears", "Bunny-ear headband", "토끼 귀 머리띠"),
    ("flower_crown", "Flower crown", "꽃 화관"),
    ("bow_tie", "Bow tie", "나비넥타이"),
    ("rain_boots", "Yellow boots", "노란 장화"),
    ("cushion_bed", "Cushion bed", "쿠션 침대"),
    ("wood_bed", "Wood bed", "원목 침대"),
    ("bookshelf", "Bookshelf", "책장"),
    ("round_table", "Round table", "원형 테이블"),
    ("floor_lamp", "Floor lamp", "스탠드 조명"),
    ("round_chair", "Round chair", "동글 의자"),
    ("mint_rug", "Mint rug", "민트 러그"),
    ("flower_pot", "Flower pot", "화분"),
    ("window", "Window", "창문"),
    ("string_lights", "String lights", "줄 조명"),
    ("fish_tank", "Fish tank", "어항"),
    ("piano", "Piano", "피아노"),
    ("tv", "TV", "TV"),
    ("desk", "Desk", "책상"),
    ("wardrobe", "Wardrobe", "옷장"),
    ("bathtub", "Bathtub", "욕조"),
    ("cat_tower", "Cat tower", "캣타워"),
    ("food_bowl", "Food bowl", "밥그릇"),
    ("wall_clock", "Wall clock", "벽시계"),
    ("blanket", "Blanket", "담요"),
    ("sofa", "Mini sofa", "미니 소파"),
    ("cloud_lamp", "Cloud lamp", "구름 조명"),
    ("picture_frame", "Picture frame", "액자"),
    ("seashell", "Seashell", "조개껍데기"),
    ("clover", "Four-leaf clover", "네잎클로버"),
    ("acorn", "Acorn", "도토리"),
    ("pebble", "Pebble", "조약돌"),
    ("feather", "Feather", "깃털"),
    ("star_piece", "Star piece", "별조각"),
    ("mushroom_item", "Mushroom", "버섯"),
    ("letter", "Letter", "편지"),
    ("maple_leaf", "Maple leaf", "단풍잎"),
    ("wildflower", "Wildflower", "들꽃"),
    ("moon_bell", "Moon bell", "달종"),
    ("glass_marble", "Glass marble", "유리구슬"),
    ("dawn_feather", "Dawn feather", "새벽깃"),
    ("comet_shard", "Comet shard", "혜성조각"),
]
for iid, en, ko in ITEMS:
    add(f"item_{iid}", en, ko)

SPECIES = [
    ("cat", "Cat", "고양이"),
    ("rabbit", "Rabbit", "토끼"),
    ("bear", "Bear", "곰"),
    ("frog", "Frog", "개구리"),
    ("chick", "Chick", "병아리"),
    ("mandu", "Dumpling", "만두"),
    ("strawberry", "Strawberry", "딸기"),
    ("greentea", "Green tea", "녹차"),
    ("peach", "Peach", "복숭아"),
    ("cloud", "Cloud", "구름"),
    ("star", "Star", "별"),
    ("ghost", "Ghost", "유령"),
    ("sprout", "Sprout", "새싹"),
    ("penguin", "Penguin", "펭귄"),
    ("hamster", "Hamster", "햄스터"),
    ("fox", "Fox", "여우"),
    ("seal", "Seal", "물범"),
    ("panda", "Panda", "판다"),
    ("babydragon", "Baby dragon", "아기용"),
    ("egg", "Egg", "달걀"),
    ("milk", "Milk", "우유"),
    ("injeolmi", "Injeolmi", "인절미"),
    ("lion", "Lion", "사자"),
    ("chestnut", "Chestnut", "밤"),
    ("tiger", "Tiger", "호랑이"),
    ("squirrel", "Squirrel", "다람쥐"),
    ("duck", "Duck", "오리"),
    ("sheep", "Sheep", "양"),
    ("whale", "Whale", "고래"),
    ("octopus", "Octopus", "문어"),
    ("mushroom", "Mushroom", "버섯"),
    ("sunflower", "Sunflower", "해바라기"),
    ("lemon", "Lemon", "레몬"),
    ("pudding", "Pudding", "푸딩"),
    ("macaron", "Macaron", "마카롱"),
    ("donut", "Donut", "도넛"),
    ("sweetpotato", "Sweet potato", "고구마"),
    ("snowman", "Snowman", "눈사람"),
    ("rainbow", "Rainbow", "무지개"),
    ("moon", "Moon", "달"),
    ("owl", "Owl", "부엉이"),
    ("koala", "Koala", "코알라"),
    ("otter", "Otter", "수달"),
    ("hen", "Hen", "암탉"),
    ("pig", "Pig", "돼지"),
    ("puppy", "Puppy", "강아지"),
    ("shark", "Shark", "상어"),
    ("butter", "Butter", "버터"),
    ("palette", "Palette", "팔레트"),
    ("barley", "Barley", "보리"),
]
for sid, en, ko in SPECIES:
    add(f"species_{sid}", en, ko)
    add(f"species_{sid}_blurb", f"A Mongle {en.lower()}.", f"{ko} 몽글.")

IAP = [
    ("dingrow_coins_small", "A handful of coins", "코인 한 줌", "500", "500"),
    ("dingrow_coins_medium", "A pouch of coins", "코인 주머니", "1,200", "1,200"),
    ("dingrow_coins_large", "A chest of coins", "코인 상자", "3,000", "3,000"),
    ("dingrow_coins_vault", "A vault of coins", "코인 금고", "6,500", "6,500"),
    ("dingrow_premium", "Premium pack", "프리미엄 팩", "10,000 coins + legendary", "코인 10,000 + 전설"),
]
for iid, en, ko, en_b, ko_b in IAP:
    add(f"iap_{iid}", en, ko)
    add(f"iap_{iid}_blurb", en_b, ko_b)

# widget / notification subset also lives in the full catalog
WIDGET = [
    ("widget_small_name", "Dingrow", "딩그로"),
    ("widget_small_desc", "Pet, mood, and the next task. Feed and complete from the widget.", "펫, 기분, 다음 일정. 먹이와 완료를 위젯에서 할 수 있어요."),
    ("widget_large_name", "Dingrow room", "딩그로의 방"),
    ("widget_large_desc", "The decorated room and pet. Feed, complete, sell a gift, today's shop, change the room.", "꾸민 방과 펫. 먹이, 완료, 선물 판매, 오늘의 상점, 방 바꾸기."),
    ("wallpaper_name", "Dingrow wallpaper", "딩그로 배경"),
    ("channel_schedule", "Schedule reminders", "일정 알림"),
    ("channel_schedule_desc", "Dingrow reminds you when a task is due.", "딩그로가 일정 시간에 알려 줘요."),
    ("channel_gift", "Pet news", "펫 소식"),
    ("channel_gift_desc", "Gifts and outings.", "선물과 외출 소식."),
    ("action_ack", "Check", "확인"),
    ("action_snooze", "In 5 min", "5분 뒤"),
    ("action_done", "Done", "완료"),
    ("channel_silent", "Silent", "무음"),
    ("channel_vibrate", "Vibrate", "진동"),
    ("channel_soft", "Soft", "부드럽게"),
    ("channel_chime", "Chime", "차임"),
    ("channel_bell", "Dingrow bell", "딩그로 벨"),
    ("widget_mood", "Today's mood", "오늘의 기분"),
    ("widget_next", "Next task", "다음 일정"),
    ("widget_none", "No task", "일정 없음"),
    ("widget_done", "Done", "완료"),
    ("widget_feed", "Feed", "밥주기"),
    ("widget_sell", "Sell gift", "선물 팔기"),
    ("widget_today", "Today's item", "오늘의 상품"),
    ("widget_decorate", "Decorate", "꾸미기"),
    ("widget_confirm", "Confirm", "확인"),
    ("widget_cancel", "Cancel", "취소"),
    ("widget_add", "Add a task", "일정 추가"),
    ("widget_none_large", "No upcoming task", "예정된 일정 없음"),
    ("widget_name", "Dingrow", "딩그로"),
    ("widget_description", "Care for Dingrow on your Home Screen and finish tasks.", "홈 화면에서 딩그로를 돌보고 일정을 마쳐요."),
    ("widget_gift_line", "Gift %s · %d coins", "선물 %s · %d코인"),
    ("widget_room_cd", "Room", "방"),
    ("widget_preview_line", "3:00 PM Time for a walk", "15:00 산책할 시간이에요"),
    ("widget_preview_speech", "A walk in 10 minutes!", "10분 뒤에 산책이에요!"),
    ("widget_preview_pet", "Mongi", "몽이"),
    ("widget_preview_mood", "Happy", "기분 좋아요"),
    ("widget_preview_stage", "Baby", "아기"),
    ("widget_preview_task", "Drink water", "물 마시기"),
    ("widget_preview_time", "Today 9:00", "오늘 09:00"),
    ("widget_preview_item", "Leaf cookie", "풀잎 쿠키"),
    ("widget_preview_preset", "Cozy room", "포근한 방"),
    ("widget_preview_progress", "XP to child 8/50", "어린이까지 경험치 8/50"),
    ("intent_feed", "Feed", "먹이 주기"),
    ("intent_feed_desc", "Feed the pet.", "펫에게 먹이를 줍니다."),
    ("intent_complete", "Complete the next task", "다음 일정 완료"),
    ("intent_complete_desc", "Complete the nearest task.", "가장 가까운 일정을 완료합니다."),
    ("intent_sell", "Sell gift", "선물 팔기"),
    ("intent_sell_desc", "Sell the gift that arrived, or the latest gift.", "도착한 선물이나 최근 선물을 팝니다."),
    ("intent_buy", "Buy today's item", "오늘의 물건 사기"),
    ("intent_buy_desc", "Ask before buying today's shop item.", "오늘의 상점 물건을 살지 확인합니다."),
    ("intent_confirm", "Confirm purchase", "구매 확정"),
    ("intent_confirm_desc", "Buy the item you just picked.", "방금 고른 물건을 삽니다."),
    ("intent_cancel", "Cancel purchase", "구매 취소"),
    ("intent_cancel_desc", "Cancel the purchase confirmation.", "구매 확인을 취소합니다."),
    ("intent_preset", "Change the room", "방 바꾸기"),
    ("intent_preset_desc", "Switch to the next room preset.", "다음 방 프리셋으로 바꿉니다."),
]
for row in WIDGET:
    add(*row)

add("msg_no_confirm", "Nothing to confirm", "확인할 구매가 없어요")
add("progress_hatch", "Tasks until hatch %d/%d", "부화까지 일정 %d/%d")
add("coins_amount", "%d coins", "%d코인")
add("msg_billing_screen", "Can't open the payment screen", "결제 화면을 열 수 없어요")
add("msg_billing_ok", "Paid", "결제됐어요")
add("msg_billing_cancel", "Payment cancelled", "결제를 취소했어요")
add("msg_billing_fail", "Payment failed", "결제에 실패했어요")
add("msg_billing_connect", "Couldn't connect to billing", "결제 연결에 실패했어요")
add("msg_billing_missing", "Couldn't find that product", "상품을 찾지 못했어요")
add("msg_billing_restored", "Restored", "복원")
add("msg_test_purchase", "Test purchase", "테스트 구매")
add("listing_feature_line1", "Finish a task", "할 일을 끝내면")
add("listing_feature_line2", "and grow together", "함께 자라요")
add("listing_feature_sub", "Reminders · rooms · a friend dex", "일정 알림 · 방 꾸미기 · 친구 도감")
add("listing_home_title", "Finish tasks and grow", "할 일을 끝내면 자라요")
add("listing_home_sub", "Feed, walk, and spend the day together", "밥 주고, 산책하고, 같이 지내요")
add("listing_schedule_title", "A reminder, right on time", "제시간에 알려 줄게요")
add("listing_schedule_sub", "Complete it, or snooze for 5 minutes", "완료하거나 5분 뒤로 미뤄요")
add("listing_room_title", "Decorate the starry room", "밤하늘 방을 꾸며요")
add("listing_room_sub", "Change the room with furniture and clothes", "가구와 옷으로 방을 바꿔요")
add("listing_shop_title", "Sell gifts to save coins", "선물을 팔아 코인을 모아요")
add("listing_shop_sub", "Spend the coins on furniture and clothes", "모은 코인으로 가구와 옷을 사요")
add("listing_dex_title", "Collect 50 friends", "친구 50종을 모아 봐요")
add("listing_dex_sub", "Bring them home from the dex or a walk", "도감에서 데려오거나 산책에서 만나요")
add("listing_widgets_title", "Together on the home screen", "홈 화면에서도 함께")
add("listing_widgets_sub", "Feed and finish tasks from the widget", "위젯으로 밥 주고 일정을 완료해요")
add("listing_now", "Now", "지금")
add("listing_alarm_body", "Bori in the egg: time for \"drink water\"", "알 속의 보리: \"물 마시기\" 할 시간이에요")
add("listing_date_sample", "Wednesday, Oct 21", "10월 21일 수요일")
add("listing_bori", "Bori", "보리")

keys = [k for k, _, _ in ROWS]
assert len(keys) == len(set(keys)), [k for k in keys if keys.count(k) > 1][:10]


def xml_escape(text):
    return (text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "\\'")
                .replace('"', '\\"'))


def write_xml(path, lang_index):
    lines = ["<resources>"]
    for key, en, ko in ROWS:
        value = en if lang_index == 0 else ko
        lines.append(f'    <string name="{key}">{xml_escape(value)}</string>')
    lines.append("</resources>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def kotlin_map(name, lang_index):
    lines = [f"internal val {name}: Map<String, String> = buildMap {{"]
    for key, en, ko in ROWS:
        value = en if lang_index == 0 else ko
        escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        lines.append(f'    put("{key}", "{escaped}")')
    lines.append("}")
    return "\n".join(lines)


write_xml(ROOT / "shared/src/commonMain/resources/l10n/values/strings.xml", 0)
write_xml(ROOT / "shared/src/commonMain/resources/l10n/values-ko/strings.xml", 1)
write_xml(ROOT / "composeApp/src/commonMain/composeResources/values/strings.xml", 0)
write_xml(ROOT / "composeApp/src/commonMain/composeResources/values-ko/strings.xml", 1)

def mobile_keys():
    prefixes = ("widget_", "action_", "channel_", "intent_")
    names = {"app_name", "wallpaper_name"}
    picked = []
    for key, _, _ in ROWS:
        if key in names or key.startswith(prefixes):
            if key not in picked:
                picked.append(key)
    return picked

def write_android_exact(path, lang_index):
    lookup = {k: (en, ko) for k, en, ko in ROWS}
    lines = ["<resources>"]
    for key in mobile_keys():
        en, ko = lookup[key]
        value = en if lang_index == 0 else ko
        formatted = ' formatted="false"' if value.count("%") > 1 else ""
        lines.append(f'    <string name="{key}"{formatted}>{xml_escape(value)}</string>')
    lines.append("</resources>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")

write_android_exact(ROOT / "composeApp/src/androidMain/res/values/strings.xml", 0)
write_android_exact(ROOT / "composeApp/src/androidMain/res/values-ko/strings.xml", 1)

def write_ios(path, lang_index):
    lookup = {k: (en, ko) for k, en, ko in ROWS}
    lines = []
    for key in mobile_keys():
        en, ko = lookup[key]
        value = (en if lang_index == 0 else ko).replace("\\", "\\\\").replace('"', '\\"')
        lines.append(f'"{key}" = "{value}";')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")

write_ios(ROOT / "iosApp/PetWidget/en.lproj/Localizable.strings", 0)
write_ios(ROOT / "iosApp/PetWidget/ko.lproj/Localizable.strings", 1)
write_ios(ROOT / "iosApp/iosApp/en.lproj/Localizable.strings", 0)
write_ios(ROOT / "iosApp/iosApp/ko.lproj/Localizable.strings", 1)

kt = """package app.mongle.l10n

/** Mirrors l10n/values and values-ko. LocaleParityTest fails if the XML drifts. */
""" + kotlin_map("En", 0) + "\n\n" + kotlin_map("Ko", 1) + "\n"
tables = ROOT / "shared/src/commonMain/kotlin/app/mongle/l10n/L10nTables.kt"
tables.parent.mkdir(parents=True, exist_ok=True)
tables.write_text(kt, encoding="utf-8")

# compose accessor
ui = """package app.mongle.ui

import app.mongle.generated.resources.Res
import app.mongle.generated.resources.*
import org.jetbrains.compose.resources.StringResource

internal fun uiString(key: String): StringResource = when (key) {
"""
for key, _, _ in ROWS:
    ui += f'    "{key}" -> Res.string.{key}\n'
ui += """    else -> Res.string.app_name
}
"""
(ROOT / "composeApp/src/commonMain/kotlin/app/mongle/ui/UiStrings.kt").write_text(ui, encoding="utf-8")
print("keys", len(ROWS))
