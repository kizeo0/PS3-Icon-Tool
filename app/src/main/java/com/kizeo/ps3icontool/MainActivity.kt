package com.kizeo.ps3icontool

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.kizeo.ps3icontool.ui.theme.PS3IconToolTheme
import kotlinx.coroutines.*
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

// --- COLORES ---
val FondoNegro = Color(0xFF0F0F13)
val GrisOscuro = Color(0xFF1E1E26)
val AzulCarpeta = Color(0xFF5082E6)
val AmarilloCarpeta = Color(0xFFE6C650)
val RojoCarpeta = Color(0xFFE65050)
val AmarilloPS3 = Color(0xFFF1C40F)
val VerdeCheck = Color(0xFF2ECC71)
val VerdeLima = Color(0xFF32CD32)
val Celeste = Color(0xFF00E5FF)

val bitmapCache = mutableMapOf<String, Bitmap>()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PS3IconToolTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = FondoNegro) {
                    PantallaPrincipal()
                }
            }
        }
    }
}

@Composable
fun PantallaPrincipal() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollStateTop = rememberScrollState()

    val prefs = remember { context.getSharedPreferences("config", Context.MODE_PRIVATE) }
    var ip by remember { mutableStateOf(prefs.getString("last_ip", "") ?: "") }

    var carpetas by remember { mutableStateOf(listOf<String>()) }
    var carpetaSeleccionada by remember { mutableStateOf("") }
    var archivosEnPS3 by remember { mutableStateOf(setOf<String>()) }
    var modoActual by remember { mutableStateOf("ICON0.PNG") }
    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }
    var originalBmp by remember { mutableStateOf<Bitmap?>(null) }
    var cargando by remember { mutableStateOf(false) }

    var nombreJuego by remember { mutableStateOf("") }
    var nombreOriginal by remember { mutableStateOf("") }

    var mostrarDialogoMusica by remember { mutableStateOf(false) }
    var mostrarDialogoVideo by remember { mutableStateOf(false) }

    val gestorGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                cargando = true
                previewBmp = procesarImagen(context, it, modoActual)
                cargando = false
            }
        }
    }

    if (mostrarDialogoMusica) {
        ConfirmacionBorradoDialog(onConfirm = { mostrarDialogoMusica = false; scope.launch { if(borrarArchivo(ip, carpetaSeleccionada, "SND0.AT3", context)) archivosEnPS3 -= "SND0.AT3" } }, onDismiss = { mostrarDialogoMusica = false })
    }

    if (mostrarDialogoVideo) {
        ConfirmacionBorradoDialog(onConfirm = { mostrarDialogoVideo = false; scope.launch { if(borrarArchivo(ip, carpetaSeleccionada, "ICON1.PAM", context)) archivosEnPS3 -= "ICON1.PAM" } }, onDismiss = { mostrarDialogoVideo = false })
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {

        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(scrollStateTop)) {
            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.Black, RoundedCornerShape(12.dp)).border(2.dp, GrisOscuro, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (cargando) { CircularProgressIndicator(color = AmarilloPS3) } else {
                    previewBmp?.let { bmp ->
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit)
                        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(0.7f)).clickable { guardarImagenEnGaleria(context, bmp, modoActual) }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("Descargar", color = Color.White.copy(0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } ?: Text("SIN IMAGEN", color = Color.White.copy(0.2f), fontWeight = FontWeight.Bold)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = nombreJuego, onValueChange = { nombreJuego = it },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    placeholder = { Text("Nombre del juego...", color = Color.Gray, fontSize = 13.sp) },
                    singleLine = true, textStyle = LocalTextStyle.current.copy(color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 14.sp),
                    trailingIcon = { if(nombreJuego.isNotEmpty()) { IconButton(onClick = { nombreJuego = "" }) { Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(18.dp)) } } },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmarilloPS3, unfocusedBorderColor = GrisOscuro, cursorColor = AmarilloPS3), shape = RoundedCornerShape(8.dp)
                )
                Text("Designed by KiZeo", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { previewBmp = originalBmp; nombreJuego = nombreOriginal }, modifier = Modifier.size(50.dp)) { Icon(Icons.Default.Refresh, "Reset", tint = Celeste, modifier = Modifier.size(45.dp)) }

                Row(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val existe = archivosEnPS3.contains(modoActual)
                    Button(onClick = { scope.launch { if (borrarArchivo(ip, carpetaSeleccionada, modoActual, context)) { archivosEnPS3 -= modoActual; previewBmp = null; originalBmp = null } } }, enabled = existe && carpetaSeleccionada.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D1F1F), disabledContainerColor = Color.DarkGray.copy(0.2f)), modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("BORRAR", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(onClick = { gestorGaleria.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1.4f).height(42.dp), shape = RoundedCornerShape(8.dp)) { Text(if (existe) "CAMBIAR" else "CREAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerdeLima) }
                }

                val hayCambios = (previewBmp != originalBmp && previewBmp != null) || (nombreJuego != nombreOriginal && nombreJuego.isNotEmpty())
                val animScale by animateFloatAsState(if (hayCambios) 1.15f else 1f)
                IconButton(onClick = { scope.launch { cargando = true; var exito = true
                    if (previewBmp != originalBmp && previewBmp != null) exito = subirArchivo(ip, carpetaSeleccionada, modoActual, previewBmp!!, context)
                    if (nombreJuego != nombreOriginal && nombreJuego.isNotEmpty() && exito) {
                        if (modificarNombreSFO(ip, carpetaSeleccionada, nombreJuego)) {
                            nombreOriginal = nombreJuego
                        } else exito = false
                    }
                    if (exito) { archivosEnPS3 += modoActual; originalBmp = previewBmp }; cargando = false } }, enabled = hayCambios, modifier = Modifier.size(55.dp)) {
                    Icon(Icons.Default.CheckCircle, "Aplicar", tint = if (hayCambios) VerdeCheck else Color.White.copy(0.1f), modifier = Modifier.size(50.dp).scale(animScale))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BotonLimpieza("QUITAR MÚSICA", archivosEnPS3.contains("SND0.AT3"), Modifier.weight(1f)) { mostrarDialogoMusica = true }
                BotonLimpieza("QUITAR VIDEO", archivosEnPS3.contains("ICON1.PAM"), Modifier.weight(1f)) { mostrarDialogoVideo = true }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { modoActual = "ICON0.PNG"; scope.launch { val img = obtenerConCache(ip, carpetaSeleccionada, "ICON0.PNG"); previewBmp = img; originalBmp = img } }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = if(modoActual == "ICON0.PNG") AmarilloPS3 else AmarilloPS3.copy(0.12f)), shape = RoundedCornerShape(8.dp)) { Text("ICONO", color = if(modoActual == "ICON0.PNG") Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                val esModoPic = modoActual.startsWith("PIC")
                Box(modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp)).background(if (esModoPic) AmarilloPS3 else AmarilloPS3.copy(0.12f)).pointerInput(carpetaSeleccionada) { detectTapGestures(onTap = { if (!esModoPic) { modoActual = "PIC1.PNG"; scope.launch { val img = obtenerConCache(ip, carpetaSeleccionada, "PIC1.PNG"); previewBmp = img; originalBmp = img } } }, onLongPress = { if (carpetaSeleccionada.isNotEmpty()) { val ciclo = listOf("PIC1.PNG", "PIC0.PNG", "PIC2.PNG"); modoActual = ciclo[(ciclo.indexOf(modoActual) + 1) % ciclo.size]; scope.launch { val img = obtenerConCache(ip, carpetaSeleccionada, modoActual); previewBmp = img; originalBmp = img } } }) }, contentAlignment = Alignment.Center) { Text(when(modoActual) { "PIC1.PNG" -> "PORTADA"; "PIC0.PNG" -> "PIC0"; "PIC2.PNG" -> "PIC2"; else -> "PORTADA" }, color = if(esModoPic) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }

            OutlinedTextField(
                value = ip, onValueChange = { ip = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                placeholder = { Text("IP de tu PS3...", color = Color.Gray, fontSize = 13.sp) }, label = { Text("IP PS3", color = Color.Gray, fontSize = 11.sp) },
                trailingIcon = { IconButton(onClick = { scope.launch { cargando = true; val res = conectarFTP(ip); if (res != null) { carpetas = res; prefs.edit().putString("last_ip", ip).apply() } else { Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show() }; cargando = false } }) { Icon(Icons.Default.Send, null, tint = VerdeCheck) } },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AzulCarpeta), shape = RoundedCornerShape(8.dp)
            )
        }

        // --- LISTA DE JUEGOS (4 COLUMNAS) ---
        Text("LISTA DE JUEGOS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().height(250.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(carpetas) { item ->
                val sel = item == carpetaSeleccionada
                val colorIcono = when {
                    item.contains("NP") -> AzulCarpeta
                    item.startsWith("B") -> AmarilloCarpeta
                    else -> RojoCarpeta
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                    carpetaSeleccionada = item; modoActual = "ICON0.PNG"
                    scope.launch { cargando = true; archivosEnPS3 = escanearArchivos(ip, item); val img = obtenerConCache(ip, item, "ICON0.PNG"); previewBmp = img; originalBmp = img; val n = obtenerNombreSFO(ip, item); nombreJuego = n; nombreOriginal = n; cargando = false }
                }) {
                    Box(modifier = Modifier.size(70.dp).background(colorIcono, RoundedCornerShape(10.dp)).border(if(sel) 2.dp else 0.dp, Color.White, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.List, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Text(text = item, fontSize = 8.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp).fillMaxWidth())
                }
            }
        }
    }
}

