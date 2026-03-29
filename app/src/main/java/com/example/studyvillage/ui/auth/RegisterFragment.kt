package com.example.studyvillage.ui.auth

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.studyvillage.R
import com.example.studyvillage.data.shop.local.DataBaseProvider
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.remote.UserRemote
import com.example.studyvillage.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AuthViewModel
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                binding.ivProfile.setImageURI(uri)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRegisterBinding.bind(view)

        val db = DataBaseProvider.get(requireContext())
        val userRepo = UserRepository(db.userDao(), UserRemote())
        val factory = AuthViewModelFactory(requireActivity().application, userRepo)
        viewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]

        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.ivProfile.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString()?.trim().orEmpty()
            val confirm = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()

            when {
                name.isBlank() -> toast("Enter name")
                email.isBlank() -> toast("Enter email")
                password.isBlank() -> toast("Enter password")
                confirm.isBlank() -> toast("Confirm password")
                password != confirm -> toast("Passwords do not match")
                password.length < 6 -> toast("Password must be at least 6 characters")
                else -> viewModel.register(name, email, password, selectedImageUri)
            }
        }

        binding.tvGoLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        viewModel.authState.observe(viewLifecycleOwner) { state ->
            binding.progressBar.visibility =
                if (state is AuthState.Loading) View.VISIBLE else View.GONE

            when (state) {
                is AuthState.Success -> {
                    findNavController().navigate(R.id.action_registerFragment_to_focusFragment)
                }
                is AuthState.Error -> toast(state.message)
                else -> Unit
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}