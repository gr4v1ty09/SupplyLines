# Contributing to SupplyLines

Thanks for your interest in contributing! Before you start:

1. Read the Design Charter in `/docs` for vision and principles.
2. Skim the TDD for architecture and extension points.
3. Use feature branches and open a draft PR early for feedback.

## Code Style
- Java 17
- No platform imports in `common/`
- Keep adapters thin; put logic in `-common` or `common/`

## Commit Messages
Use conventional commits:
- `feat(core): ...`
- `fix(adapter): ...`
- `docs(tdd): ...`
