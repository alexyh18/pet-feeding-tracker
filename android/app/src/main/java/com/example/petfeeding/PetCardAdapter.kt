package com.example.petfeeding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        fun onEditName(index: Int)
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
        private val name = itemView.findViewById<TextView>(R.id.name)
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
            name.text = pet.name
            // A small colored dot in the pet's calendar color, to the left of the name.
            name.setTextColor(0xFF5A4A52.toInt())
            name.setCompoundDrawablesWithIntrinsicBounds(makeDot(pet.color), null, null, null)

            val lf = pet.lastFed()
            last.text = if (lf != null) "Last fed:\n${FeedingStore.formatFull(lf)}" else "Not fed yet"

            val fedToday = pet.fedToday()
            root.setBackgroundResource(if (fedToday) R.drawable.card_fed else R.drawable.card_bg)
            badge.visibility = if (fedToday) View.VISIBLE else View.GONE
            feed.text = if (fedToday) "✓ Fed — tap to undo"
                else feed.context.getString(R.string.feed)

            hist.text = "📖 ${pet.history.size}"
            interval.text = if (pet.intervalDays > 0) "Reminder: every ${pet.intervalDays}d"
                else "Reminder: off"
            delete.visibility = if (callbacks.canDelete()) View.VISIBLE else View.INVISIBLE

            icon.setOnClickListener { callbacks.onPickIcon(bindingAdapterPosition) }
            feed.setOnClickListener { callbacks.onFeedToggle(bindingAdapterPosition) }
            hist.setOnClickListener { callbacks.onHistory(bindingAdapterPosition) }
            calendar.setOnClickListener { callbacks.onCalendar(bindingAdapterPosition) }
            settings.setOnClickListener { callbacks.onSettings(bindingAdapterPosition) }
            delete.setOnClickListener { callbacks.onDelete(bindingAdapterPosition) }
            name.setOnClickListener { callbacks.onEditName(bindingAdapterPosition) }
        }

        private fun makeDot(color: Int): android.graphics.drawable.Drawable {
            val size = (12 * itemView.resources.displayMetrics.density).toInt()
            val d = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
                setSize(size, size)
            }
            d.setBounds(0, 0, size, size)
            return d
        }
    }
}
