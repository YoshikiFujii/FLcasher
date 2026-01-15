package com.example.flcasher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_mode_admin).setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
        }

        findViewById<Button>(R.id.btn_mode_casher).setOnClickListener {
            startActivity(Intent(this, CasherActivity::class.java))
        }

        findViewById<Button>(R.id.btn_mode_kitchen).setOnClickListener {
            startActivity(Intent(this, KitchenActivity::class.java))
        }
    }
}
