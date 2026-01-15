package com.example.flcasher

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.flcasher.data.AppDatabase
import com.example.flcasher.data.model.Product
import com.example.flcasher.databinding.ActivityProductBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductBinding
    private lateinit var adapter: ProductAdapter
    private val db by lazy { AppDatabase.getDatabase(this) }
    
    // Dialog UI references for result callback
    private var tempImageUri: String? = null
    private var dialogPreview: ImageView? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val savedPath = saveImageToInternalStorage(uri)
            if (savedPath != null) {
                tempImageUri = savedPath
                dialogPreview?.let {
                    Glide.with(this).load(File(savedPath)).into(it)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()

        binding.fabAddProduct.setOnClickListener {
            showProductDialog(null)
        }
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter(
            onEdit = { product -> showProductDialog(product) },
            onDelete = { product -> deleteProduct(product) }
        )
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = adapter

        db.productDao().getAllProducts().observe(this) { products ->
            adapter.submitList(products)
        }
    }

    private fun showProductDialog(product: Product?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_name)
        val etPrice = dialogView.findViewById<TextInputEditText>(R.id.et_price)
        val btnImage = dialogView.findViewById<Button>(R.id.btn_pick_image)
        dialogPreview = dialogView.findViewById(R.id.iv_preview)

        tempImageUri = null // Reset

        if (product != null) {
            etName.setText(product.name)
            etPrice.setText(product.price.toString())
            tempImageUri = product.imageUri
            if (!product.imageUri.isNullOrEmpty()) {
                Glide.with(this).load(File(product.imageUri)).into(dialogPreview!!)
            }
        }

        btnImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString()
                val priceStr = etPrice.text.toString()

                if (name.isBlank() || priceStr.isBlank()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val price = priceStr.toIntOrNull() ?: 0
                val newProduct = Product(
                    id = product?.id ?: 0,
                    name = name,
                    price = price,
                    imageUri = tempImageUri
                )
                saveProduct(newProduct)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveProduct(product: Product) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (product.id == 0L) {
                db.productDao().insertProduct(product)
            } else {
                db.productDao().updateProduct(product)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ProductActivity, "Product Saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteProduct(product: Product) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${product.name}?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.productDao().deleteProduct(product)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = "prod_${UUID.randomUUID()}.jpg"
            val file = File(filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
