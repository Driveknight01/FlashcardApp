package com.example.flashcardapp.models;

import java.util.List;

public class Deck {
    private String id;
    private String title;
    private String description;
    private List<Flashcard> cards;
    private String creatorId;

    // Required empty constructor for Firestore deserialization
    public Deck() {
    }

    public Deck(String id, String title, String description, List<Flashcard> cards, String creatorId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.cards = cards;
        this.creatorId = creatorId;
    }

    /**
     * Returns count of mastered flashcards in the deck.
     */
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

    /**
     * Returns total number of flashcards in the deck.
     */
    public int getTotalCount() {
        return cards != null ? cards.size() : 0;
    }

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
