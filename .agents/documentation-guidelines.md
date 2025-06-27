# Documentation & comments

## KDoc style
- Write concise descriptions for all public and internal APIs.
- Start parameter descriptions with capital letters.
- End parameter descriptions with commas.
- Use inline code with backticks for code references (`example`).
- Format code blocks with fences and language identifiers:
  ```kotlin
  // Example code
  fun example() {
      // Implementation
  }
  ```

## Commenting guidelines
- Avoid inline comments in production code unless necessary.
- Inline comments are helpful in tests.
- When using TODO comments, follow the format on [dedicated page][todo-comments].
- File and directory names should be formatted as code.

## Tex width
- Wrap `.md` text to 80 characters for readability.
- Wrap KDoc comments at 75 characters. 

## Using periods
- Use periods at the end of complete sentences.
- Use periods for full or multi-clause bullets.
- Use NO periods for short bullets.
- Use NO periods for fragments.
- Use NO periods in titles and headers.
- Use NO periods in parameter descriptions in Javadoc.
- DO USE periods in parameter and property descriptions in KDoc.
- Be consistent within the list!

[todo-comments]: https://github.com/SpineEventEngine/documentation/wiki/TODO-comments
