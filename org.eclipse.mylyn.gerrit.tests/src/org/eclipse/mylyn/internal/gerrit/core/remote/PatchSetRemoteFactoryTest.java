/*******************************************************************************
 * Copyright (c) 2012 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.gerrit.core.remote;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.mylyn.internal.gerrit.core.client.PatchSetContent;
import org.eclipse.mylyn.reviews.core.model.IComment;
import org.eclipse.mylyn.reviews.core.model.IFileItem;
import org.eclipse.mylyn.reviews.core.model.IFileVersion;
import org.eclipse.mylyn.reviews.core.model.IReview;
import org.eclipse.mylyn.reviews.core.model.IReviewItem;
import org.eclipse.mylyn.reviews.core.model.IReviewItemSet;
import org.eclipse.mylyn.reviews.core.spi.remote.emf.RemoteEmfConsumer;
import org.junit.Test;

import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Patch;

/**
 * @author Miles Parker
 */
public class PatchSetRemoteFactoryTest extends GerritRemoteTest {

	@Test
	public void testPatchSetFiles() throws Exception {
		CommitCommand command2 = reviewHarness.git.commit()
				.setAmend(true)
				.setAll(true)
				.setMessage("Test Change " + reviewHarness.testIdent + " [2]\n\nChange-Id: " + reviewHarness.changeId);
		reviewHarness.gerritHarness.project().addFile("testFile2.txt");
		reviewHarness.gerritHarness.project().addFile("testFile3.txt");
		reviewHarness.gerritHarness.project().commitAndPush(command2);
		CommitCommand command3 = reviewHarness.git.commit()
				.setAmend(true)
				.setAll(true)
				.setMessage("Test Change " + reviewHarness.testIdent + " [2]\n\nChange-Id: " + reviewHarness.changeId);
		reviewHarness.gerritHarness.project().addFile("testFile2.txt", "testmod");
		reviewHarness.gerritHarness.project().addFile("testFile4.txt");
		reviewHarness.gerritHarness.project().addFile("testFile5.txt");
		reviewHarness.gerritHarness.project().commitAndPush(command3);
		reviewHarness.consumer.retrieve(false);
		reviewHarness.listener.waitForResponse(2, 2);

		assertThat(getReview().getSets().size(), is(3));
		IReviewItemSet testPatchSet = getReview().getSets().get(2);
		RemoteEmfConsumer<IReview, IReviewItemSet, String, PatchSetDetail, PatchSetDetail, String> itemSetConsumer = reviewHarness.provider.getReviewItemSetFactory()
				.getConsumerForLocalKey(getReview(), "3");
		TestRemoteObserver<IReview, IReviewItemSet, String, String> itemSetObserver = new TestRemoteObserver<IReview, IReviewItemSet, String, String>(
				reviewHarness.provider.getReviewItemSetFactory());
		itemSetConsumer.addObserver(itemSetObserver);
		itemSetConsumer.retrieve(false);
		itemSetObserver.waitForResponse(1, 0);
		PatchSetDetail detail = itemSetConsumer.getRemoteObject();
		assertThat(detail.getInfo().getKey().get(), is(3));

		PatchSetContentIdRemoteFactory patchFactory = reviewHarness.provider.getReviewItemSetContentFactory();
		List<IFileItem> fileItems = testPatchSet.getItems();
		assertThat(fileItems.size(), is(0));
		TestRemoteObserver<IReviewItemSet, List<IFileItem>, String, Long> patchSetListener = new TestRemoteObserver<IReviewItemSet, List<IFileItem>, String, Long>(
				patchFactory);
		RemoteEmfConsumer<IReviewItemSet, List<IFileItem>, String, PatchSetContent, String, Long> patchSetConsumer = patchFactory.getConsumerForRemoteKey(
				testPatchSet, "3");
		patchSetConsumer.addObserver(patchSetListener);
		patchSetConsumer.retrieve(false);
		patchSetListener.waitForResponse(1, 1);

		assertThat(fileItems.size(), is(6));
		for (IReviewItem fileItem : fileItems) {
			assertThat(fileItem, instanceOf(IFileItem.class));
			assertThat(fileItem.getAddedBy().getDisplayName(), is("tests"));
			assertThat(fileItem.getCommittedBy().getDisplayName(), is("tests"));
		}
		IFileItem fileItem0 = fileItems.get(0);
		assertThat(fileItem0.getName(), is("/COMMIT_MSG"));

		IFileItem fileItem1 = fileItems.get(1);
		assertThat(fileItem1.getName(), is("testFile1.txt"));

		IFileItem fileItem2 = fileItems.get(2);
		assertThat(fileItem2.getName(), is("testFile2.txt"));

		IFileVersion base2 = fileItem2.getBase();
		assertThat(base2.getAddedBy(), nullValue());
		assertThat(base2.getCommittedBy(), nullValue());
		assertThat(base2.getContent(), is(""));
		assertThat(base2.getId(), is("base-" + reviewHarness.shortId + ",3,testFile2.txt"));
		assertThat(base2.getName(), is("testFile2.txt"));
		assertThat(base2.getPath(), nullValue());
		assertThat(base2.getReference(), nullValue());
		assertThat(base2.getDescription(), is("Base"));

		IFileVersion target2 = fileItem2.getTarget();
		assertThat(target2.getAddedBy().getDisplayName(), is("tests"));
		assertThat(target2.getCommittedBy().getDisplayName(), is("tests"));
		assertThat(target2.getContent(), is("testmod"));
		assertThat(target2.getId(), is(reviewHarness.shortId + ",3,testFile2.txt"));
		assertThat(target2.getName(), is("testFile2.txt"));
		assertThat(target2.getPath(), is("testFile2.txt"));
		assertThat(target2.getReference(), nullValue());
		assertThat(target2.getDescription(), is("Patch Set 3"));
	}

