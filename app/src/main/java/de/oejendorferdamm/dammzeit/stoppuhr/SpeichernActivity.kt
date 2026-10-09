package de.oejendorferdamm.dammzeit.stoppuhr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.MainActivity
import de.oejendorferdamm.dammzeit.ui.Akzent
import de.oejendorferdamm.dammzeit.ui.GrosserKnopf
import de.oejendorferdamm.dammzeit.ui.HellerGrund
import de.oejendorferdamm.dammzeit.ui.Krone
import de.oejendorferdamm.dammzeit.ui.SpeichernDialog
import de.oejendorferdamm.dammzeit.ui.Text1
import de.oejendorferdamm.dammzeit.ui.VorbildRaster

/** Vom Stoppuhr-Widget: Zeit mit Name und Challenge speichern, danach Platz zeigen. */
class SpeichernActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StoppuhrSpeicher.init(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Akzent)) {
                var ergebnis by remember { mutableStateOf<Pair<Bestzeit, Int>?>(null) }
                VorbildRaster(faktor = 1f) {
                    val e = ergebnis
                    if (e == null) {
                        SpeichernDialog(onAbbrechen = { finish() }) { b, platz -> ergebnis = b to platz }
                    } else {
                        val (b, platz) = e
                        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.width(900.dp).clip(RoundedCornerShape(36.dp)).background(HellerGrund).padding(44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (platz == 1) Krone(Modifier.size(width = 170.dp, height = 115.dp))
                                Text(
                                    when (platz) { 1 -> "Platz 1 – Bestzeit!"; 2, 3 -> "Platz $platz – aufs Treppchen!"; else -> "Platz $platz" },
                                    color = Text1, fontSize = 52.sp, fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(10.dp))
                                Text("${b.name} · ${formatStoppzeit(b.ms, true)}" + if (b.challenge.isNotBlank()) " · ${b.challenge}" else "", color = Text1, fontSize = 34.sp)
                                Spacer(Modifier.height(30.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    GrosserKnopf("Fertig", Color.White, Text1, Modifier.width(300.dp).height(88.dp)) { finish() }
                                    GrosserKnopf("Bestenliste", Akzent, Color.White, Modifier.width(360.dp).height(88.dp)) {
                                        startActivity(
                                            Intent(this@SpeichernActivity, MainActivity::class.java)
                                                .putExtra(MainActivity.EXTRA_BESTENLISTE, b.challenge)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                        Ansicht.neu.value = b.id
                                        finish()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
