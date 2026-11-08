import SwiftUI
import UIKit
import Shared

/// Hosts the shared Compose Multiplatform UI. MainViewController() comes from
/// the Kotlin :shared module, so iOS renders exactly the same screens Android does.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
