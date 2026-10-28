package com.testhub.core.feature.kb;

import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.core.feature.kb.KbDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class KbService {

    private static final Set<String> CONTROL_TYPES = Set.of("BUTTON", "TEXT", "INPUT", "LIST", "IMAGE",
            "CHECKBOX", "SWITCH", "TAB", "OTHER");
    private static final Set<String> ACTION_TYPES = Set.of("CLICK", "LONG_PRESS", "SWIPE", "INPUT",
            "ASSERT_TEXT", "SCROLL", "CUSTOM");
    private static final Set<String> SOURCES = Set.of("MANUAL", "AI", "CRAWLER");

    private final KbAppRepository apps;
    private final KbPageRepository pages;
    private final KbControlRepository controls;
    private final KbActionRepository actions;

    public KbService(KbAppRepository apps, KbPageRepository pages, KbControlRepository controls,
                     KbActionRepository actions) {
        this.apps = apps;
        this.pages = pages;
        this.controls = controls;
        this.actions = actions;
    }

    public List<AppView> listApps() {
        return apps.findAllByOrderByUpdatedAtDesc().stream().map(this::appView).toList();
    }

    public AppView getApp(String id) {
        return appView(requireApp(id));
    }

    @Transactional
    public AppView createApp(AppRequest request) {
        validateApp(request);
        apps.findByName(request.name().trim()).ifPresent(existing -> {
            throw ApiException.conflict("应用名称已存在: " + request.name());
        });
        Instant now = Instant.now();
        KbApp app = new KbApp(Ids.newId("kbapp"), request.name().trim(), blank(request.packageName()),
                request.platform() == null ? "android" : request.platform().trim(),
                blank(request.description()), blank(request.iconUrl()),
                CurrentUser.require().username(), now, now);
        return appView(apps.save(app));
    }

    @Transactional
    public AppView updateApp(String id, AppRequest request) {
        validateApp(request);
        KbApp app = requireApp(id);
        apps.findByName(request.name().trim()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw ApiException.conflict("应用名称已存在: " + request.name());
            }
        });
        app.setName(request.name().trim());
        app.setPackageName(blank(request.packageName()));
        app.setPlatform(request.platform() == null ? "android" : request.platform().trim());
        app.setDescription(blank(request.description()));
        app.setIconUrl(blank(request.iconUrl()));
        app.setUpdatedAt(Instant.now());
        return appView(apps.save(app));
    }

    @Transactional
    public void deleteApp(String id) {
        requireApp(id);
        for (KbPage page : pages.findByAppIdOrderByNameAsc(id)) {
            deleteControlsOfPage(page.getId());
        }
        pages.deleteByAppId(id);
        apps.deleteById(id);
    }

    public List<PageView> listPages(String appId) {
        requireApp(appId);
        return pages.findByAppIdOrderByNameAsc(appId).stream().map(this::pageView).toList();
    }

    public PageView getPage(String id) {
        return pageView(requirePage(id));
    }

    @Transactional
    public PageView createPage(String appId, PageRequest request) {
        requireApp(appId);
        validatePage(request);
        Instant now = Instant.now();
        return pageView(pages.save(new KbPage(Ids.newId("kbpage"), appId, request.name().trim(),
                blank(request.route()), blank(request.description()), blank(request.screenshotUrl()), now, now)));
    }

    @Transactional
    public PageView updatePage(String id, PageRequest request) {
        validatePage(request);
        KbPage page = requirePage(id);
        page.setName(request.name().trim());
        page.setRoute(blank(request.route()));
        page.setDescription(blank(request.description()));
        page.setScreenshotUrl(blank(request.screenshotUrl()));
        page.setUpdatedAt(Instant.now());
        return pageView(pages.save(page));
    }

    @Transactional
    public void deletePage(String id) {
        requirePage(id);
        deleteControlsOfPage(id);
        pages.deleteById(id);
    }

    public List<ControlView> listControls(String pageId) {
        requirePage(pageId);
        return controls.findByPageIdOrderByUpdatedAtDesc(pageId).stream().map(this::controlView).toList();
    }

    public ControlView getControl(String id) {
        return controlView(requireControl(id));
    }

    @Transactional
    public ControlView createControl(String pageId, ControlRequest request) {
        requirePage(pageId);
        validateControl(request);
        return controlView(controls.save(buildControl(pageId, request)));
    }

    @Transactional
    public List<ControlView> batchControls(String pageId, BatchControlRequest request) {
        requirePage(pageId);
        if (request == null || request.controls() == null || request.controls().isEmpty()) {
            throw ApiException.badRequest("批量导入至少需要一条控件");
        }
        List<ControlView> result = new ArrayList<>();
        for (ControlRequest control : request.controls()) {
            validateControl(control);
            result.add(controlView(controls.save(buildControl(pageId, control))));
        }
        return result;
    }

    @Transactional
    public ControlView updateControl(String id, ControlRequest request) {
        validateControl(request);
        KbControl control = requireControl(id);
        control.setName(blank(request.name()));
        control.setText(blank(request.text()));
        control.setResourceId(blank(request.resourceId()));
        control.setDescription(blank(request.description()));
        control.setDesc(blank(request.desc()));
        control.setIntent(blank(request.intent()));
        control.setControlType(normalizeControlType(request.controlType()));
        control.setScreenshotUrl(blank(request.screenshotUrl()));
        control.setBounds(blank(request.bounds()));
        control.setSource(normalizeSource(request.source()));
        control.setConfidence(request.confidence());
        control.setUpdatedAt(Instant.now());
        return controlView(controls.save(control));
    }

    @Transactional
    public void deleteControl(String id) {
        requireControl(id);
        actions.deleteByControlId(id);
        controls.deleteById(id);
    }

    public List<ActionView> listActions(String controlId) {
        requireControl(controlId);
        return actions.findByControlIdOrderByCreatedAtAsc(controlId).stream().map(this::actionView).toList();
    }

    @Transactional
    public ActionView createAction(String controlId, ActionRequest request) {
        requireControl(controlId);
        validateAction(request);
        return actionView(actions.save(new KbAction(Ids.newId("kbact"), controlId,
                request.actionType().trim().toUpperCase(Locale.ROOT), blank(request.label()),
                blank(request.params()), normalizeSource(request.source()),
                request.enabled() == null || request.enabled(), Instant.now())));
    }

    @Transactional
    public ActionView updateAction(String id, ActionRequest request) {
        validateAction(request);
        KbAction action = actions.findById(id).orElseThrow(() -> ApiException.notFound("操作不存在: " + id));
        action.setActionType(request.actionType().trim().toUpperCase(Locale.ROOT));
        action.setLabel(blank(request.label()));
        action.setParams(blank(request.params()));
        action.setSource(normalizeSource(request.source()));
        action.setEnabled(request.enabled() == null || request.enabled());
        return actionView(actions.save(action));
    }

    @Transactional
    public void deleteAction(String id) {
        actions.deleteById(id);
    }

    private KbControl buildControl(String pageId, ControlRequest request) {
        Instant now = Instant.now();
        return new KbControl(Ids.newId("kbctl"), pageId, blank(request.name()), blank(request.text()),
                blank(request.resourceId()), blank(request.description()), blank(request.desc()),
                blank(request.intent()), normalizeControlType(request.controlType()), blank(request.screenshotUrl()),
                blank(request.bounds()), normalizeSource(request.source()), request.confidence(), now, now);
    }

    private void deleteControlsOfPage(String pageId) {
        for (KbControl control : controls.findByPageIdOrderByUpdatedAtDesc(pageId)) {
            actions.deleteByControlId(control.getId());
        }
        controls.deleteByPageId(pageId);
    }

    private void validateApp(AppRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("应用名称不能为空");
        }
    }

    private void validatePage(PageRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("页面名称不能为空");
        }
    }

    private void validateControl(ControlRequest request) {
        if (request == null) {
            throw ApiException.badRequest("控件不能为空");
        }
        if ((request.name() == null || request.name().isBlank())
                && (request.text() == null || request.text().isBlank())
                && (request.resourceId() == null || request.resourceId().isBlank())) {
            throw ApiException.badRequest("控件至少需要 name/text/resourceId 之一");
        }
        normalizeControlType(request.controlType());
        normalizeSource(request.source());
    }

    private void validateAction(ActionRequest request) {
        if (request == null || request.actionType() == null || request.actionType().isBlank()) {
            throw ApiException.badRequest("操作类型不能为空");
        }
        if (!ACTION_TYPES.contains(request.actionType().trim().toUpperCase(Locale.ROOT))) {
            throw ApiException.badRequest("操作类型仅支持 " + ACTION_TYPES);
        }
        normalizeSource(request.source());
    }

    private String normalizeControlType(String value) {
        String type = value == null || value.isBlank() ? "OTHER" : value.trim().toUpperCase(Locale.ROOT);
        if (!CONTROL_TYPES.contains(type)) {
            throw ApiException.badRequest("控件类型仅支持 " + CONTROL_TYPES);
        }
        return type;
    }

    private String normalizeSource(String value) {
        String source = value == null || value.isBlank() ? "MANUAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(source)) {
            throw ApiException.badRequest("来源仅支持 MANUAL/AI/CRAWLER");
        }
        return source;
    }

    private KbApp requireApp(String id) {
        return apps.findById(id).orElseThrow(() -> ApiException.notFound("应用不存在: " + id));
    }

    private KbPage requirePage(String id) {
        return pages.findById(id).orElseThrow(() -> ApiException.notFound("页面不存在: " + id));
    }

    private KbControl requireControl(String id) {
        return controls.findById(id).orElseThrow(() -> ApiException.notFound("控件不存在: " + id));
    }

    private AppView appView(KbApp app) {
        long pageCount = pages.countByAppId(app.getId());
        long controlCount = pages.findByAppIdOrderByNameAsc(app.getId()).stream()
                .mapToLong(page -> controls.countByPageId(page.getId())).sum();
        return new AppView(app.getId(), app.getName(), app.getPackageName(), app.getPlatform(),
                app.getDescription(), app.getIconUrl(), pageCount, controlCount, app.getCreatedBy(),
                app.getCreatedAt(), app.getUpdatedAt());
    }

    private PageView pageView(KbPage page) {
        return new PageView(page.getId(), page.getAppId(), page.getName(), page.getRoute(),
                page.getDescription(), page.getScreenshotUrl(), controls.countByPageId(page.getId()),
                page.getCreatedAt(), page.getUpdatedAt());
    }

    private ControlView controlView(KbControl control) {
        return new ControlView(control.getId(), control.getPageId(), control.getName(), control.getText(),
                control.getResourceId(), control.getDescription(), control.getDesc(), control.getIntent(),
                control.getControlType(),
                control.getScreenshotUrl(), control.getBounds(), control.getSource(), control.getConfidence(),
                actions.findByControlIdOrderByCreatedAtAsc(control.getId()).stream().map(this::actionView).toList(),
                control.getCreatedAt(), control.getUpdatedAt());
    }

    private ActionView actionView(KbAction action) {
        return new ActionView(action.getId(), action.getControlId(), action.getActionType(), action.getLabel(),
                action.getParams(), action.getSource(), action.isEnabled(), action.getCreatedAt());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
