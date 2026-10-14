
# Shared Codex Collaboration Rules

## Before Starting Work
1. Read Project.md and Architecture.md.
2. Read Contracts.md for module dependencies.
3. Check Tasks.md for ownership and blockers.
4. Read recent updates in Shared_Memory.md.

## During Development
1. Work only on your assigned module unless coordinated.
2. Follow interfaces defined in Contracts.md.
3. Never silently modify shared API contracts.
4. Communicate breaking changes through a PR.
5. Record cross-module blockers in Tasks.md.
6. Do not assume another module is complete.

## After Completing Work
1. Update your task status in Tasks.md.
2. Add important changes to Shared_Memory.md.
3. Update Contracts.md if interfaces changed.
4. Update Architecture.md if design changed.
5. Commit documentation alongside code changes.

## Collaboration Rules
- GitHub is the shared source of truth.
- Each developer works on a separate branch.
- Changes to shared contracts require review
  from affected module owners.
- Avoid editing unrelated module code.
- Resolve documentation conflicts carefully.
- Never overwrite another agent's updates.
- Sync the latest main branch before starting
  a new development session.
- Never push directly to main.
