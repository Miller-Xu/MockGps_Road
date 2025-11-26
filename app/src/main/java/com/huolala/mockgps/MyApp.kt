package com.huolala.mockgps

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.os.Build
// [修改1] 删除百度依赖，引入高德依赖
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.blankj.utilcode.util.Utils
import com.castiel.common.BaseApp
import me.weishu.reflection.Reflection
import java.lang.reflect.Field


/**
 * @author jiayu.liu
 * 已适配高德地图 (AMap)
 */
class MyApp : BaseApp() {
    private lateinit var mMockReceiver: MockReceiver

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 这个反射是为了绕过系统限制，保留即可
        Reflection.unseal(base)
        reflectionValueAnimator()
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun reflectionValueAnimator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    val field: Field = ValueAnimator::class.java.getDeclaredField("sDurationScale")
                    field.isAccessible = true
                    field.set(null, 1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    override fun onCreate() {
        super.onCreate()

        // 【强制添加这两行】
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        Utils.init(this)

        // [修改2] 高德地图 SDK 初始化配置
        // 必须在调用任何高德 SDK 接口之前调用隐私合规接口，否则会崩溃或无法使用
        val context = this

        // 1. 提交隐私合规状态 (地图SDK)
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)

        // 2. 提交隐私合规状态 (定位SDK)
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)

        // 3. 坐标系设置
        // 百度需要手动设为 GCJ02，但高德默认就是 GCJ02，所以不需要额外设置 CoordType

        // 4. 初始化 Receiver
        initReceiver()
    }


    private fun initReceiver() {
        mMockReceiver = MockReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction("com.huolala.mockgps.navi")

        // Android 12+ (API 33) 建议增加 export 标志，但为了兼容性，原作者的写法暂时保留
        // 如果你的 targetSDK 升到了 33 以上，这里可能需要改为：
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        //     registerReceiver(mMockReceiver, intentFilter, RECEIVER_EXPORTED)
        // } else { ... }
        registerReceiver(mMockReceiver, intentFilter)
    }
}