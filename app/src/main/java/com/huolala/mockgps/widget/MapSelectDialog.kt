package com.huolala.mockgps.widget

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
// [修改1] 引入高德地图相关类
import com.amap.api.maps.AMap
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.services.route.DrivePath
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.ScreenUtils
import com.castiel.common.dialog.BaseDialog
import com.huolala.mockgps.R
// MapConvertUtils 不需要了，我们直接解析 DrivePath
import com.huolala.mockgps.manager.utils.MapDrawUtils
import com.huolala.mockgps.model.PoiInfoModel
import kotlinx.android.synthetic.main.dialog_select_navi_map.*

/**
 * @author jiayu.liu
 * 已适配高德地图 (AMap)
 */
class MapSelectDialog(
    context: Context,
    private var routeLines: List<DrivePath>, // [修改2] 类型改为 DrivePath
    private var start: LatLng?,
    private var end: LatLng?,
    wayList: MutableList<PoiInfoModel>?
) : BaseDialog(context) {

    private val mHorizontalPadding = ConvertUtils.dp2px(20f)
    private val mMapPadding = ConvertUtils.dp2px(30f)
    private val screenWidth = ScreenUtils.getScreenWidth()
    // [修改3] 存储绘制出的线，方便清除。高德画线返回的是 Polyline
    private val mOverlayList = ArrayList<Polyline>()
    private var mainIndex = 0
    var listener: MapSelectDialogListener? = null

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        layoutInflater.inflate(R.layout.dialog_select_navi_map, null, false).apply {
            setContentView(this)
        }

        // [修改4] 初始化地图
        texture_mapview.onCreate(null) // Bundle传null即可

        // 高德的 UI 设置
        texture_mapview.map?.uiSettings?.run {
            isScaleControlsEnabled = false
            isZoomControlsEnabled = false
            isCompassEnabled = false
            // 禁用手势（缩放、移动等），只做静态展示
            setAllGesturesEnabled(false)
        }

        // 移除百度特有的去Logo逻辑，高德不需要 getChildAt(1)

        texture_mapview.map?.let { aMap ->
            // [修改5] 加载回调 Callback -> Listener
            aMap.setOnMapLoadedListener {
                start?.let { start ->
                    MapDrawUtils.drawMarkerToMap(aMap, start, "marker_start.png")
                }
                wayList?.map { model ->
                    model.latLng?.let { latLng ->
                        MapDrawUtils.drawMarkerToMap(aMap, latLng, "marker_way.png")
                    }
                }
                end?.let { end ->
                    MapDrawUtils.drawMarkerToMap(aMap, end, "marker_end.png")
                }
                drawLine(aMap)
            }
        }

        ClickUtils.applySingleDebouncing(btn_change) {
            mainIndex = ++mainIndex % routeLines.size
            texture_mapview.map?.let {
                drawLine(it)
            }
        }
        ClickUtils.applySingleDebouncing(btn_select) {
            listener?.onSelectLine(routeLines[mainIndex])
            dismiss()
        }

        window?.let {
            val lp: WindowManager.LayoutParams = it.attributes
            lp.width = screenWidth - mHorizontalPadding
            lp.height = screenWidth + ConvertUtils.dp2px(60f)
            lp.gravity = Gravity.CENTER
            lp.dimAmount = 0.5f
            it.attributes = lp
        }

    }

    private fun drawLine(aMap: AMap) {
        // [修改6] 清除旧线
        mOverlayList.map {
            it.remove()
        }.also {
            mOverlayList.clear()
        }

        routeLines.mapIndexed { index, line ->
            // [修改7] 解析 DrivePath 为 List<LatLng>
            val latLngList = parseDrivePath(line)

            // 调用 MapDrawUtils (注意：需确保 MapDrawUtils 返回的是 Polyline?)
            MapDrawUtils.drawLineToMap(
                aMap,
                latLngList,
                Rect(mMapPadding, mMapPadding, mMapPadding, mMapPadding),
                index == mainIndex
            )?.let { polyline ->
                mOverlayList.add(polyline)
            }
        }
    }

    /**
     * [新增] 辅助方法：将高德 DrivePath 解析为 List<LatLng>
     */
    private fun parseDrivePath(drivePath: DrivePath): List<LatLng> {
        val latLngs = ArrayList<LatLng>()
        for (step in drivePath.steps) {
            if (step.polyline != null && step.polyline.isNotEmpty()) {
                for (point in step.polyline) {
                    latLngs.add(LatLng(point.latitude, point.longitude))
                }
            }
        }
        return latLngs
    }

    public fun onResume() {
        texture_mapview.onResume()
    }

    public fun onPause() {
        texture_mapview.onPause()
    }

    override fun dismiss() {
        texture_mapview.onDestroy()
        super.dismiss()
    }

    interface MapSelectDialogListener {
        // [修改8] 回调 DrivePath
        fun onSelectLine(routeLine: DrivePath)
    }
}