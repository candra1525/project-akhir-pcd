package com.pcd.mdp

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


class ClassifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassifyBinding
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

        val imageUriString = intent.getStringExtra("GARBAGE_IMAGE")
        val imageUri = Uri.parse(imageUriString)

        binding.backToDetectActivity.setOnClickListener {
            finish();
        }

        binding.garbageImage.setImageURI(imageUri)

        // Jadikan bitmap
        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
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

        // Return the predicted class as a string
        return "$maxScoreIndex"
    }
}