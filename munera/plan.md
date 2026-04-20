# bbum-marketplace — Plan

## Purpose

A central registry and discovery layer for bbum task libraries. Publishers submit
their libraries; consumers browse, search, and install. Stars accumulate automatically
as libraries are adopted.

## Open Tasks

1. [001-marketplace-for-bbum-task-libraries](open/001-marketplace-for-bbum-task-libraries/) — Design and build the marketplace
2. [002-one-star-per-github-user](open/002-one-star-per-github-user/) — Enforce one star per GitHub user; prevent trivial gaming
3. [003-add-publisher-and-consumer-skills](open/003-add-publisher-and-consumer-skills/) — Add `bbum-publisher` and `bbum-consumer` agent skills to `./skills/`

## Intended Order

Start with task 001. It covers the full scope: registry data format, publisher agent
workflow, consumer CLI extensions, and the star mechanic.

Task 002 is a follow-on integrity hardening task.  It can be worked independently of
001 completion but depends on the star file structure being finalised first.
