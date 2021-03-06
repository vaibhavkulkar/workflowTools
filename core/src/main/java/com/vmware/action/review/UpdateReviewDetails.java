package com.vmware.action.review;

import com.vmware.action.base.AbstractCommitWithReviewAction;
import com.vmware.config.ActionDescription;
import com.vmware.config.WorkflowConfig;
import com.vmware.reviewboard.domain.ReviewRequest;

import java.io.IOException;
import java.net.URISyntaxException;

@ActionDescription("Updates the review request draft details (summary, description, testing done, bug number, groups, people).")
public class UpdateReviewDetails extends AbstractCommitWithReviewAction {
    public UpdateReviewDetails(WorkflowConfig config) throws IOException, URISyntaxException, IllegalAccessException {
        super(config);
    }

    @Override
    public void process() throws IOException, IllegalAccessException, URISyntaxException {
        ReviewRequest reviewRequest = draft.reviewRequest;
        log.info("Updating information for review " + reviewRequest.id);

        draft.setTargetGroups(config.targetGroups);

        reviewBoard.updateReviewRequestDraft(reviewRequest.getDraftLink(), draft);
        log.info("Successfully updated review information");
    }
}