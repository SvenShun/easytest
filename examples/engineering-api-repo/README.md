# Git Demo API Engineering Repository

Independent Git repository demo for EasyTest Git engineering integration.

This directory is already initialized as an independent local Git repository (`main`).
Push it to a remote to use it from another machine:

```bash
git remote add origin <your-git-url>
git push -u origin main
```

Local platform URL:

```text
file:///Users/wen/DeepseekWorkspace/EasyTest/examples/engineering-api-repo
```

The platform scans `easytest.api.yaml` and loads `git.login` / `git.health`.
`target/git-demo-api.jar` is committed as the artifact; scanning uploads it to the API Agent automatically.
Rebuild it with the EasyTest `contract-api` dependency when changing Java code.
