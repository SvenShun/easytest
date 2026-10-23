package com.testhub.uiexec.service;

import com.testhub.common.web.ApiException;
import com.testhub.uiexec.domain.SkillsPointerEntity;
import com.testhub.uiexec.repository.SkillsPointerRepository;
import com.testhub.uiexec.web.dto.SkillsDtos.SkillsPointerView;
import com.testhub.uiexec.web.dto.SkillsDtos.SkillsSyncResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Skills pointer management. {@code sync} resolves the branch head with
 * {@code git ls-remote}; routing/dispatch only ever read the pointer (the
 * commit is captured on the task at dispatch time so queued tasks use the
 * newest pointer).
 */
@Service
public class SkillsService {

    private static final Logger log = LoggerFactory.getLogger(SkillsService.class);
    private static final Pattern COMMIT_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$|^[0-9a-fA-F]{64}$");
    private static final long GIT_TIMEOUT_SECONDS = 20;

    private final SkillsPointerRepository repository;
    private final AuditService auditService;

    public SkillsService(SkillsPointerRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /** Single-row pointer; inserts the empty seed pointer when missing. */
    public synchronized SkillsPointerEntity getOrCreate() {
        return repository.findById(SkillsPointerEntity.SINGLETON_ID).orElseGet(() -> {
            Instant now = Instant.now();
            return repository.save(new SkillsPointerEntity(SkillsPointerEntity.SINGLETON_ID, "", "main", null,
                    "system", now, null));
        });
    }

    public SkillsPointerView view() {
        SkillsPointerEntity pointer = getOrCreate();
        return new SkillsPointerView(pointer.getGitUrl(), pointer.getBranch(), pointer.getActiveCommit(),
                pointer.getUpdatedBy(), pointer.getUpdatedAt(), pointer.getSyncedAt(), configured(pointer));
    }

    public synchronized SkillsPointerEntity update(String gitUrl, String branch, String actor) {
        SkillsPointerEntity pointer = getOrCreate();
        pointer.setGitUrl(gitUrl == null ? "" : gitUrl.trim());
        pointer.setBranch(branch == null || branch.isBlank() ? "main" : branch.trim());
        pointer.setUpdatedBy(actor == null ? "system" : actor);
        pointer.setUpdatedAt(Instant.now());
        SkillsPointerEntity saved = repository.save(pointer);
        auditService.record(actor, "SKILLS_POINTER_UPDATE", "skills-pointer",
                "gitUrl=" + saved.getGitUrl() + ", branch=" + saved.getBranch());
        return saved;
    }

    public synchronized SkillsSyncResponse sync(String actor) {
        SkillsPointerEntity pointer = getOrCreate();
        String gitUrl = pointer.getGitUrl();
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "SKILLS_SYNC_FAILED", "Skills 仓库地址未配置");
        }
        String branch = pointer.getBranch() == null || pointer.getBranch().isBlank() ? "main" : pointer.getBranch();
        String commit = resolveRemoteCommit(gitUrl, branch);
        pointer.setActiveCommit(commit);
        pointer.setSyncedAt(Instant.now());
        pointer.setUpdatedAt(Instant.now());
        pointer.setUpdatedBy(actor == null ? "system" : actor);
        repository.save(pointer);
        auditService.record(actor, "SKILLS_SYNC", "skills-pointer",
                "gitUrl=" + gitUrl + ", branch=" + branch + ", commit=" + commit);
        return new SkillsSyncResponse(commit, pointer.getSyncedAt());
    }

    public synchronized SkillsPointerEntity rollback(String commit, String actor) {
        if (commit == null || !COMMIT_PATTERN.matcher(commit.trim()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMMIT", "commit 必须是 40 或 64 位十六进制字符串");
        }
        SkillsPointerEntity pointer = getOrCreate();
        pointer.setActiveCommit(commit.trim().toLowerCase());
        pointer.setUpdatedAt(Instant.now());
        pointer.setUpdatedBy(actor == null ? "system" : actor);
        SkillsPointerEntity saved = repository.save(pointer);
        auditService.record(actor, "SKILLS_ROLLBACK", "skills-pointer", "commit=" + saved.getActiveCommit());
        return saved;
    }

    public boolean configured(SkillsPointerEntity pointer) {
        return pointer != null && pointer.getGitUrl() != null && !pointer.getGitUrl().isBlank();
    }

    private String resolveRemoteCommit(String gitUrl, String branch) {
        ProcessBuilder builder = new ProcessBuilder("git", "ls-remote", gitUrl, branch);
        builder.redirectErrorStream(true);
        Process process = null;
        try {
            process = builder.start();
            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw syncFailed("git ls-remote 超时（" + GIT_TIMEOUT_SECONDS + "秒）");
            }
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (process.exitValue() != 0) {
                throw syncFailed("git ls-remote 退出码 " + process.exitValue() + ": " + firstLine(output));
            }
            String first = firstLine(output);
            if (first.isBlank()) {
                throw syncFailed("分支不存在或没有返回 commit: " + branch);
            }
            String candidate = first.split("\\s+")[0].trim();
            if (!COMMIT_PATTERN.matcher(candidate).matches()) {
                throw syncFailed("git ls-remote 返回了非法 commit: " + candidate);
            }
            return candidate.toLowerCase();
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw syncFailed("无法执行 git: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw syncFailed("git ls-remote 被中断");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String firstLine(String output) {
        String[] lines = output.split("\\R");
        for (String line : lines) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    private ApiException syncFailed(String message) {
        log.warn("Skills 同步失败: {}", message);
        return new ApiException(HttpStatus.CONFLICT, "SKILLS_SYNC_FAILED", message);
    }
}
