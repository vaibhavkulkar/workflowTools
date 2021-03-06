package com.vmware.action.batch;

import com.vmware.action.base.AbstractBatchCloseReviews;
import com.vmware.config.ActionDescription;
import com.vmware.config.WorkflowConfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

@ActionDescription("Checks review board for assigned reviews that have at least one ship it." +
        "\nReviews older in days than the config property value closeOldShipItReviewsAfter are marked as submitted.")
public class HardSubmitOldShipItReviews extends AbstractBatchCloseReviews {

    public HardSubmitOldShipItReviews(WorkflowConfig config) throws IllegalAccessException, IOException, URISyntaxException {
        super(config, "ship its", config.closeOldShipItReviewsAfter);
    }

    @Override
    public void process() throws IOException, IllegalAccessException, URISyntaxException, ParseException {
        super.closeReviews(reviewBoard.getOpenReviewRequestsWithShipIts().review_requests);
    }
}
