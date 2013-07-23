/*******************************************************************************
 * Copyright (c) 2012, 2013 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.gerrit.core.remote;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.mylyn.gerrit.tests.support.GerritProject.CommitResult;
import org.eclipse.mylyn.internal.gerrit.core.client.GerritChange;
import org.eclipse.mylyn.internal.gerrit.core.client.GerritException;
import org.eclipse.mylyn.reviews.core.model.IApprovalType;
import org.eclipse.mylyn.reviews.core.model.IChange;
import org.eclipse.mylyn.reviews.core.model.IComment;
import org.eclipse.mylyn.reviews.core.model.IRepository;
import org.eclipse.mylyn.reviews.core.model.IRequirementEntry;
import org.eclipse.mylyn.reviews.core.model.IReview;
import org.eclipse.mylyn.reviews.core.model.IReviewItemSet;
import org.eclipse.mylyn.reviews.core.model.IReviewerEntry;
import org.eclipse.mylyn.reviews.core.model.IUser;
import org.eclipse.mylyn.reviews.core.model.RequirementStatus;
import org.eclipse.mylyn.reviews.core.model.ReviewStatus;
import org.eclipse.mylyn.reviews.core.spi.remote.emf.RemoteEmfConsumer;
import org.junit.Test;

import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change.Status;
import com.google.gerrit.reviewdb.ChangeMessage;

/**
 * @author Miles Parker
 */
public class GerritReviewRemoteFactoryTest extends GerritRemoteTest {

	public void testGlobalComments() throws Exception {
		String message1 = "new comment, time: " + System.currentTimeMillis(); //$NON-NLS-1$
		reviewHarness.client.publishComments(reviewHarness.shortId, 1, message1,
				Collections.<ApprovalCategoryValue.Id> emptySet(), null);
		String message2 = "new comment, time: " + System.currentTimeMillis(); //$NON-NLS-1$
		reviewHarness.client.publishComments(reviewHarness.shortId, 1, message2,
				Collections.<ApprovalCategoryValue.Id> emptySet(), null);
		reviewHarness.consumer.retrieve(false);
		reviewHarness.listener.waitForResponse(2, 2);
		List<IComment> comments = getReview().getComments();
		assertThat(comments.size(), is(2));
		IComment comment1 = comments.get(0);
		assertThat(comment1.getAuthor().getDisplayName(), is("tests"));
		assertThat(comment1.getDescription(), is("Patch Set 1:\n\n" + message1));
		IComment comment2 = comments.get(1);
		assertThat(comment2.getAuthor().getDisplayName(), is("tests"));
		assertThat(comment2.getDescription(), is("Patch Set 1:\n\n" + message2));
	}

	@Test
	public void testReviewStatus() throws Exception {
		assertThat(GerritReviewRemoteFactory.getReviewStatus(Status.ABANDONED), is(ReviewStatus.ABANDONED));
		assertThat(GerritReviewRemoteFactory.getReviewStatus(Status.MERGED), is(ReviewStatus.MERGED));
		assertThat(GerritReviewRemoteFactory.getReviewStatus(Status.NEW), is(ReviewStatus.NEW));
		assertThat(GerritReviewRemoteFactory.getReviewStatus(Status.SUBMITTED), is(ReviewStatus.SUBMITTED));
		//Test for drafts hack
		assertThat(GerritReviewRemoteFactory.getReviewStatus(null), is(ReviewStatus.DRAFT));
	}

	@Test
	public void testNewChange() throws Exception {
		CommitCommand command2 = reviewHarness.git.commit()
				.setAmend(true)
				.setAll(true)
				.setMessage("Test Change " + reviewHarness.testIdent + " [2]\n\nChange-Id: " + reviewHarness.changeId);
		reviewHarness.gerritHarness.project().addFile("testFile2.txt");
		reviewHarness.gerritHarness.project().commitAndPush(command2);
		reviewHarness.consumer.retrieve(false);
		reviewHarness.listener.waitForResponse(2, 2);
		List<IReviewItemSet> items = getReview().getSets();
		assertThat(items.size(), is(2));
		IReviewItemSet patchSet2 = items.get(1);
		assertThat(patchSet2.getReference(), endsWith("/2"));
		reviewHarness.assertIsRecent(patchSet2.getCreationDate());
	}

