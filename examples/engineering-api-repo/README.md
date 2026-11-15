# Git Demo API Engineering Repository

Independent Git repository demo for EasyTest Git engineering integration.

```bash
git init
git add .
git commit -m "demo: api engineering cases"
git remote add origin <your-git-url>
git push -u origin main
```

The platform scans `easytest.api.yaml` and loads `git.login` / `git.health`.
Build the JAR with the EasyTest `contract-api` dependency and put it into the API agent's
`data/engineering` directory for execution.
