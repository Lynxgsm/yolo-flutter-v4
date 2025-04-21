package com.ultralytics.yolo_android
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.metadata.MetadataExtractor
import org.tensorflow.lite.support.metadata.schema.ModelMetadata
import org.yaml.snakeyaml.Yaml
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 90度回転不要前提の高速版 ObjectDetector
 * ・"リサイズ -> getPixels -> ByteBuffer" を1回で行い、Canvas 描画を極力省く
 * ・Bitmap / ByteBuffer を再利用してアロケーションを減らす
 * ・推論出力用配列も再利用
 */
class ObjectDetector(
    context: Context,
    modelPath: String,
    override var labels: List<String>,
    private val useGpu: Boolean = true
) : BasePredictor() {
    // 推論出力の次元
    private var out1 = 0
    private var out2 = 0
    // Two image processors: one for camera feed (with rotation) and one for single images (no rotation)
    private lateinit var imageProcessorCamera: ImageProcessor
    private lateinit var imageProcessorSingleImage: ImageProcessor


//    companion object {
//
//    }
    // 推論出力配列 ([1][out1][out2]) を使い回す
    private lateinit var rawOutput: Array<Array<FloatArray>>
    // 後処理用転置配列
    private lateinit var predictions: Array<FloatArray>

    // ======== 前処理高速化のためのワーク領域 ========
    // (1) モデル入力サイズに合わせた一時スケール用 Bitmap
    //     "90度回転不要" なので、単純に createScaledBitmap() 相当の処理をキャッシュ
    private lateinit var scaledBitmap: Bitmap

    // (2) ピクセルを一時格納する配列 (inWidth*inHeight)
    private lateinit var intValues: IntArray

    // (3) TFLite 入力用 ByteBuffer (1 * height * width * 3 * 4 bytes)
    private lateinit var inputBuffer: ByteBuffer

    // TensorFlow Lite Interpreter のオプション
    private val interpreterOptions: Interpreter.Options = Interpreter.Options().apply {
        setNumThreads(4) // CPUスレッド数
        if (useGpu) {
            try {
                addDelegate(GpuDelegate())
                Log.d("TAG", "GPU delegate is used.")
            } catch (e: Exception) {
                Log.e("TAG", "GPU delegate error: ${e.message}")
            }
        }
    }

    // ========== TFLite Interpreter ==========
    // BasePredictor 側に protected var interpreter: Interpreter? = null 等がある前提ならそちらを利用
    // そうでない場合は普通にこのクラス内で保持してOK
    init {
        val modelBuffer = FileUtil.loadMappedFile(context, modelPath)

        // ===== Parse metadata for labels =====
        try {
            val metadataExtractor = MetadataExtractor(modelBuffer)
            val modelMetadata: ModelMetadata? = metadataExtractor.modelMetadata
            if (modelMetadata != null) {
                Log.d(TAG, "Model metadata retrieved successfully.")
            }

            // associatedFileNames may contain one or more YAML or text files with label info
            val associatedFiles = metadataExtractor.associatedFileNames
            if (!associatedFiles.isNullOrEmpty()) {
                for (fileName in associatedFiles) {
                    Log.d(TAG, "Found associated file: $fileName")
                    val inputStream = metadataExtractor.getAssociatedFile(fileName)
                    inputStream?.use { stream ->
                        val fileContent = stream.readBytes()
                        val fileString = fileContent.toString(Charsets.UTF_8)
                        Log.d(TAG, "Associated file contents:\n$fileString")

                        // Attempt to parse the YAML
                        try {
                            val yaml = Yaml()
                            @Suppress("UNCHECKED_CAST")
                            val data = yaml.load<Map<String, Any>>(fileString)
                            if (data != null && data.containsKey("names")) {
                                // "names" is typically a Map<Int, String>
                                val namesMap = data["names"] as? Map<Int, String>
                                if (namesMap != null) {
                                    // Here we set the *parent class* property 'labels'
                                    this.labels = namesMap.values.toList()
                                    Log.d(TAG, "Loaded labels from metadata: $labels")
                                } else {

                                }
                            } else {

                            }
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to parse YAML from metadata: ${ex.message}")
                        }
                    }
                }
            } else {
                Log.d(TAG, "No associated files found in the metadata.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata: ${e.message}")
            // Fallback if needed, or re-throw
        }
        interpreter = Interpreter(modelBuffer, interpreterOptions)
        Log.d("TAG", "TFLite model loaded: $modelPath")

        // 入力形状を調べる（例: [1, inHeight, inWidth, 3]）
        val inputShape = interpreter.getInputTensor(0).shape()
        val inBatch = inputShape[0]         // 普通は 1
        val inHeight = inputShape[1]        // 例: 320
        val inWidth = inputShape[2]         // 例: 320
        val inChannels = inputShape[3]      // 3 (RGB)
        require(inBatch == 1 && inChannels == 3) {
            "Input tensor shape not supported. Expected [1, H, W, 3]. But got ${inputShape.joinToString()}"
        }
        inputSize = Size(inWidth, inHeight) // BasePredictor 側の変数に設定
        modelInputSize = Pair(inWidth, inHeight)
        Log.d("TAG", "Model input size = $inWidth x $inHeight")

        // 出力形状（モデルにより異なるので適宜修正）
        // 例: [1, 84, 2100] = [batch, outHeight, outWidth]
        val outputShape = interpreter.getOutputTensor(0).shape()
        out1 = outputShape[1] // 84
        out2 = outputShape[2] // 2100
        // Log.d("TAG", "Model output shape = [1, $out1, $out2]")

        // 前処理用リソースの確保
        initPreprocessingResources(inWidth, inHeight)

        // 推論出力配列の確保
        rawOutput = Array(1) { Array(out1) { FloatArray(out2) } }
        predictions = Array(out2) { FloatArray(out1) }
        
        // Initialize two image processors:
        
        // 1. For camera feed - includes 90-degree rotation
        imageProcessorCamera = ImageProcessor.Builder()
            .add(Rot90Op(3))  // 270-degree rotation (3 * 90 degrees)
            .add(ResizeOp(inputSize.height, inputSize.width, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
            .add(CastOp(INPUT_IMAGE_TYPE))
            .build()
            
        // 2. For single images - no rotation needed
        imageProcessorSingleImage = ImageProcessor.Builder()
            .add(ResizeOp(inputSize.height, inputSize.width, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
            .add(CastOp(INPUT_IMAGE_TYPE))
            .build()
            
        Log.d("TAG", "ObjectDetector initialized.")
    }


    private fun initPreprocessingResources(width: Int, height: Int) {
        // 入力サイズ（例: 320x320）の ARGB_8888 Bitmap
        scaledBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // ピクセル読み取り用 int配列
        intValues = IntArray(width * height)

        // TFLite 入力用バッファ
        inputBuffer = ByteBuffer.allocateDirect(1 * width * height * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    /**
     * メインの推論メソッド
     * - 前処理：bitmapをリサイズ(scaledBitmap) → getPixels → inputBuffer
     * - TFLite run
     * - 後処理 (JNI経由でNMSなど)
     * @param bitmap Input bitmap to process
     * @param origWidth Original width of the source image
     * @param origHeight Original height of the source image
     * @param rotateForCamera Whether this is a camera feed that requires rotation (true) or a single image (false)
     * @return YOLOResult containing detection results
     */
    override fun predict(bitmap: Bitmap, origWidth: Int, origHeight: Int, rotateForCamera: Boolean): YOLOResult {
        val startTime = System.nanoTime()

        // ======== 前処理: Bitmap を TensorImage 経由で ByteBuffer に変換 ========
        // 1. 入力サイズにリサイズ（元の scaledBitmap の代わりに createScaledBitmap を利用）
//        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize.width, inputSize.height, false)

        // 2. TensorImage にロード
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)

        // 3. ImageProcessor による正規化＆キャスト（元の [pixel/255] と同等）
        // カメラフレームの場合は回転も適用し、単一画像の場合は回転なしで処理
        val processedImage = if (rotateForCamera) {
            // Use camera processor (with rotation) for camera feed
            imageProcessorCamera.process(tensorImage)
        } else {
            // Use single image processor (no rotation) for regular images
            imageProcessorSingleImage.process(tensorImage)
        }
        val imageBuffer = processedImage.buffer

        // ======== 推論 ============
        interpreter.run(imageBuffer, rawOutput)

        // ======== 後処理 (既存コードと同様) ============
        val postStart = System.nanoTime()
        val outHeight = rawOutput[0].size      // out1
        val outWidth = rawOutput[0][0].size      // out2
        val shape = interpreter.getOutputTensor(0).shape() // 例: [1, 84, 8400]
        // Log.d("TFLite", "Output shape: " + shape.contentToString())

//        // 出力の転置（[1][c][w] → [w][c]）
//        for (i in 0 until outHeight) {
//            for (j in 0 until outWidth) {
//                predictions[j][i] = rawOutput[0][i][j]
//            }
//        }
//
//        val outHeight = rawOutput[0].size      // out1
//        val outWidth = rawOutput[0][0].size      // out2
        val resultBoxes = postprocess(
            rawOutput[0],
            w = outWidth,   // 幅は out2
            h = outHeight,  // 高さは out1
            confidenceThreshold = confidenceThreshold,
            iouThreshold = iouThreshold,
            numItemsThreshold = numItemsThreshold,
            numClasses = labels.size
        )
        for ((index, boxArray) in resultBoxes.withIndex()) {
            Log.d(TAG, "Postprocess result - Box $index: ${boxArray.joinToString(", ")}")
        }
        // Box リストへ変換
        val boxes = mutableListOf<Box>()
        for (boxArray in resultBoxes) {
            if (boxArray.size >= 6) {
                // Create xywh (absolute pixel coordinates)
                val rect = RectF(
                    boxArray[0] * origWidth,
                    boxArray[1] * origHeight,
                    boxArray[0] * origWidth + boxArray[2] * origWidth,
                    boxArray[1] * origHeight + boxArray[3] * origHeight
                )
                
                // Create xywhn (normalized coordinates 0-1)
                val normRect = RectF(
                    boxArray[0],                    // normalized x
                    boxArray[1],                    // normalized y
                    boxArray[0] + boxArray[2],      // normalized right
                    boxArray[1] + boxArray[3]       // normalized bottom
                )
                
                val classIdx = boxArray[5].toInt()
                val label = if (classIdx in labels.indices) labels[classIdx] else "Unknown"
                boxes.add(Box(classIdx, label, boxArray[4], rect, normRect))
            }
        }

        val postEnd = System.nanoTime()
        val totalMs = (postEnd - startTime) / 1_000_000.0
        Log.d("TAG", "Total time: $totalMs ms")

        updateTiming()

        return YOLOResult(
            origShape = com.ultralytics.yolo_android.Size(bitmap.height, bitmap.width),
            boxes = boxes,
            speed = t2,
            fps = if (t4 > 0) 1.0 / t4 else 0.0,
            names = labels
        )
    }

    // しきい値など（TFLiteDetector でいう setConfidenceThreshold, setIouThreshold ...）
    private var confidenceThreshold = 0.25f
    private var iouThreshold = 0.45f
    private var numItemsThreshold = 30

    fun setConfidenceThreshold(conf: Float) {
        confidenceThreshold = conf
    }

    fun setIouThreshold(iou: Float) {
        iouThreshold = iou
    }

    override fun setNumItemsThreshold(n: Int) {
        numItemsThreshold = n
    }

    // JNI経由の後処理
    private external fun postprocess(
        predictions: Array<FloatArray>,
        w: Int,
        h: Int,
        confidenceThreshold: Float,
        iouThreshold: Float,
        numItemsThreshold: Int,
        numClasses: Int
    ): Array<FloatArray>

    companion object {
        private const val TAG = "ObjectDetector"
        // JNIライブラリ読み込み
        init {
            System.loadLibrary("ultralytics")
        }
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.25F
        private const val IOU_THRESHOLD = 0.4F
    }
}
