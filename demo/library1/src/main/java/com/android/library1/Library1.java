package com.android.library1;

import android.util.Log;

import com.android.library11.Library11Test;
import com.hihonor.push.sdk.utils.HonorIdUtils;

/**
 * @author ZhuPeipei
 * @date 2022/11/8 17:23
 */
public class Library1 {
    public static void print() {
        Log.i("Library1", "print library1");
        Library11Test.test();
        new HonorIdUtils();
    }
}
