import SwiftUI
import UIKit
import shared

struct ContentView: View {
    var body: some View {
        ComposeRootView()
            .ignoresSafeArea()
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

#Preview {
    ContentView()
}
