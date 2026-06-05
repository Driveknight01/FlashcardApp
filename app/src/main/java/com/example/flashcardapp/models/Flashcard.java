package com.example.flashcardapp.models;

public class Flashcard {
    private String term;        // Thuật ngữ (Mặt trước thẻ)
    private String definition;  // Định nghĩa (Mặt sau thẻ)
    private boolean isLearned;  // Trạng thái cũ: true nếu đã thuộc, false nếu chưa thuộc

    // 💡 ĐỒNG BỘ THÊM: Để Java hiểu 2 trường đang có trên Firebase Console của bạn
    private String status;         // Trạng thái thuật toán ("learning", "reviewing", "mastered")
    private long nextReviewTime;   // Mốc thời gian ôn tập tiếp theo (timestamp)

    // Constructor không tham số bắt buộc phải có để Firebase tự động ánh xạ dữ liệu
    public Flashcard() {
    }

    // Constructor dùng khi tạo thẻ mới bằng tay
    public Flashcard(String term, String definition) {
        this.term = term;
        this.definition = definition;
        this.isLearned = false;
        this.status = "learning"; // Gán mặc định là đang học
        this.nextReviewTime = 0L; // Sẵn sàng để học ngay lập tức
    }

    // Constructor đầy đủ thuộc tính (Dành cho các luồng xử lý nâng cao)
    public Flashcard(String term, String definition, boolean isLearned, String status, long nextReviewTime) {
        this.term = term;
        this.definition = definition;
        this.isLearned = isLearned;
        this.status = status;
        this.nextReviewTime = nextReviewTime;
    }

    // --- CÁC HÀM GETTER VÀ SETTER CHUẨN CHỈNH ---

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public boolean isLearned() {
        return isLearned;
    }

    public void setLearned(boolean learned) {
        isLearned = learned;
    }

    // 🔥 GIẢI QUYẾT LỖI: Giúp CreateDeckActivity gọi được hàm không bị báo lỗi đỏ
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getNextReviewTime() {
        return nextReviewTime;
    }

    public void setNextReviewTime(long nextReviewTime) {
        this.nextReviewTime = nextReviewTime;
    }
}