package com.example.flashcardapp.models;

public class Flashcard {
    private String term;
    private String definition;
    private boolean isLearned;

    private String status;         // Study state: "learning", "reviewing", "mastered"
    private long nextReviewTime;   // Scheduled timestamp (epoch ms) for next review

    // Required empty constructor for Firestore deserialization
    public Flashcard() {
    }

    public Flashcard(String term, String definition) {
        this.term = term;
        this.definition = definition;
        this.isLearned = false;
        this.status = "learning";
        this.nextReviewTime = 0L;
    }

    public Flashcard(String term, String definition, boolean isLearned, String status, long nextReviewTime) {
        this.term = term;
        this.definition = definition;
        this.isLearned = isLearned;
        this.status = status;
        this.nextReviewTime = nextReviewTime;
    }

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
