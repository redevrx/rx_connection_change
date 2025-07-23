import SwiftUI
import Shared
import Network

struct ContentView: View {
    @State private var showContent = false
    @State private var isConnected = false

    var body: some View {
        VStack {
            Button("Click me!") {
                withAnimation {
                    showContent = !showContent
                }
            }

            if showContent {
                VStack(spacing: 16) {
                    Image(systemName: "swift")
                        .font(.system(size: 200))
                        .foregroundColor(.accentColor)
                    Text("SwiftUI: \(Greeting().greet())")
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }

            Text("online: \(isConnected)")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .padding()
        .onAppear {
            let connectivity = ConnectivityObserver()
            let job = FlowUtilsKt.toFlowX(flow:connectivity.isConnected,scope: nil)
            job.subscribe { it in
                if let connected = it as? KotlinBoolean {
                    isConnected = connected.boolValue
                } else {
                    isConnected = false
                }
            } onCompletion: { err in
                print("error: \(err)")
            }
            
//            job.cancel()
        
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}

