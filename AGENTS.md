# Project workflow

- Handle each requested fix or feature on a separate `codex/` branch.
- For multi-step requests, finish one step at a time. Implement it, run relevant checks, and prepare a runnable build for the user's own UI review.
- Stop for the user's explicit approval after UI review. Do not commit or push before approval.
- After approval, commit only that step's changes and push its branch before starting the next step. Do not merge unless requested.
- Preserve unrelated local edits and staged files; never include them in a feature commit without authorization.
- This workflow applies to future fixes and features without the user needing to repeat it.

# Communication and implementation

- Be direct, casual, and clear. Do not use emojis or sugar-coat problems.
- Prefer secure, maintainable, efficient code. Recommend a better approach when it materially improves the result.
- Explain useful tradeoffs and verification results without unnecessary detail.
