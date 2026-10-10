import Foundation
import UIKit
@preconcurrency import OpenKuiklyIOSRender
@preconcurrency import GycLocationKuikly

private func trace(_ message: String) {
    diagnosticLock.lock()
    defer { diagnosticLock.unlock() }
    let line = message + "\n"
    FileHandle.standardError.write(Data(line.utf8))
    do {
        let folder = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let url = folder.appendingPathComponent("module-check-result.log")
        let previous = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
        try (previous + line).write(to: url, atomically: true, encoding: .utf8)
    } catch {
        FileHandle.standardError.write(Data("FAIL: persist fixture result: \(error)\n".utf8))
        exit(1)
    }
}
private let diagnosticLock = NSLock()

private func require(_ condition: @autoclosure () -> Bool, _ message: String) {
    if !condition() { trace("FAIL: " + message); exit(1) }
}

private final class Bridge: NSObject, TDFBridgeDelegate {
    weak var rootView: UIView?
    var pageName: String? { "lifecycle-fixture" }
    var bridgeType: TDF_BRIDGE_TYPE { .KUIKLY }
    var hippyBridge: AnyObject? { nil }
    func send(withEvent event: String, data: [AnyHashable: Any]?) {}
    func module(withName moduleName: String) -> Any? { nil }
    func view(withTag tag: Int) -> UIView? { nil }
    func performCallback(_ callbackId: NSNumber, params: Any) {}
}

@MainActor
private func attached(_ view: UIView) -> GycLocationModule {
    let receiver = GycLocationModule()
    let bridge = Bridge()
    bridge.rootView = view
    receiver.delegate = bridge
    // 仅测试 receiver 生命周期；占位 UIView 不启动被排除的实际 Renderer。
    receiver.setValue(view, forKey: "hr_rootView")
    return receiver
}

@MainActor
private func drainMain() async {
    trace("STAGE: Main drain requested")
    await withCheckedContinuation { continuation in
        DispatchQueue.main.async { continuation.resume() }
    }
    trace("STAGE: Main drain completed")
}

private final class CallbackState: @unchecked Sendable {
    private let lock = NSLock()
    private var value = 0
    var count: Int { lock.lock(); defer { lock.unlock() }; return value }
    func receive() {
        require(!Thread.isMainThread, "callback must run on SDK Context thread")
        lock.lock(); value += 1; lock.unlock()
    }
}

@MainActor
private func drainContext() async {
    trace("STAGE: Context drain requested")
    await withCheckedContinuation { continuation in
        KuiklyRenderThreadManager.performOnContextQueue { continuation.resume() }
    }
    trace("STAGE: Context drain completed")
}

@MainActor
private func checks() async {
    let root = UIView()
    var created = 0
    var disposed = 0
    var replies: [(String) -> Void] = []
    var duringFactory: (() -> Void)?
    let callbackState = CallbackState()
    GycLocationModule.configure {
        created += 1
        duringFactory?()
        return (call: { _, _, reply in replies.append(reply) }, dispose: { disposed += 1 })
    }
    trace("STAGE: first call / repeated invalidate")
    let old = attached(root)
    let callback: KuiklyRenderCallback = { _ in callbackState.receive() }
    _ = old.hrv_call(withMethod: "currentLocation", params: "{}", callback: callback)
    require(created == 1 && replies.count == 1, "first call must create one handler")
    old.invalidate()
    old.invalidate()
    require(disposed == 1, "repeated invalidate must dispose once")
    replies[0]("{}")
    require(callbackState.count == 0, "invalidated owner must reject old reply")
    trace("STAGE: fresh owner / Context drain")
    let fresh = attached(root)
    _ = fresh.hrv_call(withMethod: "currentLocation", params: "{}", callback: callback)
    replies[0]("{}")
    replies[1]("{}")
    await drainContext()
    require(created == 2 && callbackState.count == 1, "fresh owner must receive one Context callback")
    trace("STAGE: blocked Context callback / invalidate")
    let contextEntered = DispatchSemaphore(value: 0)
    let contextContinue = DispatchSemaphore(value: 0)
    KuiklyRenderThreadManager.performOnContextQueue {
        contextEntered.signal()
        require(contextContinue.wait(timeout: .now() + 2) == .success, "Main did not release blocked Context queue")
    }
    require(contextEntered.wait(timeout: .now() + 2) == .success, "SDK Context queue did not enter blocker")
    replies[1]("{}")
    fresh.invalidate()
    contextContinue.signal()
    await drainContext()
    require(callbackState.count == 1, "cancelled operation must not deliver another callback")

    trace("STAGE: invalidate during factory creation")
    let creating = attached(root)
    duringFactory = {
        let invalidated = DispatchSemaphore(value: 0)
        DispatchQueue.global().async { creating.invalidate(); invalidated.signal() }
        require(invalidated.wait(timeout: .now() + 2) == .success, "worker invalidate blocked during factory creation")
    }
    _ = creating.hrv_call(withMethod: "currentLocation", params: "{}", callback: callback)
    duringFactory = nil
    require(created == 3 && disposed == 3 && replies.count == 2, "factory race must dispose without calling handler")

    trace("STAGE: queued worker call / Main drain")
    let queued = attached(root)
    let done = DispatchSemaphore(value: 0)
    DispatchQueue.global().async {
        _ = queued.hrv_call(withMethod: "currentLocation", params: "{}", callback: callback)
        queued.invalidate()
        queued.invalidate()
        done.signal()
    }
    // Main 被占用时，worker 必须能立即失效；Main.sync 会在这里失败。
    require(done.wait(timeout: .now() + 2) == .success, "queued worker call/invalidate blocked on Main")
    await drainMain()
    require(created == 3 && disposed == 3 && callbackState.count == 1, "invalidated queued call must not create a handler")

    trace("STAGE: worker SDK dealloc / Main and Context drains")
    final class Holder: @unchecked Sendable { var receiver: GycLocationModule? }
    let holder = Holder()
    holder.receiver = attached(root)
    weak var released = holder.receiver
    _ = holder.receiver?.hrv_call(withMethod: "currentLocation", params: "{}", callback: callback)
    let releasedOnWorker = DispatchSemaphore(value: 0)
    DispatchQueue.global().async {
        autoreleasepool { holder.receiver = nil }
        releasedOnWorker.signal()
    }
    require(releasedOnWorker.wait(timeout: .now() + 2) == .success, "worker dealloc did not return")
    await drainMain()
    require(released == nil && created == 4 && disposed == 4, "SDK dealloc must release receiver and dispose handler")
    replies[2]("{}")
    await drainContext()
    require(callbackState.count == 1, "cancelled operation must not deliver another callback")
    trace("PASS: location receiver repeated invalidate, queued call, factory race, Context callback and SDK dealloc")
}

@main
private final class CheckApp: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        trace("STAGE: didFinishLaunching pid=\(getpid())")
        // 独立 worker deadline 也能诊断 Main/Context 被阻塞的 fixture。
        DispatchQueue.global().asyncAfter(deadline: .now() + 60) {
            trace("FAIL: lifecycle fixture exceeded 60 seconds")
            exit(1)
        }
        Task { @MainActor in
            trace("STAGE: MainActor checks started")
            await checks()
            trace("COMPLETE: exit=0")
            exit(0)
        }
        return true
    }
}
