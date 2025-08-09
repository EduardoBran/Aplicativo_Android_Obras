package com.luizeduardobrandao.obra.ui.funcionario.adapter

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
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

class FuncionarioAdapter(
    private val onEdit: (Funcionario) -> Unit = {},
    private val onDetail: (Funcionario) -> Unit = {},
    private val onDelete: (Funcionario) -> Unit = {},
    private val showActions: Boolean = true
) : ListAdapter<Funcionario, FuncionarioAdapter.FuncViewHolder>(DIFF) {

    // melhora animações / reciclagem
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = getItem(position).id.hashCode().toLong()


    // ————— ViewHolder —————
    inner class FuncViewHolder(
        private val binding: ItemFuncionarioBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Funcionario) = with(binding) {
            // ① Nome / Função(s) / Status
            tvNomeFunc.text = item.nome

            // Função/Funções com prefixo em negrito (mostra no máx. 4; se >4, adiciona " ...")
            val funcoesLista = item.funcao
                .split("/")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val funcoesTexto = if (funcoesLista.size > 3) {
                funcoesLista.take(4).joinToString(", ") + " ..."
            } else {
                funcoesLista.joinToString(", ")
            }

            val prefix = root.context.getString(
                if (funcoesLista.size > 1) R.string.func_label_funcoes else R.string.func_label_funcao
            )

            val funcoesSpannable = SpannableStringBuilder("$prefix $funcoesTexto").apply {
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    prefix.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            tvFuncao.text = funcoesSpannable

            // Status (com cor)
            tvStatus.apply {
                text = item.status.replaceFirstChar { it.uppercase() }
                val colorRes = if (item.status.equals("ativo", true))
                    R.color.success else R.color.md_theme_light_error
                setTextColor(ContextCompat.getColor(root.context, colorRes))
            }

            // ② Salário (formatação + forma de pagamento)
            val formatoBR = NumberFormat.getCurrencyInstance(
                Locale(Constants.Format.CURRENCY_LOCALE, Constants.Format.CURRENCY_COUNTRY)
            )
            tvSalario.text = root.resources.getString(
                R.string.func_list_salary_format,
                formatoBR.format(item.salario),
                item.formaPagamento.lowercase()
            )

            // ②.1 Dias trabalhados → singular para 0 ou 1, plural para >1
            val diasStrRes = if (item.diasTrabalhados <= 1)
                R.string.func_dias_trabalhados_singular
            else
                R.string.func_dias_trabalhados_plural
            tvDiasTrabalhados.text = root.resources.getString(diasStrRes, item.diasTrabalhados)

            // ③ Ações
            if (showActions) {
                layoutActions.visibility = View.VISIBLE
                dividerActions.visibility = View.VISIBLE

                btnEdit.setOnClickListener { onEdit(item) }
                btnDetail.setOnClickListener { onDetail(item) }
                btnDelete.setOnClickListener { onDelete(item) }
            } else {
                layoutActions.visibility = View.GONE
                dividerActions.visibility = View.GONE
                btnEdit.setOnClickListener(null)
                btnDetail.setOnClickListener(null)
                btnDelete.setOnClickListener(null)
            }
        }
    }

    // ————— ListAdapter overrides —————————————————
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FuncViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemFuncionarioBinding.inflate(inflater, parent, false)
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