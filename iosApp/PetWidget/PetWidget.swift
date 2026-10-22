import SwiftUI
import UIKit
import WidgetKit

struct PetEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

struct PetTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> PetEntry {
        PetEntry(date: Date(), snapshot: .placeholder)
    }

    func getSnapshot(in context: Context, completion: @escaping (PetEntry) -> Void) {
        let snapshot = context.isPreview ? WidgetSnapshot.placeholder : MongleKit.snapshot()
        completion(PetEntry(date: Date(), snapshot: snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PetEntry>) -> Void) {
        let entry = PetEntry(date: Date(), snapshot: MongleKit.snapshot())
        let next = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date().addingTimeInterval(900)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }
}

struct PetHomeWidget: Widget {
    let kind = "app.dingrow.pet"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PetTimelineProvider()) { entry in
            PetWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    Color(hex: entry.snapshot.wallHex)
                }
        }
        .configurationDisplayName(Text("widget_name"))
        .description(Text("widget_description"))
        .supportedFamilies([.systemSmall, .systemLarge])
    }
}

@main
struct PetWidgetBundle: WidgetBundle {
    var body: some Widget {
        PetHomeWidget()
    }
}

struct PetWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: PetEntry

    var body: some View {
        let snap = entry.snapshot
        let ink = snap.darkRoom ? Color.white : Color(hex: "#4A3728")
        let muted = snap.darkRoom ? Color.white.opacity(0.75) : Color(hex: "#8A6A55")
        Group {
            if family == .systemSmall {
                small(snap, ink: ink, muted: muted)
            } else {
                large(snap, ink: ink, muted: muted)
            }
        }
    }

    private func small(_ snap: WidgetSnapshot, ink: Color, muted: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center, spacing: 8) {
                MongleArtImage(name: MongleArt.pet(speciesId: snap.speciesId, pose: snap.pose, stageLabel: snap.stageLabel))
                    .frame(width: 56, height: 56)
                VStack(alignment: .leading, spacing: 2) {
                    Text(DingrowStrings.text("widget_mood", language: snap.language)).font(.caption2).foregroundStyle(muted)
                    Text(snap.moodLabel).font(.headline).foregroundStyle(ink)
                }
            }
            if !snap.speech.isEmpty {
                Text(snap.speech)
                    .font(.caption2)
                    .foregroundStyle(ink)
                    .lineLimit(1)
            }
            Text(snap.nextTime.isEmpty ? DingrowStrings.text("widget_next", language: snap.language) : snap.nextTime)
                .font(.caption2)
                .foregroundStyle(muted)
                .lineLimit(1)
            Text(snap.nextTitle.isEmpty ? DingrowStrings.text("widget_none", language: snap.language) : snap.nextTitle)
                .font(.caption)
                .foregroundStyle(ink)
                .lineLimit(1)
            Spacer(minLength: 0)
            Button(intent: CompleteNextIntent()) {
                Text(DingrowStrings.text("widget_done", language: snap.language)).frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color(hex: "#F6C1B0"))
            .foregroundStyle(Color(hex: "#5C4636"))
            .font(.caption)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func large(_ snap: WidgetSnapshot, ink: Color, muted: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack(alignment: .bottom) {
                MongleArtImage(name: MongleArt.room(wallHex: snap.wallHex, timeOfDay: snap.timeOfDay, dark: snap.darkRoom), fill: false)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                MongleArtImage(name: MongleArt.pet(
                    speciesId: snap.speciesId,
                    pose: snap.nextTitle.isEmpty ? snap.pose : "idle",
                    stageLabel: snap.stageLabel
                ))
                    .frame(width: 64, height: 64)
                    .padding(.bottom, 6)
                if !snap.speech.isEmpty {
                    Text(snap.speech)
                        .font(.caption2)
                        .foregroundStyle(ink)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.white.opacity(0.9), in: Capsule())
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                        .padding(8)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 150)
            .background(Color(hex: "#FFF6EE"))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            HStack {
                Text(DingrowStrings.format("widget_room", language: snap.language, [snap.petName]))
                    .font(.headline)
                    .foregroundStyle(ink)
                Spacer()
                Text("\(snap.coins)")
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color(hex: "#FFF3C9"), in: Capsule())
                    .foregroundStyle(ink)
            }
            if !snap.confirmText.isEmpty {
                Text(snap.confirmText)
                    .font(.caption)
                    .foregroundStyle(ink)
                    .lineLimit(2)
                HStack {
                    Button(intent: ConfirmBuyIntent()) { Text(DingrowStrings.text("widget_confirm", language: snap.language)) }
                    Button(intent: CancelBuyIntent()) { Text(DingrowStrings.text("widget_cancel", language: snap.language)) }
                }
                .font(.caption)
                .buttonStyle(.bordered)
            } else {
                Text(snap.nextTitle.isEmpty ? DingrowStrings.text("widget_none_large", language: snap.language) : "\(snap.nextTime)  \(snap.nextTitle)")
                    .font(.caption)
                    .foregroundStyle(ink)
                    .lineLimit(1)
                if !snap.pendingGiftName.isEmpty {
                    Text(DingrowStrings.format("widget_gift_line", language: snap.language, [snap.pendingGiftName, snap.pendingGiftPrice]))
                        .font(.caption2)
                        .foregroundStyle(muted)
                }
                HStack(spacing: 6) {
                    Button(intent: FeedPetIntent()) { Text(DingrowStrings.text("widget_feed", language: snap.language)).frame(maxWidth: .infinity) }
                        .tint(Color(hex: "#FFE3D6"))
                    Button(intent: SellGiftIntent()) { Text(DingrowStrings.text("widget_sell", language: snap.language)).frame(maxWidth: .infinity) }
                        .tint(Color(hex: "#DDF3EA"))
                }
                .font(.caption2)
                .buttonStyle(.bordered)
                HStack(spacing: 6) {
                    Button(intent: BeginBuyIntent()) { Text(DingrowStrings.text("widget_today", language: snap.language)).frame(maxWidth: .infinity) }
                        .tint(Color(hex: "#FFF3C9"))
                    Button(intent: CyclePresetIntent()) { Text(DingrowStrings.text("widget_decorate", language: snap.language)).frame(maxWidth: .infinity) }
                        .tint(Color(hex: "#FFE3D6"))
                }
                .font(.caption2)
                .buttonStyle(.bordered)
                Link(destination: URL(string: "dingrow://schedule/add")!) {
                    Text(DingrowStrings.text("widget_add", language: snap.language))
                        .font(.caption2)
                        .foregroundStyle(Color(hex: "#8A6A55"))
                }
            }
            if !snap.statusMessage.isEmpty {
                Text(snap.statusMessage)
                    .font(.caption2)
                    .foregroundStyle(muted)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

enum MongleArt {
    static func pet(speciesId: String, pose: String, stageLabel: String) -> String {
        if stageLabel == "알" || stageLabel == "Egg" || pose == "egg" { return "egg" }
        let base = speciesId == "base" || speciesId == "sp_peach" || speciesId.isEmpty
        if base {
            switch pose {
            case "sleep": return "base_sleep"
            case "happy": return "base_happy"
            case "walk", "out": return "base_walk_left"
            case "eat": return "base_eat"
            default: return "base_idle"
            }
        }
        switch pose {
        case "sleep": return "\(speciesId)_sleep"
        case "happy": return "\(speciesId)_happy"
        case "walk", "out": return "\(speciesId)_walk_left"
        case "eat": return "\(speciesId)_eat"
        default: return speciesId
        }
    }

    static func room(wallHex: String, timeOfDay: String, dark: Bool) -> String {
        let theme: String
        if dark || wallHex.uppercased().contains("2C3A5A") {
            theme = "starry"
        } else if wallHex.uppercased().contains("D7F3EC") {
            theme = "mint"
        } else {
            theme = "wood"
        }
        let day = timeOfDay == "night" ? "night" : "day"
        return "room_\(theme)_\(day)_1x1"
    }

    static func image(_ name: String) -> UIImage? {
        load(name) ?? load("base_idle")
    }

    private static func load(_ name: String) -> UIImage? {
        let bundle = Bundle.main
        let folders = ["Art", "characters", nil as String?]
        for folder in folders {
            let url = if let folder {
                bundle.url(forResource: name, withExtension: "webp", subdirectory: folder)
            } else {
                bundle.url(forResource: name, withExtension: "webp")
            }
            if let url, let image = UIImage(contentsOfFile: url.path) {
                return image
            }
        }
        return nil
    }
}

struct MongleArtImage: View {
    let name: String
    var fill = false

    var body: some View {
        if let ui = MongleArt.image(name) {
            Image(uiImage: ui)
                .resizable()
                .aspectRatio(contentMode: fill ? .fill : .fit)
        } else {
            Color.clear
        }
    }
}

extension Color {
    init(hex: String) {
        var text = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("#") { text.removeFirst() }
        var value: UInt64 = 0
        Scanner(string: text).scanHexInt64(&value)
        let red = Double((value >> 16) & 0xFF) / 255
        let green = Double((value >> 8) & 0xFF) / 255
        let blue = Double(value & 0xFF) / 255
        self.init(red: red, green: green, blue: blue)
    }
}
