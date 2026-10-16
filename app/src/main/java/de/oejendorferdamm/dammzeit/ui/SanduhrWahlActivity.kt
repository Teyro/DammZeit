package de.oejendorferdamm.dammzeit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.data.Speicher

/** Vom Sanduhr-Widget: farbige Sanduhren zum Antippen – stellt die Zeit ein und startet. */
class SanduhrWahlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Speicher.init(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Akzent)) {
                VorbildRaster(faktor = 1f) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                            .clickable(remember { MutableInteractionSource() }, null) { finish() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.shadow(24.dp, RoundedCornerShape(32.dp)).clip(RoundedCornerShape(32.dp)).background(Karte)
                                .clickable(remember { MutableInteractionSource() }, null) {}.padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Sanduhr wählen", color = Text1, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.size(40.dp))
                                RundKnopf("Schließen", { finish() }, groesse = 72.dp, farbe = Flaeche) { SymbolBild(Symbol.SCHLIESSEN, Text1, Modifier.size(30.dp)) }
                            }
                            Spacer(Modifier.height(28.dp))
                            SANDUHREN.chunked(4).forEach { reihe ->
                                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                                    reihe.forEach { z -> SandKnopf(z, gewaehlt = false) { zeitStarten(this@SanduhrWahlActivity, z.minuten * 60_000L); finish() } }
                                }
                                Spacer(Modifier.height(26.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
