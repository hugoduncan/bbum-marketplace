# Design — Add bbum-publisher and bbum-consumer Skills

## Goal

Add two focused agent skills to `./skills/`:

- `bbum-publisher` — guides an AI agent through publishing a bbum task library to the marketplace
- `bbum-consumer` — guides an AI agent through discovering, evaluating, and installing libraries from the marketplace

## Context

`skills/bbum-marketplace/SKILL.md` already exists but covers the whole marketplace
as a single monolithic skill. Splitting into publisher and consumer perspectives
provides:

1. **Smaller, more focused context** — each skill loads only what's relevant to
   the agent's current role
2. **Clearer trigger conditions** — publisher skill fires on "publish my library";
   consumer skill fires on "find/install/search/star"
3. **Reusability** — a publisher-only project never needs consumer noise and vice versa

## Constraints

- Must use proper SKILL.md frontmatter (matches existing `skills/bbum-marketplace/SKILL.md`
  and `~/.psi/agent/skills/*/SKILL.md` conventions):
  - Required: `name`, `description`, `lambda`
  - Optional: `license`, `metadata`, `allowed-tools`
- Each skill lives in its own directory under `./skills/`
- Content should be self-contained — no "see the other skill" cross-references
  (agent may only have one loaded)

## Acceptance

- `skills/bbum-publisher/SKILL.md` exists, has valid frontmatter, covers the full
  publish workflow including prereqs, errors, and agent patterns
- `skills/bbum-consumer/SKILL.md` exists, has valid frontmatter, covers discover /
  search / info / install / star workflows
- Both files are well-formed Markdown
