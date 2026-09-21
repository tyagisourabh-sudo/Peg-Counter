package com.example.pegcounter

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private var type = Calc.BEER
    private var spiritMl = 30
    private val settings by lazy { getSharedPreferences("peg_settings", MODE_PRIVATE) }
    private val pegButtons = mutableListOf<MaterialButton>()

    private lateinit var beerRow: View
    private lateinit var beerMlInput: EditText
    private lateinit var beerAbvInput: EditText
    private lateinit var spiritAbvInput: EditText
    private lateinit var spiritRow: View
    private lateinit var sizeLabel: TextView
    private lateinit var sizeGroup: MaterialButtonToggleGroup
    private lateinit var pegsText: TextView
    private lateinit var unitsText: TextView
    private lateinit var weekText: TextView
    private lateinit var weekProgress: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        beerRow = findViewById(R.id.beerRow)
        beerMlInput = findViewById(R.id.beerMl)
        beerAbvInput = findViewById(R.id.beerAbv)
        spiritAbvInput = findViewById(R.id.spiritAbv)
        spiritRow = findViewById(R.id.spiritRow)
        sizeLabel = findViewById(R.id.sizeLabel)
        sizeGroup = findViewById(R.id.sizeGroup)
        pegsText = findViewById(R.id.pegsText)
        unitsText = findViewById(R.id.unitsText)
        weekText = findViewById(R.id.weekText)
        weekProgress = findViewById(R.id.weekProgress)

        // remember what you typed last time
        beerMlInput.setText(settings.getString("beer_ml", Calc.DEFAULT_BEER_ML.toString()))
        beerAbvInput.setText(settings.getString("beer_abv", Calc.DEFAULT_BEER_ABV.toString()))
        spiritAbvInput.setText(settings.getString("spirit_abv", Calc.DEFAULT_SPIRIT_ABV.toString()))

        buildPegGrid()

        beerMlInput.doAfterTextChanged { remember("beer_ml", it?.toString() ?: ""); refresh() }
        beerAbvInput.doAfterTextChanged { remember("beer_abv", it?.toString() ?: ""); refresh() }
        spiritAbvInput.doAfterTextChanged { remember("spirit_abv", it?.toString() ?: ""); refresh() }

        val typeGroup = findViewById<MaterialButtonToggleGroup>(R.id.typeGroup)
        typeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                type = if (checkedId == R.id.btnBeer) Calc.BEER else Calc.SPIRITS
                val spirits = type == Calc.SPIRITS
                beerRow.visibility = if (spirits) View.GONE else View.VISIBLE
                sizeLabel.visibility = if (spirits) View.VISIBLE else View.GONE
                sizeGroup.visibility = if (spirits) View.VISIBLE else View.GONE
                spiritRow.visibility = if (spirits) View.VISIBLE else View.GONE
                refresh()
            }
        }
        sizeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                spiritMl = if (checkedId == R.id.btnSize60) 60 else 30
                refresh()
            }
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { saveSession() }
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener { clearTicks() }
        findViewById<MaterialButton>(R.id.btnReport).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        refresh()
    }

    private fun buildPegGrid() {
        val grid = findViewById<GridLayout>(R.id.pegGrid)
        val density = resources.displayMetrics.density
        val margin = (4 * density).toInt()
        val height = (76 * density).toInt()

        for (i in 1..9) {
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            b.tag = i.toString()
            b.textSize = 22f
            b.isCheckable = true
            applyPegStyle(b, false)

            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = 0
            lp.height = height
            lp.setMargins(margin, margin, margin, margin)
            b.layoutParams = lp

            b.addOnCheckedChangeListener { button, checked ->
                applyPegStyle(button, checked)
                refresh()
            }
            grid.addView(b)
            pegButtons.add(b)
        }
    }

    private fun applyPegStyle(b: MaterialButton, checked: Boolean) {
        if (checked) {
            b.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
            b.setTextColor(Color.WHITE)
            b.text = "\u2713 " + b.tag
        } else {
            b.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            b.setTextColor(Color.parseColor("#37474F"))
            b.text = b.tag.toString()
        }
    }

    private fun sizeMl(): Int =
        if (type == Calc.BEER) (beerMlInput.text.toString().trim().toIntOrNull() ?: 0) else spiritMl

    private fun abv(): Double {
        val box = if (type == Calc.BEER) beerAbvInput else spiritAbvInput
        return box.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    }

    private fun remember(key: String, value: String) {
        settings.edit().putString(key, value).apply()
    }

    private fun ticked(): Int = pegButtons.count { it.isChecked }

    private fun refresh() {
        val pegs = ticked()
        val perPeg = Calc.unitsPerPeg(sizeMl(), abv())
        val sessionUnits = pegs * perPeg
        val weekTotal = Calc.unitsThisWeek(Store.load(this)) + sessionUnits
        val pct = (weekTotal / Calc.WEEKLY_LIMIT_UNITS * 100).roundToInt()

        pegsText.text = "Pegs ticked: $pegs   (1 peg = %.2f units)".format(perPeg)
        unitsText.text = "This session: %.1f units".format(sessionUnits)
        weekText.text = "This week: %.1f of %.0f units (%d%% of safe limit)".format(
            weekTotal, Calc.WEEKLY_LIMIT_UNITS, pct
        ) + if (pct >= 100) "\nOver the weekly limit" else ""

        val color = Color.parseColor(
            when {
                pct >= 100 -> "#C62828"
                pct >= 60 -> "#EF6C00"
                else -> "#2E7D32"
            }
        )
        weekText.setTextColor(color)
        weekProgress.setIndicatorColor(color)
        weekProgress.setProgressCompat(pct.coerceIn(0, 100), true)
    }

    private fun saveSession() {
        val pegs = ticked()
        if (pegs == 0) {
            Toast.makeText(this, "Tick at least one peg first", Toast.LENGTH_SHORT).show()
            return
        }
        val ml = sizeMl()
        val strength = abv()
        if (ml <= 0 || strength <= 0.0 || strength > 100.0) {
            Toast.makeText(this, "Enter a valid size (ml) and alcohol %", Toast.LENGTH_SHORT).show()
            return
        }
        val list = Store.load(this)
        list.add(
            Entry(System.currentTimeMillis(), type, ml, strength, pegs, pegs * Calc.unitsPerPeg(ml, strength))
        )
        Store.save(this, list)
        clearTicks()
        Toast.makeText(this, "Session saved", Toast.LENGTH_SHORT).show()
    }

    private fun clearTicks() {
        pegButtons.forEach { it.isChecked = false }
        refresh()
    }
}
