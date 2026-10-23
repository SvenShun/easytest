package com.testhub.core.feature.requirement;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.support.PageSupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RequirementService {

    private final RequirementRepository repository;

    public RequirementService(RequirementRepository repository) {
        this.repository = repository;
    }

    public PageResponse<RequirementView> search(String keyword, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Requirement> result = repository.findAll(keywordSpec(keyword), pageRequest);
        return PageSupport.of(result, RequirementView::of);
    }

    public RequirementView get(String id) {
        return RequirementView.of(require(id));
    }

    public Requirement require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("需求不存在: " + id));
    }

    @Transactional
    public RequirementView create(RequirementRequest request) {
        validate(request);
        AuthPrincipal principal = CurrentUser.require();
        Instant now = Instant.now();
        Requirement requirement = new Requirement(Ids.newId("req"), request.title().trim(), request.description(),
                principal.username(), now, now);
        return RequirementView.of(repository.save(requirement));
    }

    @Transactional
    public RequirementView update(String id, RequirementRequest request) {
        validate(request);
        Requirement requirement = require(id);
        requirement.setTitle(request.title().trim());
        requirement.setDescription(request.description());
        requirement.setUpdatedAt(Instant.now());
        return RequirementView.of(repository.save(requirement));
    }

    @Transactional
    public void delete(String id) {
        repository.delete(require(id));
    }

    private void validate(RequirementRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw ApiException.badRequest("需求标题不能为空");
        }
    }

    private Specification<Requirement> keywordSpec(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            predicates.add(cb.like(cb.lower(root.get("description")), pattern));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
