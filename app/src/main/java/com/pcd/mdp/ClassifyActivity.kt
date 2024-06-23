package com.pcd.mdp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pcd.mdp.databinding.ActivityClassifyBinding
import com.pcd.mdp.ml.SvmGarbageModel70
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sqrt


class ClassifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassifyBinding
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityClassifyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Menerima Image dari Intent
        val imageUriString = intent.getStringExtra("GARBAGE_IMAGE")
        val imageUri = Uri.parse(imageUriString)

        // Button Back To Detect Activity
        binding.backToDetectActivity.setOnClickListener {
            finish();
        }

        // Menampilkan Original Image
        binding.originalImage.setImageURI(imageUri)

        // Grayscale Image
        val grayscaleIm =
            toGrayscale(MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri))
        binding.grayScaleImage.setImageBitmap(grayscaleIm)

        // Mengubah gambar menjadi bitmap
        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)

        // Color Extraction
        val colorAverage = getAverageColor(bitmap)
        binding.tvRed.text = "Red : ${colorAverage.red}"
        binding.tvGreen.text = "Green : ${colorAverage.green}"
        binding.tvBlue.text = "Blue : ${colorAverage.blue}"

        // Color Moment
        val colorMoments = calculateColorMomentsRGB(bitmap)
        binding.tvMean.text = "Mean : ${colorMoments.mean[0]}"
        binding.tvVariance.text = "Variance : ${colorMoments.mean[1]}"
        binding.tvSkewness.text = "Skewness : ${colorMoments.mean[2]}"

        // GLCM Features
        val glcmFeatures = calculateGLCMFeatures(bitmap)
        binding.tvContrast.text = "Contrast : ${glcmFeatures.contrast}"
        binding.tvDissimilarity.text = "Dissimilarity : ${glcmFeatures.dissimilarity}"
        binding.tvHomogeneity.text = "Homogeneity : ${glcmFeatures.homogeneity}"
        binding.tvEnergy.text = "Energy : ${glcmFeatures.energy}"
        binding.tvCorrelation.text = "Correlation : ${glcmFeatures.correlation}"
        binding.tvAsm.text = "ASM : ${glcmFeatures.asm}"

        // Proses klasifikasi
        val result = classifyImage(this, bitmap)
        Log.d("ClassifyActivity", result)
        // Tampilkan hasil klasifikasi
        if (result == "0") {
            binding.textClassify.text = "Cardboard"
        } else if (result == "1") {
            binding.textClassify.text = "Glass"
        } else if (result == "2") {
            binding.textClassify.text = "Metal"
        } else if (result == "3") {
            binding.textClassify.text = "Paper"
        } else if (result == "4") {
            binding.textClassify.text = "Plastic"
        } else {
            binding.textClassify.text = "Tidak Diketahui"
        }
    }


    // Fungsi untuk mengklasifikasikan gambar
    private fun classifyImage(context: Context, bitmap: Bitmap): String {
        // Load the model
        val model = SvmGarbageModel70.newInstance(context)

        // Preprocess the image to the required input size and convert to ByteBuffer
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 3, 3, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * 3 * 3 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (i in 0 until 3) {
            for (j in 0 until 3) {
                val pixel = resizedBitmap.getPixel(i, j)
                byteBuffer.putFloat(Color.red(pixel) / 255.0f)
                byteBuffer.putFloat(Color.green(pixel) / 255.0f)
                byteBuffer.putFloat(Color.blue(pixel) / 255.0f)
            }
        }

        // Creates inputs for reference
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 27), DataType.FLOAT32)
        inputFeature0.loadBuffer(byteBuffer)

        // Runs model inference and gets result
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        // Get the predicted class
        val scores = outputFeature0.floatArray
        val maxScoreIndex = scores.indices.maxByOrNull { scores[it] } ?: -1

        // Releases model resources if no longer used
        model.close()

        return "$maxScoreIndex"
    }

    // Convert To GrayScale
    private fun toGrayscale(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val bmpGrayScale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (i in 0 until width) {
            for (j in 0 until height) {
                val pixel = src.getPixel(i, j)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val gray = (0.3 * r + 0.59 * g + 0.11 * b).toInt()
                val newPixel = Color.rgb(gray, gray, gray)
                bmpGrayScale.setPixel(i, j, newPixel)
            }
        }
        return bmpGrayScale
    }

    // Color Extraction
    private fun getAverageColor(bitmap: Bitmap): ColorAverage {
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                redSum += (pixel shr 16) and 0xFF
                greenSum += (pixel shr 8) and 0xFF
                blueSum += pixel and 0xFF
            }
        }

        val redAverage = (redSum / totalPixels).toInt()
        val greenAverage = (greenSum / totalPixels).toInt()
        val blueAverage = (blueSum / totalPixels).toInt()

        return ColorAverage(redAverage, greenAverage, blueAverage)
    }

    // GLCM Features
    private fun calculateGLCMFeatures(bitmap: Bitmap): GLCMFeatures {
        val grayScaleMatrix = convertToGrayscaleMatrix(bitmap)
        val glcm = computeGLCM(grayScaleMatrix)
        return computeGLCMFeatures(glcm)
    }

    private fun convertToGrayscaleMatrix(bitmap: Bitmap): Array<IntArray> {
        val width = bitmap.width
        val height = bitmap.height
        val grayScaleMatrix = Array(height) { IntArray(width) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = Color.red(pixel) // Assume the image is already grayscale
                grayScaleMatrix[y][x] = gray
            }
        }
        return grayScaleMatrix
    }

    private fun computeGLCM(matrix: Array<IntArray>, distance: Int = 1, angle: Int = 0): Array<Array<Int>> {
        val size = 256 // Assuming 8-bit grayscale images
        val glcm = Array(size) { Array(size) { 0 } }

        val dx = when (angle) {
            0 -> distance
            45 -> distance
            90 -> 0
            135 -> -distance
            else -> 0
        }

        val dy = when (angle) {
            0 -> 0
            45 -> distance
            90 -> distance
            135 -> distance
            else -> 0
        }

        for (y in 0 until matrix.size) {
            for (x in 0 until matrix[0].size) {
                val i = matrix[y][x]
                val newX = x + dx
                val newY = y + dy

                if (newX >= 0 && newX < matrix[0].size && newY >= 0 && newY < matrix.size) {
                    val j = matrix[newY][newX]
                    glcm[i][j]++
                }
            }
        }

        return glcm
    }

    private fun computeGLCMFeatures(glcm: Array<Array<Int>>): GLCMFeatures {
        var contrast = 0.0
        var dissimilarity = 0.0
        var homogeneity = 0.0
        var energy = 0.0
        var asm = 0.0
        var meanI = 0.0
        var meanJ = 0.0
        var varianceI = 0.0
        var varianceJ = 0.0
        var correlation = 0.0

        val total = glcm.sumOf { row -> row.sum().toDouble() }
        val probabilities = glcm.map { row -> row.map { it / total } }

        for (i in probabilities.indices) {
            for (j in probabilities[i].indices) {
                val p = probabilities[i][j]
                contrast += (i - j) * (i - j) * p
                dissimilarity += kotlin.math.abs(i - j) * p
                homogeneity += p / (1.0 + (i - j) * (i - j))
                energy += p * p
                asm += p * p
                meanI += i * p
                meanJ += j * p
            }
        }

        for (i in probabilities.indices) {
            for (j in probabilities[i].indices) {
                val p = probabilities[i][j]
                varianceI += (i - meanI) * (i - meanI) * p
                varianceJ += (j - meanJ) * (j - meanJ) * p
            }
        }

        for (i in probabilities.indices) {
            for (j in probabilities[i].indices) {
                val p = probabilities[i][j]
                correlation += (i - meanI) * (j - meanJ) * p / (sqrt(varianceI) * sqrt(varianceJ))
            }
        }

        return GLCMFeatures(
            contrast = contrast,
            dissimilarity = dissimilarity,
            homogeneity = homogeneity,
            energy = sqrt(energy),
            correlation = correlation,
            asm = asm
        )
    }

    // Color Moment
    private fun calculateColorMomentsRGB(bitmap: Bitmap): ColorMoments {
        val mean = DoubleArray(3)
        val variance = DoubleArray(3)
        val skewness = DoubleArray(3)

        val totalPixels = bitmap.width * bitmap.height
        val colorSum = mutableListOf(0.0, 0.0, 0.0)  // Using MutableList

        // First pass: Calculate sum for mean
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                colorSum[0] += Color.red(pixel).toDouble()
                colorSum[1] += Color.green(pixel).toDouble()
                colorSum[2] += Color.blue(pixel).toDouble()
            }
        }

        // Calculate mean
        for (i in 0..2) {
            mean[i] = colorSum[i] / totalPixels
        }

        val colorVariance = mutableListOf(0.0, 0.0, 0.0)
        val colorSkewness = mutableListOf(0.0, 0.0, 0.0)

        // Second pass: Calculate variance and skewness
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                colorVariance[0] += (Color.red(pixel) - mean[0]).pow(2.0)
                colorVariance[1] += (Color.green(pixel) - mean[1]).pow(2.0)
                colorVariance[2] += (Color.blue(pixel) - mean[2]).pow(2.0)

                colorSkewness[0] += (Color.red(pixel) - mean[0]).pow(3.0)
                colorSkewness[1] += (Color.green(pixel) - mean[1]).pow(3.0)
                colorSkewness[2] += (Color.blue(pixel) - mean[2]).pow(3.0)
            }
        }

        // Calculate variance and skewness
        for (i in 0..2) {
            variance[i] = colorVariance[i] / totalPixels
            skewness[i] = colorSkewness[i] / totalPixels
            skewness[i] = skewness[i] / sqrt(variance[i]).pow(3.0)  // Standardize skewness
        }

        return ColorMoments(mean, variance, skewness)
    }
}