// --- LÓGICA DE EDICIÓN SFO SIN RECONSTRUCCIÓN ---

suspend fun obtenerNombreSFO(ip: String, carp: String): String = withContext(Dispatchers.IO) {
    val ftp = FTPClient()
    try {
        ftp.connect(ip, 21); ftp.login("anonymous", ""); ftp.setFileType(FTP.BINARY_FILE_TYPE)
        val stream = ftp.retrieveFileStream("/dev_hdd0/game/$carp/PARAM.SFO")
        val data = stream.readBytes(); stream.close(); ftp.completePendingCommand(); ftp.disconnect()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val kTO = buffer.getInt(0x08); val dTO = buffer.getInt(0x0C); val count = buffer.getInt(0x10)
        for (i in 0 until count) {
            val kO = buffer.getShort(0x14 + (i * 16)).toInt() and 0xFFFF
            val dO = buffer.getInt(0x14 + (i * 16) + 0x0C)
            val kB = StringBuilder(); var k = kTO + kO
            while (k < data.size && data[k] != 0.toByte()) { kB.append(data[k].toInt().toChar()); k++ }
            if (kB.toString() == "TITLE") {
                val s = dTO + dO; var e = s; while (e < data.size && data[e] != 0.toByte()) e++
                return@withContext String(data, s, e - s, Charsets.UTF_8)
            }
        }
        "Sin Nombre"
    } catch (e: Exception) { "Error SFO" }
}

