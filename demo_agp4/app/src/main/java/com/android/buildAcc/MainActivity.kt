package com.android.buildAcc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.shuzilm.core.AIClient
import com.alibaba.fastjson.JSONObject
import com.android.library1.Library1

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Library1.print()
        AIClient(this)
        JSONObject().getString("")
    }
}