	@Test
	public void testAccount() throws Exception {
		assertThat(reviewHarness.getRepository().getAccount(), notNullValue());
		assertThat(reviewHarness.getRepository().getAccount().getDisplayName(), is("tests"));
		assertThat(reviewHarness.getRepository().getAccount().getEmail(), is("tests@mylyn.eclipse.org"));
		assertThat(reviewHarness.getRepository().getUsers().get(0), is(reviewHarness.getRepository().getAccount()));
	}

	@Test
	public void testUsers() throws Exception {
		assertThat(reviewHarness.getRepository().getUsers().size(), is(1));
		assertThat(reviewHarness.getRepository().getUsers().get(0).getDisplayName(), is("tests"));
		assertThat(reviewHarness.getRepository().getUsers().get(0).getEmail(), is("tests@mylyn.eclipse.org"));
	}

	@Test
	public void testApprovals() throws Exception {
		assertThat(reviewHarness.getRepository().getApprovalTypes().size(), is(2));
		IApprovalType verifyApproval = reviewHarness.getRepository().getApprovalTypes().get(0);
		assertThat(verifyApproval.getKey(), is("VRIF"));
		assertThat(verifyApproval.getName(), is("Verified"));
		IApprovalType codeReviewApproval = reviewHarness.getRepository().getApprovalTypes().get(1);
		assertThat(codeReviewApproval.getKey(), is("CRVW"));
		assertThat(codeReviewApproval.getName(), is("Code Review"));

		String approvalMessage = "approval, time: " + System.currentTimeMillis(); //$NON-NLS-1$
		reviewHarness.client.publishComments(
				reviewHarness.shortId,
				1,
				approvalMessage,
				new HashSet<ApprovalCategoryValue.Id>(Collections.singleton(new ApprovalCategoryValue.Id(
						new ApprovalCategory.Id("CRVW"), (short) 1))), null);
		reviewHarness.consumer.retrieve(false);
		reviewHarness.listener.waitForResponse(2, 2);
		assertThat(getReview().getReviewerApprovals().size(), is(1));
		Entry<IUser, IReviewerEntry> reviewerEntry = getReview().getReviewerApprovals().entrySet().iterator().next();
		Map<IApprovalType, Integer> reviewerApprovals = reviewerEntry.getValue().getApprovals();
		assertThat(reviewerApprovals.size(), is(1));
		Entry<IApprovalType, Integer> next = reviewerApprovals.entrySet().iterator().next();
		assertThat(next.getKey(), sameInstance(codeReviewApproval));
		assertThat(next.getValue(), is(1));

		Set<Entry<IApprovalType, IRequirementEntry>> reviewApprovals = getReview().getRequirements().entrySet();
		assertThat(reviewApprovals.size(), is(2));
		IRequirementEntry codeReviewEntry = getReview().getRequirements().get(codeReviewApproval);
		assertThat(codeReviewEntry, notNullValue());
		assertThat(codeReviewEntry.getBy(), nullValue());
		assertThat(codeReviewEntry.getStatus(), is(RequirementStatus.NOT_SATISFIED));
		IRequirementEntry verifyEntry = getReview().getRequirements().get(verifyApproval);
		assertThat(verifyEntry, notNullValue());
		assertThat(verifyEntry.getBy(), nullValue());
		assertThat(verifyEntry.getStatus(), is(RequirementStatus.NOT_SATISFIED));
		assertThat(getReview().getState(), is(ReviewStatus.NEW));
	}

