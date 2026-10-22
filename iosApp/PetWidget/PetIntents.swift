import AppIntents
import WidgetKit

struct FeedPetIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_feed"
    static var description = IntentDescription("intent_feed_desc")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.feed()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct CompleteNextIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_complete"
    static var description = IntentDescription("intent_complete_desc")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.completeNext()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct SellGiftIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_sell"
    static var description = IntentDescription("intent_sell_desc")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.sellLatestGift()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct BeginBuyIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_buy"
    static var description = IntentDescription("intent_buy_desc")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.beginBuy()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct ConfirmBuyIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_confirm"
    static var description = IntentDescription("intent_confirm_desc")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.confirmBuy()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct CancelBuyIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_cancel"
    static var description = IntentDescription("intent_cancel_desc")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.cancelBuy()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct CyclePresetIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_preset"
    static var description = IntentDescription("intent_preset_desc")

    func perform() async throws -> some IntentResult {
        _ = MongleKit.cyclePreset()
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}
