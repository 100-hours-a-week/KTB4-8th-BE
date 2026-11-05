# Domain Docs

This repository uses a single-context layout: `GLOSSARY.md` at the repo root and architectural decision records under `docs/adr/`.

Before exploring the codebase, read `GLOSSARY.md` and ADRs relevant to the area being changed.
If these files do not exist, proceed silently. Do not suggest creating them upfront; `/domain-modeling` creates them when terms or decisions are resolved.

Use domain terms as defined in the glossary in issue titles, proposals, code, and tests. If a term is missing, reconsider the terminology or note the gap for `/domain-modeling`.

Explicitly flag any proposal that contradicts an existing ADR, referencing the ADR and explaining why the decision should be reconsidered.
