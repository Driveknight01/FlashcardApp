package com.example.flashcardapp.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.List;

public class OcrOverlayView extends View {

    private final List<Text.Element> textElements = new ArrayList<>();
    private final List<Integer> selectedIndices = new ArrayList<>();
    private Paint boxPaint;
    private Paint selectedBoxPaint;

    private float scaleX = 1.0f, scaleY = 1.0f;
    private float offsetX = 0.0f, offsetY = 0.0f;

    public interface OnWordSelectedListener {
        void onWordSelected(String currentText);
    }

    private OnWordSelectedListener wordListener;

    public OcrOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(Color.parseColor("#3F51B5"));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(2f);

        selectedBoxPaint = new Paint();
        selectedBoxPaint.setColor(Color.parseColor("#4CAF50"));
        selectedBoxPaint.setStyle(Paint.Style.FILL);
        selectedBoxPaint.setAlpha(80);
    }

    public void setOnWordSelectedListener(OnWordSelectedListener listener) {
        this.wordListener = listener;
    }

    public void setScaleAndOffsets(ImageView imageView, int imageWidth, int imageHeight) {
        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);
        this.scaleX = scale;
        this.scaleY = scale;
        this.offsetX = (viewWidth - (imageWidth * scale)) / 2.0f;
        this.offsetY = (viewHeight - (imageHeight * scale)) / 2.0f;
    }

    public void setRecognizedText(Text visionText) {
        textElements.clear();
        selectedIndices.clear();
        if (visionText != null) {
            for (Text.TextBlock block : visionText.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    textElements.addAll(line.getElements());
                }
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (textElements.isEmpty()) return;

        for (int i = 0; i < textElements.size(); i++) {
            Rect originalRect = textElements.get(i).getBoundingBox();
            if (originalRect != null) {
                Rect mappedRect = new Rect(
                        (int) (originalRect.left * scaleX + offsetX),
                        (int) (originalRect.top * scaleY + offsetY),
                        (int) (originalRect.right * scaleX + offsetX),
                        (int) (originalRect.bottom * scaleY + offsetY)
                );

                if (selectedIndices.contains(i)) {
                    canvas.drawRect(mappedRect, selectedBoxPaint);
                } else {
                    canvas.drawRect(mappedRect, boxPaint);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        int x = (int) event.getX();
        int y = (int) event.getY();

        // TỰ ĐỘNG RESET: Khi người dùng bắt đầu lượt chạm mới, dọn sạch bộ nhớ bôi chọn cũ
        if (action == MotionEvent.ACTION_DOWN) {
            selectedIndices.clear();
            invalidate();
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < textElements.size(); i++) {
                Rect originalRect = textElements.get(i).getBoundingBox();
                if (originalRect != null) {
                    Rect mappedRect = new Rect(
                            (int) (originalRect.left * scaleX + offsetX),
                            (int) (originalRect.top * scaleY + offsetY),
                            (int) (originalRect.right * scaleX + offsetX),
                            (int) (originalRect.bottom * scaleY + offsetY)
                    );

                    if (mappedRect.contains(x, y)) {
                        if (!selectedIndices.contains(i)) {
                            selectedIndices.add(i);
                            invalidate();

                            if (wordListener != null) {
                                StringBuilder sb = new StringBuilder();
                                for (int index : selectedIndices) {
                                    sb.append(textElements.get(index).getText()).append(" ");
                                }
                                wordListener.onWordSelected(sb.toString().trim());
                            }
                        }
                        break;
                    }
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void clearSelection() {
        selectedIndices.clear();
        invalidate();
    }
}