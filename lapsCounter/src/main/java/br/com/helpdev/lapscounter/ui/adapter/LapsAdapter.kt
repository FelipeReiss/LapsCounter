package br.com.helpdev.lapscounter.ui.adapter

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import br.com.helpdev.chronometerlib.Chronometer
import br.com.helpdev.chronometerlib.objects.ObLap
import br.com.helpdev.lapscounter.R

class LapsAdapter(
    private val context: Context,
    private val laps: List<ObLap>,
    private val onLongClick: (Int, ObLap) -> Unit,
    private val removeLastLap: Boolean = true
) : RecyclerView.Adapter<LapsAdapter.ItemHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val inflater = LayoutInflater.from(context)
        return ItemHolder(inflater.inflate(R.layout.item_lap_log, parent, false))
    }

    override fun getItemCount() = if (laps.isEmpty()) 0 else {
        if (removeLastLap) laps.size - 1 else laps.size
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        holder.bind(laps[position], position)
        holder.itemView.setOnLongClickListener {
            onLongClick(position, laps[position])
            true
        }
    }

    class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val numLap: TextView = itemView.findViewById(R.id.numberOfLap_fix)
        private val tvTime: TextView = itemView.findViewById(R.id.chronometer_current_fix)
        private val tvTotalTime: TextView = itemView.findViewById(R.id.chronometer_total_fix)
        private val tvTotalPauseTime: TextView = itemView.findViewById(R.id.chronometer_pause_fix)

        fun bind(obLap: ObLap, lapPosition: Int) {
            numLap.text = itemView.context.getString(R.string.num_lap, String.format("%02d", lapPosition + 1))
            tvTime.text = Chronometer.getFormattedTime(obLap.getRunningTime())
            if (obLap.pausedTime > 0) {
                tvTotalPauseTime.text = Chronometer.getFormattedTime(obLap.pausedTime)
                tvTotalPauseTime.setTextColor(Color.RED)
            } else {
                tvTotalPauseTime.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorSecondaryText))
            }
            tvTotalTime.text = Chronometer.getFormattedTime(obLap.chronometerTime)
        }
    }
}