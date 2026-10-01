package com.example.petfeeding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Renders the variable-length list of pet cards on the Pets page. All feeding/edit
 * actions are delegated back to the host via callbacks so the Activity owns the dialogs.
 */
class PetCardAdapter(
    private var pets: List<FeedingStore.Pet>,
    private val callbacks: Callbacks
) : RecyclerView.Adapter<PetCardAdapter.CardVH>() {

    interface Callbacks {
        fun onFeedToggle(index: Int)
        fun onPickIcon(index: Int)
        fun onRename(index: Int, name: String)
        fun onHistory(index: Int)
        fun onCalendar(index: Int)
        fun onSettings(index: Int)
        fun onDelete(index: Int)
        fun canDelete(): Boolean
    }

    private var boundRecycler: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        boundRecycler = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (boundRecycler === recyclerView) boundRecycler = null
    }

    fun submit(newPets: List<FeedingStore.Pet>) {
        pets = newPets
        val rv = boundRecycler
        // If the RecyclerView is mid-layout/scroll (e.g. a focus-loss callback fired
        // during a previous update), defer the refresh to avoid IllegalStateException.
        if (rv != null && (rv.isComputingLayout || rv.scrollState != RecyclerView.SCROLL_STATE_IDLE)) {
            rv.post { notifyDataSetChanged() }
        } else {
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.pet_card, parent, false)
        return CardVH(v)
    }

    override fun getItemCount(): Int = pets.size

    override fun onBindViewHolder(holder: CardVH, position: Int) {
        holder.bind(pets[position])
    }

    inner class CardVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root = itemView
        private val icon = itemView.findViewById<TextView>(R.id.icon)
        private val name = itemView.findViewById<EditText>(R.id.name)
        private val feed = itemView.findViewById<Button>(R.id.feed)
        private val last = itemView.findViewById<TextView>(R.id.last)
        private val interval = itemView.findViewById<TextView>(R.id.interval)
        private val badge = itemView.findViewById<View>(R.id.badge)
        private val hist = itemView.findViewById<Button>(R.id.hist)
        private val calendar = itemView.findViewById<Button>(R.id.calendar)
        private val settings = itemView.findViewById<Button>(R.id.settings)
        private val delete = itemView.findViewById<TextView>(R.id.delete)

        fun bind(pet: FeedingStore.Pet) {
            icon.text = pet.icon
            if (name.text.toString() != pet.name) name.setText(pet.name)

            val lf = pet.lastFed()
            last.text = if (lf != null) "Last fed:\n${FeedingStore.formatFull(lf)}" else "Not fed yet"

            val fedToday = pet.fedToday()
            root.setBackgroundResource(if (fedToday) R.drawable.card_fed else R.drawable.card_bg)
            badge.visibility = if (fedToday) View.VISIBLE else View.GONE
            feed.text = if (fedToday) "✓ Fed — tap to undo"
                else feed.context.getString(R.string.feed)

            hist.text = "History (${pet.history.size})"
            interval.text = if (pet.intervalDays > 0) "Reminder: every ${pet.intervalDays}d"
                else "Reminder: off"
            delete.visibility = if (callbacks.canDelete()) View.VISIBLE else View.INVISIBLE

            icon.setOnClickListener { callbacks.onPickIcon(bindingAdapterPosition) }
            feed.setOnClickListener { callbacks.onFeedToggle(bindingAdapterPosition) }
            hist.setOnClickListener { callbacks.onHistory(bindingAdapterPosition) }
            calendar.setOnClickListener { callbacks.onCalendar(bindingAdapterPosition) }
            settings.setOnClickListener { callbacks.onSettings(bindingAdapterPosition) }
            delete.setOnClickListener { callbacks.onDelete(bindingAdapterPosition) }
            name.setOnFocusChangeListener { _, hasFocus ->
                val pos = bindingAdapterPosition
                // Guard against a late focus-loss after a delete/reorder: only rename
                // when the position is still valid AND the text actually changed. This
                // avoids writing stale text onto whatever pet now occupies this slot.
                if (!hasFocus && pos != RecyclerView.NO_POSITION && pos in pets.indices &&
                    name.text.toString() != pets[pos].name
                ) {
                    callbacks.onRename(pos, name.text.toString())
                }
            }
        }
    }
}
