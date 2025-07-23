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
    var data: ArrayList<T> = ArrayList()
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
        data[position]?.let {
            holder.binding.run {
                bind(this, it, holder.adapterPosition)
            }
        }
    }
    
    abstract val itemLayout: Int
    
    abstract fun bind(binding: VB, data: T, position: Int)
    
    override fun getItemCount(): Int = data.size
    
    fun notifyDataChanged(data: List<T>) {
        if (this.data.size > 0) {
            this.data.clear()
        }
        this.data.addAll(data)
        notifyDataSetChanged()
    }
    
    fun appendData(data: List<T>) {
        val oldSize = this.data.size
        this.data.addAll(data)
        val newSize = this.data.size
        notifyItemRangeChanged(oldSize, newSize)
    }
    
    fun removeItem(position: Int) {
        data.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, data.size)
    }
}
