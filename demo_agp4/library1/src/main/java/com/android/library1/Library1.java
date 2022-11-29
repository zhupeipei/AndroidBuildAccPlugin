package com.android.library1;

import android.content.Context;
import android.util.Log;

import com.github.promeg.pinyinhelper.Pinyin;
import com.hihonor.push.sdk.utils.HonorIdUtils;

import cn.shuzilm.core.AIClient;

/**
 * @author ZhuPeipei
 * @date 2022/11/8 17:23
 */
public class Library1 {
    public static void print() {
        Log.i("Library1", "print library1 update");
        new HonorIdUtils();
        Pinyin.add(null);

        Context context = null;
        new AIClient(context);
        new AIClient(context);
//        com.github.promeg.pinyinhelper.Utils()
    }
}
