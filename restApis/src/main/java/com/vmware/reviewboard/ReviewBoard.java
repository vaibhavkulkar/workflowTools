package com.vmware.reviewboard;

import com.vmware.AbstractRestService;
import com.vmware.rest.cookie.ApiAuthentication;
import com.vmware.rest.RestConnection;
import com.vmware.rest.request.UrlParam;
import com.vmware.rest.credentials.UsernamePasswordAsker;
import com.vmware.rest.credentials.UsernamePasswordCredentials;
import com.vmware.rest.request.RequestBodyHandling;
import com.vmware.reviewboard.domain.DiffToUpload;
import com.vmware.reviewboard.domain.Link;
import com.vmware.reviewboard.domain.ResultsCount;
import com.vmware.reviewboard.domain.ReviewRequest;
import com.vmware.reviewboard.domain.ReviewRequestDiff;
import com.vmware.reviewboard.domain.ReviewRequestDiffsResponse;
import com.vmware.reviewboard.domain.ReviewRequestDraft;
import com.vmware.reviewboard.domain.ReviewRequestDraftResponse;
import com.vmware.reviewboard.domain.ReviewRequestResponse;
import com.vmware.reviewboard.domain.ReviewRequestStatus;
import com.vmware.reviewboard.domain.ReviewRequests;
import com.vmware.reviewboard.domain.RootList;
import com.vmware.reviewboard.domain.ServerInfo;
import com.vmware.reviewboard.domain.ServerInfoResponse;
import com.vmware.reviewboard.domain.UserReview;
import com.vmware.reviewboard.domain.UserReviewsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static com.vmware.rest.cookie.ApiAuthentication.reviewBoard;
import static com.vmware.rest.request.RequestHeader.anAcceptHeader;
import static com.vmware.reviewboard.domain.ReviewRequestDraft.anEmptyDraftForPublishingAReview;
import static com.vmware.reviewboard.domain.ReviewRequestStatus.all;
import static com.vmware.reviewboard.domain.ReviewRequestStatus.pending;

public class ReviewBoard extends AbstractRestService {
    private ServerInfo cachedServerInfo = null;
    private RootList cachedRootList = null;

    private Logger log = LoggerFactory.getLogger(this.getClass());

    public ReviewBoard(String reviewboardUrl, String username) throws IOException, URISyntaxException, IllegalAccessException {
        super(reviewboardUrl, "api/", ApiAuthentication.reviewBoard, username);
        connection = new RestConnection(RequestBodyHandling.AsUrlEncodedFormEntity);
    }

    public RootList getRootLinkList() throws IOException, URISyntaxException {
        if (cachedRootList == null) {
            cachedRootList = connection.get(apiUrl, RootList.class);
        }
        return cachedRootList;
    }

    public ReviewRequests getReviewRequests(ReviewRequestStatus status) throws IOException, URISyntaxException {
        Link reviewRequestLink = getRootLinkList().getReviewRequestsLink();
        return connection.get(reviewRequestLink.getHref(), ReviewRequests.class,
                new UrlParam("from-user", username), new UrlParam("status", status.name()));
    }

    public ReviewRequest[] getOpenReviewRequestsWithSubmittedComment() throws IOException, URISyntaxException {
        List<ReviewRequest> reviewRequestsWithSubmittedComments = new ArrayList<ReviewRequest>();
        for (ReviewRequest reviewRequest : getOpenReviewRequestsWithShipIts().review_requests) {
            UserReview softSubmitReview = getSoftSubmitReview(reviewRequest);
            if (softSubmitReview != null) {
                reviewRequestsWithSubmittedComments.add(reviewRequest);
            }
        }
        return reviewRequestsWithSubmittedComments.toArray(new ReviewRequest[reviewRequestsWithSubmittedComments.size()]);
    }

    public ReviewRequests getOpenReviewRequestsWithShipIts() throws IOException, URISyntaxException {
        Link reviewRequestLink = getRootLinkList().getReviewRequestsLink();
        return connection.get(reviewRequestLink.getHref(), ReviewRequests.class, new UrlParam("from-user", username),
                new UrlParam("status", pending.name()), new UrlParam("ship-it", "1"));
    }

    public int getFilesCountForReviewRequestDiff(Link filesLink) throws IOException, URISyntaxException {
        return connection.get(filesLink.getHref(), ResultsCount.class, new UrlParam("counts-only", "1")).count;
    }

    public ReviewRequests getReviewRequestsWithShipItsForGroups(String groupNames, Date fromDate) throws IOException, URISyntaxException {
        SimpleDateFormat formatter = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
        String formattedDate = formatter.format(fromDate);
        Link reviewRequestLink = getRootLinkList().getReviewRequestsLink();
        return connection.get(reviewRequestLink.getHref(), ReviewRequests.class,
                new UrlParam("to-groups", groupNames), new UrlParam("max-results", "200"),
                new UrlParam("time-added-from", formattedDate),
                new UrlParam("ship-it", "1"), new UrlParam("status", all.name()));
    }

    public ReviewRequest getReviewRequestById(Integer id) throws IOException, URISyntaxException {
        if (id == null) {
            return null;
        }
        Link reviewRequestLink = getRootLinkList().getReviewRequestsLink();
        reviewRequestLink.addPathParam(String.valueOf(id));
        return connection.get(reviewRequestLink.getHref(),ReviewRequestResponse.class).review_request;
    }

