package com.testhub.core.feature.uielement;

import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.support.PageSupport;
import com.testhub.core.feature.uielement.UiElementDtos.ElementRequest;
import com.testhub.core.feature.uielement.UiElementDtos.ElementView;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UiElementService {

    private static final Set<String> TYPES = Set.of("TEXT", "ID", "XPATH", "COORD");

    private final UiElementRepository elements;

    public UiElementService(UiElementRepository elements) {
        this.elements = elements;
    }

    public PageResponse<ElementView> list(String keyword, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<UiElement> result = elements.findAll(spec(keyword), pageRequest);
        return PageSupport.of(result, this::view);
    }

    public ElementView get(String id) {
        return view(require(id));
    }

    @Transactional
    public ElementView create(ElementRequest request) {
        validate(request);
        if (elements.existsByName(request.name().trim())) {
            throw ApiException.conflict("元素名称已存在: " + request.name());
        }
        Instant now = Instant.now();
        UiElement element = new UiElement(Ids.newId("ele"), request.name().trim(), request.elementType().trim(),
                request.elementValue().trim(), blankToNull(request.description()),
                CurrentUser.require().username(), now, now);
        return view(elements.save(element));
    }

    @Transactional
    public ElementView update(String id, ElementRequest request) {
        validate(request);
        UiElement element = require(id);
        elements.findByName(request.name().trim()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw ApiException.conflict("元素名称已存在: " + request.name());
            }
        });
        element.setName(request.name().trim());
        element.setElementType(request.elementType().trim());
        element.setElementValue(request.elementValue().trim());
        element.setDescription(blankToNull(request.description()));
        element.setUpdatedAt(Instant.now());
        return view(elements.save(element));
    }

    @Transactional
    public void delete(String id) {
        elements.delete(require(id));
    }

    private void validate(ElementRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("元素名称不能为空");
        }
        if (request.elementType() == null || !TYPES.contains(request.elementType().trim().toUpperCase(Locale.ROOT))) {
            throw ApiException.badRequest("元素类型仅支持 TEXT/ID/XPATH/COORD");
        }
        if (request.elementValue() == null || request.elementValue().isBlank()) {
            throw ApiException.badRequest("元素值不能为空");
        }
    }

    private UiElement require(String id) {
        return elements.findById(id).orElseThrow(() -> ApiException.notFound("元素不存在: " + id));
    }

    private ElementView view(UiElement element) {
        return new ElementView(element.getId(), element.getName(), element.getElementType(),
                element.getElementValue(), element.getDescription(), element.getCreatedBy(),
                element.getCreatedAt(), element.getUpdatedAt());
    }

    private Specification<UiElement> spec(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            predicates.add(cb.like(cb.lower(root.get("elementValue")), pattern));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
