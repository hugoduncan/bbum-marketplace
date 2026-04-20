# Plan — Add bbum-publisher and bbum-consumer Skills

## Approach

Write both SKILL.md files in one pass. The content is well-understood — we already
have `skills/bbum-marketplace/SKILL.md` as the source of truth; we're splitting and
sharpening it rather than inventing.

## Steps

1. Delete `skills/bbum-marketplace/` — replaced entirely by the two new skills

2. Create `skills/bbum-consumer/SKILL.md` first (publisher references it)
   - Frontmatter: name, description, lambda, metadata tags
   - Sections: install (marketplace tasks into project), list, search, info, star,
     errors+recovery, agent patterns
   - Trigger: "find a bbum library", "search marketplace", "star a library",
     "discover tasks", "install from marketplace"

3. Create `skills/bbum-publisher/SKILL.md`
   - Frontmatter: name, description, lambda, metadata tags
   - Sections: publish workflow, prereqs, errors+recovery, agent patterns;
     reference bbum-consumer for the install step rather than duplicating it
   - Trigger: "publish my library", "add to marketplace", "submit library"

4. Commit

## Risks

None significant — pure documentation work.