    public ReviewRequest createReviewRequest(String repository) throws IllegalAccessException, IOException, URISyntaxException {
        Link createLink = getReviewRequests(pending).getCreateLink();

        ReviewRequest initialReviewRequest = new ReviewRequest();
        initialReviewRequest.repository = repository;

        return connection.post(createLink.getHref(), ReviewRequestResponse.class, initialReviewRequest).review_request;
    }

    public ReviewRequest createReviewRequestFromDraft(ReviewRequestDraft reviewRequestDraft, String repository)
            throws IllegalAccessException, IOException, URISyntaxException {
        ReviewRequest addedRequest = createReviewRequest(repository);
        updateReviewRequestDraft(addedRequest.getDraftLink(), reviewRequestDraft);
        return getReviewRequestById(addedRequest.id);
    }

    public ReviewRequestDraft getReviewRequestDraft(Link draftLink) throws IOException, URISyntaxException {
        return connection.get(draftLink.getHref(), ReviewRequestDraftResponse.class).draft;
    }

    public ReviewRequestDraft updateReviewRequestDraft(Link draftLink, ReviewRequestDraft draft)
            throws IOException, URISyntaxException, IllegalAccessException {
        String existingTestingDone = draft.testingDone;
        draft.testingDone = draft.fullTestingDoneSectionWithoutJobResults();

        ReviewRequestDraft updatedDraft = connection.put(draftLink.getHref(), ReviewRequestDraftResponse.class, draft).draft;
        draft.testingDone = existingTestingDone;
        return updatedDraft;
    }

    public void publishReview(Link draftLink) throws IllegalAccessException, IOException, URISyntaxException {
        connection.put(draftLink.getHref(), anEmptyDraftForPublishingAReview());
    }

    public void updateReviewRequest(ReviewRequest reviewRequest)
            throws IllegalAccessException, IOException, URISyntaxException {
        connection.put(reviewRequest.getUpdateLink().getHref(), ReviewRequestResponse.class, reviewRequest);
    }

    public void createReviewRequestDiff(Link diffLink, DiffToUpload diffToCreate)
            throws IllegalAccessException, IOException, URISyntaxException {
        connection.post(diffLink.getHref(), diffToCreate);
    }

    public void createUserReview(ReviewRequest reviewRequest, UserReview review) throws IllegalAccessException, IOException, URISyntaxException {
        connection.post(reviewRequest.getReviewsLink().getHref(), review);
    }

    public UserReview[] getReviewsForReviewRequest(Link reviewsLink) throws IOException, URISyntaxException {
        return connection.get(reviewsLink.getHref(), UserReviewsResponse.class).reviews;
    }

    public ReviewRequestDiff[] getDiffsForReviewRequest(Link diffsLink) throws IOException, URISyntaxException {
        return connection.get(diffsLink.getHref(), ReviewRequestDiffsResponse.class).diffs;
    }

    public String getDiffData(Link diffLink) throws IOException, URISyntaxException {
        String diffData = connection.get(diffLink.getHref(), String.class, anAcceptHeader("text/x-patch"));
        // need to add in a trailing newline for git apply to work correctly
        diffData += "\n";
        return diffData;
    }

    public String getShipItReviewerList(ReviewRequest reviewRequest) throws IOException, URISyntaxException {
        UserReview[] reviews = this.getReviewsForReviewRequest(reviewRequest.getReviewsLink());

        String reviewerList = "";
        for (UserReview review : reviews) {
            if (review.isPublic && review.ship_it) {
                if (!reviewerList.contains(review.getReviewUsername())) {
                    if (!reviewerList.isEmpty()) {
                        reviewerList += ",";
                    }
                    reviewerList += review.getReviewUsername();
                }
            }
        }
        return reviewerList;
    }

    public UserReview getSoftSubmitReview(ReviewRequest reviewRequest) throws IOException, URISyntaxException {
        UserReview[] reviews = this.getReviewsForReviewRequest(reviewRequest.getReviewsLink());
        for (UserReview review : reviews) {
            if (review.getReviewUsername().equals(reviewRequest.getSubmitter())) {
                if (review.body_top.startsWith("Submitted as ref ")) {
                    return review;
                }
            }
        }
        return null;
    }

    public ServerInfo getServerInfo() throws IOException, URISyntaxException {
        if (cachedServerInfo == null) {
            cachedServerInfo = connection.get(getRootLinkList().getInfoLink().getHref(), ServerInfoResponse.class).info;
        }
        return cachedServerInfo;
    }

    public String getVersion() throws IOException, URISyntaxException {
        return getServerInfo().product.version;
    }

    public void updateServerTimeZone(String serverDateFormat) throws IOException, URISyntaxException {
        String serverTimeZone = getServerInfo().site.serverTimeZone;
        connection.updateServerTimeZone(TimeZone.getTimeZone(serverTimeZone), serverDateFormat);
    }

    @Override
    public void setupAuthenticatedConnection() throws IOException, URISyntaxException, IllegalAccessException {
        super.setupAuthenticatedConnection();
    }

    @Override
    protected void loginManually() throws IllegalAccessException, IOException, URISyntaxException {
        UsernamePasswordCredentials credentials = UsernamePasswordAsker.askUserForUsernameAndPassword(reviewBoard);
        connection.setupBasicAuthHeader(credentials);
    }

    @Override
    public void checkAuthenticationAgainstServer() throws IOException, URISyntaxException {
        getServerInfo();
        if (!connection.hasCookie(reviewBoard)) {
            log.warn("Cookie {} should have been retrieved from reviewboard login!", reviewBoard.getCookieName());
        }
    }

}
