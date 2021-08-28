package com.github.avano.pr.workflow.config;

public class Constants {
    public static final String CHECKRUN_EVENT = "check_run";
    public static final String PULL_REQUEST_EVENT = "pull_request";
    public static final String REVIEW_EVENT = "pull_request_review";
    public static final String STATUS_EVENT = "status";

    public static final String PR_UPDATED = "pr.updated";

    public static final String PR_REVIEW_REQUESTED = "pr.review.requested";
    public static final String PR_REVIEW_REQUEST_REMOVED = "pr.review.request.removed";

    public static final String PR_READY_FOR_REVIEW = "pr.ready.for.review";

    public static final String PR_REVIEW_SUBMITTED = "pr.review.submitted";

    public static final String PR_MERGE = "pr.merge";

    public static final String PR_CHECK_CONFLICT = "pr.conflict";

    public static final String PR_UNLABELED = "pr.unlabeled";
    public static final String EDIT_LABELS = "pr.labels";

    public static final String PR_REOPENED = "pr.reopened";

    public static final String STATUS_CHANGED = "status.changed";

    public static final String CHECK_RUN_FINISHED = "run.finished";
    public static final String CHECK_RUN_CREATE = "run.create";

    public static final String EVENT_PUBLISHED_MESSAGE = "Event published to destination: ";
    public static final String EVENT_RECEIVED_MESSAGE = "Event received from destination: ";


    public static final String JSON_REPOSITORY = "repository";
    public static final String JSON_REPOSITORY_NAME = "full_name";

    public static final String JSON_REQUESTED_REVIEWER = "requested_reviewer";
    public static final String JSON_REQUESTED_REVIEWER_LOGIN = "login";

    public static final String JSON_LABEL = "label";
    public static final String JSON_LABEL_NAME = "name";

    public static final String DEPENDABOT_NAME = "dependabot[bot]";
}
