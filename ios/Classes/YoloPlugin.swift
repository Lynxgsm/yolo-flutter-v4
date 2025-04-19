import Flutter
import UIKit

/// シングルトンインスタンスとしてYOLOモデルを管理するクラス
@MainActor
class SingleImageYOLO {
    static let shared = SingleImageYOLO()
    private var yolo: YOLO?
    private var isLoadingModel = false
    private var loadCompletionHandlers: [(Result<YOLO, Error>) -> Void] = []
    
    private init() {}
    
    func loadModel(modelName: String, task: YOLOTask, completion: @escaping (Result<Void, Error>) -> Void) {
        // モデルが既に読み込まれている場合は成功を返す
        if let _ = yolo {
            completion(.success(()))
            return
        }
        
        // モデルが読み込み中の場合は完了ハンドラーを追加
        if isLoadingModel {
            loadCompletionHandlers.append({ result in
                switch result {
                case .success:
                    completion(.success(()))
                case .failure(let error):
                    completion(.failure(error))
                }
            })
            return
        }
        
        isLoadingModel = true
        
        // YOLOモデルを初期化し読み込む
        YOLO(modelName, task: task) { [weak self] result in
            guard let self = self else { return }
            
            self.isLoadingModel = false
            
            switch result {
            case .success(let loadedYolo):
                self.yolo = loadedYolo
                completion(.success(()))
                
                // 保留中の完了ハンドラーを実行
                for handler in self.loadCompletionHandlers {
                    handler(.success(loadedYolo))
                }
                
            case .failure(let error):
                completion(.failure(error))
                
                // 保留中の完了ハンドラーにエラーを通知
                for handler in self.loadCompletionHandlers {
                    handler(.failure(error))
                }
            }
            
            self.loadCompletionHandlers.removeAll()
        }
    }
    
    func predict(imageData: Data) -> [String: Any]? {
        guard let yolo = yolo, let uiImage = UIImage(data: imageData) else {
            return nil
        }
        
        // 推論を実行
        let result = yolo(uiImage)
        
        // YOLOResultをFlutter用のディクショナリに変換
        return convertToFlutterFormat(result: result)
    }
    
    private func convertToFlutterFormat(result: YOLOResult) -> [String: Any] {
        // 検出結果を変換
        var flutterResults: [Dictionary<String, Any>] = []
        
        for box in result.boxes {
            var boxDict: [String: Any] = [
                "label": box.cls,
                "confidence": box.conf,
                "index": box.index
            ]
            
            // 正規化された座標を追加
            boxDict["x"] = box.xywhn.minX
            boxDict["y"] = box.xywhn.minY
            boxDict["width"] = box.xywhn.width
            boxDict["height"] = box.xywhn.height
            
            // 画像座標値（ピクセル単位）も追加
            boxDict["xImg"] = box.xywh.minX
            boxDict["yImg"] = box.xywh.minY
            boxDict["widthImg"] = box.xywh.width
            boxDict["heightImg"] = box.xywh.height
            
            // バウンディングボックス座標をリスト形式でも追加
            boxDict["bbox"] = [box.xywh.minX, box.xywh.minY, box.xywh.width, box.xywh.height]
            
            flutterResults.append(boxDict)
        }
        
        // 結果全体を格納するディクショナリ
        var resultDict: [String: Any] = [
            "boxes": flutterResults
        ]
        
        // アノテーション画像がある場合、それをBase64エンコードして追加
        if let annotatedImage = result.annotatedImage {
            if let imageData = annotatedImage.pngData() {
                resultDict["annotatedImage"] = FlutterStandardTypedData(bytes: imageData)
            }
        }
        
        return resultDict
    }
}

@MainActor
public class YoloPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        // 1) Register the platform view
        let factory = SwiftYoloPlatformViewFactory(messenger: registrar.messenger())
        registrar.register(factory, withId: "com.ultralytics.yolo/YoloPlatformView")

        // 2) Register the method channel for single-image inference
        let channel = FlutterMethodChannel(
            name: "yolo_single_image_channel",
            binaryMessenger: registrar.messenger()
        )
        let instance = YoloPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        Task { @MainActor in
            switch call.method {
            case "loadModel":
                guard let args = call.arguments as? [String: Any],
                      let modelPath = args["modelPath"] as? String,
                      let taskString = args["task"] as? String else {
                    result(FlutterError(code: "bad_args", message: "Invalid arguments for loadModel", details: nil))
                    return
                }
                
                let task = YOLOTask.fromString(taskString)
                
                do {
                    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                        SingleImageYOLO.shared.loadModel(modelName: modelPath, task: task) { modelResult in
                            switch modelResult {
                            case .success:
                                continuation.resume()
                            case .failure(let error):
                                continuation.resume(throwing: error)
                            }
                        }
                    }
                    result(nil) // 成功
                } catch {
                    result(FlutterError(code: "model_load_error", message: error.localizedDescription, details: nil))
                }

            case "predictSingleImage":
                guard let args = call.arguments as? [String: Any],
                      let data = args["image"] as? FlutterStandardTypedData else {
                    result(FlutterError(code: "bad_args", message: "Invalid arguments for predictSingleImage", details: nil))
                    return
                }
                
                // 実際に画像推論を実行
                if let resultDict = SingleImageYOLO.shared.predict(imageData: data.data) {
                    result(resultDict)
                } else {
                    result(FlutterError(code: "inference_error", message: "Failed to run inference", details: nil))
                }

            default:
                result(FlutterMethodNotImplemented)
            }
        }
    }
}
