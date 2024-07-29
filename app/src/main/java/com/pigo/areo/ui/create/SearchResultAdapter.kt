package com.pigo.areo.ui.create

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pigo.areo.data.model.SearchResult
import com.pigo.areo.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val onItemClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder>() {

    private val items = mutableListOf<SearchResult>()

    fun setItems(newItems: List<SearchResult>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun clearItems() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(items[position], onItemClick, ::clearItems)
    }

    override fun getItemCount(): Int = minOf(items.size, 5)

    class SearchResultViewHolder(private val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchResult, onItemClick: (SearchResult) -> Unit, clearItems: () -> Unit) {
            binding.searchResult = item
            binding.root.setOnClickListener {
                onItemClick(item)
                clearItems()
            }
            binding.executePendingBindings()
        }
    }
}
