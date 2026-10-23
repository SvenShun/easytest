package com.testhub.core.feature.casemgmt;

import java.time.Instant;

public record CaseReviewView(String id, String action, String actor, String comment, Instant createdAt) {

    public static CaseReviewView of(CaseReview review) {
        return new CaseReviewView(review.getId(), review.getAction().name(), review.getActor(),
                review.getComment(), review.getCreatedAt());
    }
}
