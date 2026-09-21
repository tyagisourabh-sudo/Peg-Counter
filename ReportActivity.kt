package com.example.pegcounter

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val report = buildReport()
        findViewById<TextView>(R.id.reportText).text = report

        findViewById<MaterialButton>(R.id.shareButton).setOnClickListener {
            val send = Intent(Intent.ACTION_SEND)
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_TEXT, report)
            startActivity(Intent.createChooser(send, "Share weekly report"))
        }
    }

    private fun buildReport(): String {
        val entries = Store.load(this)
        if (entries.isEmpty()) {
            return "No sessions saved yet.\n\nGo back, tick your pegs and press \"Save session\"."
        }

        val dayFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val rangeFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val limit = Calc.WEEKLY_LIMIT_UNITS
        val sb = StringBuilder()
        sb.append("Weekly limit: ${limit.toInt()} units (1 unit = 10 ml pure alcohol)\n\n")

        val byWeek = entries.groupBy { Calc.weekStart(it.time) }
        for (ws in byWeek.keys.sortedDescending()) {
            val list = byWeek.getValue(ws).sortedBy { it.time }
            val cal = Calendar.getInstance()
            cal.timeInMillis = ws
            cal.add(Calendar.DAY_OF_YEAR, 6)

            val pegs = list.sumOf { it.pegs }
            val units = list.sumOf { it.units }
            val pct = (units / limit * 100).roundToInt()

            sb.append("WEEK ${rangeFmt.format(Date(ws))} to ${rangeFmt.format(cal.time)}\n")
            sb.append("Total pegs: $pegs\n")
            sb.append("Total units: %.1f\n".format(units))
            sb.append("Safe limit used: $pct%\n")
            if (units > limit) {
                sb.append("Status: OVER limit by %.1f units\n".format(units - limit))
            } else {
                sb.append("Status: within limit (%.1f units left)\n".format(limit - units))
            }
            sb.append("\n")
            for (e in list) {
                val kind = if (e.type == Calc.BEER) "Beer" else "Spirits"
                sb.append(dayFmt.format(Date(e.time)))
                sb.append(": $kind ${e.sizeMl} ml, ")
                sb.append("%.1f".format(e.abv))
                sb.append("% x ${e.pegs} pegs = ")
                sb.append("%.1f units\n".format(e.units))
            }
            sb.append("\n----------------------\n\n")
        }
        return sb.toString()
    }
}
