package com.dmytroandriichuk.finalpizzaprojectadminapp


import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.User

import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

// provide screen for registration
class RegistrationActivity : AppCompatActivity() {


    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var emailET: EditText
    private lateinit var passwordET: EditText
    private lateinit var nameET: EditText

    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var nameLayout: TextInputLayout
    private lateinit var progressBar: ProgressBar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        mAuth = FirebaseAuth.getInstance();
        database = Firebase.database

        emailET = findViewById<EditText>(R.id.emailRegistrationET)
        passwordET = findViewById<EditText>(R.id.passwordlRegistrationET)
        nameET = findViewById<EditText>(R.id.nameRegistrationET)

        emailLayout = findViewById<TextInputLayout>(R.id.emailRegistrationLayout)
        passwordLayout = findViewById<TextInputLayout>(R.id.passwordRegistrationLayout)
        nameLayout = findViewById<TextInputLayout>(R.id.nameRegistrationLayout)

        progressBar = findViewById<ProgressBar>(R.id.registrationProgressBar)

        val registerButton = findViewById<Button>(R.id.confirmRegistrationButton)
        registerButton.setOnClickListener { registerUser() }
    }

    //check input and sen email verification letter
    private fun registerUser() {

        val email = emailET.text.toString().trim()
        val password = passwordET.text.toString().trim()
        val name = nameET.text.toString().trim()

        var errors = false
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

        if (password.isEmpty()) {
            passwordLayout.error = "password is required"
            errors = true
        } else {
            passwordLayout.error = ""
        }

        if (password.length < 6) {
            passwordLayout.error = "password must be at least 6 characters"
            errors = true
        } else {
            passwordLayout.error = ""
        }

        if (name.isEmpty()) {
            nameLayout.error = "password is required"
            errors = true
        } else {
            nameLayout.error = ""
        }

        if (!errors) {
            progressBar.visibility = View.VISIBLE
            Log.i("progress", "1: ")
            mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { taskCreateUser ->
                if (taskCreateUser.isSuccessful) {
                    val user = User(email, name, true)
                    mAuth.currentUser?.let { currentUser ->
                        database.getReference("Users").child(currentUser.uid).setValue(user).addOnCompleteListener {
                            if (it.isSuccessful) {
                                currentUser.sendEmailVerification()
                                Toast.makeText(this,
                                    "User registered successfully! Verification letter will be send to your email",
                                    Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "Failed to register", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {Toast.makeText(this, "Failed to register", Toast.LENGTH_LONG).show()}
            }
            Log.i("progress", "2: ")
            progressBar.visibility = View.GONE
        }
    }
}