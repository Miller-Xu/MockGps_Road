package com.huolala.mockgps.manager.utils

import android.graphics.Rect
import android.text.TextUtils
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions

/**
 * @author jiayu.liu
 * Modified for AMap (Gaode)
 */
object MapDrawUtils {

    /**
     * 在地图上画一个 Marker
     */
    fun drawMarkerToMap(aMap: AMap, point: LatLng, assetName: String) {
        if (TextUtils.isEmpty(assetName)) {
            return
        }
        val bitmap = BitmapDescriptorFactory.fromAsset(assetName)

        val option = MarkerOptions()
            .position(point)
            .icon(bitmap)
            .zIndex(2f) // 高德 zIndex 是 float 类型

        aMap.addMarker(option)
    }

    /**
     * 在地图上画线
     * 返回类型从 Overlay 改为了 Polyline，因为高德 addPolyline 直接返回 Polyline 对象
     */
    fun drawLineToMap(
        aMap: AMap,
        polylineList: List<LatLng>,
        rect: Rect,
        isMainLine: Boolean = true,
        animateMapStatus: Boolean = true
    ): Polyline? {
        if (polylineList.isEmpty()) {
            return null
        }

        val mOverlayOptions = PolylineOptions()
            .width(30f) // 线宽
            .useGradient(false) // 不使用渐变
            .setUseTexture(true) // 【关键】高德使用自定义纹理图片必须开启这个开关
            .setCustomTexture(
                BitmapDescriptorFactory.fromAsset(
                    if (isMainLine)
                        "navi_lbs_texture.png"
                    else
                        "navi_lbs_texture_unselected.png"
                )
            )
            .lineJoinType(PolylineOptions.LineJoinType.LineJoinRound)
            .lineCapType(PolylineOptions.LineCapType.LineCapRound)
            .addAll(polylineList) // 百度是 points(), 高德是 addAll()
            .zIndex(if (isMainLine) 1f else 0f)

        val polyline = aMap.addPolyline(mOverlayOptions)

        if (isMainLine && animateMapStatus) {
            val builder = LatLngBounds.Builder()
            // 高德的 builder 需要逐个 include
            for (p in polylineList) {
                builder.include(p)
            }
            val bounds = builder.build()

            // 移动相机视角以适应所有点
            // 高德的 newLatLngBounds 通常接受 (bounds, padding)
            // 这里使用 rect.left 作为 padding 的参考值，或者给一个固定值比如 200
            val padding = if (rect.left > 0) rect.left else 200

            try {
                aMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, padding)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return polyline
    }
}