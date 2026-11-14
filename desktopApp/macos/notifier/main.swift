import AppKit
import Foundation
import UserNotifications

let requestKey = "request"
let requestsDirectoryKey = "requestsDirectory"
let appKey = "app"
let idleSeconds = 15.0

func option(_ name: String, in arguments: [String]) -> String? {
    guard let index = arguments.firstIndex(of: name), index + 1 < arguments.count else { return nil }
    return arguments[index + 1]
}

func stampedRequest(_ text: String) -> Data? {
    guard let data = text.data(using: .utf8),
          var request = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
    let now = Int64(Date().timeIntervalSince1970 * 1000)
    request["createdAt"] = now
    return try? JSONSerialization.data(withJSONObject: request)
}

func writeRequest(_ data: Data, into directory: String) {
    let folder = URL(fileURLWithPath: directory, isDirectory: true)
    try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    let now = Int64(Date().timeIntervalSince1970 * 1000)
    let name = String(format: "%013lld-%@", now, UUID().uuidString.lowercased())
    let temporary = folder.appendingPathComponent("\(name).tmp")
    let target = folder.appendingPathComponent("\(name).json")
    guard (try? data.write(to: temporary)) != nil else { return }
    if (try? FileManager.default.moveItem(at: temporary, to: target)) == nil {
        try? FileManager.default.removeItem(at: temporary)
    }
}

func attachment(for image: String) -> UNNotificationAttachment? {
    let source = URL(fileURLWithPath: image)
    let copy = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).\(source.pathExtension)")
    guard (try? FileManager.default.copyItem(at: source, to: copy)) != nil else { return nil }
    return try? UNNotificationAttachment(identifier: "badge", url: copy)
}

final class Notifier: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
    private let arguments: [String]

    init(arguments: [String]) {
        self.arguments = arguments
    }

    func applicationWillFinishLaunching(_ notification: Notification) {
        UNUserNotificationCenter.current().delegate = self
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        if arguments.first == "post" {
            post()
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + idleSeconds) { NSApp.terminate(nil) }
        }
    }

    private func post() {
        guard let title = option("--title", in: arguments),
              let body = option("--body", in: arguments),
              let request = option("--request", in: arguments),
              let requestsDirectory = option("--requests-dir", in: arguments) else {
            finish(code: 2)
            return
        }
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert]) { granted, _ in
            guard granted else {
                self.finish(code: 3)
                return
            }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            var userInfo: [String: String] = [requestKey: request, requestsDirectoryKey: requestsDirectory]
            if let app = option("--app", in: self.arguments) { userInfo[appKey] = app }
            content.userInfo = userInfo
            if let image = option("--image", in: self.arguments), let attachment = attachment(for: image) {
                content.attachments = [attachment]
            }
            let notification = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
            center.add(notification) { error in self.finish(code: error == nil ? 0 : 4) }
        }
    }

    private func finish(code: Int32) {
        DispatchQueue.main.async { exit(code) }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier,
              let request = userInfo[requestKey] as? String,
              let directory = userInfo[requestsDirectoryKey] as? String,
              let data = stampedRequest(request) else {
            completionHandler()
            return
        }
        writeRequest(data, into: directory)
        completionHandler()
        guard let app = userInfo[appKey] as? String else { return }
        NSWorkspace.shared.openApplication(at: URL(fileURLWithPath: app), configuration: NSWorkspace.OpenConfiguration()) { _, _ in }
    }
}

let application = NSApplication.shared
let notifier = Notifier(arguments: Array(CommandLine.arguments.dropFirst()))
application.delegate = notifier
application.setActivationPolicy(.accessory)
application.run()
