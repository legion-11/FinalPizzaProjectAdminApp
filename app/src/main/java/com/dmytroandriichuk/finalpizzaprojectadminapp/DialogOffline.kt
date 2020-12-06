package com.dmytroandriichuk.finalpizzaprojectadminapp

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.annotation.NonNull
import androidx.fragment.app.DialogFragment

class DialogOffline(val message: String): DialogFragment() {

    @NonNull
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setTitle("Failed to sign in!")
                .setMessage(message)
                .setPositiveButton("Ok") {
                        dialog, id ->  dialog.cancel()
                }

            if (message == "Account is not verified"){
                builder.setNegativeButton("Send Verification Letter") { dialog, id ->
                    (activity as MainActivity).sendVerificationLetter()
                    dialog.cancel()
                }
            }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}