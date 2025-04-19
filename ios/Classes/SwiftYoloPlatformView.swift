import Flutter
import UIKit

@MainActor
public class SwiftYoloPlatformView: NSObject, FlutterPlatformView {
    private let frame: CGRect
    private let viewId: Int64

    private var yoloView: YOLOView?

    init(
        frame: CGRect,
        viewId: Int64,
        args: Any?,
        messenger: FlutterBinaryMessenger
    ) {
        self.frame = frame
        self.viewId = viewId
        super.init()

        // Extract creation params
        if let dict = args as? [String: Any],
           let modelName = dict["modelPath"] as? String,
           let taskRaw = dict["task"] as? String
            {
            let task = YOLOTask.fromString(taskRaw)
            // Get showBoxes parameter with default value of true
            let showBoxes = dict["showBoxes"] as? Bool ?? true
            
            // Create the YOLO view with the specified params
            yoloView = YOLOView(
                frame: frame,
                modelPathOrName: modelName,
                task: task
            )
            
            // Set whether to show detection boxes
            yoloView?.setShowBoxes(showBoxes)
        }
    }

    public func view() -> UIView {
        return yoloView ?? UIView()
    }
}

