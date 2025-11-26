package com.huolala.mockgps.manager

import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.BusRouteResult
import com.amap.api.services.route.DrivePath
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.RideRouteResult
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.RouteSearch.DriveRouteQuery
import com.amap.api.services.route.RouteSearch.FromAndTo
import com.amap.api.services.route.RouteSearch.OnRouteSearchListener
import com.amap.api.services.route.WalkRouteResult
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.Utils

/**
 * @author jiayu.liu
 * 已适配高德地图 (AMap)
 */
class SearchManager private constructor() {
    // 高德的路径规划搜索对象，需要Context
    private var mSearch: RouteSearch = RouteSearch(Utils.getApp())
    private var isSearchIng = false
    private var listenerList: ArrayList<SearchManagerListener> = arrayListOf()

    // 用于存储规划出的路线点串 (供地图绘制和模拟使用)
    var polylineList: ArrayList<LatLng> = arrayListOf()

    companion object {
        val INSTANCE: SearchManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            SearchManager()
        }
    }

    init {
        mSearch.setRouteSearchListener(object : OnRouteSearchListener {
            override fun onBusRouteSearched(result: BusRouteResult?, errorCode: Int) {
                // 不处理公交
            }

            override fun onDriveRouteSearched(result: DriveRouteResult?, errorCode: Int) {
                isSearchIng = false
                if (errorCode == 1000) { // 1000 表示成功
                    if (result != null && result.paths != null) {
                        listenerList.map {
                            // 回调结果给 MockReceiver，注意这里传的是 paths (List<DrivePath>)
                            it.onDrivingRouteResultLines(result.paths)
                        }
                    } else {
                        ToastUtils.showShort("未搜索到驾车路径")
                    }
                } else {
                    ToastUtils.showShort("路径规划失败，错误码: $errorCode")
                }
            }

            override fun onWalkRouteSearched(result: WalkRouteResult?, errorCode: Int) {
                // 不处理步行
            }

            override fun onRideRouteSearched(result: RideRouteResult?, errorCode: Int) {
                // 不处理骑行
            }
        })
    }

    fun addSearchManagerListener(listener: SearchManagerListener) {
        listenerList.add(listener)
    }

    fun removeSearchManagerListener(listener: SearchManagerListener) {
        listenerList.remove(listener)
    }

    /**
     * 发起驾车路线规划
     */
    fun driverSearch(
        startLatLng: LatLng?,
        endLatLng: LatLng?,
        multiRoute: Boolean,
        wayList: MutableList<LatLng>? = null,
    ) {
        if (startLatLng == null || endLatLng == null) {
            return
        }
        if (isSearchIng) {
            ToastUtils.showShort("路线规划中,请稍后.")
            return
        }
        isSearchIng = true

        // 1. 转换坐标：LatLng (地图包) -> LatLonPoint (服务包)
        val fromPoint = LatLonPoint(startLatLng.latitude, startLatLng.longitude)
        val toPoint = LatLonPoint(endLatLng.latitude, endLatLng.longitude)
        val fromAndTo = FromAndTo(fromPoint, toPoint)

        // 2. 处理途经点
        val passedByPoints: MutableList<LatLonPoint>? = if (wayList != null && wayList.isNotEmpty()) {
            val list = ArrayList<LatLonPoint>()
            for (p in wayList) {
                list.add(LatLonPoint(p.latitude, p.longitude))
            }
            list
        } else {
            null
        }

        // 3. 构建查询参数
        // 参数含义: FromAndTo, mode (默认/多策略), passedByPoints, avoidPolygons, avoidRoad
        val mode = if (multiRoute) RouteSearch.DrivingMultiStrategy else RouteSearch.DrivingDefault
        val query = DriveRouteQuery(fromAndTo, mode, passedByPoints, null, "")

        // 4. 异步计算
        mSearch.calculateDriveRouteAsyn(query)
    }

    /**
     * 选中一条路线，解析出所有的经纬度点，存入 polylineList
     */
    fun selectDriverLine(routeLine: DrivePath?) {
        routeLine?.let { path ->
            val tempList = arrayListOf<LatLng>()

            // 高德的路线是由多个 Step 组成的
            for (step in path.steps) {
                // 每个 Step 里有一串点 (polyline)
                if (step.polyline != null && step.polyline.isNotEmpty()) {
                    for (point in step.polyline) {
                        // 需要把 LatLonPoint 转回 LatLng
                        tempList.add(LatLng(point.latitude, point.longitude))
                    }
                }
            }

            this@SearchManager.polylineList.run {
                clear()
                addAll(tempList)
            }
        }
    }

    interface SearchManagerListener {
        // 接口定义修改：List<DrivingRouteLine> -> List<DrivePath>
        fun onDrivingRouteResultLines(routeLines: List<DrivePath>?)
    }
}