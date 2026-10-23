package com.testhub.uiexec.service;

import com.testhub.common.util.Ids;
import com.testhub.uiexec.artifact.ArtifactPaths;
import com.testhub.uiexec.artifact.ArtifactStore;
import com.testhub.uiexec.domain.ArtifactEntity;
import com.testhub.uiexec.repository.ArtifactRepository;
import com.testhub.uiexec.web.dto.TaskDtos.ArtifactView;
import com.testhub.uiexec.web.dto.TaskDtos.ResultArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Stores agent-reported result artifacts and reads the task artifact list. */
@Service
public class ArtifactService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);

    private final ArtifactStore store;
    private final ArtifactRepository repository;

    public ArtifactService(ArtifactStore store, ArtifactRepository repository) {
        this.store = store;
        this.repository = repository;
    }

    public List<ArtifactView> storeResultArtifacts(String taskId, List<ResultArtifact> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<ArtifactView> views = new ArrayList<>();
        Instant now = Instant.now();
        for (ResultArtifact input : inputs) {
            if (input == null || input.contentBase64() == null || input.contentBase64().isBlank()) {
                continue;
            }
            byte[] content;
            try {
                content = Base64.getDecoder().decode(input.contentBase64());
            } catch (IllegalArgumentException e) {
                log.warn("忽略非法的产物 base64 taskId={} name={}", taskId, input.name());
                continue;
            }
            String name = ArtifactPaths.safeName(input.name());
            String type = input.type() == null || input.type().isBlank() ? "FILE" : input.type().trim();
            String relative;
            try {
                relative = store.put(taskId, "artifacts/" + name, content);
            } catch (RuntimeException e) {
                log.warn("产物保存失败 taskId={} name={}: {}", taskId, name, e.getMessage());
                continue;
            }
            String url = store.publicUrl(relative);
            ArtifactEntity entity = repository.findByTaskIdAndName(taskId, name)
                    .orElseGet(() -> new ArtifactEntity(Ids.newId("artifact"), taskId, name, type, url, now));
            entity.setName(name);
            entity.setType(type);
            entity.setUrl(url);
            repository.save(entity);
            views.add(new ArtifactView(name, type, url));
        }
        return views;
    }

    public List<ArtifactView> list(String taskId) {
        return repository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(entity -> new ArtifactView(entity.getName(), entity.getType(), entity.getUrl()))
                .toList();
    }
}
