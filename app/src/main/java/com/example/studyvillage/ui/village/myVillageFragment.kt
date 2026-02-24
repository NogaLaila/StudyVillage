package com.example.studyvillage.ui.village

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.studyvillage.R
import com.example.studyvillage.databinding.FragmentMyVillageBinding

class MyVillageFragment : Fragment(R.layout.fragment_my_village) {

    private var _binding: FragmentMyVillageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyVillageViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMyVillageBinding.bind(view)

        viewModel.coins.observe(viewLifecycleOwner) { coins ->
            binding.txtCoins.text = coins.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
