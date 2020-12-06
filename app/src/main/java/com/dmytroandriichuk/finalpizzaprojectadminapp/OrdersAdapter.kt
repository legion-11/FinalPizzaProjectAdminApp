package com.dmytroandriichuk.finalpizzaprojectadminapp



import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.Order
import java.util.*

class OrdersAdapter(private val dataSet: List<Order?>,
                    private val onOrderClickListener :OnOrderClickListener
): RecyclerView.Adapter<OrdersAdapter.ViewHolder>() {

    class ViewHolder(view: View, private val onOrderClickListener :OnOrderClickListener) : RecyclerView.ViewHolder(view), View.OnClickListener {

        val nametv: TextView = view.findViewById(R.id.orderItemCustomerName)
        val addresstv: TextView = view.findViewById(R.id.orderItemAddress)
        val pizzatv: TextView = view.findViewById(R.id.orderItemPizzaType)
        val toppingstv: TextView = view.findViewById(R.id.orderItemToppings)
        val pricetv: TextView = view.findViewById(R.id.orderItemPrice)
        val datetv: TextView = view.findViewById(R.id.orderItemDate)

        init {
            view.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            onOrderClickListener.onOrderClick(adapterPosition)
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_orders, viewGroup, false)

        return ViewHolder(view, onOrderClickListener)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        val order = dataSet[position] as Order
        viewHolder.nametv.text = order.name
        viewHolder.addresstv.text = order.address
        val sizes = arrayOf("Small", "Medium", "Large", "Extra")
        viewHolder.pizzatv.text = "${sizes[order.size ?: 1]}\n${order.pizza}"

        viewHolder.toppingstv.text = order.toppings?.joinToString(" ") ?: ""
        viewHolder.pricetv.text = "${order.price.toString()}$"

        viewHolder.datetv.text = order.date?.let { Date(it).toString() }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    public interface OnOrderClickListener {
        fun onOrderClick(position: Int)
    }
}