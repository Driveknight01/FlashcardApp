package com.example.flashcardapp.views;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.flashcardapp.R;
import com.example.flashcardapp.databinding.ActivityOcrScanBinding;
import com.example.flashcardapp.models.Flashcard;
import com.google.gson.Gson;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class OcrScanActivity extends AppCompatActivity {

    private ActivityOcrScanBinding binding;
    private TextRecognizer recognizer;
    private Uri tempImageUri;
    private boolean isTargetTermFocused = true;

    // Dynamic list holding scanned flashcard entries
    private final ArrayList<Flashcard> scannedCardsList = new ArrayList<>();

    // Activity result launcher for gallery image picking
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedUri = result.getData().getData();
                    if (selectedUri != null) startOcrProcessing(selectedUri);
                }
            }
    );

    // Activity result launcher for camera capture
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && tempImageUri != null) startOcrProcessing(tempImageUri);
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOcrScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize ML Kit Text Recognition client
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        binding.toolbarOcr.setNavigationOnClickListener(v -> finishWithResult());

        binding.toolbarOcr.inflateMenu(R.menu.menu_create_deck);
        binding.toolbarOcr.getMenu().findItem(R.id.action_save).setTitle("THÊM THẺ");

        // Save current scanned term/definition pair to batch and clear inputs
        binding.toolbarOcr.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                String term = binding.edtOcrTargetTerm.getText().toString().trim();
                String def = binding.edtOcrTargetDef.getText().toString().trim();

                if (term.isEmpty() || def.isEmpty()) {
                    Toast.makeText(this, "Vui lòng vuốt đầy đủ cả Thuật ngữ và Định nghĩa!", Toast.LENGTH_SHORT).show();
                } else {
                    scannedCardsList.add(new Flashcard(term, def));

                    binding.edtOcrTargetTerm.setText("");
                    binding.edtOcrTargetDef.setText("");
                    binding.edtOcrTargetTerm.requestFocus();
                    isTargetTermFocused = true;
                    binding.ocrDisplayOverlay.clearSelection();

                    Toast.makeText(this, "Đã lưu tạm thẻ thứ " + scannedCardsList.size() + "! Hãy tiếp tục vuốt từ tiếp theo.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });

        // Track which input field has focus for OCR text assignment
        binding.edtOcrTargetTerm.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) isTargetTermFocused = true; });
        binding.edtOcrTargetDef.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) isTargetTermFocused = false; });

        binding.btnOcrCapture.setOnClickListener(v -> {
            File tempFile = new File(getExternalCacheDir(), "scan_ocr_" + System.currentTimeMillis() + ".jpg");
            tempImageUri = androidx.core.content.FileProvider.getUriForFile(
                    OcrScanActivity.this, getApplicationContext().getPackageName() + ".provider", tempFile);
            cameraLauncher.launch(tempImageUri);
        });

        binding.btnOcrGallery.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        // Assign selected OCR text to the active focused input field
        binding.ocrDisplayOverlay.setOnWordSelectedListener(currentText -> {
            if (isTargetTermFocused) {
                binding.edtOcrTargetTerm.setText(currentText);
            } else {
                binding.edtOcrTargetDef.setText(currentText);
            }
        });

        // Handle back button / back gesture to return scanned cards payload
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithResult();
            }
        });
    }

    // Packages scanned cards into JSON string and returns them to caller
    private void finishWithResult() {
        if (!scannedCardsList.isEmpty()) {
            Intent intent = new Intent();
            String jsonCards = new Gson().toJson(scannedCardsList);
            intent.putExtra("SCANNED_CARDS_JSON", jsonCards);
            setResult(RESULT_OK, intent);
        }
        finish();
    }

    private void startOcrProcessing(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            binding.imgOcrDisplay.setImageBitmap(bitmap);

            binding.lnOcrSourceOptions.setVisibility(View.GONE);
            binding.rlOcrWorkspaceContainer.setVisibility(View.VISIBLE);

            InputImage image = InputImage.fromBitmap(bitmap, 0);
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        binding.ocrDisplayOverlay.setScaleAndOffsets(binding.imgOcrDisplay, bitmap.getWidth(), bitmap.getHeight());
                        binding.ocrDisplayOverlay.setRecognizedText(visionText);
                        Toast.makeText(OcrScanActivity.this, "Quét thành công! Hãy chạm chọn ô nhập rồi bôi chữ.", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(OcrScanActivity.this, "Lỗi nhận diện: " + e.getMessage(), Toast.LENGTH_SHORT).show());

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Không thể tải ảnh tài liệu!", Toast.LENGTH_SHORT).show();
        }
    }
}
