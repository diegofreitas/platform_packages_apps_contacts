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

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Groups;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.list.ContactTileAdapter;
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.interactions.GroupDeletionDialogFragment;
import com.android.contacts.list.GroupMemberTileAdapter;

/**
 * Displays the details of a group and shows a list of actions possible for the group.
 */
public class GroupDetailFragment extends Fragment implements OnScrollListener {

    public static interface Listener {
        /**
         * The group title has been loaded
         */
        public void onGroupTitleUpdated(String title);

        /**
         * The number of group members has been determined
         */
        public void onGroupSizeUpdated(String size);

        /**
         * The account type and dataset have been determined.
         */
        public void onAccountTypeUpdated(String accountTypeString, String dataSet);

        /**
         * User decided to go to Edit-Mode
         */
        public void onEditRequested(Uri groupUri);

        /**
         * Contact is selected and should launch details page
         */
        public void onContactSelected(Uri contactUri);
    }

    private static final String TAG = "GroupDetailFragment";

    private static final int LOADER_METADATA = 0;
    private static final int LOADER_MEMBERS = 1;

    private Context mContext;

    private View mRootView;
    private ViewGroup mGroupSourceViewContainer;
    private View mGroupSourceView;
    private TextView mGroupTitle;
    private TextView mGroupSize;
    private ListView mMemberListView;
    private View mEmptyView;

    private Listener mListener;

    private ContactTileAdapter mAdapter;
    private ContactPhotoManager mPhotoManager;
    
    private boolean mOptionsMenuGroupDeletable;
    private boolean mOptionsMenuGroupEditable;
    private boolean mCloseActivityAfterDelete;

	private GroupDetailPresenter groupDetailPresenter;

    public GroupDetailFragment() {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        groupDetailPresenter = new GroupDetailPresenter(this);
        groupDetailPresenter.performOnAttach();
    }

