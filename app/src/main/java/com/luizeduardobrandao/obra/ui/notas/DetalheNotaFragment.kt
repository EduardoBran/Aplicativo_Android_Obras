package com.luizeduardobrandao.obra.ui.notas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.databinding.FragmentDetalheNotaBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaPagerAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class DetalheNotaFragment : Fragment() {

    private var _binding: FragmentDetalheNotaBinding? = null
    private val binding get() = _binding!!

    private val args: DetalheNotaFragmentArgs by navArgs()
    private val viewModel: NotasViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentDetalheNotaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            toolbarDetNota.setNavigationOnClickListener {
                findNavController().navigateUp()
            }

            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.observeNota(args.obraId, args.notaId).collect { nota ->
                        if (nota == null) {
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.snack_error),
                                getString(R.string.nota_load_error2),
                                getString(R.string.snack_button_ok)
                            ) {
                                findNavController().navigateUp()
                            }
                            return@collect
                        }

                        toolbarDetNota.title = getString(
                            R.string.nota_detail_title,
                            nota.data
                        )
                        tvDetNomeMaterial.text = nota.nomeMaterial
                        tvDetDescricao.text = nota.descricao
                            ?.ifBlank { "—" }
                            ?: "—"
                        tvDetLoja.text = nota.loja
                        tvDetTipos.text = nota.tipos.joinToString(", ")
                        tvDetData.text = nota.data
                        tvDetStatus.text = getString(
                            if (nota.status == NotaPagerAdapter.STATUS_A_PAGAR)
                                R.string.nota_status_purchased
                            else
                                R.string.nota_status_paid_client
                        )
                        tvDetValor.text = formatMoneyBR(nota.valor)
                    }
                }
            }
        }
    }

    private fun formatMoneyBR(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}