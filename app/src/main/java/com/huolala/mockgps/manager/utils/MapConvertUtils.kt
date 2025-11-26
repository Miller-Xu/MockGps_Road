package com.huolala.mockgps.manager.utils

// [修改1] 引入高德相关类
import com.amap.api.maps.model.LatLng
import com.amap.api.services.route.DrivePath

/**
 * @author jiayu.liu
 * 已适配高德地图 (AMap)
 */
object MapConvertUtils {
    /**
     * 导航数据转换点串
     * [修改2] 参数类型改为 DrivePath
     */
    fun convertLatLngList(routeLine: DrivePath?): List<LatLng> {
        val polylineList = arrayListOf<LatLng>()
        routeLine?.let { path ->
            // 高德的路线是由多个 Step 组成的
            for (step in path.steps) {
                // 每个 Step 里包含一段轨迹 (polyline)，类型是 List<LatLonPoint>
                if (step.polyline != null && step.polyline.isNotEmpty()) {
                    for (point in step.polyline) {
                        // [修改3] 将 LatLonPoint 转换为地图可用的 LatLng
                        polylineList.add(LatLng(point.latitude, point.longitude))
                    }
                }
            }
        }
        return polylineList
    }
}