import AppIntents
import WidgetKit

struct FeedPetIntent: AppIntent {
    static var title: LocalizedStringResource = "먹이 주기"
    static var description = IntentDescription("몽이에게 먹이를 줍니다.")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.feed()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct CompleteNextIntent: AppIntent {
    static var title: LocalizedStringResource = "다음 일정 완료"
    static var description = IntentDescription("가장 가까운 일정을 완료합니다.")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.completeNext()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct SellGiftIntent: AppIntent {
    static var title: LocalizedStringResource = "선물 팔기"
    static var description = IntentDescription("도착한 선물이나 최근 선물을 팝니다.")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.sellLatestGift()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct BeginBuyIntent: AppIntent {
    static var title: LocalizedStringResource = "오늘의 물건 사기"
    static var description = IntentDescription("오늘의 상점 물건을 살지 확인합니다.")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.beginBuy()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct ConfirmBuyIntent: AppIntent {
    static var title: LocalizedStringResource = "구매 확정"
    static var description = IntentDescription("방금 고른 물건을 삽니다.")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.confirmBuy()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct CancelBuyIntent: AppIntent {
    static var title: LocalizedStringResource = "구매 취소"
    static var description = IntentDescription("구매 확인을 취소합니다.")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.cancelBuy()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct CyclePresetIntent: AppIntent {
    static var title: LocalizedStringResource = "방 바꾸기"
    static var description = IntentDescription("다음 방 프리셋으로 바꿉니다.")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.cyclePreset()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}
