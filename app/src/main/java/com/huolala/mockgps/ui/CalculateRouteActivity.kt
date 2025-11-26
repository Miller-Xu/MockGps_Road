package com.huolala.mockgps.ui

import android.content.Intent
import android.graphics.Rect
import android.text.TextUtils
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import android.widget.Toast
// [修改1] 导入高德相关类
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.services.route.DrivePath
import com.blankj.utilcode.util.*
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.huolala.mockgps.R
import com.huolala.mockgps.databinding.ActivityCalculateRouteBinding
import com.huolala.mockgps.manager.FollowMode
import com.huolala.mockgps.manager.MapLocationManager
import com.huolala.mockgps.manager.SearchManager
import com.huolala.mockgps.manager.utils.MapDrawUtils
import com.huolala.mockgps.model.PoiInfoModel
import com.huolala.mockgps.model.PoiInfoType
import java.io.File
import kotlin.collections.ArrayList

/**
 * @author jiayu.liu
 * 已适配高德地图 (AMap)
 */
class CalculateRouteActivity : BaseActivity<ActivityCalculateRouteBinding, BaseViewModel>(),
    View.OnClickListener {

    // [修改2] 变量改为 AMap
    private lateinit var aMap: AMap
    private var mapLocationManager: MapLocationManager? = null
    private val mDefaultPadding = ConvertUtils.dp2px(50f)
    private var mainIndex = 0

    /**
     * 算路成功的路线
     */
    // [修改3] 列表泛型改为 DrivePath
    private var routeLines: ArrayList<DrivePath> = arrayListOf()

    private val mSearchManagerListener = object : SearchManager.SearchManagerListener {
        // [修改4] 回调参数改为 List<DrivePath>
        override fun onDrivingRouteResultLines(routeLines: List<DrivePath>?) {
            viewModel.loading.value = false
            if (routeLines?.isEmpty() != false) {
                ToastUtils.showShort("路线规划数据获取失败,请检测网络or数据是否正确!")
                return
            }
            this@CalculateRouteActivity.routeLines = routeLines as ArrayList<DrivePath>

            // [修改5] 绘制逻辑
            aMap.let { map ->
                // 绘制起点 Marker
                (dataBinding.tvStart.tag as PoiInfoModel?)?.latLng?.let { start ->
                    MapDrawUtils.drawMarkerToMap(map, start, "marker_start.png")
                }
                // 绘制终点 Marker
                (dataBinding.tvEnd.tag as PoiInfoModel?)?.latLng?.let { end ->
                    MapDrawUtils.drawMarkerToMap(map, end, "marker_end.png")
                }

                routeLines.mapIndexed { index, line ->
                    // [关键修改] 解析 DrivePath 为 List<LatLng>
                    val latLngList = parseDrivePath(line)

                    MapDrawUtils.drawLineToMap(
                        map,
                        latLngList,
                        Rect(
                            mDefaultPadding,
                            mDefaultPadding + dataBinding.clPanel.height,
                            mDefaultPadding,
                            mDefaultPadding
                        ),
                        index == mainIndex // 只高亮显示当前选中的那条
                    )
                }
            }
            dataBinding.fileName = "${dataBinding.tvStart.text}-${dataBinding.tvEnd.text}"
        }
    }

    private var registerForActivityResultToStartPoi =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                it.data?.run {
                    setDataToView(getParcelableExtra<PoiInfoModel>("poiInfo"), dataBinding.tvStart)
                }
            }
        }


    private var registerForActivityResultToEndPoi =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                it.data?.run {
                    setDataToView(getParcelableExtra<PoiInfoModel>("poiInfo"), dataBinding.tvEnd)
                }
            }
        }

    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_calculate_route
    }

    override fun initView() {
        initMap()

        ClickUtils.applySingleDebouncing(dataBinding.tvStart, this)
        ClickUtils.applySingleDebouncing(dataBinding.tvEnd, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnChange, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnStartRoute, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnSaveFile, this)
    }


    private fun initMap() {
        if (dataBinding.mapview.map == null) return
        aMap = dataBinding.mapview.map

        // [修改6] 设置默认缩放级别
        aMap.moveCamera(CameraUpdateFactory.zoomTo(16f))

        // [修改7] 初始化定位管理器
        mapLocationManager = MapLocationManager(this, aMap, FollowMode.MODE_SINGLE)
    }


    override fun initData() {

    }


    override fun initObserver() {
    }

    override fun onClick(v: View?) {
        when (v) {
            dataBinding.tvStart -> {
                registerForActivityResultToStartPoi.launch(Intent(
                    this@CalculateRouteActivity,
                    PickMapPoiActivity::class.java
                ).apply {
                    putExtra("from_tag", PoiInfoType.DEFAULT)
                    if (dataBinding.tvStart.tag != null) {
                        putExtra("model", dataBinding.tvStart.tag as PoiInfoModel?)
                    }
                })
            }

            dataBinding.tvEnd -> {
                registerForActivityResultToEndPoi.launch(Intent(
                    this@CalculateRouteActivity,
                    PickMapPoiActivity::class.java
                ).apply {
                    putExtra("from_tag", PoiInfoType.DEFAULT)
                    if (dataBinding.tvEnd.tag != null) {
                        putExtra("model", dataBinding.tvEnd.tag as PoiInfoModel?)
                    }
                })
            }

            dataBinding.btnStartRoute -> {
                // [修改8] 不再使用 PlanNode，直接取 LatLng
                var stLatLng: LatLng? = null
                (dataBinding.tvStart.tag as PoiInfoModel?)?.run {
                    stLatLng = latLng
                }

                var enLatLng: LatLng? = null
                (dataBinding.tvEnd.tag as PoiInfoModel?)?.run {
                    enLatLng = latLng
                }

                if (stLatLng == null || enLatLng == null) {
                    ToastUtils.showShort("起终点不能null")
                    return
                }

                // 清空地图和缓存列表
                aMap.clear()
                routeLines.clear()
                viewModel.loading.value = true

                // 发起搜索
                SearchManager.INSTANCE.driverSearch(stLatLng, enLatLng, true)
            }

            dataBinding.btnChange -> {
                if (routeLines.isEmpty()) {
                    ToastUtils.showShort("没有路线切换")
                    return
                }
                aMap.clear()

                mainIndex = ++mainIndex % routeLines.size

                // 重新绘制，根据 mainIndex 高亮
                aMap.let { map ->
                    routeLines.mapIndexed { index, line ->
                        val latLngList = parseDrivePath(line)
                        MapDrawUtils.drawLineToMap(
                            map,
                            latLngList,
                            Rect(
                                mDefaultPadding,
                                mDefaultPadding + dataBinding.clPanel.height,
                                mDefaultPadding,
                                mDefaultPadding
                            ),
                            index == mainIndex
                        )
                    }
                }
                dataBinding.fileName = "${dataBinding.tvStart.text}-${dataBinding.tvEnd.text}"
            }

            dataBinding.btnSaveFile -> {
                if (routeLines.isEmpty() || mainIndex < 0 || mainIndex >= routeLines.size) {
                    ToastUtils.showShort("数据列表为null！，无法保存")
                    return
                }

                // [修改9] 解析当前选中的路线为点串
                val convertLatLngList = parseDrivePath(routeLines[mainIndex])

                val builder = StringBuilder()
                convertLatLngList.map {
                    builder.append(it.longitude).append(",").append(it.latitude).append(";")
                }
                if (!TextUtils.isEmpty(builder)) {
                    val file = File(getExternalFilesDir("nav_path"), "${dataBinding.fileName}.txt")
                    if (FileUtils.isFileExists(file)) {
                        ToastUtils.showShort("文件已经存在！请重命名文件名称")
                        return
                    }
                    FileIOUtils.writeFileFromString(
                        file,
                        builder.toString()
                    ).let {
                        ToastUtils.showShort(if (it) "保存成功" else "保存失败")
                        if (it) {
                            finish()
                        }
                    }
                }
            }

            else -> {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SearchManager.INSTANCE.addSearchManagerListener(mSearchManagerListener)
        dataBinding.mapview.onResume()
    }

    override fun onPause() {
        super.onPause()
        SearchManager.INSTANCE.removeSearchManagerListener(mSearchManagerListener)
        dataBinding.mapview.onPause()
        if (isFinishing) {
            destroy()
        }
    }

    private fun destroy() {
        mapLocationManager?.onDestroy()
        dataBinding.mapview.onDestroy()
    }


    private fun setDataToView(model: PoiInfoModel?, view: AppCompatTextView) {
        model?.run {
            when (poiInfoType) {
                PoiInfoType.DEFAULT -> {
                    view.text = String.format(
                        "%s",
                        name
                    )
                    view.tag = this
                }

                else -> {
                }
            }
        }
    }

    /**
     * [辅助方法] 将 DrivePath 解析为 List<LatLng>
     * 原本你调用的是 MapConvertUtils.convertLatLngList，现在在这里直接实现
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
}