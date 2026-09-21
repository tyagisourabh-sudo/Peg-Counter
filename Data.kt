package com.example.pegcounter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Entry(
    val time: Long,
    val type: String,
    val sizeMl: Int,
    val abv: Double,
    val pegs: Int,
    val units: Double
)

object Calc {
    const val BEER = "Beer"
    const val SPIRITS = "Spirits"

    // ---- Change these numbers if you want different assumptions ----
    const val DEFAULT_BEER_ML = 330      // starting value in the beer size box
    const val DEFAULT_BEER_ABV = 5.0     // starting value in the beer % box
    const val DEFAULT_SPIRIT_ABV = 40.0  // starting value in the spirits % box
    const val WEEKLY_LIMIT_UNITS = 14.0  // weekly safe limit (UK guideline)
    // 1 unit = 10 ml of pure alcohol

    fun unitsPerPeg(sizeMl: Int, abv: Double): Double = sizeMl * abv / 1000.0

    /** Monday 00:00 of the week containing time t. */
    fun weekStart(t: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = t
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val diff = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
        c.add(Calendar.DAY_OF_YEAR, -diff)
        return c.timeInMillis
    }

    fun unitsThisWeek(entries: List<Entry>): Double {
        val ws = weekStart(System.currentTimeMillis())
        return entries.filter { weekStart(it.time) == ws }.sumOf { it.units }
    }
}

object Store {
    private const val PREF = "peg_store"
    private const val KEY = "entries"

    fun load(ctx: Context): MutableList<Entry> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val type = o.getString("type")
            val defaultAbv = if (type == Calc.BEER) Calc.DEFAULT_BEER_ABV else Calc.DEFAULT_SPIRIT_ABV
            list.add(
                Entry(
                    o.getLong("t"),
                    type,
                    o.getInt("ml"),
                    o.optDouble("abv", defaultAbv),
                    o.getInt("pegs"),
                    o.getDouble("u")
                )
            )
        }
        return list
    }

    fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("t", e.time)
            o.put("type", e.type)
            o.put("ml", e.sizeMl)
            o.put("abv", e.abv)
            o.put("pegs", e.pegs)
            o.put("u", e.units)
            arr.put(o)
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
