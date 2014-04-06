/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.contacts.group;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.group.GroupEditorPresenter.GroupEditorMemento;
import com.android.contacts.group.SuggestedMemberListAdapter.SuggestedMember;
import com.google.common.base.Objects;

public class GroupEditorFragment extends Fragment implements SelectAccountDialogFragment.Listener {
    private static final String TAG = "GroupEditorFragment";

    private static final String LEGACY_CONTACTS_AUTHORITY = "contacts";
    private static final String KEY_STATUS = "status";
    private static final String KEY_GROUP_NAME_IS_READ_ONLY = "groupNameIsReadOnly";
    private static final String KEY_ORIGINAL_GROUP_NAME = "originalGroupName";

    private static final String CURRENT_EDITOR_TAG = "currentEditorForAccount";

    public static interface Listener {
        /**
         * Group metadata was not found, close the fragment now.
         */
        public void onGroupNotFound();

        /**
         * User has tapped Revert, close the fragment now.
         */
        void onReverted();

        /**
         * Contact was saved and the Fragment can now be closed safely.
         */
        void onSaveFinished(int resultCode, Intent resultIntent);

        /**
         * Fragment is created but there's no accounts set up.
         */
        void onAccountsNotFound();
    }

    private static final int LOADER_GROUP_METADATA = 1;
    private static final int LOADER_NEW_GROUP_MEMBER = 3;

    private static final String MEMBER_RAW_CONTACT_ID_KEY = "rawContactId";
    private static final String MEMBER_LOOKUP_URI_KEY = "memberLookupUri";

    protected static final String[] PROJECTION_CONTACT = new String[] {
        Contacts._ID,                           // 0
        Contacts.DISPLAY_NAME_PRIMARY,          // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,      // 2
        Contacts.SORT_KEY_PRIMARY,              // 3
        Contacts.STARRED,                       // 4
        Contacts.CONTACT_PRESENCE,              // 5
        Contacts.CONTACT_CHAT_CAPABILITY,       // 6
        Contacts.PHOTO_ID,                      // 7
        Contacts.PHOTO_THUMBNAIL_URI,           // 8
        Contacts.LOOKUP_KEY,                    // 9
        Contacts.PHONETIC_NAME,                 // 10
        Contacts.HAS_PHONE_NUMBER,              // 11
        Contacts.IS_USER_PROFILE,               // 12
    };

    protected static final int CONTACT_ID_COLUMN_INDEX = 0;
    protected static final int CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    protected static final int CONTACT_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    protected static final int CONTACT_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    protected static final int CONTACT_STARRED_COLUMN_INDEX = 4;
    protected static final int CONTACT_PRESENCE_STATUS_COLUMN_INDEX = 5;
    protected static final int CONTACT_CHAT_CAPABILITY_COLUMN_INDEX = 6;
    protected static final int CONTACT_PHOTO_ID_COLUMN_INDEX = 7;
    protected static final int CONTACT_PHOTO_URI_COLUMN_INDEX = 8;
    protected static final int CONTACT_LOOKUP_KEY_COLUMN_INDEX = 9;
    protected static final int CONTACT_PHONETIC_NAME_COLUMN_INDEX = 10;
    protected static final int CONTACT_HAS_PHONE_COLUMN_INDEX = 11;
    protected static final int CONTACT_IS_USER_PROFILE = 12;

	private static final String KEY_MEMENTO = "KEY_MEMENTO";

    /**
     * Modes that specify the status of the editor
     */
    public enum Status {
        SELECTING_ACCOUNT, // Account select dialog is showing
        LOADING,    // Loader is fetching the group metadata
        EDITING,    // Not currently busy. We are waiting forthe user to enter data.
        SAVING,     // Data is currently being saved
        CLOSING     // Prevents any more saves
    }

    private Context mContext;
    private Listener mListener;

    private Status mStatus;

    private ViewGroup mRootView;
    private ListView mListView;
    private LayoutInflater mLayoutInflater;

