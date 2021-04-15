package com.dmytroandriichuk.finalpizzaprojectadminapp.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import com.dmytroandriichuk.finalpizzaprojectadminapp.MapsActivity
import com.dmytroandriichuk.finalpizzaprojectadminapp.R
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.Order

class OrderTakeDialog(val order: Order,
                      val key: String,
                      var status: MapsActivity.Companion.OrderStatus,
                      val listener: DialogClickListener): AppCompatDialogFragment() {

    lateinit var button: Button

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val view = inflater.inflate(R.layout.dialog_order, null)
            builder.setView(view)
            val name = view.findViewById<TextView>(R.id.dialogPersonsNameTV)
            name.text = order.name
            val phone = view.findViewById<TextView>(R.id.dialogPhoneNumberTV2)
            phone.text = order.phone
            val address = view.findViewById<TextView>(R.id.dialogAddressTV)
            address.text = order.address
            val pizza = view.findViewById<TextView>(R.id.dialogPizzaTV)
            pizza.text = order.pizza
            val top = view.findViewById<TextView>(R.id.dialogToppingsTV)
            top.text = order.toppings?.joinToString(" ") ?: "no toppings"
            val price = view.findViewById<TextView>(R.id.dialogPriceTV)
            price.text = resources.getString(R.string.format_price).format(order.price)
            val progressBar = view.findViewById<ProgressBar>(R.id.mapsProgressBar)

            button = view.findViewById(R.id.takeDismissButton)
            button.text = if (orderIsTaken()) "complete" else "take order"

            if (orderIsNew()){
                button.setOnClickListener {
                    progressBar?.visibility = View.VISIBLE
                    if (orderIsNew()) { listener.takeOrder(key, order, this) }
                    else { listener.giveBackOrder(key, this) }
                    progressBar?.visibility = View.GONE
                }
            } else {
                button.setOnClickListener {
                    progressBar?.visibility = View.VISIBLE
                    if (orderIsTaken()) { listener.completeOrder(key, order, this) }
                    else { listener.uncompleteOrder(key, order, this) }
                    progressBar?.visibility = View.GONE
                }
            }

            dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun orderIsTaken(): Boolean {
        return status == MapsActivity.Companion.OrderStatus.TAKEN
    }

    private fun orderIsNew(): Boolean {
        return status == MapsActivity.Companion.OrderStatus.NEW
    }

    interface DialogClickListener {
        fun completeOrder(key: String, order: Order, dialog: OrderTakeDialog)
        fun uncompleteOrder(key: String, order: Order, dialog: OrderTakeDialog)
        fun takeOrder(key: String, initialOrder: Order, dialog: OrderTakeDialog)
        fun giveBackOrder(key: String, dialog: OrderTakeDialog)
    }
}