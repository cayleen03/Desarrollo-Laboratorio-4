package com.example.lab4

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.lab4.ui.theme.Lab4Theme
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Lab4Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AdivinaJuegoPantalla()
                }
            }
        }
    }
}

@Composable
fun AdivinaJuegoPantalla() {
    val contexto = LocalContext.current
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(contexto) }

    var numeroObjetivo by remember { mutableStateOf(Random.nextInt(0, 101)) }
    var textoEntrada by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("¿Cuál número crees que es?") }
    var intentos by remember { mutableStateOf(3) }
    var tiempo by remember { mutableStateOf(60) }
    var terminado by remember { mutableStateOf(false) }
    var ubicacionActual by remember { mutableStateOf<Location?>(null) }

    // Controlador de permisos
    val pedirPermisoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { aprobado ->
        if (aprobado) {
            locationClient.lastLocation
                .addOnSuccessListener { ubicacion -> ubicacionActual = ubicacion }
        } else {
            Toast.makeText(contexto, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Obtener ubicación al inicio
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationClient.lastLocation.addOnSuccessListener { ubicacion -> ubicacionActual = ubicacion }
        } else {
            pedirPermisoLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Temporizador
    LaunchedEffect(terminado) {
        if (!terminado) {
            while (tiempo > 0) {
                delay(1000)
                tiempo--
            }
            if (tiempo == 0) {
                mensaje = "Tiempo agotado ⏳. Era $numeroObjetivo"
                terminado = true
            }
        }
    }

    // Diseño principal
    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("¡Adivina el número oculto!", style = MaterialTheme.typography.headlineSmall)
        Text("Intentos restantes: $intentos — Tiempo: ${tiempo}s")

        OutlinedTextField(
            value = textoEntrada,
            onValueChange = {
                if (!terminado && intentos > 0) {
                    textoEntrada = it.filter { char -> char.isDigit() }
                }
            },
            label = { Text("Número del 0 al 100") },
            enabled = !terminado && intentos > 0,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val intento = textoEntrada.toIntOrNull()
                if (intento == null) {
                    mensaje = "⚠️ Ingresa un número válido."
                } else {
                    intentos--
                    mensaje = when {
                        intento < numeroObjetivo -> "🔺 Es mayor."
                        intento > numeroObjetivo -> "🔻 Es menor."
                        else -> {
                            terminado = true
                            "🎯 ¡Acertaste! El número era $numeroObjetivo"
                        }
                    }
                    if (intentos == 0 && !terminado) {
                        mensaje = "❌ Sin intentos. El número era $numeroObjetivo"
                        terminado = true
                    }
                }
                textoEntrada = ""
            },
            enabled = !terminado && intentos > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Probar suerte")
        }

        Text(mensaje)

        if (terminado) {
            Button(
                onClick = {
                    numeroObjetivo = Random.nextInt(0, 101)
                    textoEntrada = ""
                    mensaje = "¿Cuál número crees que es?"
                    intentos = 3
                    tiempo = 60
                    terminado = false
                }
            ) {
                Text("Reiniciar juego")
            }
        }

        EnvioUbicacionWhatsApp(ubicacionActual, pedirPermisoLauncher)
    }
}

@Composable
fun EnvioUbicacionWhatsApp(
    ubicacion: Location?,
    pedirPermiso: ActivityResultLauncher<String>
) {
    val contexto = LocalContext.current
    var telefono by remember { mutableStateOf("+507") }
    var mensaje by remember { mutableStateOf("Hola, te comparto mi ubicación:") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Compartir por WhatsApp", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = telefono,
            onValueChange = { telefono = it },
            label = { Text("Número con código") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = mensaje,
            onValueChange = { mensaje = it },
            label = { Text("Mensaje personalizado") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    pedirPermiso.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    Toast.makeText(contexto, "Pidiendo permiso de ubicación", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val loc = ubicacion
                if (loc == null) {
                    Toast.makeText(contexto, "Ubicación aún no disponible", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val link = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                val textoFinal = "$mensaje\n$link"
                val uri = "https://api.whatsapp.com/send?phone=$telefono&text=${Uri.encode(textoFinal)}".toUri()

                contexto.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enviar ubicación")
        }
    }
}
