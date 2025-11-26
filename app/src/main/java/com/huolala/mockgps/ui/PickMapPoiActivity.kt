package com.huolala.mockgps.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
// [修改1] 导入高德地图相关包
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.huolala.mockgps.R
import com.huolala.mockgps.adaper.PoiListAdapter
import com.huolala.mockgps.adaper.SimpleDividerDecoration
import com.huolala.mockgps.databinding.ActivityPickBinding
import com.huolala.mockgps.manager.FollowMode
import com.huolala.mockgps.manager.MapLocationManager
import com.huolala.mockgps.model.PoiInfoModel
import com.huolala.mockgps.model.PoiInfoType
import com.huolala.mockgps.widget.InputLatLngDialog
import kotlinx.android.synthetic.main.activity_pick.*
import java.lang.ref.WeakReference

/**
 * @author jiayu.liu
 * 已适配高德地图 (AMap)
 */
class PickMapPoiActivity : BaseActivity<ActivityPickBinding, BaseViewModel>(),
    View.OnClickListener {
    private val REVERSE_GEO_CODE = 0
    private val DEFAULT_DELAYED: Long = 300 // 高德地图拖动停止判定建议稍微长一点
    private lateinit var aMap: AMap // [修改] 变量名改为 aMap 更符合习惯
    private lateinit var mGeocodeSearch: GeocodeSearch // [修改] 高德逆地理编码
    private var mInputLatLngDialog: InputLatLngDialog? = null
    private var poiListAdapter: PoiListAdapter = PoiListAdapter()
    private var mPoiInfoModel: PoiInfoModel? = null
    // [修改] 高德不需要 SuggestionSearch 对象，直接 Inputtips(context, query).requestInputtipsAsyn()
    private var mHandler: PickMapPoiHandler? = null
    private var mapLocationManager: MapLocationManager? = null
    private var mIndex = -1

    @PoiInfoType
    private var poiInfoType: Int = PoiInfoType.DEFAULT

    // [修改] 检索监听器：Inputtips.InputtipsListener
    private val inputTipsListener: Inputtips.InputtipsListener =
        Inputtips.InputtipsListener { tipList, rCode ->
            if (rCode == 1000) { // 1000 表示成功
                if (et_search.visibility == View.VISIBLE && !TextUtils.isEmpty(et_search.text)) {
                    tipList?.let {
                        // 过滤掉经纬度为空的提示（有些只是建议词，没坐标）
                        val validTips = it.filter { tip -> tip.point != null }.toMutableList()
                        poiListAdapter.setData(validTips)
                        recycler.visibility = View.VISIBLE
                    }
                }
            } else {
                // ToastUtils.showShort("搜索失败，错误码：$rCode")
            }
        }

    // [修改] 逆地理编码
    private fun reverseGeoCode(latLng: LatLng?) {
        latLng?.let {
            val query = RegeocodeQuery(
                LatLonPoint(it.latitude, it.longitude),
                200f, // 范围半径
                GeocodeSearch.AMAP // 坐标系类型
            )
            mGeocodeSearch.getFromLocationAsyn(query)
        }
    }

    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_pick
    }

    override fun initView() {
        dataBinding.clicklistener = this
        dataBinding.isShowSearch = false

        mHandler = PickMapPoiHandler(this)
        poiInfoType =
            intent?.run { getIntExtra("from_tag", PoiInfoType.DEFAULT) } ?: PoiInfoType.DEFAULT
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = poiListAdapter
        recycler.addItemDecoration(SimpleDividerDecoration(this))
        recycler.itemAnimator = null

        // [修改] Adapter 点击事件回调 Tip 对象
        poiListAdapter.setOnItemClickListener(object : PoiListAdapter.OnItemClickListener {
            override fun onItemClick(poiInfo: Tip) {
                poiInfo.run {
                    if (point == null) {
                        return@run
                    }
                    // Tip 中的 point 是 LatLonPoint，需转为 LatLng
                    val latLng = LatLng(point.latitude, point.longitude)
                    this@PickMapPoiActivity.mPoiInfoModel = PoiInfoModel(
                        latLng,
                        poiID, // uid -> poiID
                        name,  // key -> name
                        poiInfoType,
                        district // city -> district
                    )
                    tv_poi_name.text = name
                    tv_lonlat.text = latLng.toString()
                    editViewShow(false)
                    mHandler = null
                    changeCenterLatLng(latLng.latitude, latLng.longitude)
                    dataBinding.city = district
                }
            }
        })

        et_search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (!TextUtils.isEmpty(s)) {
                    // [修改] 发起高德输入提示搜索
                    val keyword = s.toString()
                    val city = if (et_search_city.text?.isNotEmpty() == true) et_search_city.text.toString() else ""
                    val query = InputtipsQuery(keyword, city)
                    query.cityLimit = false // 是否限制在当前城市
                    val inputTips = Inputtips(this@PickMapPoiActivity, query)
                    inputTips.setInputtipsListener(inputTipsListener)
                    inputTips.requestInputtipsAsyn()
                } else {
                    poiListAdapter.setData(null)
                    recycler.visibility = View.GONE
                }
            }
        })
        initMap()
    }

    override fun initData() {
        mIndex = intent.getIntExtra("index", -1)
    }

    override fun initObserver() {
    }

    private fun initMap() {
        // [修改] 获取 AMap 对象
        if (mapview.map == null) {
            // 防止地图未加载时崩溃
            return
        }
        aMap = mapview.map

        aMap.uiSettings?.run {
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false // 高德叫 Tilt (倾斜)，对应百度的 Overlooking (俯视)
            // 高德没有直接的双击放大中心开关，默认开启
        }

        // [修改] 初始化 GeocodeSearch
        mGeocodeSearch = GeocodeSearch(this)
        mGeocodeSearch.setOnGeocodeSearchListener(object : OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                if (rCode == 1000) { // 1000表示成功
                    result?.regeocodeAddress?.run {
                        var name: String? = null
                        // 优先取 AOI (兴趣面) 名称，其次取 POI，最后取格式化地址
                        if (aois != null && aois.isNotEmpty()) {
                            name = aois[0].aoiName
                        } else if (pois != null && pois.isNotEmpty()) {
                            name = pois[0].title
                        }

                        if (TextUtils.isEmpty(name)) {
                            name = formatAddress ?: "未知地址"
                        }

                        dataBinding.city = city ?: "北京市"

                        // 获取中心点坐标
                        // 这里 result.regeocodeQuery.point 是查询点，直接用地图中心点更准
                        val center = aMap.cameraPosition.target

                        mPoiInfoModel = PoiInfoModel(
                            center,
                            "", // 逆地理没有直接的 uid
                            name,
                            poiInfoType,
                            city ?: "北京市"
                        )
                        tv_poi_name.text = name
                        tv_lonlat.text = center.toString()
                    }
                } else {
                    Toast.makeText(this@PickMapPoiActivity, "逆地理编码失败:$rCode", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onGeocodeSearched(p0: GeocodeResult?, p1: Int) {
                // 正向地理编码（地址转坐标），这里不需要处理
            }
        })

        var follow = FollowMode.MODE_SINGLE

        intent.getParcelableExtra<PoiInfoModel>("model")?.run model@{
            mPoiInfoModel = this
            latLng?.run {
                mPoiInfoModel = this@model
                tv_poi_name.text = this@model.name
                tv_lonlat.text = this@model.latLng.toString()
                editViewShow(false)
                mHandler = null
                changeCenterLatLng(latitude, longitude)
                follow = FollowMode.MODE_NONE
                dataBinding.city = this@model.city
            }
        }

        //设置locationClientOption
        mapLocationManager = MapLocationManager(this, aMap, follow)
        // inputTipsListener 在上面定义了，这里不需要单独 set

        // [修改] 地图状态监听
        aMap.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition?) {
                mHandler?.removeMessages(REVERSE_GEO_CODE)
                editViewShow(false)
            }

            override fun onCameraChangeFinish(position: CameraPosition?) {
                val latLng = position?.target
                aMap.clear()
                mHandler?.removeMessages(REVERSE_GEO_CODE)
                mHandler?.sendMessageDelayed(Message.obtain().apply {
                    what = REVERSE_GEO_CODE
                    obj = latLng
                }, DEFAULT_DELAYED)
                //处理搜索选址还原拖图选址功能
                if (mHandler == null) {
                    mHandler = PickMapPoiHandler(this@PickMapPoiActivity)
                }
            }
        })
    }

    private fun changeCenterLatLng(latitude: Double, longitude: Double) {
        if (latitude > 0.0 && longitude > 0.0) {
            // [修改] 移动相机
            aMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(latitude, longitude), 16f
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mapview.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapview.onPause()
        if (isFinishing) {
            destroy()
        }
    }

    private fun destroy() {
        mapLocationManager?.onDestroy()
        // 高德 GeocodeSearch 没有 destroy 方法
        mapview.onDestroy()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.iv_search -> {
                et_search.setText("")
                editViewShow(true)
            }

            R.id.confirm_location -> {
                mPoiInfoModel?.let {
                    if ((it.latLng?.longitude ?: 0.0) <= 0.0 || (it.latLng?.latitude
                            ?: 0.0) <= 0.0
                    ) {
                        ToastUtils.showShort("数据异常，请重新选择！")
                        return@let
                    }
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putParcelable(
                        "poiInfo",
                        it
                    )
                    if (mIndex != -1) {
                        bundle.putInt("index", mIndex)
                    }
                    intent.putExtras(bundle)
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }

            R.id.iv_cur_location -> {
                // [修改] 获取当前定位点
                if (aMap.myLocation != null) {
                    changeCenterLatLng(aMap.myLocation.latitude, aMap.myLocation.longitude)
                } else {
                    ToastUtils.showShort("正在定位中...")
                }
            }

            R.id.iv_back -> {
                finish()
            }

            R.id.tv_input -> {
                mInputLatLngDialog?.dismiss()
                mInputLatLngDialog =
                    InputLatLngDialog(this, object : InputLatLngDialog.InputLatLngDialogListener {
                        override fun onConfirm(latLng: LatLng) {
                            changeCenterLatLng(latLng.latitude, latLng.longitude)
                        }
                    }).apply { show() }
            }

            else -> {
            }
        }
    }

    private fun editViewShow(isShow: Boolean) {
        dataBinding.isShowSearch = isShow
        val layoutParams = ll_search.layoutParams
        layoutParams?.run {
            width =
                if (isShow) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        }
        ll_search.layoutParams = layoutParams
        et_search.visibility = if (isShow) View.VISIBLE else View.GONE
        et_search_city.visibility = if (isShow) View.VISIBLE else View.GONE
        if (!isShow) {
            KeyboardUtils.hideSoftInput(et_search)
            recycler.visibility = View.GONE
        }
    }


    class PickMapPoiHandler(activity: PickMapPoiActivity) : Handler(Looper.getMainLooper()) {
        private var mWeakReference: WeakReference<PickMapPoiActivity>? = null

        init {
            mWeakReference = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            mWeakReference?.get()?.let {
                when (msg.what) {
                    it.REVERSE_GEO_CODE -> {
                        it.reverseGeoCode(msg.obj as LatLng)
                    }

                    else -> {
                    }
                }
            }
        }
    }
}