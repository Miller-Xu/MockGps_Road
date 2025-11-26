package com.huolala.mockgps.adaper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
// [修改1] 引入高德的搜索提示类 Tip
import com.amap.api.services.help.Tip
import com.huolala.mockgps.R
import kotlinx.android.synthetic.main.item_poiinfo.view.*

/**
 * @author jiayu.liu
 * 已适配高德地图 (AMap)
 */
class PoiListAdapter :
    ListAdapter<Tip, PoiListAdapter.ViewHolder>(object :
        DiffUtil.ItemCallback<Tip>() {
        override fun areItemsTheSame(
            oldItem: Tip,
            newItem: Tip
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: Tip,
            newItem: Tip
        ): Boolean {
            // [修改2] 字段替换：uid -> poiID
            return oldItem.poiID == newItem.poiID
        }
    }) {
    private var mOnItemClickListener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_poiinfo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val poiInfo = getItem(position)
        // [修改3] 字段替换：
        // city -> district (高德Tip里没有city字段，通常用district行政区代替)
        // key -> name
        holder.itemView.tv_item_poi_name.text =
            String.format("district: ${poiInfo.district}    name: ${poiInfo.name}")

        holder.itemView.tv_item_poi_address.text = poiInfo.address
        if (mOnItemClickListener != null) {
            holder.itemView.setOnClickListener {
                mOnItemClickListener!!.onItemClick(poiInfo)
            }
        }
    }

    fun setData(list: MutableList<Tip>?) {
        submitList(list)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    fun setOnItemClickListener(mOnItemClickListener: OnItemClickListener?) {
        this.mOnItemClickListener = mOnItemClickListener
    }

    interface OnItemClickListener {
        // [修改4] 接口回调对象改为 Tip
        fun onItemClick(poiInfo: Tip)
    }
}