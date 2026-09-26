# AuditAgent Git hooks

`pre-commit` is a documentation guard. When staged implementation, configuration, scanner-rule, skill, build, or runtime files change, the hook requires at least one staged Markdown update under `doc/`.

The hook cannot decide which statements changed or safely write documentation itself. Authors must review `doc/README.md` and update every affected guide. `AGENTS.md` contains the mandatory maintenance rules for AI assistants.

## Enable after cloning

PowerShell:

```powershell
.\scripts\install-git-hooks.ps1
```

Git Bash, Linux, or macOS:

```bash
./scripts/install-git-hooks.sh
```

Equivalent manual configuration:

```bash
git config --local core.hooksPath .githooks
```

On Unix-like systems, also ensure the hook is executable:

```bash
chmod +x .githooks/pre-commit
```

## Run manually

The hook evaluates staged changes:

```bash
.githooks/pre-commit
```

## Exceptional bypass

For a demonstrably documentation-neutral change:

```bash
AUDITAGENT_SKIP_DOCS_CHECK=1 git commit ...
```

On PowerShell:

```powershell
$env:AUDITAGENT_SKIP_DOCS_CHECK = '1'
git commit ...
Remove-Item Env:AUDITAGENT_SKIP_DOCS_CHECK
```

Record the reason for bypassing the check in the change handoff. Do not use the bypass for behavior, API, schema, security, configuration, UI, scanner, agent, or operational changes.
