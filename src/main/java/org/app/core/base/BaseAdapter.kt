package org.app.core.base

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

abstract class BaseAdapter<T, VB : ViewDataBinding> :
    RecyclerView.Adapter<BaseViewHolder<VB>>() {
    protected var context: Context? = null

    var datas: ArrayList<T> = ArrayList()
    var onItemClickListener: OnItemClickListener<T>? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<VB> {
        context = parent.context
        val viewBinding: VB = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            itemLayout,
            parent,
            false
        )
        return BaseViewHolder(viewBinding)
    }

    override fun onBindViewHolder(holder: BaseViewHolder<VB>, position: Int) {
        datas[position]?.let {
            holder.binding.run {
                bind(this, it, holder.adapterPosition)
                this.executePendingBindings()
            }
        }
    }

    abstract val itemLayout: Int

    abstract fun bind(binding: VB, data: T, position: Int)

    override fun getItemCount(): Int = datas.size

    fun notifyDataChanged(data: List<T>) {
        if (this.datas.size > 0) {
            this.datas.clear()
        }
        this.datas.addAll(data)
        notifyDataSetChanged()
    }

    fun appendData(data: List<T>) {
        val oldSize = this.datas.size
        this.datas.addAll(data)
        val newSize = this.datas.size
        notifyItemRangeChanged(oldSize, newSize)
    }

    fun removeItem(position: Int) {
        datas.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, datas.size)
    }
}
