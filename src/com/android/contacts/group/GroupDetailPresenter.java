package com.android.contacts.group;

import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.android.contacts.GroupMemberLoader;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;

public class GroupDetailPresenter {

	private GroupDetailFragment view;
	private AccountTypeManager mAccountTypeManager;

	private String mAccountTypeString;
	private boolean mShowGroupActionInActionBar;
	private long mGroupId;
	private boolean mIsReadOnly;
	private String mDataSet;
	private String mGroupName;
	private boolean mIsMembershipEditable;
	private Uri mGroupUri;

	public GroupDetailPresenter(GroupDetailFragment groupDetailFragment) {
		this.view = groupDetailFragment;
		mAccountTypeManager = AccountTypeManager
				.getInstance(groupDetailFragment.getActivity());
	}

	public void performOnAttach() {
		view.configurePhotoLoader();
		view.buildContactAdapter();
	}

	public CursorLoader createGroupMetadataLoader() {
		return new GroupMetaDataLoader(view.getActivity(), mGroupUri);
	}

	public void performGroupLoadFinished(Cursor data) {
		data.moveToPosition(-1);
		if (data.moveToNext()) {
			boolean deleted = data.getInt(GroupMetaDataLoader.DELETED) == 1;
			if (!deleted) {
				this.bindGroupMetaData(data);
				// Retrieve the list of members
				view.startGroupMembersLoader();
				return;
			}
		}
		updateSize(-1);
		view.updateTitle(null);
	}

	void bindGroupMetaData(Cursor cursor) {
		cursor.moveToPosition(-1);
		if (cursor.moveToNext()) {
			mAccountTypeString = cursor
					.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
			mDataSet = cursor.getString(GroupMetaDataLoader.DATA_SET);
			mGroupId = cursor.getLong(GroupMetaDataLoader.GROUP_ID);
			mGroupName = cursor.getString(GroupMetaDataLoader.TITLE);
			mIsReadOnly = cursor.getInt(GroupMetaDataLoader.IS_READ_ONLY) == 1;
			view.updateTitle(mGroupName);
			// Must call invalidate so that the option menu will get updated
			view.refreshOptions();

			final String accountTypeString = cursor
					.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
			final String dataSet = cursor
					.getString(GroupMetaDataLoader.DATA_SET);
			updateAccountType(accountTypeString, dataSet);
		}
	}

	/**
	 * Once the account type, group source action, and group source URI have
	 * been determined (based on the result from the {@link Loader}), then we
	 * can display this to the user in 1 of 2 ways depending on screen size and
	 * orientation: either as a button in the action bar or as a button in a
	 * static header on the page. We also use isGroupMembershipEditable() of
	 * accountType to determine whether or not we should display the Edit option
	 * in the Actionbar.
	 */
	private void updateAccountType(final String accountTypeString,
			final String dataSet) {
		final AccountTypeManager manager = AccountTypeManager.getInstance(view
				.getActivity());
		final AccountType accountType = manager.getAccountType(
				accountTypeString, dataSet);

		mIsMembershipEditable = accountType.isGroupMembershipEditable();

		// If the group action should be shown in the action bar, then pass the
		// data to the
		// listener who will take care of setting up the view and click
		// listener. There is nothing
		// else to be done by this {@link Fragment}.
		if (mShowGroupActionInActionBar) {
			view.onAccountTypeUpdated(accountTypeString, dataSet);
			return;
		}
		// Otherwise, if the {@link Fragment} needs to create and setup the
		// button, then first
		// verify that there is a valid action.
		if (!TextUtils.isEmpty(accountType.getViewGroupActivity())) {
			prepareGroupSourceView();
			// Rebind the data since this action can change if the loader
			// returns updated data
			view.rebindDataToGroupSourceView(accountTypeString, dataSet,
					accountType);
		} else if (!view.hasNotGroupSourceView()) {
			view.hideGroupSourceView();
		}
	}

	private void prepareGroupSourceView() {
		if (view.hasNotGroupSourceView()) {
			view.createGroupSourceView();
			// Figure out how to add the view to the fragment.
			// If there is a static header with a container for the group source
			// view, insert
			// the view there.
			if (view.hasGroupSourceViewContainer()) {
				view.addGroupSourceViewToContainer();
			}
		}
	}

	public void setShowGroupSourceInActionBar(boolean show) {
		mShowGroupActionInActionBar = show;
	}

	public CursorLoader creatGroupMemberListLoader() {
		return GroupMemberLoader.constructLoaderForGroupDetailQuery(
				view.getActivity(), mGroupId);
	}

	public long getGroupId() {
		// TODO Auto-generated method stub
		return mGroupId;
	}

	public String getGroupName() {
		// TODO Auto-generated method stub
		return this.mGroupName;
	}

	public void onMemberListLoadFinished(Cursor data) {
		updateSize(data.getCount());
	}

	/**
	 * Display the count of the number of group members.
	 * 
	 * @param size
	 *            of the group (can be -1 if no size could be determined)
	 */
	void updateSize(int size) {
		String groupSizeString;
		if (size == -1) {
			groupSizeString = null;
		} else {
			String groupSizeTemplateString = view.getResources()
					.getQuantityString(R.plurals.num_contacts_in_group, size);
			AccountType accountType = mAccountTypeManager.getAccountType(
					mAccountTypeString, mDataSet);
			groupSizeString = String.format(groupSizeTemplateString, size,
					accountType.getDisplayLabel(view.getActivity()));
		}

		view.updateGroupSizeView(groupSizeString);
	}

	public boolean isReadOnly() {
		// TODO Auto-generated method stub
		return mIsReadOnly;
	}

	public boolean isMembershipEditable() {
		// TODO Auto-generated method stub
		return mIsMembershipEditable;
	}

	public void loadGroup(Uri groupUri) {
		mGroupUri = groupUri;
		view.startGroupMetadataLoader();
	}

	public Uri getGroupUri() {
		// TODO Auto-generated method stub
		return mGroupUri;
	}

	public boolean isGroupEditableAndPresent() {
		return mGroupUri != null && isMembershipEditable();
	}
	
	public boolean isGroupDeletable() {
        return mGroupUri != null && ! isReadOnly();
    }

}
