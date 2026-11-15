# Git Demo UI Engineering Repository

Independent Git repository demo for EasyTest Git engineering integration.

This directory is already initialized as an independent local Git repository (`main`).
Push it to a remote to use it from another machine:

```bash
git remote add origin <your-git-url>
git push -u origin main
```

Local platform URL:

```text
file:///Users/wen/DeepseekWorkspace/EasyTest/examples/engineering-ui-repo
```

The platform scans `easytest.ui.yaml` and loads `ui.git.login` / `ui.git.smoke`
as UI STEP cases (steps are stored in method payload for the UI runner).