	@Test
	public void testDependencies() throws Exception {
		String changeIdDep1 = "I" + StringUtils.rightPad(System.currentTimeMillis() + "", 40, "a");
		CommitCommand commandDep1 = reviewHarness.git.commit()
				.setAll(true)
				.setMessage("Test Change Dependent 1 " + reviewHarness.testIdent + "\n\nChange-Id: " + changeIdDep1);
		reviewHarness.gerritHarness.project().addFile("testFile1.txt", "test 2");
		CommitResult resultDep1 = reviewHarness.gerritHarness.project().commitAndPush(commandDep1);
		String resultIdDep1 = StringUtils.trimToEmpty(StringUtils.substringAfterLast(resultDep1.push.getMessages(), "/"));
		assertThat("Bad Push: " + resultDep1.push.getMessages(), resultIdDep1.length(), greaterThan(0));

		TestRemoteObserver<IRepository, IReview, String, Date> reviewListenerDep1 = new TestRemoteObserver<IRepository, IReview, String, Date>(
				reviewHarness.provider.getReviewFactory());
		RemoteEmfConsumer<IRepository, IReview, String, GerritChange, String, Date> consumerDep1 = reviewHarness.provider.getReviewFactory()
				.getConsumerForRemoteKey(reviewHarness.getRepository(), resultIdDep1);
		consumerDep1.addObserver(reviewListenerDep1);
		consumerDep1.retrieve(false);
		reviewListenerDep1.waitForResponse(1, 1);
		IReview reviewDep1 = consumerDep1.getModelObject();

		assertThat(reviewDep1.getParents().size(), is(1));
		IChange parentChange = reviewDep1.getParents().get(0);
		//Not expected to be same instance
		assertThat(parentChange.getId(), is(getReview().getId()));
		assertThat(parentChange.getSubject(), is(getReview().getSubject()));
		assertThat(parentChange.getModificationDate(), is(getReview().getModificationDate()));

		reviewHarness.consumer.retrieve(false);
		reviewHarness.listener.waitForResponse(2, 2);
		assertThat(getReview().getChildren().size(), is(1));
		IChange childChange = getReview().getChildren().get(0);
		//Not expected to be same instance
		assertThat(childChange.getId(), is(reviewDep1.getId()));
		assertThat(childChange.getSubject(), is(reviewDep1.getSubject()));
		assertThat(childChange.getModificationDate(), is(reviewDep1.getModificationDate()));
	}

	@Test
	public void testAbandonChange() throws Exception {
		String message1 = "abandon, time: " + System.currentTimeMillis(); //$NON-NLS-1$

		ChangeDetail changeDetail = reviewHarness.client.abandon(reviewHarness.shortId, 1, message1,
				new NullProgressMonitor());
		reviewHarness.consumer.retrieve(false);
		reviewHarness.listener.waitForResponse(2, 2);

		assertThat(changeDetail, notNullValue());
		assertThat(changeDetail.getChange().getStatus(), is(Status.ABANDONED));
		List<ChangeMessage> messages = changeDetail.getMessages();
		assertThat(messages.size(), is(1));
		ChangeMessage lastMessage = messages.get(0);
		assertThat(lastMessage.getAuthor().get(), is(1000001));
		assertThat(lastMessage.getMessage(), endsWith("Abandoned\n\n" + message1));

		assertThat(getReview().getState(), is(ReviewStatus.ABANDONED));
		List<IComment> comments = getReview().getComments();
		assertThat(comments.size(), is(1));
		IComment lastComment = comments.get(0);
		assertThat(lastComment.getAuthor().getDisplayName(), is("tests"));
		assertThat(lastComment.getAuthor().getId(), is("1000001"));
		assertThat(lastComment.getDescription(), endsWith("Abandoned\n\n" + message1));
	}

	@Test
	public void testRestoreChange() throws Exception {
		String message1 = "abandon, time: " + System.currentTimeMillis();
		reviewHarness.client.abandon(reviewHarness.shortId, 1, message1, new NullProgressMonitor());
		reviewHarness.consumer.retrieve(false);
		reviewHarness.listener.waitForResponse(2, 2);
		String message2 = "restore, time: " + System.currentTimeMillis();

		reviewHarness.client.restore(reviewHarness.shortId, 1, message2, new NullProgressMonitor());
		reviewHarness.consumer.retrieve(false);
		reviewHarness.listener.waitForResponse(3, 3);

		assertThat(getReview().getState(), is(ReviewStatus.NEW));
		List<IComment> comments = getReview().getComments();
		assertThat(comments.size(), is(2)); // abandon + restore
		IComment lastComment = comments.get(1);
		assertThat(lastComment.getAuthor().getDisplayName(), is("tests"));
		assertThat(lastComment.getDescription(), is("Patch Set 1: Restored\n\n" + message2));
	}

