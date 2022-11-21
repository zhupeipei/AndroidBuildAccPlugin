package com.android.buildAcc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.host.Host
import com.android.library1.Library1

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Library1.print()
        Host.test()
    }
}