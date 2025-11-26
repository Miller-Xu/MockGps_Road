package com.huolala.mockgps.ui

import android.content.Intent
import android.graphics.Rect
import android.os.*
import android.view.View
import android.widget.Toast
import com.baidu.mapapi.map.*
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.ConvertUtils
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.huolala.mockgps.R
import com.huolala.mockgps.databinding.ActivityNaviBinding
import com.huolala.mockgps.manager.FollowMode
import com.huolala.mockgps.manager.MapLocationManager
import com.huolala.mockgps.manager.utils.MapDrawUtils
import com.huolala.mockgps.manager.SearchManager
import com.huolala.mockgps.model.MockMessageModel
import com.huolala.mockgps.model.NaviType
import com.huolala.mockgps.server.GpsService
import com.huolala.mockgps.utils.Utils

// 【修改点1】：删除了 kotlinx.android.synthetic... 那个报错的引用

/**
 * @author jiayu.liu
 */
class MockLocationActivity : BaseActivity<ActivityNaviBinding, BaseViewModel>(),
    View.OnClickListener {
    private lateinit var mBaiduMap: BaiduMap
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
        // 【修改点2】：iv_back 改为 dataBinding.ivBack
        ClickUtils.applySingleDebouncing(dataBinding.ivBack, this)

        // 【修改点3】：mapview 改为 dataBinding.mapview
        mBaiduMap = dataBinding.mapview.map
        dataBinding.mapview.showScaleControl(false)
        dataBinding.mapview.showZoomControls(false)
        mBaiduMap.uiSettings?.isCompassEnabled = false

        mBaiduMap.setOnMapLoadedCallback {
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
            //开启定位小蓝点展示
            mapLocationManager = MapLocationManager(
                this@MockLocationActivity,
                mBaiduMap,
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
                        mBaiduMap.clear()
                        startNavi?.latLng?.let { start ->
                            MapDrawUtils.drawMarkerToMap(mBaiduMap, start, "marker_start.png")
                        } ?: run {
                            MapDrawUtils.drawMarkerToMap(mBaiduMap, it[0], "marker_start.png")
                        }
                        wayNaviList?.map {
                            it.latLng?.let { latLng ->
                                MapDrawUtils.drawMarkerToMap(mBaiduMap, latLng, "marker_way.png")
                            }
                        }
                        endNavi?.latLng?.let { end ->
                            MapDrawUtils.drawMarkerToMap(mBaiduMap, end, "marker_end.png")
                        } ?: run {
                            MapDrawUtils.drawMarkerToMap(
                                mBaiduMap,
                                it[it.size - 1],
                                "marker_end.png"
                            )
                        }
                        MapDrawUtils.drawLineToMap(
                            mBaiduMap,
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
            // 1. 传递原有的位置信息
            parcelable?.let {
                putExtras(Bundle().apply {
                    putParcelable("info", it)
                })
            }

            // --- 【核心转发逻辑】 ---
            val captureMode = this@MockLocationActivity.intent.getBooleanExtra("is_capture_mode", false)
            if (captureMode) {
                putExtra("is_capture_mode", true)
                putExtra("projection_data", this@MockLocationActivity.intent.getParcelableExtra<Intent>("projection_data"))
            }
        }

        startService(serviceIntent)
    }

    override fun onResume() {
        super.onResume()
        // 【修改点4】：mapview 改为 dataBinding.mapview
        dataBinding.mapview.onResume()
    }

    override fun onPause() {
        super.onPause()
        // 【修改点5】：mapview 改为 dataBinding.mapview
        dataBinding.mapview.onPause()
        if (isFinishing) {
            destroy()
        }
    }

    private fun destroy() {
        mapLocationManager?.onDestroy()
        // 【修改点6】：mapview 改为 dataBinding.mapview
        dataBinding.mapview.onDestroy()
    }

    override fun onClick(v: View?) {
        when (v) {
            // 【修改点7】：iv_back 改为 dataBinding.ivBack
            dataBinding.ivBack -> {
                finish()
            }

            else -> {
            }
        }
    }
}