	@Test
	public void testPatchSetComments() throws Exception {
		CommitCommand command2 = reviewHarness.git.commit()
				.setAmend(true)
				.setAll(true)
				.setMessage("Test Change " + reviewHarness.testIdent + " [2]\n\nChange-Id: " + reviewHarness.changeId);
		reviewHarness.gerritHarness.project().addFile("testComments.txt",
				"line1\nline2\nline3\nline4\nline5\nline6\nline7\n");
		reviewHarness.gerritHarness.project().commitAndPush(command2);
		reviewHarness.consumer.retrieve(false);
		reviewHarness.listener.waitForResponse(2, 2);
		RemoteEmfConsumer<IReview, IReviewItemSet, String, PatchSetDetail, PatchSetDetail, String> itemSetConsumer = reviewHarness.provider.getReviewItemSetFactory()
				.getConsumerForLocalKey(getReview(), "2");
		TestRemoteObserver<IReview, IReviewItemSet, String, String> itemSetObserver = new TestRemoteObserver<IReview, IReviewItemSet, String, String>(
				reviewHarness.provider.getReviewItemSetFactory());
		itemSetConsumer.addObserver(itemSetObserver);
		itemSetConsumer.retrieve(false);
		itemSetObserver.waitForResponse(1, 0);
		PatchSetDetail detail = itemSetConsumer.getRemoteObject();
		assertThat(detail.getInfo().getKey().get(), is(2));

		IReviewItemSet testPatchSet = getReview().getSets().get(1);
		PatchSetContentIdRemoteFactory patchFactory = reviewHarness.provider.getReviewItemSetContentFactory();
		TestRemoteObserver<IReviewItemSet, List<IFileItem>, String, Long> patchSetListener = new TestRemoteObserver<IReviewItemSet, List<IFileItem>, String, Long>(
				patchFactory);
		RemoteEmfConsumer<IReviewItemSet, List<IFileItem>, String, PatchSetContent, String, Long> patchSetConsumer = patchFactory.getConsumerForRemoteKey(
				testPatchSet, "2");
		patchSetConsumer.addObserver(patchSetListener);
		patchSetConsumer.retrieve(false);
		patchSetListener.waitForResponse(1, 1);

		IFileItem commentFile = testPatchSet.getItems().get(1);
		assertThat(commentFile.getName(), is("testComments.txt"));
		assertThat(commentFile.getAllComments().size(), is(0));

		String id = commentFile.getReference();
		reviewHarness.client.saveDraft(Patch.Key.parse(id), "Line 2 Comment", 2, (short) 1, null,
				new NullProgressMonitor());
		patchSetConsumer.retrieve(false);
		patchSetListener.waitForResponse(2, 2);

		commentFile = testPatchSet.getItems().get(1);
		List<IComment> allComments = commentFile.getAllComments();
		assertThat(allComments.size(), is(1));
		IComment fileComment = allComments.get(0);
		assertThat(fileComment, notNullValue());
		assertThat(fileComment.isDraft(), is(true));
		assertThat(fileComment.getAuthor().getDisplayName(), is("tests"));
		assertThat(fileComment.getDescription(), is("Line 2 Comment"));

		reviewHarness.client.publishComments(reviewHarness.shortId, 2, "Submit Comments",
				Collections.<ApprovalCategoryValue.Id> emptySet(), null);
		patchSetConsumer.retrieve(false);
		patchSetListener.waitForResponse(3, 3);
		allComments = commentFile.getAllComments();
		assertThat(allComments.size(), is(1));
		fileComment = allComments.get(0);
		assertThat(fileComment, notNullValue());
		assertThat(fileComment.isDraft(), is(false));
		assertThat(fileComment.getAuthor().getDisplayName(), is("tests"));
		assertThat(fileComment.getDescription(), is("Line 2 Comment"));

	}
}