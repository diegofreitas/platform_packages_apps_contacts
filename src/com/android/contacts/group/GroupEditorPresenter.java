package com.android.contacts.group;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import android.accounts.Account;
import android.app.Activity;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Intents;

import com.android.contacts.ContactSaveService;
import com.android.contacts.GroupMemberLoader;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.GroupMemberLoader.GroupEditorQuery;
import com.android.contacts.activities.GroupEditorActivity;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.group.GroupEditorFragment.Member;
import com.android.contacts.group.GroupEditorFragment.Status;

public class GroupEditorPresenter implements LoaderCallbacks<Cursor> {

	private GroupEditorFragment view;
	private Activity context;
	private GroupEditorMemento memento;
	private Bundle mIntentExtras;
	private Status mStatus;

	private static final int LOADER_EXISTING_MEMBERS = 2;

	static class GroupEditorMemento implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 104623013054768034L;
		Uri mGroupUri;
		public long mGroupId;
		public String mAction;
		public String mAccountName;
		public String mAccountType;
		public String mDataSet;

	    public ArrayList<Member> mListMembersToAdd = new ArrayList<Member>();
	    public ArrayList<Member> mListMembersToRemove = new ArrayList<Member>();
	    public ArrayList<Member> mListToDisplay = new ArrayList<Member>();
	}

	public GroupEditorPresenter(GroupEditorFragment groupEditorFragment) {
		this.view = groupEditorFragment;
		context = groupEditorFragment.getActivity();
		// TODO Auto-generated constructor stub
		memento = new GroupEditorMemento();
	}

	@Override
	public CursorLoader onCreateLoader(int id, Bundle args) {
		return new GroupMetaDataLoader(context, memento.mGroupUri);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		view.bindGroupMetaData(data);

		// Load existing members
		view.getLoaderManager().initLoader(LOADER_EXISTING_MEMBERS, null,
				mGroupMemberListLoaderListener);
	}

	/**
	 * The loader listener for the list of existing group members.
	 */
	private final LoaderManager.LoaderCallbacks<Cursor> mGroupMemberListLoaderListener = new LoaderCallbacks<Cursor>() {

		@Override
		public CursorLoader onCreateLoader(int id, Bundle args) {
			return GroupMemberLoader.constructLoaderForGroupEditorQuery(
					view.getActivity(), getMemento().mGroupId);
		}

		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
			List<Member> listExistingMembers = new ArrayList<Member>();
			data.moveToPosition(-1);
			while (data.moveToNext()) {
				long contactId = data.getLong(GroupEditorQuery.CONTACT_ID);
				long rawContactId = data
						.getLong(GroupEditorQuery.RAW_CONTACT_ID);
				String lookupKey = data
						.getString(GroupEditorQuery.CONTACT_LOOKUP_KEY);
				String displayName = data
						.getString(GroupEditorQuery.CONTACT_DISPLAY_NAME_PRIMARY);
				String photoUri = data
						.getString(GroupEditorQuery.CONTACT_PHOTO_URI);
				listExistingMembers.add(new Member(rawContactId, lookupKey,
						contactId, displayName, photoUri));
			}

			// Update the display list
			addExistingMembers(listExistingMembers);

			// No more updates
			// TODO: move to a runnable
			view.getLoaderManager().destroyLoader(LOADER_EXISTING_MEMBERS);
		}

		@Override
		public void onLoaderReset(Loader<Cursor> loader) {
		}
	};

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
	}

	public GroupEditorMemento getMemento() {
		// TODO Auto-generated method stub
		return this.memento;
	}

	public void setMemento(GroupEditorMemento memento) {
		this.memento = memento;
	}

	public void load(String action, Uri groupUri, Bundle intentExtras) {
		memento.mAction = action;
		memento.mGroupUri = groupUri;
		memento.mGroupId = (groupUri != null) ? ContentUris
				.parseId(memento.mGroupUri) : 0;
		mIntentExtras = intentExtras;
	}

	private void addExistingMembers(List<Member> listExistingMembers) {

		// Re-create the list to display
		memento.mListToDisplay.clear();
		memento.mListToDisplay.addAll(listExistingMembers);
		memento.mListToDisplay.addAll(memento.mListMembersToAdd);
		memento.mListToDisplay.removeAll(memento.mListMembersToRemove);

		// Update the autocomplete adapter (if there is one) so these contacts
		// don't get suggested
		view.updateAutoCompleteAdapter(listExistingMembers);
	}

	public boolean save() {
		if (!view.hasValidGroupName() || mStatus != Status.EDITING) {
			mStatus = Status.CLOSING;
			view.onReverted();
			return false;
		}

		// If we are about to close the editor - there is no need to refresh the
		// data
		view.getLoaderManager().destroyLoader(LOADER_EXISTING_MEMBERS);

		// If there are no changes, then go straight to onSaveCompleted()
		if (!view.hasNameChange() && !view.hasMembershipChange()) {
			view.onSaveCompleted(false, memento.mGroupUri);
			return true;
		}

		mStatus = Status.SAVING;

		if (!view.hasActivity()) {
			return false;
		}
		Intent saveIntent = null;
		if (Intent.ACTION_INSERT.equals(memento.mAction)) {
			// Create array of raw contact IDs for contacts to add to the group
			long[] membersToAddArray = convertToArray(memento.mListMembersToAdd);

			// Create the save intent to create the group and add members at the
			// same time
			saveIntent = ContactSaveService.createNewGroupIntent(view
					.getActivity(), new AccountWithDataSet(
					memento.mAccountName, memento.mAccountType,
					memento.mDataSet), view.getGroupName(), membersToAddArray,
					view.getActivity().getClass(),
					GroupEditorActivity.ACTION_SAVE_COMPLETED);
		} else if (Intent.ACTION_EDIT.equals(memento.mAction)) {
			// Create array of raw contact IDs for contacts to add to the group
			long[] membersToAddArray = convertToArray(memento.mListMembersToAdd);

			// Create array of raw contact IDs for contacts to add to the group
			long[] membersToRemoveArray = convertToArray(memento.mListMembersToRemove);

			// Create the update intent (which includes the updated group name
			// if necessary)
			saveIntent = ContactSaveService.createGroupUpdateIntent(
					view.getActivity(), memento.mGroupId,
					view.getUpdatedName(), membersToAddArray,
					membersToRemoveArray, view.getActivity().getClass(),
					GroupEditorActivity.ACTION_SAVE_COMPLETED);
		} else {
			throw new IllegalStateException("Invalid intent action type "
					+ memento.mAction);
		}
		view.getActivity().startService(saveIntent);
		return true;

	}

	public void onActivityCreated(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			// Just restore from the saved state. No loading.
			view.onRestoreInstanceState(savedInstanceState);
			if (mStatus == Status.SELECTING_ACCOUNT) {
				// Account select dialog is showing. Don't setup the editor yet.
			} else if (mStatus == Status.LOADING) {
				view.startGroupMetaDataLoader();
			} else {
				view.setupEditorForAccount();
			}
		} else if (Intent.ACTION_EDIT.equals(memento.mAction)) {
			view.startGroupMetaDataLoader();
		} else if (Intent.ACTION_INSERT.equals(memento.mAction)) {
			final Account account = mIntentExtras == null ? null
					: (Account) mIntentExtras
							.getParcelable(Intents.Insert.ACCOUNT);
			final String dataSet = mIntentExtras == null ? null : mIntentExtras
					.getString(Intents.Insert.DATA_SET);

			if (account != null) {
				// Account specified in Intent - no data set can be specified in
				// this manner.
				memento.mAccountName = account.name;
				memento.mAccountType = account.type;
				memento.mDataSet = dataSet;
				view.setupEditorForAccount();
			} else {
				// No Account specified. Let the user choose from a
				// disambiguation dialog.
				view.selectAccountAndCreateGroup();
			}
		} else {
			throw new IllegalArgumentException("Unknown Action String "
					+ memento.mAction + ". Only support " + Intent.ACTION_EDIT
					+ " or " + Intent.ACTION_INSERT);
		}
	}

	private static long[] convertToArray(List<Member> listMembers) {
		int size = listMembers.size();
		long[] membersArray = new long[size];
		for (int i = 0; i < size; i++) {
			membersArray[i] = listMembers.get(i).getRawContactId();
		}
		return membersArray;
	}
}
