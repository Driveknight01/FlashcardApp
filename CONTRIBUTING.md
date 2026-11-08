# Contribution Guidelines

Guidelines for team members collaborating on the **FlashcardApp** course project.

---

## 1. Branching Workflow

Use descriptive branch names for task-based work:
- `feature/<feature-name>`: e.g. `feature/ocr-improvements`, `feature/quiz-mode`
- `fix/<bug-name>`: e.g. `fix/login-crash`, `fix/flip-animation`

---

## 2. Commit Message Format

Keep commit messages clear and concise:
```
<type>: <short summary>

Examples:
feat: add card flip animation in learn activity
fix: handle null user role on first login
docs: update readme with setup instructions
```

---

## 3. Coding Standards

- **Language**: Java 11.
- **Comments**: Keep comments in clear, professional English. Focus on non-obvious logic.
- **ViewBinding**: Use ViewBinding for all UI references instead of `findViewById`.
- **Firebase Listeners**: Always call `remove()` on `ListenerRegistration` in `onDestroy()` to prevent memory leaks.
- **Test Before Push**: Always test the build locally before pushing:
  ```bash
  ./gradlew assembleDebug
  ```
