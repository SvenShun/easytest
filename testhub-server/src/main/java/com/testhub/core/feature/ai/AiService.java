package com.testhub.core.feature.ai;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.casemgmt.CaseSource;
import com.testhub.core.feature.casemgmt.ClientCase;
import com.testhub.core.feature.casemgmt.ClientCaseRepository;
import com.testhub.core.feature.casemgmt.ReviewStatus;
import com.testhub.core.feature.requirement.Requirement;
import com.testhub.core.feature.requirement.RequirementRepository;
import com.testhub.core.feature.support.PageSupport;
import com.testhub.core.feature.system.ModelConfigService;
import com.testhub.core.feature.system.ModelCredential;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Requirement -> BDD draft generation, persisted as draft cases. */
@Service
public class AiService {

    private static final String PROMPT_VERSION = "v1";
    private static final int DEFAULT_SAMPLE_COUNT = 3;
    private static final int MAX_SAMPLE_COUNT = 10;
    private static final int MAX_DRAFTS = 3;

    private final List<TextModelProvider> providers;
    private final ModelConfigService modelConfigService;
    private final AiJobRepository jobRepository;
    private final ClientCaseRepository caseRepository;
    private final RequirementRepository requirementRepository;

    public AiService(List<TextModelProvider> providers, ModelConfigService modelConfigService,
                     AiJobRepository jobRepository, ClientCaseRepository caseRepository,
                     RequirementRepository requirementRepository) {
        this.providers = providers;
        this.modelConfigService = modelConfigService;
        this.jobRepository = jobRepository;
        this.caseRepository = caseRepository;
        this.requirementRepository = requirementRepository;
    }

    public GenerateBddResponse generate(GenerateBddRequest request) {
        ModelCredential credential = modelConfigService.requireTextCredential();
        String requirementText = resolveRequirementText(request);
        TextModelProvider provider = providers.stream()
                .filter(candidate -> candidate.supports(credential.provider()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER",
                        "不支持的文本模型 provider: " + credential.provider()));
        List<BddSample> samples = samples(sampleCount(request));
        TextModelInput input = new TextModelInput(credential.provider(), credential.endpoint(),
                credential.apiKey(), credential.modelId(), requirementText, samples);

        AiJob job = new AiJob(Ids.newId("job"), blankToNull(request.requirementId()), credential.modelId(),
                PROMPT_VERSION, AiJobStatus.RUNNING, Instant.now());
        jobRepository.save(job);
        try {
            List<BddDraft> drafts = provider.generate(input);
            List<BddDraft> valid = (drafts == null ? List.<BddDraft>of() : drafts).stream()
                    .filter(this::isValid)
                    .limit(MAX_DRAFTS)
                    .toList();
            if (valid.isEmpty()) {
                throw new IllegalArgumentException("模型未返回有效的 BDD 草稿");
            }
            String actor = currentActor();
            Instant now = Instant.now();
            List<BddDraftResult> results = new ArrayList<>();
            List<String> caseIds = new ArrayList<>();
            for (BddDraft draft : valid) {
                ClientCase clientCase = new ClientCase();
                clientCase.setId(Ids.newId("case"));
                clientCase.setTitle(draft.title().trim());
                clientCase.setBddGiven(draft.given().trim());
                clientCase.setBddWhen(draft.when().trim());
                clientCase.setBddThen(draft.then().trim());
                clientCase.setRequirementId(blankToNull(request.requirementId()));
                clientCase.setReviewStatus(ReviewStatus.DRAFT);
                clientCase.setSource(CaseSource.AI);
                clientCase.setModelId(credential.modelId());
                clientCase.setCreatedBy(actor);
                clientCase.setCreatedAt(now);
                clientCase.setUpdatedAt(now);
                ClientCase saved = caseRepository.save(clientCase);
                caseIds.add(saved.getId());
                results.add(new BddDraftResult(saved.getId(), saved.getTitle(), saved.getBddGiven(),
                        saved.getBddWhen(), saved.getBddThen(), saved.getReviewStatus().name()));
            }
            job.setStatus(AiJobStatus.SUCCEEDED);
            job.setOutputCaseIds(String.join(",", caseIds));
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            return new GenerateBddResponse(job.getId(), AiJobStatus.SUCCEEDED.name(), credential.modelId(),
                    results);
        } catch (RuntimeException e) {
            job.setStatus(AiJobStatus.FAILED);
            job.setError(truncate(e.getMessage()));
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED",
                    e.getMessage() == null ? "AI 生成失败" : e.getMessage());
        }
    }

    public PageResponse<AiJobView> listJobs(Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageSupport.of(jobRepository.findAll(pageRequest), AiJobView::of);
    }

    public AiJobView getJob(String id) {
        return jobRepository.findById(id)
                .map(AiJobView::of)
                .orElseThrow(() -> ApiException.notFound("AI 分析任务不存在: " + id));
    }

    private String resolveRequirementText(GenerateBddRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        if (request.requirementText() != null && !request.requirementText().isBlank()) {
            return request.requirementText().trim();
        }
        if (request.requirementId() != null && !request.requirementId().isBlank()) {
            Requirement requirement = requirementRepository.findById(request.requirementId().trim())
                    .orElseThrow(() -> ApiException.notFound("需求不存在: " + request.requirementId()));
            if (requirement.getDescription() != null && !requirement.getDescription().isBlank()) {
                return requirement.getDescription().trim();
            }
            return requirement.getTitle();
        }
        throw ApiException.badRequest("requirementText 不能为空");
    }

    private int sampleCount(GenerateBddRequest request) {
        Integer requested = request == null ? null : request.sampleCount();
        if (requested == null) {
            return DEFAULT_SAMPLE_COUNT;
        }
        return Math.max(0, Math.min(requested, MAX_SAMPLE_COUNT));
    }

    private List<BddSample> samples(int count) {
        if (count <= 0) {
            return List.of();
        }
        return caseRepository
                .findByReviewStatusOrderByUpdatedAtDesc(ReviewStatus.APPROVED, PageRequest.of(0, count))
                .stream()
                .map(clientCase -> new BddSample(clientCase.getBddGiven(), clientCase.getBddWhen(),
                        clientCase.getBddThen()))
                .toList();
    }

    private boolean isValid(BddDraft draft) {
        return draft != null
                && draft.title() != null && !draft.title().isBlank()
                && draft.given() != null && !draft.given().isBlank()
                && draft.when() != null && !draft.when().isBlank()
                && draft.then() != null && !draft.then().isBlank();
    }

    private String currentActor() {
        AuthPrincipal principal = CurrentUser.get();
        return principal == null ? "system" : principal.username();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
