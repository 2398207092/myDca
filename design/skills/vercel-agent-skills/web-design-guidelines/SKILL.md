---
name: web-design-guidelines
description: Review UI code for Web Interface Guidelines compliance. Use when asked to "review my UI", "check accessibility", "audit design", "review UX", or "check my site against best practices".
metadata:
  author: vercel
  version: "1.0.0"
  argument-hint: <file-or-pattern>
---

# Web Interface Guidelines

Review files for compliance with Web Interface Guidelines.

## How It Works

1. Read the vendored guidelines at `references/command.md` (fetch the source URL below only to refresh the snapshot)
2. Read the specified files (or prompt user for files/pattern)
3. Check against all rules in the fetched guidelines
4. Output findings in the terse `file:line` format

## Guidelines Source

Rules are vendored at `references/command.md` (snapshot 2026-11-12, 104 rules / 17 sections). Read that file; no network needed.

To refresh the snapshot, fetch the latest guidelines from:

```
https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md
```

The fetched content contains all the rules and output format instructions; update `references/command.md` and `references/SNAPSHOT.md` after refreshing.

## Usage

When a user provides a file or pattern argument:
1. Read the vendored guidelines at `references/command.md`
2. Read the specified files
3. Apply all rules from the fetched guidelines
4. Output findings using the format specified in the guidelines

If no files specified, ask the user which files to review.
