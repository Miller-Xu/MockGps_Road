package com.huolala.mockgps.manager

import android.content.Context
import android.graphics.Color
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.LocationSource
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle

/**
 * 定位小蓝点显示控制类
 * @author jiayu.liu
 * 已适配高德地图 (AMap)
 */

enum class FollowMode {
    MODE_SINGLE,     // 单次调整中心点
    MODE_PERSISTENT, // 跟随调整中心点
    MODE_NONE        // 不调整
}

/**
 * 来源
 */
enum class Source {
    FLOATING, // 悬浮窗
    OTHER,    // 其他
}

// [修改] 实现 LocationSource 和 AMapLocationListener 接口
class MapLocationManager(
    context: Context,
    private var aMap: AMap, // BaiduMap -> AMap
    private var follow: FollowMode,
    private val source: Source = Source.OTHER
) : LocationSource, AMapLocationListener {

    private var mLocationClient: AMapLocationClient? = null
    private var mListener: LocationSource.OnLocationChangedListener? = null
    private var isZoom = false

    // [修改] 高德定位监听回调
    override fun onLocationChanged(location: AMapLocation?) {
        // location 为 null 或者报错都不处理
        if (mListener != null && location != null) {
            if (location.errorCode == 0) {
                // 1. 显示小蓝点：调用 mListener.onLocationChanged 让地图画出蓝点
                mListener?.onLocationChanged(location)

                // 2. 更新中心点逻辑 (模仿原代码逻辑)
                when (follow) {
                    FollowMode.MODE_SINGLE -> {
                        if (!isZoom) {
                            zoom(location)
                            isZoom = true
                        }
                    }
                    FollowMode.MODE_PERSISTENT -> {
                        zoom(location)
                    }
                    else -> {
                        // MODE_NONE 不做处理
                    }
                }
            } else {
                // 定位失败，可以在这里打印日志
                // Log.e("AmapError", "location Error, ErrCode:" + location.errorCode + ", errInfo:" + location.errorInfo)
            }
        }
    }

    private fun zoom(location: AMapLocation) {
        aMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(
                    location.latitude,
                    location.longitude
                ), if (source == Source.FLOATING) 18f else 16f
            )
        )
    }

    /**
     * 设置定位模式
     * 对应百度的 MyLocationConfiguration.LocationMode
     * 高德使用 MyLocationStyle
     */
    fun setLocationMode(mode: Int) {
        // 初始化定位蓝点样式类
        val myLocationStyle = MyLocationStyle()

        // 这里的 mode 参数建议直接传高德的 MyLocationStyle 常量
        // 例如: MyLocationStyle.LOCATION_TYPE_LOCATE
        myLocationStyle.myLocationType(mode)

        // 设置是否显示定位小蓝点
        myLocationStyle.showMyLocation(true)

        // 设置定位蓝点的精度圆圈颜色 (原代码是透明)
        myLocationStyle.strokeColor(Color.TRANSPARENT)
        myLocationStyle.radiusFillColor(Color.TRANSPARENT)

        // 如果需要隐藏蓝点下面的箭头/图标，可以设置一个透明图片，或者默认不设
        // myLocationStyle.myLocationIcon(BitmapDescriptorFactory.fromResource(R.drawable.transparent))

        aMap.myLocationStyle = myLocationStyle

        // 如果是普通模式，重置旋转和俯视角度 (模仿原代码逻辑)
        if (mode == MyLocationStyle.LOCATION_TYPE_LOCATE || mode == MyLocationStyle.LOCATION_TYPE_SHOW) {
            // 高德没有直接的一键重置状态，通常不需要手动 reset，除非为了强制视角
            aMap.animateCamera(CameraUpdateFactory.changeTilt(0f))
            aMap.animateCamera(CameraUpdateFactory.changeBearing(0f))
        }
    }

    init {
        // 初始化定位
        mLocationClient = AMapLocationClient(context)

        // 设置定位回调监听
        mLocationClient?.setLocationListener(this)

        // 初始化 AMAPLocationClientOption 对象
        val mLocationOption = AMapLocationClientOption()

        // 设置定位模式为 AMAPLocationMode.Hight_Accuracy，高精度模式
        mLocationOption.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy

        // 设置定位间隔,单位毫秒,默认为2000ms，原代码是1000
        mLocationOption.interval = 1000

        // 设置是否返回地址信息 (默认返回，原代码 setIsNeedAddress 逻辑)
        mLocationOption.isNeedAddress = true

        // 给定位客户端对象设置定位参数
        mLocationClient?.setLocationOption(mLocationOption)

        // 设置地图的 LocationSource
        aMap.setLocationSource(this)

        // 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认false
        aMap.isMyLocationEnabled = true

        // 默认样式：只定位，不强行移动地图（因为上面有自定义的 zoom 逻辑）
        // 对应百度的 NORMAL
        setLocationMode(MyLocationStyle.LOCATION_TYPE_SHOW)
    }

    // [LocationSource 接口实现] 激活定位
    override fun activate(listener: LocationSource.OnLocationChangedListener?) {
        mListener = listener
        mLocationClient?.startLocation()
    }

    // [LocationSource 接口实现] 停止定位
    override fun deactivate() {
        mListener = null
        mLocationClient?.stopLocation()
        mLocationClient?.onDestroy()
        mLocationClient = null
    }

    fun onDestroy() {
        deactivate()
        aMap.isMyLocationEnabled = false
    }
}