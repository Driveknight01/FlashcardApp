package com.example.flashcardapp.views;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.flashcardapp.databinding.ActivityAdminMainBinding;

public class AdminMainActivity extends AppCompatActivity {

    private ActivityAdminMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 🔍 CLICK VÀO THANH SEARCH: Chuyển ngay sang màn hình tìm kiếm chuyên nghiệp
        binding.btnFakeSearchContainer.setOnClickListener(v -> {
            startActivity(new Intent(AdminMainActivity.this, AdminSearchDecksActivity.class));
        });

        binding.btnMenuCreateDeck.setOnClickListener(v -> {
            Intent intent = new Intent(AdminMainActivity.this, CreateDeckActivity.class);
            intent.putExtra("USER_ROLE", "admin");
            intent.putExtra("IS_EDIT_MODE", false);
            startActivity(intent);
        });

        binding.btnMenuGlobalDecks.setOnClickListener(v -> {
            startActivity(new Intent(AdminMainActivity.this, AdminDecksActivity.class));
        });

        binding.btnMenuUserList.setOnClickListener(v -> {
            startActivity(new Intent(AdminMainActivity.this, AdminUsersActivity.class));
        });
    }
}