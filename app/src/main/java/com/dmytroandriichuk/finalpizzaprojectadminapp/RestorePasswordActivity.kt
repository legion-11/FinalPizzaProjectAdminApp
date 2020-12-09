package com.dmytroandriichuk.finalpizzaprojectadminapp


import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

//provide screen for sending Password Reset Email
class RestorePasswordActivity : AppCompatActivity() {
    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restore_password)
        mAuth = FirebaseAuth.getInstance()

        val emailET = findViewById<TextInputEditText>(R.id.restorePasswordET)
        val emailLayout = findViewById<TextInputLayout>(R.id.restorePasswordLayout)
        val button = findViewById<Button>(R.id.restorePasswordButton)
        val probressBar = findViewById<ProgressBar>(R.id.restorepasswordProgressBar)
        button.setOnClickListener {
            var errors = false
            val email = emailET.text.toString().trim()
            if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailLayout.error = "invalid email"
                errors = true
            } else {
                emailLayout.error = ""
            }

            if (email.isEmpty()) {
                emailLayout.error = "email is required"
                errors = true
            } else {
                emailLayout.error = ""
            }
            probressBar.visibility = View.VISIBLE
            if (errors) return@setOnClickListener
            //send email verification letter
            mAuth.sendPasswordResetEmail(email).addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Check your email", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Check your email", Toast.LENGTH_LONG).show()
                }
            }
            probressBar.visibility = View.GONE
        }
    }
}