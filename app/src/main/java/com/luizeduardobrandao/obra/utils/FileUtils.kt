package com.luizeduardobrandao.obra.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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

    // PRECISO PEDIR WRITE_EXTERNAL_STORAGE? (APIs <= 28)
    fun shouldRequestLegacyWrite(): Boolean {
        return android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P
    }
}