    private TextView mGroupNameView;
    private AutoCompleteTextView mAutoCompleteTextView;

    private boolean mGroupNameIsReadOnly;
    private String mOriginalGroupName = "";
    private int mLastGroupEditorId;

    private MemberListAdapter mMemberListAdapter;
    private ContactPhotoManager mPhotoManager;

    private ContentResolver mContentResolver;
    private SuggestedMemberListAdapter mAutoCompleteAdapter;

    private ArrayList<Member> mListMembersToAdd = new ArrayList<Member>();
    private ArrayList<Member> mListMembersToRemove = new ArrayList<Member>();
    private ArrayList<Member> mListToDisplay = new ArrayList<Member>();

	private GroupEditorPresenter presenter;

    public GroupEditorFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);
        mLayoutInflater = inflater;
        mRootView = (ViewGroup) inflater.inflate(R.layout.group_editor_fragment, container, false);
        presenter = new GroupEditorPresenter(this);
        return mRootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mPhotoManager = ContactPhotoManager.getInstance(mContext);
        mMemberListAdapter = new MemberListAdapter();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        presenter.onActivityCreated(savedInstanceState);
    }

    void startGroupMetaDataLoader() {
        mStatus = Status.LOADING;
        getLoaderManager().initLoader(LOADER_GROUP_METADATA, null,
                presenter);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_MEMENTO, presenter.getMemento());

        outState.putSerializable(KEY_STATUS, mStatus);

        outState.putBoolean(KEY_GROUP_NAME_IS_READ_ONLY, mGroupNameIsReadOnly);
        outState.putString(KEY_ORIGINAL_GROUP_NAME, mOriginalGroupName);
    }

    void onRestoreInstanceState(Bundle state) {
    	presenter.setMemento( (GroupEditorMemento) state.getSerializable(KEY_MEMENTO));

        mStatus = (Status) state.getSerializable(KEY_STATUS);
        mGroupNameIsReadOnly = state.getBoolean(KEY_GROUP_NAME_IS_READ_ONLY);
        mOriginalGroupName = state.getString(KEY_ORIGINAL_GROUP_NAME);
    }

    public void setContentResolver(ContentResolver resolver) {
        mContentResolver = resolver;
        if (mAutoCompleteAdapter != null) {
            mAutoCompleteAdapter.setContentResolver(mContentResolver);
        }
    }

    void selectAccountAndCreateGroup() {
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(mContext).getAccounts(true /* writeable */);
        // No Accounts available
        if (accounts.isEmpty()) {
            Log.e(TAG, "No accounts were found.");
            if (mListener != null) {
                mListener.onAccountsNotFound();
            }
            return;
        }

        // In the common case of a single account being writable, auto-select
        // it without showing a dialog.
        if (accounts.size() == 1) {
        	 presenter.getMemento().mAccountName = accounts.get(0).name;
        	 presenter.getMemento().mAccountType = accounts.get(0).type;
        	 presenter.getMemento().mDataSet = accounts.get(0).dataSet;
            setupEditorForAccount();
            return;  // Don't show a dialog.
        }

        mStatus = Status.SELECTING_ACCOUNT;
        SelectAccountDialogFragment.show(getFragmentManager(), this,
                R.string.dialog_new_group_account, AccountListFilter.ACCOUNTS_GROUP_WRITABLE,
                null);
    }

    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        presenter.getMemento().mAccountName = account.name;
        presenter.getMemento().mAccountType = account.type;
        presenter.getMemento().mDataSet = account.dataSet;
        setupEditorForAccount();
    }

    @Override
    public void onAccountSelectorCancelled() {
        if (mListener != null) {
            // Exit the fragment because we cannot continue without selecting an account
            mListener.onGroupNotFound();
        }
    }

    private AccountType getAccountType() {
        return AccountTypeManager.getInstance(mContext).getAccountType( presenter.getMemento().mAccountType,  presenter.getMemento().mDataSet);
    }

    /**
     * @return true if the group membership is editable on this account type.  false otherwise,
     *         or account is not set yet.
     */
    private boolean isGroupMembershipEditable() {
        if ( presenter.getMemento().mAccountType == null) {
            return false;
        }
        return getAccountType().isGroupMembershipEditable();
    }

    /**
     * Sets up the editor based on the group's account name and type.
     */
    void setupEditorForAccount() {
        final AccountType accountType = getAccountType();
        final boolean editable = isGroupMembershipEditable();
        boolean isNewEditor = false;
        mMemberListAdapter.setIsGroupMembershipEditable(editable);

        // Since this method can be called multiple time, remove old editor if the editor type
        // is different from the new one and mark the editor with a tag so it can be found for
        // removal if needed
        View editorView;
        int newGroupEditorId =
                editable ? R.layout.group_editor_view : R.layout.external_group_editor_view;
        if (newGroupEditorId != mLastGroupEditorId) {
            View oldEditorView = mRootView.findViewWithTag(CURRENT_EDITOR_TAG);
            if (oldEditorView != null) {
                mRootView.removeView(oldEditorView);
            }
            editorView = mLayoutInflater.inflate(newGroupEditorId, mRootView, false);
            editorView.setTag(CURRENT_EDITOR_TAG);
            mAutoCompleteAdapter = null;
            mLastGroupEditorId = newGroupEditorId;
            isNewEditor = true;
        } else {
            editorView = mRootView.findViewWithTag(CURRENT_EDITOR_TAG);
            if (editorView == null) {
                throw new IllegalStateException("Group editor view not found");
            }
        }

        mGroupNameView = (TextView) editorView.findViewById(R.id.group_name);
        mAutoCompleteTextView = (AutoCompleteTextView) editorView.findViewById(
                R.id.add_member_field);

        mListView = (ListView) editorView.findViewById(android.R.id.list);
        mListView.setAdapter(mMemberListAdapter);

        // Setup the account header, only when exists.
        if (editorView.findViewById(R.id.account_header) != null) {
            CharSequence accountTypeDisplayLabel = accountType.getDisplayLabel(mContext);
            ImageView accountIcon = (ImageView) editorView.findViewById(R.id.account_icon);
            TextView accountTypeTextView = (TextView) editorView.findViewById(R.id.account_type);
            TextView accountNameTextView = (TextView) editorView.findViewById(R.id.account_name);
            if (!TextUtils.isEmpty( presenter.getMemento().mAccountName)) {
                accountNameTextView.setText(
                        mContext.getString(R.string.from_account_format,  presenter.getMemento().mAccountName));
            }
            accountTypeTextView.setText(accountTypeDisplayLabel);
            accountIcon.setImageDrawable(accountType.getDisplayIcon(mContext));
        }

        // Setup the autocomplete adapter (for contacts to suggest to add to the group) based on the
        // account name and type. For groups that cannot have membership edited, there will be no
        // autocomplete text view.
        if (mAutoCompleteTextView != null) {
            mAutoCompleteAdapter = new SuggestedMemberListAdapter(mContext,
                    android.R.layout.simple_dropdown_item_1line);
            mAutoCompleteAdapter.setContentResolver(mContentResolver);
            mAutoCompleteAdapter.setAccountType( presenter.getMemento().mAccountType);
            mAutoCompleteAdapter.setAccountName( presenter.getMemento().mAccountName);
            mAutoCompleteAdapter.setDataSet( presenter.getMemento().mDataSet);
            mAutoCompleteTextView.setAdapter(mAutoCompleteAdapter);
            mAutoCompleteTextView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    SuggestedMember member = (SuggestedMember) view.getTag();
                    if (member == null) {
                        return; // just in case
                    }
                    loadMemberToAddToGroup(member.getRawContactId(),
                            String.valueOf(member.getContactId()));

                    // Update the autocomplete adapter so the contact doesn't get suggested again
                    mAutoCompleteAdapter.addNewMember(member.getContactId());

                    // Clear out the text field
                    mAutoCompleteTextView.setText("");
                }
            });
            // Update the exempt list.  (mListToDisplay might have been restored from the saved
            // state.)
            mAutoCompleteAdapter.updateExistingMembersList(mListToDisplay);
        }

        // If the group name is ready only, don't let the user focus on the field.
        mGroupNameView.setFocusable(!mGroupNameIsReadOnly);
        if(isNewEditor) {
            mRootView.addView(editorView);
        }
        mStatus = Status.EDITING;
    }

    public void load(String action, Uri groupUri, Bundle intentExtras) {
    	presenter.load(action, groupUri, intentExtras);
    }

    void bindGroupMetaData(Cursor cursor) {
        if (!cursor.moveToFirst()) {
            if (mListener != null) {
                mListener.onGroupNotFound();
            }
            return;
        }
        mOriginalGroupName = cursor.getString(GroupMetaDataLoader.TITLE);
        presenter.getMemento().mAccountName = cursor.getString(GroupMetaDataLoader.ACCOUNT_NAME);
        presenter.getMemento().mAccountType = cursor.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
        presenter.getMemento().mDataSet = cursor.getString(GroupMetaDataLoader.DATA_SET);
        mGroupNameIsReadOnly = (cursor.getInt(GroupMetaDataLoader.IS_READ_ONLY) == 1);
        setupEditorForAccount();

        // Setup the group metadata display
        mGroupNameView.setText(mOriginalGroupName);
    }

    public void loadMemberToAddToGroup(long rawContactId, String contactId) {
        Bundle args = new Bundle();
        args.putLong(MEMBER_RAW_CONTACT_ID_KEY, rawContactId);
        args.putString(MEMBER_LOOKUP_URI_KEY, contactId);
        getLoaderManager().restartLoader(LOADER_NEW_GROUP_MEMBER, args, mContactLoaderListener);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    public void onDoneClicked() {
        if (isGroupMembershipEditable()) {
            save();
        } else {
            // Just revert it.
            doRevertAction();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.edit_group, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_discard:
                return revert();
        }
        return false;
    }

    private boolean revert() {
        if (!hasNameChange() && !hasMembershipChange()) {
            doRevertAction();
        } else {
            CancelEditDialogFragment.show(this);
        }
        return true;
    }

    private void doRevertAction() {
        // When this Fragment is closed we don't want it to auto-save
        mStatus = Status.CLOSING;
        if (mListener != null) mListener.onReverted();
    }

    public static class CancelEditDialogFragment extends DialogFragment {

        public static void show(GroupEditorFragment fragment) {
            CancelEditDialogFragment dialog = new CancelEditDialogFragment();
            dialog.setTargetFragment(fragment, 0);
            dialog.show(fragment.getFragmentManager(), "cancelEditor");
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.cancel_confirmation_dialog_message)
                    .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int whichButton) {
                                ((GroupEditorFragment) getTargetFragment()).doRevertAction();
                            }
                        }
                    )
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            return dialog;
        }
    }

    /**
     * Saves or creates the group based on the mode, and if successful
     * finishes the activity. This actually only handles saving the group name.
     * @return true when successful
     */
    public boolean save() {
    	return presenter.save();
    }

    public void onSaveCompleted(boolean hadChanges, Uri groupUri) {
        boolean success = groupUri != null;
        Log.d(TAG, "onSaveCompleted(" + groupUri + ")");
        if (hadChanges) {
            Toast.makeText(mContext, success ? R.string.groupSavedToast :
                    R.string.groupSavedErrorToast, Toast.LENGTH_SHORT).show();
        }
        final Intent resultIntent;
        final int resultCode;
        if (success && groupUri != null) {
            final String requestAuthority = groupUri.getAuthority();

            resultIntent = new Intent();
            if (LEGACY_CONTACTS_AUTHORITY.equals(requestAuthority)) {
                // Build legacy Uri when requested by caller
                final long groupId = ContentUris.parseId(groupUri);
                final Uri legacyContentUri = Uri.parse("content://contacts/groups");
                final Uri legacyUri = ContentUris.withAppendedId(
                        legacyContentUri, groupId);
                resultIntent.setData(legacyUri);
            } else {
                // Otherwise pass back the given Uri
                resultIntent.setData(groupUri);
            }

            resultCode = Activity.RESULT_OK;
        } else {
            resultCode = Activity.RESULT_CANCELED;
            resultIntent = null;
        }
        // It is already saved, so prevent that it is saved again
        mStatus = Status.CLOSING;
        if (mListener != null) {
            mListener.onSaveFinished(resultCode, resultIntent);
        }
    }

    boolean hasValidGroupName() {
        return mGroupNameView != null && !TextUtils.isEmpty(mGroupNameView.getText());
    }

    boolean hasNameChange() {
        return mGroupNameView != null &&
                !mGroupNameView.getText().toString().equals(mOriginalGroupName);
    }

    boolean hasMembershipChange() {
        return mListMembersToAdd.size() > 0 || mListMembersToRemove.size() > 0;
    }

    /**
     * Returns the group's new name or null if there is no change from the
     * original name that was loaded for the group.
     */
    String getUpdatedName() {
        String groupNameFromTextView = mGroupNameView.getText().toString();
        if (groupNameFromTextView.equals(mOriginalGroupName)) {
            // No name change, so return null
            return null;
        }
        return groupNameFromTextView;
    }

	void updateAutoCompleteAdapter(List<Member> members) {
		mMemberListAdapter.notifyDataSetChanged();
		if (mAutoCompleteAdapter != null) {
            mAutoCompleteAdapter.updateExistingMembersList(members);
        }
	}

    private void addMember(Member member) {
        // Update the display list
        mListMembersToAdd.add(member);
        mListToDisplay.add(member);
        mMemberListAdapter.notifyDataSetChanged();

        // Update the autocomplete adapter so the contact doesn't get suggested again
        mAutoCompleteAdapter.addNewMember(member.getContactId());
    }

    private void removeMember(Member member) {
        // If the contact was just added during this session, remove it from the list of
        // members to add
        if (mListMembersToAdd.contains(member)) {
            mListMembersToAdd.remove(member);
        } else {
            // Otherwise this contact was already part of the existing list of contacts,
            // so we need to do a content provider deletion operation
            mListMembersToRemove.add(member);
        }
        // In either case, update the UI so the contact is no longer in the list of
        // members
        mListToDisplay.remove(member);
        mMemberListAdapter.notifyDataSetChanged();

        // Update the autocomplete adapter so the contact can get suggested again
        mAutoCompleteAdapter.removeMember(member.getContactId());
    }

   
    /**
     * The listener to load a summary of details for a contact.
     */
    // TODO: Remove this step because showing the aggregate contact can be confusing when the user
    // just selected a raw contact
    private final LoaderManager.LoaderCallbacks<Cursor> mContactLoaderListener =
            new LoaderCallbacks<Cursor>() {

        private long mRawContactId;

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            String memberId = args.getString(MEMBER_LOOKUP_URI_KEY);
            mRawContactId = args.getLong(MEMBER_RAW_CONTACT_ID_KEY);
            return new CursorLoader(mContext, Uri.withAppendedPath(Contacts.CONTENT_URI, memberId),
                    PROJECTION_CONTACT, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            if (!cursor.moveToFirst()) {
                return;
            }
            // Retrieve the contact data fields that will be sufficient to update the adapter with
            // a new entry for this contact
            long contactId = cursor.getLong(CONTACT_ID_COLUMN_INDEX);
            String displayName = cursor.getString(CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
            String lookupKey = cursor.getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX);
            String photoUri = cursor.getString(CONTACT_PHOTO_URI_COLUMN_INDEX);
            getLoaderManager().destroyLoader(LOADER_NEW_GROUP_MEMBER);
            Member member = new Member(mRawContactId, lookupKey, contactId, displayName, photoUri);
            addMember(member);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    /**
     * This represents a single member of the current group.
     */
    public static class Member implements Parcelable {

        // TODO: Switch to just dealing with raw contact IDs everywhere if possible
        private final long mRawContactId;
        private final long mContactId;
        private final Uri mLookupUri;
        private final String mDisplayName;
        private final Uri mPhotoUri;

        public Member(long rawContactId, String lookupKey, long contactId, String displayName,
                String photoUri) {
            mRawContactId = rawContactId;
            mContactId = contactId;
            mLookupUri = Contacts.getLookupUri(contactId, lookupKey);
            mDisplayName = displayName;
            mPhotoUri = (photoUri != null) ? Uri.parse(photoUri) : null;
        }

        public long getRawContactId() {
            return mRawContactId;
        }

        public long getContactId() {
            return mContactId;
        }

        public Uri getLookupUri() {
            return mLookupUri;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public Uri getPhotoUri() {
            return mPhotoUri;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof Member) {
                Member otherMember = (Member) object;
                return Objects.equal(mLookupUri, otherMember.getLookupUri());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return mLookupUri == null ? 0 : mLookupUri.hashCode();
        }

        // Parcelable
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mRawContactId);
            dest.writeLong(mContactId);
            dest.writeParcelable(mLookupUri, flags);
            dest.writeString(mDisplayName);
            dest.writeParcelable(mPhotoUri, flags);
        }

        private Member(Parcel in) {
            mRawContactId = in.readLong();
            mContactId = in.readLong();
            mLookupUri = in.readParcelable(getClass().getClassLoader());
            mDisplayName = in.readString();
            mPhotoUri = in.readParcelable(getClass().getClassLoader());
        }

        public static final Parcelable.Creator<Member> CREATOR = new Parcelable.Creator<Member>() {
            @Override
            public Member createFromParcel(Parcel in) {
                return new Member(in);
            }

            @Override
            public Member[] newArray(int size) {
                return new Member[size];
            }
        };
    }

    /**
     * This adapter displays a list of members for the current group being edited.
     */
    private final class MemberListAdapter extends BaseAdapter {

        private boolean mIsGroupMembershipEditable = true;

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View result;
            if (convertView == null) {
                result = mLayoutInflater.inflate(mIsGroupMembershipEditable ?
                        R.layout.group_member_item : R.layout.external_group_member_item,
                        parent, false);
            } else {
                result = convertView;
            }
            final Member member = getItem(position);

            QuickContactBadge badge = (QuickContactBadge) result.findViewById(R.id.badge);
            badge.assignContactUri(member.getLookupUri());

            TextView name = (TextView) result.findViewById(R.id.name);
            name.setText(member.getDisplayName());

            View deleteButton = result.findViewById(R.id.delete_button_container);
            if (deleteButton != null) {
                deleteButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        removeMember(member);
                    }
                });
            }

            mPhotoManager.loadPhoto(badge, member.getPhotoUri(),
                    ViewUtil.getConstantPreLayoutWidth(badge), false);
            return result;
        }

        @Override
        public int getCount() {
            return mListToDisplay.size();
        }

        @Override
        public Member getItem(int position) {
            return mListToDisplay.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public void setIsGroupMembershipEditable(boolean editable) {
            mIsGroupMembershipEditable = editable;
        }
    }

	public boolean hasActivity() {
		  Activity activity = getActivity();
	        // If the activity is not there anymore, then we can't continue with the save process.
	      return activity != null;	   
	}

	public String getGroupName() {
		// TODO Auto-generated method stub
		return  mGroupNameView.getText().toString();
	}

	public void onReverted() {
		 if (mListener != null) {
             mListener.onReverted();
         }
	}
}
