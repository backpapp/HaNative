import SwiftUI
import shared

@main
struct iOSApp: App {
    init() {
        KoinHelperKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Story 3.5 — OAuth callback (hanative://auth-callback?code=...)
                .onOpenURL { url in
                    guard url.scheme == "hanative", url.host == "auth-callback" else { return }
                    let code = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                        .queryItems?
                        .first { $0.name == "code" }?
                        .value
                    KoinHelperKt.handleOAuthCallback(code: code)
                }
        }
    }
}