	@Test
	public void testRestoreNewChange() throws Exception {
		assertThat(getReview().getState(), is(ReviewStatus.NEW));
		String message1 = "restore, time: " + System.currentTimeMillis();
		try {
			reviewHarness.client.restore(reviewHarness.shortId, 1, message1, new NullProgressMonitor());
			fail("Expected to fail when restoring a new change");
		} catch (GerritException e) {
			assertThat(e.getMessage(), is("Not Found"));
		}
	}

	public void testCannotSubmitChange() throws Exception {
		try {
			reviewHarness.client.submit(reviewHarness.shortId, 1, new NullProgressMonitor());
			fail("Expected to fail when submitting a change without approvals");
		} catch (GerritException e) {
			assertThat(e.getMessage(), startsWith("Cannot submit change"));
		}
	}

	@Test
	public void testAddNullReviewers() throws Exception {
		try {
			reviewHarness.client.addReviewers(reviewHarness.shortId, null, new NullProgressMonitor());
			fail("Expected to fail when trying to add null reviewers");
		} catch (GerritException e) {
			assertThat(e.getMessage(), is("Internal Server Error"));
		}
	}

	@Test
	public void testAddEmptyReviewers() throws Exception {
		ReviewerResult reviewerResult = reviewHarness.client.addReviewers(reviewHarness.shortId,
				Collections.<String> emptyList(), new NullProgressMonitor());
		assertThat(reviewerResult.getErrors().isEmpty(), is(true));
	}

	@Test
	public void testAddInvalidReviewers() throws Exception {
		List<String> reviewers = Arrays.asList(new String[] { "foo" });
		ReviewerResult reviewerResult = reviewHarness.client.addReviewers(reviewHarness.shortId, reviewers,
				new NullProgressMonitor());
		assertThat(reviewerResult.getErrors().size(), is(1));
		assertThat(reviewerResult.getErrors().get(0).getName(), is("foo"));
	}

	@Test
	public void testAddSomeInvalidReviewers() throws Exception {
		List<String> reviewers = Arrays.asList(new String[] { "tests", "foo" });
		ReviewerResult reviewerResult = reviewHarness.client.addReviewers(reviewHarness.shortId, reviewers,
				new NullProgressMonitor());
		assertThat(reviewerResult.getErrors().isEmpty(), is(false));
		assertThat(reviewerResult.getErrors().size(), is(1));
		assertThat(reviewerResult.getErrors().get(0).getName(), is("foo"));
	}

	@Test
	public void testAddReviewers() throws Exception {
		List<String> reviewers = Arrays.asList(new String[] { "tests" });
		ReviewerResult reviewerResult = reviewHarness.client.addReviewers(reviewHarness.shortId, reviewers,
				new NullProgressMonitor());
		assertThat(reviewerResult.getErrors().isEmpty(), is(true));
	}

	@Test
	public void testAddReviewersByEmail() throws Exception {
		List<String> reviewers = Arrays.asList(new String[] { "tests@mylyn.eclipse.org" });
		ReviewerResult reviewerResult = reviewHarness.client.addReviewers(reviewHarness.shortId, reviewers,
				new NullProgressMonitor());
		assertThat(reviewerResult.getErrors().isEmpty(), is(true));
	}

	@Test
	public void testCannotRebaseChangeAlreadyUpToDate() throws Exception {
		try {
			reviewHarness.client.rebase(reviewHarness.shortId, 1, new NullProgressMonitor());
			fail("Expected to fail when rebasing a change that is already up to date");
		} catch (GerritException e) {
			assertThat(e.getMessage(), is("Change is already up to date."));
		}
	}
}