suspend fun modificarNombreSFO(ip: String, carp: String, nuevoNombre: String): Boolean = withContext(Dispatchers.IO) {
    val ftp = FTPClient()
    try {
        ftp.connect(ip, 21); ftp.login("anonymous", ""); ftp.setFileType(FTP.BINARY_FILE_TYPE)
        val inputStream = ftp.retrieveFileStream("/dev_hdd0/game/$carp/PARAM.SFO")
        val data = inputStream.readBytes(); inputStream.close(); ftp.completePendingCommand()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val keyTableOffset = buffer.getInt(0x08)
        val dataTableOffset = buffer.getInt(0x0C)
        val count = buffer.getInt(0x10)

        for (i in 0 until count) {
            val kO = buffer.getShort(0x14 + (i * 16)).toInt() and 0xFFFF
            val maxLen = buffer.getInt(0x14 + (i * 16) + 0x08) // MAX Data Length (El espacio asignado, ej: 128)
            val dO = buffer.getInt(0x14 + (i * 16) + 0x0C)

            val kB = StringBuilder(); var k = keyTableOffset + kO
            while (k < data.size && data[k] != 0.toByte()) { kB.append(data[k].toInt().toChar()); k++ }
            val key = kB.toString()

            // Filtro: Editar campos de título pero NUNCA el TITLE_ID
            if (key.startsWith("TITLE") && key != "TITLE_ID") {
                val startData = dataTableOffset + dO
                val nameBytes = nuevoNombre.toByteArray(Charsets.UTF_8)

                // 1. Limpiamos con ceros todo el bloque asignado para que no queden restos del nombre viejo
                for (j in 0 until maxLen) {
                    if (startData + j < data.size) data[startData + j] = 0
                }

                // 2. Escribimos el nuevo nombre sin pasarnos del límite físico del archivo
                for (j in nameBytes.indices) {
                    if (j < maxLen - 1 && (startData + j) < data.size) {
                        data[startData + j] = nameBytes[j]
                    }
                }
            }
        }

        val ok = ftp.storeFile("/dev_hdd0/game/$carp/PARAM.SFO", ByteArrayInputStream(data))
        ftp.disconnect()
        ok
    } catch (e: Exception) { false }
}

// --- RESTO DE FUNCIONES ---

