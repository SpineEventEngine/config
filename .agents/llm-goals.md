# 🧭 LLM goals

These goals guide how agents (ChatGPT, Codex) are used in this project to:
- Help developers move faster without sacrificing code quality.
- Provide language-aware guidance on Kotlin/Java idioms.
- Lower the barrier to onboarding new contributors.
- Enable collaborative, explainable, and auditable development with AI.

## Problem-solving framework for complex tasks

When faced with complex tasks, follow this framework:

1. **Decompose**: Break down the problem into smaller, manageable parts
2. **Analyze**: Understand the architectural implications of each part
3. **Pattern-Match**: Identify established patterns that apply
4. **Implement**: Write code that follows project conventions
5. **Test**: Ensure comprehensive test coverage
6. **Document**: Provide clear explanations of your solution

*This framework helps maintain consistency across contributions from different agents.*

## 🚀 GPT-4o advanced capabilities

GPT-4o excels at these high-value tasks in our CQRS architecture:

1. **Architecture-level insights**
   - Suggesting architectural improvements in CQRS pattern implementation
   - Identifying cross-cutting concerns between command and query sides
   - Optimizing event flow and state propagation

2. **Advanced Kotlin refactoring**
   - Converting imperative code to idiomatic Kotlin (sequences, extensions, etc.)
   - Applying context receivers and other Kotlin 1.6+ features
   - Optimizing coroutine patterns and structured concurrency

3. **Testing intelligence**
   - Identifying missing property-based test scenarios
   - Suggesting event sequence combinations that could cause race conditions
   - Creating comprehensive test fixtures for complex domain objects

### Example prompts for GPT-4o

Leverage GPT-4o's advanced capabilities with prompts like these:

```text
# Architecture analysis
"Analyze this CommandHandler implementation and suggest improvements to better align with CQRS principles, especially considering event sourcing implications."

# Kotlin refactoring
"Refactor this Java-style code to use more idiomatic Kotlin patterns. Pay special attention to immutability, extension functions, and DSL opportunities."

# Test enhancement
"Review this test suite for our event processing pipeline and suggest additional test scenarios focusing on concurrent event handling edge cases."
```
