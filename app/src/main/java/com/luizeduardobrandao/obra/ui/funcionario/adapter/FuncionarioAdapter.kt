package com.luizeduardobrandao.obra.ui.funcionario.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.databinding.ItemFuncionarioBinding
import com.luizeduardobrandao.obra.utils.Constants
import java.text.NumberFormat
import java.util.Locale

/**
 * Adapter genérico para exibir funcionários.
 *
 * ⚠️  Ele **não** realiza filtragem de “Ativo/Inativo”.
 *     A fragment/pager encarrega-se de enviar apenas a lista desejada.
 *
 * @param onEdit    callback quando o usuário toca no botão ✏️ editar
 * @param onDetail  callback quando o usuário toca no botão ℹ️ detalhes
 * @param onDelete  callback quando o usuário toca no botão 🗑️ excluir
 */

class FuncionarioAdapter (
    private val onEdit:   (Funcionario) -> Unit = {},
    private val onDetail: (Funcionario) -> Unit = {},
    private val onDelete: (Funcionario) -> Unit = {}
) : ListAdapter<Funcionario, FuncionarioAdapter.FuncViewHolder>(DIFF) {

    // melhora animações / reciclagem
    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).id.hashCode().toLong()


    // ————— ViewHolder —————
    inner class FuncViewHolder(
        private val binding: ItemFuncionarioBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Funcionario) = with(binding) {
            // ① Nome / Função / Status
            tvNomeFunc.text = item.nome
            tvFuncao.text = item.funcao
            tvStatus.apply {
                text = item.status.replaceFirstChar { it.uppercase() }   // Ativo / Inativo

                // Cor conforme status
                val coloRes = if(item.status.equals("ativo", true))
                    R.color.success else R.color.md_theme_light_error
                setTextColor(ContextCompat.getColor(root.context, coloRes))
            }

            // ② Salário (formatação + forma de pagamento)
            val formatoBR = NumberFormat.getCurrencyInstance(
                Locale(Constants.Format.CURRENCY_LOCALE, Constants.Format.CURRENCY_COUNTRY)
            )

            val salarioTxt = root.resources.getString(
                R.string.func_list_salary_format,
                formatoBR.format(item.salario),
                item.formaPagamento.lowercase()
            )

            tvSalario.text = salarioTxt

            // ③ Listeners dos ícones
            btnEdit.setOnClickListener { onEdit(item) }
            btnDetail.setOnClickListener { onDetail(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }


    // ————— ListAdapter overrides —————————————————
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FuncViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding  = ItemFuncionarioBinding.inflate(inflater, parent, false)
        return FuncViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FuncViewHolder, position: Int) =
        holder.bind(getItem(position))


    // ————— DiffUtil  ——————
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Funcionario>() {
            override fun areItemsTheSame(old: Funcionario, new: Funcionario) =
                old.id == new.id

            override fun areContentsTheSame(old: Funcionario, new: Funcionario) =
                old == new

        }
    }
}