fun guardarImagenEnGaleria(context: Context, bitmap: Bitmap, nombre: String) {
    val nombreArchivo = "${nombre.replace(".", "_")}_${System.currentTimeMillis()}.png"
    try {
        val outputStream: OutputStream?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver; val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo); put(MediaStore.MediaColumns.MIME_TYPE, "image/png"); put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PS3IconTool") }
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues); outputStream = imageUri?.let { resolver.openOutputStream(it) }
        } else { outputStream = FileOutputStream(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), nombreArchivo)) }
        outputStream?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); Toast.makeText(context, "Guardado en Galería", Toast.LENGTH_SHORT).show() }
    } catch (e: Exception) { Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show() }
}

@Composable
fun ConfirmacionBorradoDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Advertencia", color = Color.White, fontWeight = FontWeight.Bold) }, text = { Text("¿Seguro que quieres eliminar este archivo?", color = Color.LightGray) }, containerColor = GrisOscuro, confirmButton = { TextButton(onClick = onConfirm) { Text("SÍ", color = Color.Red, fontWeight = FontWeight.ExtraBold) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("NO", color = Color.White) } })
}

@Composable
fun BotonLimpieza(label: String, activo: Boolean, mod: Modifier, onCli: () -> Unit) {
    Button(onClick = onCli, enabled = activo, modifier = mod.height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252525), disabledContainerColor = Color(0xFF151515)), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text(label, fontSize = 8.sp, color = if(activo) Color.White else Color.DarkGray) }
}

suspend fun procesarImagen(ctx: Context, uri: Uri, modo: String): Bitmap = withContext(Dispatchers.IO) {
    val stream = ctx.contentResolver.openInputStream(uri); val original = BitmapFactory.decodeStream(stream)
    val (w, h) = when (modo) { "ICON0.PNG" -> 320 to 176; "PIC1.PNG" -> 1920 to 1080; "PIC0.PNG" -> 1000 to 560; "PIC2.PNG" -> 310 to 250; else -> 320 to 176 }
    Bitmap.createScaledBitmap(original, w, h, true)
}

suspend fun conectarFTP(ip: String): List<String>? = withContext(Dispatchers.IO) {
    if (ip.isEmpty()) return@withContext null
    val ftp = FTPClient(); try { ftp.connect(ip, 21); ftp.login("anonymous", ""); val dirs = ftp.listDirectories("/dev_hdd0/game/").map { it.name }.filter { !it.startsWith(".") && it.length > 4 }; ftp.disconnect(); dirs.sorted() } catch (e: Exception) { null }
}

suspend fun escanearArchivos(ip: String, carp: String): Set<String> = withContext(Dispatchers.IO) {
    val ftp = FTPClient(); try { ftp.connect(ip, 21); ftp.login("anonymous", ""); val list = ftp.listFiles("/dev_hdd0/game/$carp/").map { it.name }.toSet(); ftp.disconnect(); list } catch (e: Exception) { emptySet() }
}

suspend fun obtenerConCache(ip: String, carp: String, file: String): Bitmap? = withContext(Dispatchers.IO) {
    val key = "$carp-$file"; if (bitmapCache.containsKey(key)) return@withContext bitmapCache[key]
    val ftp = FTPClient(); try { ftp.connect(ip, 21); ftp.login("anonymous", ""); ftp.setFileType(FTP.BINARY_FILE_TYPE); val stream = ftp.retrieveFileStream("/dev_hdd0/game/$carp/$file"); val bmp = BitmapFactory.decodeStream(stream); if (bmp != null) bitmapCache[key] = bmp; ftp.disconnect(); bmp } catch (e: Exception) { null }
}

suspend fun subirArchivo(ip: String, carp: String, nom: String, bmp: Bitmap, ctx: Context): Boolean = withContext(Dispatchers.IO) {
    val ftp = FTPClient(); try { ftp.connect(ip, 21); ftp.login("anonymous", ""); ftp.setFileType(FTP.BINARY_FILE_TYPE); val os = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, os); val ok = ftp.storeFile("/dev_hdd0/game/$carp/$nom", ByteArrayInputStream(os.toByteArray())); ftp.disconnect(); bitmapCache.remove("$carp-$nom"); withContext(Dispatchers.Main) { Toast.makeText(ctx, "¡$nom actualizado!", Toast.LENGTH_SHORT).show() }; ok } catch (e: Exception) { false }
}

suspend fun borrarArchivo(ip: String, carp: String, nom: String, ctx: Context): Boolean = withContext(Dispatchers.IO) {
    val ftp = FTPClient(); try { ftp.connect(ip, 21); ftp.login("anonymous", ""); val ok = ftp.deleteFile("/dev_hdd0/game/$carp/$nom"); ftp.disconnect(); withContext(Dispatchers.Main) { Toast.makeText(ctx, "Archivo eliminado", Toast.LENGTH_SHORT).show() }; ok } catch (e: Exception) { false }
}
