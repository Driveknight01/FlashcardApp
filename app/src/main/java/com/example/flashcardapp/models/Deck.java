package com.example.flashcardapp.models;

import java.util.List;

public class Deck {
    private String id;               // ID duy nhất của bộ thẻ trên Firebase
    private String title;            // Tiêu đề học phần (ví dụ: "Từ vựng Unit 1")
    private String description;      // Mô tả ngắn gọn
    private List<Flashcard> cards;   // Danh sách các tấm thẻ bên trong bộ này
    private String creatorId;        // ID của người tạo (Nếu là "admin" thì hiển thị cho tất cả mọi người)

    // Constructor không tham số bắt buộc cho Firebase
    public Deck() {
    }

    public Deck(String id, String title, String description, List<Flashcard> cards, String creatorId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.cards = cards;
        this.creatorId = creatorId;
    }

    // --- CÁC HÀM TIỆN ÍCH DÙNG ĐỂ HIỂN THỊ TIẾN TRÌNH TRÊN GIAO DIỆN ---

    // Lấy số lượng thẻ ĐÃ THUỘC (Phục vụ hiển thị: "Đã thuộc X / Y thẻ")
    public int getLearnedCount() {
        if (cards == null) return 0;
        int count = 0;
        for (Flashcard card : cards) {
            if (card.isLearned()) {
                count++;
            }
        }
        return count;
    }

    // Lấy TỔNG SỐ LƯỢNG THẺ có trong bộ thẻ này
    public int getTotalCount() {
        return cards != null ? cards.size() : 0;
    }

    // --- CÁC HÀM GETTER VÀ SETTER THÔNG THƯỜNG ---
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Flashcard> getCards() {
        return cards;
    }

    public void setCards(List<Flashcard> cards) {
        this.cards = cards;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }
}