    //DONE
	void buildContactAdapter() {
		Resources res = getResources();
        int columnCount = res.getInteger(R.integer.contact_tile_column_count);
        mAdapter = new GroupMemberTileAdapter(mContext, mContactTileListener, columnCount);
	}

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);
        mRootView = inflater.inflate(R.layout.group_detail_fragment, container, false);
        mGroupTitle = (TextView) mRootView.findViewById(R.id.group_title);
        mGroupSize = (TextView) mRootView.findViewById(R.id.group_size);
        mGroupSourceViewContainer = (ViewGroup) mRootView.findViewById(
                R.id.group_source_view_container);
        mEmptyView = mRootView.findViewById(android.R.id.empty);
        mMemberListView = (ListView) mRootView.findViewById(android.R.id.list);
        mMemberListView.setItemsCanFocus(true);
        mMemberListView.setAdapter(mAdapter);
        return mRootView;
    }

    public void loadGroup(Uri groupUri) {
        groupDetailPresenter.loadGroup( groupUri);
        
    }

    public void setQuickContact(boolean enableQuickContact) {
        mAdapter.enableQuickContact(enableQuickContact);
    }

    //DONE
    void configurePhotoLoader() {
        if (mContext != null) {
            if (mPhotoManager == null) {
                mPhotoManager = ContactPhotoManager.getInstance(mContext);
            }
            if (mMemberListView != null) {
                mMemberListView.setOnScrollListener(this);
            }
            if (mAdapter != null) {
                mAdapter.setPhotoLoader(mPhotoManager);
            }
        }
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    public void setShowGroupSourceInActionBar(boolean show) {
        this.groupDetailPresenter.setShowGroupSourceInActionBar(show);
    }

    public Uri getGroupUri() {
        return groupDetailPresenter.getGroupUri();
    }

    /**
     * Start the loader to retrieve the metadata for this group.
     */
    void startGroupMetadataLoader() {
        getLoaderManager().restartLoader(LOADER_METADATA, null, mGroupMetadataLoaderListener);
    }

    /**
     * Start the loader to retrieve the list of group members.
     */
    void startGroupMembersLoader() {
        getLoaderManager().restartLoader(LOADER_MEMBERS, null, mGroupMemberListLoaderListener);
    }

    private final ContactTileView.Listener mContactTileListener =
            new ContactTileView.Listener() {

        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            mListener.onContactSelected(contactUri);
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            // No need to call phone number directly from People app.
            Log.w(TAG, "unexpected invocation of onCallNumberDirectly()");
        }

        @Override
        public int getApproximateTileWidth() {
            return getView().getWidth() / mAdapter.getColumnCount();
        }
    };

    /**
     * The listener for the group metadata loader.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMetadataLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return groupDetailPresenter.createGroupMetadataLoader();
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        	groupDetailPresenter.performGroupLoadFinished(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };
    

    /**
     * The listener for the group members list loader
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMemberListLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return groupDetailPresenter.creatGroupMemberListLoader();
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        	groupDetailPresenter.onMemberListLoadFinished(data);
           
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };
    
	void extracted(Cursor data) {
		mAdapter.setContactCursor(data);
        mMemberListView.setEmptyView(mEmptyView);
	}

	void rebindDataToGroupSourceView(final String accountTypeString,
			final String dataSet, final AccountType accountType) {
		mGroupSourceView.setVisibility(View.VISIBLE);
		GroupDetailDisplayUtils.bindGroupSourceView(mContext, mGroupSourceView,
		        accountTypeString, dataSet);
		mGroupSourceView.setOnClickListener(new OnClickListener() {
		    @Override
		    public void onClick(View v) {
		        final Uri uri = ContentUris.withAppendedId(Groups.CONTENT_URI, groupDetailPresenter.getGroupId());
		        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		        intent.setClassName(accountType.syncAdapterPackageName,
		                accountType.getViewGroupActivity());
		        startActivity(intent);
		    }
		});
	}

	void hideGroupSourceView() {
		mGroupSourceView.setVisibility(View.GONE);
	}

	void onAccountTypeUpdated(final String accountTypeString, final String dataSet) {
		mListener.onAccountTypeUpdated(accountTypeString, dataSet);
	}

	void addGroupSourceViewToContainer() {
		mGroupSourceViewContainer.addView(mGroupSourceView);
	}

	boolean hasGroupSourceViewContainer() {
		return mGroupSourceViewContainer != null;
	}

	void createGroupSourceView() {
		mGroupSourceView = GroupDetailDisplayUtils.getNewGroupSourceView(mContext);
	}

	boolean hasNotGroupSourceView() {
		return mGroupSourceView == null;
	}

	void refreshOptions() {
		getActivity().invalidateOptionsMenu ();
	}

    void updateTitle(String title) {
        if (mGroupTitle != null) {
            mGroupTitle.setText(title);
        } else {
            mListener.onGroupTitleUpdated(title);
        }
    }


	void updateGroupSizeView(String groupSizeString) {
		if (mGroupSize != null) {
            mGroupSize.setText(groupSizeString);
        } else {
            mListener.onGroupSizeUpdated(groupSizeString);
        }
	}


    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
            mPhotoManager.pause();
        } else {
            mPhotoManager.resume();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.view_group, menu);
    }

    public boolean isOptionsMenuChanged() {
        return mOptionsMenuGroupDeletable != isGroupDeletable() &&
                mOptionsMenuGroupEditable != isGroupEditableAndPresent();
    }

    public boolean isGroupDeletable() {
        return groupDetailPresenter.isGroupDeletable();
    }

    public boolean isGroupEditableAndPresent() {
        return groupDetailPresenter.isGroupEditableAndPresent();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuGroupDeletable = isGroupDeletable() && isVisible();
        mOptionsMenuGroupEditable = isGroupEditableAndPresent() && isVisible();

        final MenuItem editMenu = menu.findItem(R.id.menu_edit_group);
        editMenu.setVisible(mOptionsMenuGroupEditable);

        final MenuItem deleteMenu = menu.findItem(R.id.menu_delete_group);
        deleteMenu.setVisible(mOptionsMenuGroupDeletable);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit_group: {
                if (mListener != null) mListener.onEditRequested(groupDetailPresenter.getGroupUri());
                break;
            }
            case R.id.menu_delete_group: {
                GroupDeletionDialogFragment.show(getFragmentManager(), groupDetailPresenter.getGroupId(), groupDetailPresenter.getGroupName(),
                        mCloseActivityAfterDelete);
                return true;
            }
        }
        return false;
    }

    public void closeActivityAfterDelete(boolean closeActivity) {
        mCloseActivityAfterDelete = closeActivity;
    }

    public long getGroupId() {
        return groupDetailPresenter.getGroupId();
    }
}
