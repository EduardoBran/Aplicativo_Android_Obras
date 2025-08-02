package com.luizeduardobrandao.obra.ui.snackbar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.luizeduardobrandao.obra.databinding.FragmentSnackbarBinding
import com.luizeduardobrandao.obra.utils.Constants

/**
 * Bottom-sheet customizado para mensagens de erro / sucesso / aviso.
 *
 * • Desaparece ao tocar fora.
 * • Personaliza cores e textos de acordo com *type* ("error", "success", "warning").
 */

class SnackbarFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentSnackbarBinding? = null
    private val binding get() = _binding!!

    // callback opcional exposta para a ViewExtension
    var actionCallback: (() -> Unit)? = null

    // ――――――――――――― companion / newInstance ―――――――――――――
    companion object {
        const val TAG = "SnackbarFragment"
        private const val ARG_TYPE = "type"
        private const val ARG_TITLE = "title"
        private const val ARG_MSG = "msg"
        private const val ARG_BTN = "btn"

        // Factory method usado pela extensão `showSnackbarFragment
        fun newInstance(
            type: String,
            title: String,
            msg: String,
            btnText: String?
        ) : SnackbarFragment = SnackbarFragment().apply {
            arguments = bundleOf(
                ARG_TYPE to type,
                ARG_TITLE to title,
                ARG_MSG to msg,
                ARG_BTN to btnText
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Permite fechar ao tocar fora
        isCancelable = true
    }

    // ――――――――――――― ciclo de vida ―――――――――――――
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentSnackbarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recupera tipo seguro ou default
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

        // Textos
        binding.tvSnackTitle.text = title
        binding.tvSnackMessage.text = msg

        // Cores via Constants.SnackType
        val bgColor = ContextCompat.getColor(requireContext(), type.bgColor)
        val textColor = ContextCompat.getColor(requireContext(), type.textColor)
        binding.cardSnackbar.setCardBackgroundColor(bgColor)
        binding.tvSnackTitle.setTextColor(textColor)
        binding.tvSnackMessage.setTextColor(textColor)

        // Botão de ação
        if (!btnText.isNullOrBlank()) {
            binding.btnSnackAction.apply {
                visibility = View.VISIBLE
                text = btnText
                setTextColor(textColor)
                setOnClickListener {
                    dismiss()
                    actionCallback?.invoke()
                }
            }
        } else {
            binding.btnSnackAction.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}