package com.huolala.mockgps.ui

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.Toast
// [修改1] 替换为高德地图包
import com.amap.api.maps.AMap
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.ConvertUtils
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.huolala.mockgps.R
import com.huolala.mockgps.databinding.ActivityNaviBinding
import com.huolala.mockgps.manager.FollowMode
import com.huolala.mockgps.manager.MapLocationManager
import com.huolala.mockgps.manager.SearchManager
import com.huolala.mockgps.manager.utils.MapDrawUtils
import com.huolala.mockgps.model.MockMessageModel
import com.huolala.mockgps.model.NaviType
import com.huolala.mockgps.server.GpsService
import com.huolala.mockgps.utils.Utils

/**
 * @author jiayu.liu
 * 已适配高德地图 (AMap)
 */
class MockLocationActivity : BaseActivity<ActivityNaviBinding, BaseViewModel>(),
    View.OnClickListener {
    // [修改2] 变量类型改为 AMap
    private lateinit var aMap: AMap
    private var mNaviType: Int = NaviType.LOCATION
    private val mPadding: Int = ConvertUtils.dp2px(50f)
    private var mapLocationManager: MapLocationManager? = null

    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_navi
    }

    override fun initView() {
        ClickUtils.applySingleDebouncing(dataBinding.ivBack, this)

        // [修改3] 获取 AMap 对象
        if (dataBinding.mapview.map == null) return
        aMap = dataBinding.mapview.map

        // [修改4] UI 设置 (缩放控件等在高德里是通过 UiSettings 控制的)
        aMap.uiSettings?.let {
            it.isScaleControlsEnabled = false // 隐藏比例尺
            it.isZoomControlsEnabled = false  // 隐藏缩放按钮
            it.isCompassEnabled = false       // 隐藏指南针
        }

        // [修改5] 地图加载回调 Callback -> Listener
        aMap.setOnMapLoadedListener {
            startMock()
        }
    }

    override fun initData() {}

    private fun startMock() {
        val model = intent.getParcelableExtra<MockMessageModel>("model")
        if (model == null) {
            pickPoiError()
            return
        }
        with(model) {
            this@MockLocationActivity.mNaviType = naviType
            // 开启定位小蓝点展示
            // [修改6] 传入 aMap 对象
            mapLocationManager = MapLocationManager(
                this@MockLocationActivity,
                aMap,
                if (mNaviType == NaviType.LOCATION) FollowMode.MODE_PERSISTENT else FollowMode.MODE_NONE
            )
            when (naviType) {
                NaviType.LOCATION -> {
                    locationModel?.run {
                        startMockServer(model)
                    } ?: {
                        pickPoiError()
                    }
                }

                NaviType.NAVI, NaviType.NAVI_FILE -> {
                    SearchManager.INSTANCE.polylineList.let {
                        if (it.isEmpty()) {
                            pickPoiError()
                            return
                        }
                        // [修改7] 清空地图
                        aMap.clear()

                        // [修改8] 绘制 Marker 和 Line，传入 aMap
                        startNavi?.latLng?.let { start ->
                            MapDrawUtils.drawMarkerToMap(aMap, start, "marker_start.png")
                        } ?: run {
                            MapDrawUtils.drawMarkerToMap(aMap, it[0], "marker_start.png")
                        }

                        wayNaviList?.map { way ->
                            way.latLng?.let { latLng ->
                                MapDrawUtils.drawMarkerToMap(aMap, latLng, "marker_way.png")
                            }
                        }

                        endNavi?.latLng?.let { end ->
                            MapDrawUtils.drawMarkerToMap(aMap, end, "marker_end.png")
                        } ?: run {
                            MapDrawUtils.drawMarkerToMap(
                                aMap,
                                it[it.size - 1],
                                "marker_end.png"
                            )
                        }

                        // 绘制路线
                        MapDrawUtils.drawLineToMap(
                            aMap,
                            it,
                            Rect(mPadding, mPadding, mPadding, mPadding)
                        )
                        startMockServer(model)
                    }
                }

                else -> {
                }
            }
        }
    }

    override fun initObserver() {

    }

    private fun pickPoiError() {
        Toast.makeText(this, "选址数据异常，请重新选择地址再重试", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun startMockServer(parcelable: Parcelable?) {
        // 判断  为null先启动服务  悬浮窗需要
        parcelable?.run {
            if (!Utils.isAllowMockLocation(this@MockLocationActivity)) {
                Toast.makeText(
                    this@MockLocationActivity,
                    "将本应用设置为\"模拟位置信息应用\"，否则无法正常使用",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        // 启动服务  定位以及悬浮窗
        val serviceIntent = Intent(this, GpsService::class.java).apply {
            parcelable?.let {
                putExtras(Bundle().apply {
                    putParcelable("info", it)
                })
            }

            val captureMode = this@MockLocationActivity.intent.getBooleanExtra("is_capture_mode", false)
            if (captureMode) {
                putExtra("is_capture_mode", true)
                putExtra(
                    "projection_data",
                    this@MockLocationActivity.intent.getParcelableExtra<Intent>("projection_data")
                )
            }
        }

        startService(serviceIntent)
    }

    override fun onResume() {
        super.onResume()
        dataBinding.mapview.onResume()
    }

    override fun onPause() {
        super.onPause()
        dataBinding.mapview.onPause()
        if (isFinishing) {
            destroy()
        }
    }

    private fun destroy() {
        mapLocationManager?.onDestroy()
        dataBinding.mapview.onDestroy()
    }

    override fun onClick(v: View?) {
        when (v) {
            dataBinding.ivBack -> {
                finish()
            }

            else -> {
            }
        }
    }
}