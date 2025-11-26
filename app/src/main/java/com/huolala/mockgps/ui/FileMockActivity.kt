package com.huolala.mockgps.ui

import android.content.Intent
import android.text.TextUtils
import android.view.View
import com.amap.api.maps.model.LatLng
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.huolala.mockgps.R
import com.huolala.mockgps.databinding.ActivityFileBinding
import com.huolala.mockgps.manager.SearchManager
import com.huolala.mockgps.model.MockMessageModel
import com.huolala.mockgps.model.NaviType
import com.huolala.mockgps.utils.WarnDialogUtils
import com.huolala.mockgps.utils.LocationUtils
import com.huolala.mockgps.utils.MMKVUtils
import com.huolala.mockgps.utils.Utils
import com.huolala.mockgps.widget.HintDialog
import com.huolala.mockgps.widget.NaviPathDialog
import com.huolala.mockgps.widget.NaviPopupWindow
import com.huolala.mockgps.widget.PointTypeDialog

// --- 引用保持不变 ---
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.result.contract.ActivityResultContracts

/**
 * @author jiayu.liu
 */
class FileMockActivity : BaseActivity<ActivityFileBinding, BaseViewModel>(), View.OnClickListener {
    private var mPointType = LocationUtils.gcj02

    // ================== 【修改点 1：必须在这里定义变量】 ==================
    // 1. 用来暂存导航数据
    private var pendingModel: MockMessageModel? = null

    // 2. 注册权限回调（必须放在类成员位置，不能放方法里）
    private val screenshotLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingModel != null) {
            // 权限拿到，启动 Activity
            val intent = Intent(this, MockLocationActivity::class.java)
            intent.putExtra("model", pendingModel)
            // 传递截图标记和权限数据
            intent.putExtra("is_capture_mode", true)
            intent.putExtra("projection_data", result.data)
            startActivity(intent)
        } else {
            ToastUtils.showShort("未获得录屏权限或已取消")
        }
    }
    // ====================================================================

    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_file
    }

    override fun initView() {
        KeyboardUtils.clickBlankArea2HideSoftInput()

        ClickUtils.applySingleDebouncing(dataBinding.ivNaviSetting, this)
        ClickUtils.applySingleDebouncing(dataBinding.ivWarning, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnFile, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnStartNavi, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnCreatePath, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnPointType, this)

        dataBinding.pointType = "经纬度类型：$mPointType"
    }

    override fun initData() {

    }

    override fun initObserver() {

    }

    override fun onClick(v: View?) {
        when (v) {
            dataBinding.ivWarning -> {
                HintDialog(
                    this@FileMockActivity,
                    "数据格式要求",
                    getString(R.string.file_navi_hint)
                ).show()
            }

            dataBinding.btnCreatePath -> {
                //生成路径文件
                startActivity(Intent(this, CalculateRouteActivity::class.java))
            }

            dataBinding.ivNaviSetting -> {
                NaviPopupWindow(this).apply {
                    show(dataBinding.ivNaviSetting)
                }
            }

            dataBinding.btnFile -> {
                NaviPathDialog(this).run {
                    listener = object : NaviPathDialog.NaviPathListener {
                        override fun onItemClick(path: String) {
                            if (TextUtils.isEmpty(path)) {
                                return
                            }
                            dataBinding.edFile.setText(path)
                        }
                    }
                    show()
                }
            }

            dataBinding.btnStartNavi -> {
                val text = dataBinding.edFile.text
                if (TextUtils.isEmpty(text)) {
                    ToastUtils.showShort("路径不能为null")
                    return
                }
                Utils.checkFloatWindow(this).let { it ->
                    if (!it) {
                        WarnDialogUtils.setFloatWindowDialog(this@FileMockActivity)
                        return
                    }
                    val polylineList = arrayListOf<LatLng>()
                    FileIOUtils.readFile2String(text.toString())?.run {
                        split(";").run {
                            if (isNotEmpty()) {
                                map {
                                    it.split(",").run {
                                        if (size == 2) {
                                            val lat = get(1).toDouble()
                                            val lng = get(0).toDouble()

                                            var gcj02 = doubleArrayOf(lng, lat)
                                            //将路线转换成gcj02
                                            when (mPointType) {
                                                LocationUtils.gps84 -> {
                                                    gcj02 = LocationUtils.wgs84ToGcj02(
                                                        lng,
                                                        lat
                                                    )
                                                }

                                                LocationUtils.bd09 -> {
                                                    gcj02 = LocationUtils.bd09ToGcj02(
                                                        lng,
                                                        lat
                                                    )
                                                }

                                                else -> {}
                                            }
                                            polylineList.add(
                                                LatLng(
                                                    gcj02[1],
                                                    gcj02[0]
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        //文件数据导航替换
                        SearchManager.INSTANCE.polylineList = polylineList
                    }
                    if (polylineList.isEmpty()) {
                        ToastUtils.showShort("文件数据解析失败,无法启动导航")
                        return
                    }
                    val model = MockMessageModel(
                        naviType = NaviType.NAVI_FILE,
                        speed = MMKVUtils.getSpeed(),
                    )

                    // ================== 【修改点 2：修复后的弹窗逻辑】 ==================
                    // 使用 this@FileMockActivity 确保 Context 正确
                    AlertDialog.Builder(this@FileMockActivity)
                        .setTitle("启动设置")
                        .setMessage("请选择导航模式")
                        .setPositiveButton("截图模式(2秒/张)") { _, _ ->
                            // 1. 存下 model 到我们刚才定义的成员变量里
                            pendingModel = model
                            // 2. 发起录屏权限请求
                            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            screenshotLauncher.launch(mpm.createScreenCaptureIntent())
                        }
                        .setNegativeButton("普通模式") { _, _ ->
                            // 普通模式直接启动
                            val intent = Intent(this@FileMockActivity, MockLocationActivity::class.java)
                            intent.putExtra("model", model)
                            startActivity(intent)
                        }
                        .show()
                    // =================================================================
                }
            }

            dataBinding.btnPointType -> {
                PointTypeDialog(this).apply {
                    listener = object : PointTypeDialog.PointTypeDialogListener {
                        override fun onDismiss(type: String) {
                            mPointType = type
                            dataBinding.pointType = "经纬度类型：$mPointType"
                        }
                    }
                }.show(mPointType)
            }

            else -> {
            }
        }
    }
}