package com.luizeduardobrandao.obra.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageButton
import androidx.annotation.Px
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilitário para manipulação de arquivos de imagem no app.
 *
 * Contém funções para:
 *  - Criar um arquivo temporário via FileProvider (necessário para câmera).
 *  - Ler bytes de uma Uri.
 *  - Detectar o MIME type de uma Uri.
 *  - Salvar imagem no MediaStore (galeria do usuário).
 */

object FileUtils {

    /**
     * Cria uma URI temporária usando FileProvider.
     * Essa URI será usada pela câmera para salvar a foto capturada.
     */
    fun createTempImageUri(context: Context): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        // Cria o arquivo temporário
        val file = File.createTempFile(
            imageFileName, /* prefixo */
            ".jpg",        /* sufixo */
            storageDir     /* diretório */
        )

        // Retorna a URI via FileProvider (definido no Manifest)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    fun createTempImageUri2(ctx: Context): Uri {
        val ts = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$ts.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ObraApp")
        }
        return ctx.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: error("Falha ao criar URI de imagem temporária")
    }

    fun finalizePendingImage(ctx: Context, uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        ctx.contentResolver.update(uri, values, null, null)
    }

    /**
     * Lê os bytes de uma Uri (imagem selecionada da galeria ou foto capturada).
     */
    fun detectMime(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "image/jpeg"
    }

    /**
     * Salva a imagem no MediaStore (galeria do dispositivo).
     *
     * @param context Contexto da aplicação
     * @param bytes ByteArray da imagem
     * @param mime Tipo MIME (ex: "image/jpeg")
     * @param displayName Nome do arquivo (ex: "nota123.jpg")
     * @return true se salvou com sucesso, false caso contrário
     */
    fun saveImageToMediaStore(
        context: Context,
        bytes: ByteArray,
        mime: String,
        displayName: String
    ): Boolean {
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return try {
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(bytes)
                    outputStream.flush()
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // LEIA OS BYTES DE UMA URI (galeria/câmera)
    fun readBytes(context: Context, uri: Uri): ByteArray {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir inputStream para $uri" }
            return input.readBytes()
        }
    }

    // HÁ APP DE CÂMERA DISPONÍVEL?
    fun hasCameraApp(context: Context): Boolean {
        val captureIntent = android.content.Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val pm = context.packageManager
        return captureIntent.resolveActivity(pm) != null
    }
}

/**
 * Faz o insetEnd do Toolbar igual ao insetStart, opcionalmente
 * compensando o paddingEnd do actionView (se ele tiver padding).
 */

private fun MaterialToolbar.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()

/**
 * Deixa o inset da borda direita (ações) igual ao “respiro” percebido do lado esquerdo.
 * Chama após o primeiro layout para não ser sobrescrito pelo MaterialToolbar (M3 + titleCentered).
 */
fun MaterialToolbar.syncEndInsetSymmetric(@Px fallbackDp: Int = 16) {
    doOnLayout {
        // tenta usar padding do botão de navegação; se não achar, cai em 16dp.
        val navBtn = (0 until childCount)
            .asSequence()
            .map { getChildAt(it) }
            .filterIsInstance<ImageButton>()
            .firstOrNull()
        val leftBreath = navBtn?.paddingStart ?: dpToPx(fallbackDp)

        contentInsetEndWithActions = leftBreath
    }
}

// ───────────────────────────── PDF e CSV Utils ─────────────────────────────

/**
 * Salva um PDF na pasta "Download/ObraApp" do dispositivo e retorna a Uri do arquivo salvo.
 *
 * • API 29+ (scoped storage): usa MediaStore.Downloads com RELATIVE_PATH.
 * • API 28-: grava em Download/ObraApp e registra no MediaStore para aparecer em apps de arquivos.
 *
 * @param context Context
 * @param bytes Conteúdo do PDF
 * @param displayName Nome do arquivo (ex: "resumo_obra_123_2025-09-01.pdf")
 * @return Uri do arquivo salvo (ou null em caso de falha)
 */
fun savePdfToDownloads(context: Context, bytes: ByteArray, displayName: String): Uri? {
    val resolver = context.contentResolver
    val mime = "application/pdf"

    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ → scoped storage
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                // Pasta Download/ObraApp
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + "ObraApp"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null

            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            }

            // Finaliza pendência para tornar o arquivo visível
            val done = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)

            uri
        } else {
            // Android 9 ou anterior → grava direto no FS público
            val downloads =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, "ObraApp").apply { if (!exists()) mkdirs() }
            val file = File(dir, displayName)

            FileOutputStream(file).use { out ->
                out.write(bytes)
                out.flush()
            }

            // Registra no MediaStore para aparecer em apps de arquivos
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, file.absolutePath) // legado
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
            }
            val uri = resolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            ) ?: Uri.fromFile(file)

            uri
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Salva um CSV em "Download/ObraApp" e retorna a Uri.
 */
//fun saveCsvToDownloads(context: Context, bytes: ByteArray, displayName: String): Uri? {
//    val resolver = context.contentResolver
//    val mime = "text/csv"
//
//    return try {
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
//            val values = ContentValues().apply {
//                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
//                put(MediaStore.Downloads.MIME_TYPE, mime)
//                put(
//                    MediaStore.Downloads.RELATIVE_PATH,
//                    Environment.DIRECTORY_DOWNLOADS + File.separator + "ObraApp"
//                )
//                put(MediaStore.Downloads.IS_PENDING, 1)
//            }
//
//            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
//                ?: return null
//
//            resolver.openOutputStream(uri)?.use { out ->
//                out.write(bytes)
//                out.flush()
//            }
//
//            val done = ContentValues().apply {
//                put(MediaStore.Downloads.IS_PENDING, 0)
//            }
//            resolver.update(uri, done, null, null)
//            uri
//        } else {
//            val downloads =
//                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//            val dir = File(downloads, "ObraApp").apply { if (!exists()) mkdirs() }
//            val file = File(dir, displayName)
//
//            FileOutputStream(file).use { out ->
//                out.write(bytes)
//                out.flush()
//            }
//
//            val values = ContentValues().apply {
//                put(MediaStore.MediaColumns.DATA, file.absolutePath)
//                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
//                put(MediaStore.MediaColumns.MIME_TYPE, mime)
//            }
//            val uri = resolver.insert(
//                MediaStore.Files.getContentUri("external"),
//                values
//            ) ?: Uri.fromFile(file)
//
//            uri
//        }
//    } catch (e: Exception) {
//        e.printStackTrace()
//        null
//    }
//}