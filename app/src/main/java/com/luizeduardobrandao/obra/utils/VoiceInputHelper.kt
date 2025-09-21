package com.luizeduardobrandao.obra.utils

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.R
import java.util.Locale

/**
 * Helper para ditado por voz (fala -> texto) usando RecognizerIntent.
 *
 * Uso típico no Fragment:
 *   private lateinit var voiceHelper: VoiceInputHelper
 *   ...
 *   voiceHelper = VoiceInputHelper(this, binding.etProblem, binding.tilProblem) { hasChosenType }
 *   voiceHelper.attach()
 *
 * - Se [canStartVoiceInput] retornar false (ex.: ainda não escolheu o tipo), mostra um Toast.
 * - Por padrão, concatena o texto reconhecido ao conteúdo atual do EditText (appendRecognizedText=true).
 */
class VoiceInputHelper(
    private val fragment: Fragment,
    private val etTarget: TextInputEditText,
    private val tilContainer: TextInputLayout,
    private val canStartVoiceInput: () -> Boolean,
    private val appendRecognizedText: Boolean = true
) {
    private lateinit var requestAudioPermLauncher: ActivityResultLauncher<String>
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    fun attach() {
        registerLaunchers()
        // Clique no ícone do TextInputLayout (endIcon)
        tilContainer.setEndIconOnClickListener {
            if (!canStartVoiceInput()) {
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.ia_voice_choose_type_first),
                    Toast.LENGTH_SHORT
                ).show()
                return@setEndIconOnClickListener
            }
            startVoiceInput()
        }
    }

    private fun registerLaunchers() {
        // Permissão de microfone
        requestAudioPermLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startVoiceInput()
            } else {
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.ia_voice_perm_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Resultado da atividade de reconhecimento de voz
        speechLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = list?.firstOrNull()?.trim().orEmpty()
            if (recognized.isNotEmpty()) applyRecognizedText(recognized)
        }
    }

    private fun applyRecognizedText(recognized: String) {
        val old = etTarget.text?.toString().orEmpty()
        val newText = if (appendRecognizedText && old.isNotBlank()) "$old $recognized" else recognized
        etTarget.setText(newText)
        etTarget.setSelection(newText.length)
    }

    private fun startVoiceInput() {
        // Checa permissão de áudio
        val hasAudio = ActivityCompat.checkSelfPermission(
            fragment.requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            requestAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        // Monta intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, fragment.getString(R.string.ia_voice_prompt))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        // Verifica provedor
        val pm = fragment.requireContext().packageManager
        if (intent.resolveActivity(pm) == null) {
            Toast.makeText(
                fragment.requireContext(),
                fragment.getString(R.string.ia_voice_not_available),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        speechLauncher.launch(intent)
    }
}