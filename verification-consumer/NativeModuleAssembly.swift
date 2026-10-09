import LocationConsumer
import GycLocationKuikly

// 编译生成的真实 consumer framework API；宿主只装配方法引用，不实现 transport。
public func configureLocationModule() {
    GycLocationModule.configure {
        let handler = IosLocationModuleHandler()
        return (call: handler.call, dispose: handler.dispose)
    }
}
