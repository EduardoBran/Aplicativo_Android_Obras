package com.luizeduardobrandao.obra.ui.snackbar

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.databinding.FragmentSnackbarBinding
import com.luizeduardobrandao.obra.utils.Constants
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Bottom-sheet customizado para mensagens de erro / sucesso / aviso.
 *
 * • Fecha ao tocar fora.
 * • Ícone dinâmico por tipo (usa Constants.SnackType existente).
 * • Visual: card branco; título/mensagem pretos; botões arredondados e coloridos.
 */
class SnackbarFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentSnackbarBinding? = null
    private val binding get() = _binding!!

    // callback já existente (positivo)
    var actionCallback: (() -> Unit)? = null

    // callback negativo (ex.: "NÃO")
    var secondaryActionCallback: (() -> Unit)? = null

    companion object {
        const val TAG = "SnackbarFragment"
        private const val ARG_TYPE = "type"
        private const val ARG_TITLE = "title"
        private const val ARG_MSG = "msg"
        private const val ARG_BTN = "btn"
        private const val ARG_BTN_NEG = "btn_neg"

        fun newInstance(
            type: String,
            title: String,
            msg: String,
            btnText: String?,
            btnNegativeText: String? = null
        ): SnackbarFragment = SnackbarFragment().apply {
            arguments = bundleOf(
                ARG_TYPE to type,
                ARG_TITLE to title,
                ARG_MSG to msg,
                ARG_BTN to btnText,
                ARG_BTN_NEG to btnNegativeText
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
    }

    override fun onStart() {
        super.onStart()
        expandFromBottom()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSnackbarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --------- ARGS ---------
        val type = try {
            Constants.SnackType.valueOf(
                arguments?.getString(ARG_TYPE) ?: Constants.SnackType.SUCCESS.name
            )
        } catch (_: IllegalArgumentException) {
            Constants.SnackType.SUCCESS
        }
        val title = arguments?.getString(ARG_TITLE).orEmpty()
        val msg = arguments?.getString(ARG_MSG).orEmpty()
        val btnText = arguments?.getString(ARG_BTN)
        val btnNegativeText = arguments?.getString(ARG_BTN_NEG)

        // --------- TEXTO ---------
        binding.tvSnackTitle.text = title
        binding.tvSnackMessage.text = msg

        // --------- Fundo do card + textos ---------
        when (type) {
            Constants.SnackType.SUCCESS -> {
                binding.cardSnackbar.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.snack_bg_success)
                )
                binding.tvSnackTitle.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.snack_btn_text_dark
                    )
                )
                binding.tvSnackMessage.setTextColor(Color.BLACK)
            }

            Constants.SnackType.WARNING -> {
                binding.cardSnackbar.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.snack_bg_warning)
                )
                binding.tvSnackTitle.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.snack_btn_text_dark
                    )
                )
                binding.tvSnackMessage.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.btn_text_warning
                    )
                )
            }

            Constants.SnackType.ERROR -> {
                binding.cardSnackbar.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.snack_bg_error)
                )
                binding.tvSnackTitle.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.snack_btn_text_dark
                    )
                )
                binding.tvSnackMessage.setTextColor(Color.BLACK)
            }

            else -> {
                binding.cardSnackbar.setCardBackgroundColor(Color.WHITE)
                binding.tvSnackTitle.setTextColor(Color.BLACK)
                binding.tvSnackMessage.setTextColor(Color.BLACK)
            }
        }

        // --------- ÍCONE POR TIPO (sem enum novo) ---------
        // Usa seu Constants.SnackType atual para definir o drawable
        val iconRes = when (type) {
            Constants.SnackType.ERROR -> R.drawable.ic_error
            Constants.SnackType.WARNING -> R.drawable.ic_warning
            Constants.SnackType.SUCCESS -> R.drawable.ic_check
            else -> R.drawable.ic_info
        }
        binding.imgSnackIcon.setImageResource(iconRes)
        binding.imgSnackIcon.visibility = View.VISIBLE

        // --------- BOTÕES ---------
        // --------- Botão positivo (fundo + texto por tipo) ---------
        if (!btnText.isNullOrBlank()) {
            binding.btnSnackAction.apply {
                visibility = View.VISIBLE
                text = btnText
                backgroundTintList = ColorStateList.valueOf(
                    when (type) {
                        Constants.SnackType.SUCCESS -> ContextCompat.getColor(
                            requireContext(),
                            R.color.btn_bg_success
                        )

                        Constants.SnackType.WARNING -> ContextCompat.getColor(
                            requireContext(),
                            R.color.btn_bg_warning_yes2 // AQUI
                        )

                        Constants.SnackType.ERROR -> ContextCompat.getColor(
                            requireContext(),
                            R.color.btn_bg_error
                        )

                        else -> Color.WHITE
                    }
                )
                setTextColor(
                    when (type) {
                        Constants.SnackType.SUCCESS -> ContextCompat.getColor(
                            requireContext(),
                            R.color.snack_title_black
                        )

                        Constants.SnackType.WARNING -> ContextCompat.getColor(
                            requireContext(),
                            R.color.btn_text_warning_yes // AQUI
                        )

                        Constants.SnackType.ERROR -> ContextCompat.getColor(
                            requireContext(),
                            R.color.snack_title_black
                        )

                        else -> Color.BLACK
                    }
                )
                setOnClickListener {
                    dismiss()
                    actionCallback?.invoke()
                }
            }
        } else {
            binding.btnSnackAction.visibility = View.GONE
        }

        // --------- Botão negativo (fundo + texto por tipo) ---------
        if (!btnNegativeText.isNullOrBlank()) {
            binding.spaceBetween.visibility = View.VISIBLE
            binding.btnSnackNegative.apply {
                visibility = View.VISIBLE
                text = btnNegativeText
                backgroundTintList = ColorStateList.valueOf(
                    when (type) {
                        Constants.SnackType.SUCCESS -> ContextCompat.getColor(
                            requireContext(),
                            R.color.btn_bg_success
                        )

                        Constants.SnackType.WARNING -> ContextCompat.getColor(
                            requireContext(),
                            R.color.btn_bg_warning_no2 // AQUI
                        )

                        Constants.SnackType.ERROR -> ContextCompat.getColor(
                            requireContext(),
                            R.color.btn_bg_error
                        )

                        else -> Color.WHITE
                    }
                )
                setTextColor(
                    when (type) {
                        Constants.SnackType.SUCCESS -> ContextCompat.getColor(
                            requireContext(),
                            R.color.snack_title_black
                        )

                        Constants.SnackType.WARNING -> ContextCompat.getColor(
                            requireContext(),
                            R.color.snack_title_black // AQUI
                        )

                        Constants.SnackType.ERROR -> ContextCompat.getColor(
                            requireContext(),
                            R.color.snack_title_black
                        )

                        else -> Color.BLACK
                    }
                )
                setOnClickListener {
                    dismiss()
                    secondaryActionCallback?.invoke()
                }
            }
            setButtonsWeights(equal = true)
        } else {
            binding.spaceBetween.visibility = View.GONE
            binding.btnSnackNegative.visibility = View.GONE
            setButtonsWeights(equal = false)
        }


    }

    private fun setButtonsWeights(equal: Boolean) {
        val lpPos = binding.btnSnackAction.layoutParams as LinearLayout.LayoutParams
        val lpNeg = binding.btnSnackNegative.layoutParams as LinearLayout.LayoutParams
        if (equal) {
            lpPos.width = 0; lpPos.weight = 1f
            lpNeg.width = 0; lpNeg.weight = 1f
        } else {
            lpPos.width = LinearLayout.LayoutParams.WRAP_CONTENT; lpPos.weight = 0f
            lpNeg.width = LinearLayout.LayoutParams.WRAP_CONTENT; lpNeg.weight = 0f
        }
        binding.btnSnackAction.layoutParams = lpPos
        binding.btnSnackNegative.layoutParams = lpNeg
    }

    private fun expandFromBottom() {
        val dlg = dialog as? BottomSheetDialog ?: return
        dlg.behavior.apply {
            // mantém o sheet ancorado no BOTTOM e expande o conteúdo
            isFitToContents = true     // << importante para não “colar no topo”
            skipCollapsed = true       // não passa pelo estado colapsado
            peekHeight = 0             // sem “espiada”
            state = BottomSheetBehavior.STATE_EXPANDED
            // NÃO usar expandedOffset aqui (só funciona com isFitToContents=false)
        }
        // NÃO chame window.setLayout(MATCH_PARENT, MATCH_PARENT) aqui
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}