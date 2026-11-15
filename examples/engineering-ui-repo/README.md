# Git Demo UI Engineering Repository

Independent Git repository demo for EasyTest Git engineering integration.

```bash
git init
git add .
git commit -m "demo: ui engineering cases"
git remote add origin <your-git-url>
git push -u origin main
```

The platform scans `easytest.ui.yaml` and loads `ui.git.login` / `ui.git.smoke`
as UI STEP cases (steps are stored in method payload for the UI runner).
