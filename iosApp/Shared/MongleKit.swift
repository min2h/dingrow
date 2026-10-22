import Foundation

#if WIDGET
import Shared
#else
import ComposeApp
#endif

enum AppGroup {
    static let identifier = "group.app.dingrow"

    static func databaseDirectory() -> String {
        let path = containerPath(identifier) ?? NSTemporaryDirectory()
        if let legacy = containerPath("group.app.mongle"), legacy != path {
            copyLegacyDatabase(from: legacy, to: path)
        }
        copyLegacyDatabase(from: path, to: path)
        return path
    }

    private static func containerPath(_ group: String) -> String? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)?.path
    }

    private static func copyLegacyDatabase(from sourceDir: String, to destDir: String) {
        let files = FileManager.default
        let dest = (destDir as NSString).appendingPathComponent("dingrow.db")
        if files.fileExists(atPath: dest) { return }
        let source = (sourceDir as NSString).appendingPathComponent("mongle.db")
        guard files.fileExists(atPath: source) else { return }
        for suffix in ["", "-wal", "-shm", "-journal"] {
            let from = source + suffix
            let to = dest + suffix
            if files.fileExists(atPath: from), !files.fileExists(atPath: to) {
                try? files.copyItem(atPath: from, toPath: to)
            }
        }
    }
}

struct WidgetSnapshot: Codable {
    var petName: String = "몽이"
    var stageLabel: String = "알"
    var moodLabel: String = "포근함"
    var pose: String = "idle"
    var hunger: Int = 80
    var happiness: Int = 80
    var energy: Int = 80
    var xp: Int = 0
    var xpForNext: Int = 0
    var coins: Int = 0
    var nextTitle: String = ""
    var nextTime: String = ""
    var pendingGiftName: String = ""
    var pendingGiftPrice: Int = 0
    var todayName: String = ""
    var todayPrice: Int = 0
    var confirmText: String = ""
    var presetName: String = "포근한 방"
    var wallHex: String = "#FFF6EE"
    var floorHex: String = "#F3D7B5"
    var bedId: String = ""
    var rugId: String = ""
    var lampId: String = ""
    var outfitId: String = ""
    var timeOfDay: String = "day"
    var statusMessage: String = ""
    var outingText: String = ""
    var darkRoom: Bool = false
    var progressLabel: String = ""
    var speciesId: String = "base"
    var level: Int = 1

    static let placeholder = WidgetSnapshot(
        petName: "몽이",
        stageLabel: "아기",
        moodLabel: "기분 좋아요",
        pose: "happy",
        hunger: 72,
        happiness: 84,
        energy: 70,
        coins: 40,
        nextTitle: "물 마시기",
        nextTime: "오늘 09:00",
        todayName: "풀잎 쿠키",
        todayPrice: 11,
        presetName: "포근한 방",
        progressLabel: "어린이까지 경험치 8/50"
    )
}

enum MongleKit {
    static func start() {
        IosAppConfig.shared.databaseDirectory = AppGroup.databaseDirectory()
        MongleBridge.shared.start()
    }

    static func snapshot() -> WidgetSnapshot {
        start()
        let raw = MongleBridge.shared.snapshotJson()
        guard let data = raw.data(using: .utf8) else { return .placeholder }
        let decoder = JSONDecoder()
        return (try? decoder.decode(WidgetSnapshot.self, from: data)) ?? .placeholder
    }

    @discardableResult
    static func feed() -> String {
        start()
        return MongleBridge.shared.feed()
    }

    @discardableResult
    static func completeNext() -> String {
        start()
        return MongleBridge.shared.completeNext()
    }

    @discardableResult
    static func sellLatestGift() -> String {
        start()
        return MongleBridge.shared.sellLatestGift()
    }

    @discardableResult
    static func beginBuy() -> String {
        start()
        return MongleBridge.shared.beginBuy()
    }

    @discardableResult
    static func confirmBuy() -> String {
        start()
        return MongleBridge.shared.confirmBuy()
    }

    @discardableResult
    static func cancelBuy() -> String {
        start()
        return MongleBridge.shared.cancelBuy()
    }

    @discardableResult
    static func cyclePreset() -> String {
        start()
        return MongleBridge.shared.cyclePreset()
    }

    @discardableResult
    static func completeSchedule(id: String) -> String {
        start()
        return MongleBridge.shared.completeSchedule(id: id)
    }

    @discardableResult
    static func snooze(id: String) -> String {
        start()
        return MongleBridge.shared.snooze(id: id)
    }
}
