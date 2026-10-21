import SwiftUI
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
    let kind = "app.mongle.pet"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PetTimelineProvider()) { entry in
            PetWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    Color(hex: entry.snapshot.wallHex)
                }
        }
        .configurationDisplayName("몽이")
        .description("홈 화면에서 몽이를 돌보고 일정을 마쳐요.")
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
                PetBlob(pose: snap.pose, outfitId: snap.outfitId)
                    .frame(width: 52, height: 52)
                VStack(alignment: .leading, spacing: 2) {
                    Text(snap.petName).font(.headline).foregroundStyle(ink)
                    Text(snap.moodLabel).font(.caption).foregroundStyle(muted)
                }
            }
            Text(snap.nextTitle.isEmpty ? "예정된 일정 없음" : snap.nextTitle)
                .font(.caption)
                .foregroundStyle(ink)
                .lineLimit(1)
            Text(snap.nextTime)
                .font(.caption2)
                .foregroundStyle(muted)
                .lineLimit(1)
            HStack(spacing: 6) {
                Button(intent: FeedPetIntent()) {
                    Text("먹이")
                }
                .tint(Color(hex: "#F3B6A0"))
                Button(intent: CompleteNextIntent()) {
                    Text("완료")
                }
                .tint(Color(hex: "#9ED9C7"))
            }
            .font(.caption2)
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func large(_ snap: WidgetSnapshot, ink: Color, muted: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(hex: snap.floorHex))
                    .frame(height: 92)
                HStack {
                    PetBlob(pose: snap.pose, outfitId: snap.outfitId)
                        .frame(width: 72, height: 72)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(snap.petName) · \(snap.stageLabel)")
                            .font(.headline)
                            .foregroundStyle(ink)
                        Text(snap.outingText.isEmpty ? snap.moodLabel : snap.outingText)
                            .font(.caption)
                            .foregroundStyle(muted)
                        Text("코인 \(snap.coins)")
                            .font(.caption2)
                            .foregroundStyle(muted)
                    }
                    Spacer()
                }
                .padding(.horizontal, 8)
            }
            if !snap.confirmText.isEmpty {
                Text(snap.confirmText)
                    .font(.caption)
                    .foregroundStyle(ink)
                    .lineLimit(2)
                HStack {
                    Button(intent: ConfirmBuyIntent()) { Text("확인") }
                    Button(intent: CancelBuyIntent()) { Text("취소") }
                }
                .font(.caption)
                .buttonStyle(.bordered)
            } else {
                Text(snap.nextTitle.isEmpty ? "예정된 일정 없음" : "\(snap.nextTime)  \(snap.nextTitle)")
                    .font(.caption)
                    .foregroundStyle(ink)
                    .lineLimit(1)
                if !snap.pendingGiftName.isEmpty {
                    Text("선물 \(snap.pendingGiftName) · \(snap.pendingGiftPrice)코인")
                        .font(.caption2)
                        .foregroundStyle(muted)
                }
                HStack(spacing: 6) {
                    Button(intent: FeedPetIntent()) { Text("먹이") }
                    Button(intent: CompleteNextIntent()) { Text("완료") }
                    Button(intent: SellGiftIntent()) { Text("팔기") }
                }
                .font(.caption2)
                .buttonStyle(.bordered)
                HStack(spacing: 6) {
                    Button(intent: BeginBuyIntent()) {
                        Text(snap.todayName.isEmpty ? "구매" : "\(snap.todayName) \(snap.todayPrice)")
                    }
                    Button(intent: CyclePresetIntent()) { Text(snap.presetName) }
                    Link(destination: URL(string: "mongle://schedule/add")!) {
                        Text("일정+")
                            .font(.caption2)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 6)
                            .background(Color(hex: "#F6E3A1"), in: Capsule())
                            .foregroundStyle(Color(hex: "#4A3728"))
                    }
                }
                .font(.caption2)
                .buttonStyle(.bordered)
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

struct PetBlob: View {
    let pose: String
    let outfitId: String

    var body: some View {
        ZStack {
            Ellipse()
                .fill(Color(hex: "#F6C7B0").opacity(0.45))
                .frame(width: 36, height: 10)
                .offset(y: 24)
            bodyShape
                .fill(Color(hex: bodyColor))
                .overlay(bodyShape.stroke(Color(hex: "#E7A88F"), lineWidth: 1))
            if pose != "egg" && pose != "sleep" {
                HStack(spacing: 8) {
                    Circle().fill(Color(hex: "#4A3728")).frame(width: 4, height: 4)
                    Circle().fill(Color(hex: "#4A3728")).frame(width: 4, height: 4)
                }
                .offset(y: -2)
            }
            if pose == "sleep" {
                Text("z")
                    .font(.caption2)
                    .foregroundStyle(Color(hex: "#8A6A55"))
                    .offset(x: 16, y: -16)
            }
            outfit
        }
    }

    private var bodyShape: some Shape {
        if pose == "egg" {
            return AnyShape(Ellipse())
        }
        return AnyShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var bodyColor: String {
        switch pose {
        case "sad": return "#E7B7A8"
        case "happy": return "#FFD0BE"
        default: return "#F8CDB8"
        }
    }

    @ViewBuilder
    private var outfit: some View {
        switch outfitId {
        case "hat_sprout":
            Capsule()
                .fill(Color(hex: "#7DCEA0"))
                .frame(width: 8, height: 14)
                .offset(y: -28)
        case "scarf_cloud":
            Capsule()
                .fill(Color.white)
                .frame(width: 28, height: 8)
                .offset(y: 10)
        case "ribbon_berry":
            Circle()
                .fill(Color(hex: "#E25B5B"))
                .frame(width: 8, height: 8)
                .offset(x: 14, y: -8)
        default:
            EmptyView()
        }
    }
}

struct AnyShape: Shape {
    private let pathBuilder: (CGRect) -> Path

    init<S: Shape>(_ shape: S) {
        pathBuilder = { rect in shape.path(in: rect) }
    }

    func path(in rect: CGRect) -> Path {
        pathBuilder(rect)
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
