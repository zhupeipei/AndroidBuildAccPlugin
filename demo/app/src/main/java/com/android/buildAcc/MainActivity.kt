package com.android.buildAcc

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.android.library1.Library1

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Library1.print()
    }
}