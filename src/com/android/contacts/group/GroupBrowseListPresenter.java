package com.android.contacts.group;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.android.contacts.common.ContactsUtils;

public class GroupBrowseListPresenter {
	
	private static final String EXTRA_KEY_GROUP_URI = "groups.groupUri";
	
	private GroupBrowseListFragment view;
	private Activity mContext;
	
    
    private Uri mSelectedGroupUri;
    private boolean mSelectionToScreenRequested;
    private boolean mSelectionVisible;
    private Cursor mGroupListCursor;

	public GroupBrowseListPresenter(GroupBrowseListFragment view) {
		this.view = view;
		mContext = view.getActivity();
	}

	void init(Bundle savedInstanceState) {
		this.view.setupListView();
		view.updateSelectionVisible(mSelectionVisible);
		this.view.configureVerticalScrollbar();
		this.view.setAddAccountsVisibility(!ContactsUtils.areGroupWritableAccountsAvailable(mContext));
		restoreSelectedGroupUri(savedInstanceState);
		this.view.setSelectedGroup(mSelectedGroupUri);
	}
	
	void restoreSelectedGroupUri(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
            mSelectedGroupUri = savedInstanceState.getParcelable(EXTRA_KEY_GROUP_URI);
            if (mSelectedGroupUri != null) {
                // The selection may be out of screen, if rotated from portrait to landscape,
                // so ensure it's visible.
                mSelectionToScreenRequested = true;
            }
        }
	}

	void saveSelectedGroupUri(Bundle outState) {
		outState.putParcelable(EXTRA_KEY_GROUP_URI, mSelectedGroupUri);
	}

	public void setSelectedUri(Uri groupUri) {
		view.viewGroup(groupUri);
        mSelectionToScreenRequested = true;
	}

	public void onGroupListLoadFinished(Cursor data) {
		 mGroupListCursor = data;
         bindGroupList();
	}
	

    private void bindGroupList() {
        view.updateEmptyView();
        view.setAddAccountsVisibility(!ContactsUtils.areGroupWritableAccountsAvailable(mContext));
        if (mGroupListCursor == null) {
            return;
        }
        view.updateGroupListAdapterAdapter(mGroupListCursor);

        if (mSelectionToScreenRequested) {
            mSelectionToScreenRequested = false;
            requestSelectionToScreen();
        }

        mSelectedGroupUri = view.getSelectedGroup();
        if (mSelectionVisible && mSelectedGroupUri != null) {
            view.viewGroup(mSelectedGroupUri);
        }
    }
    
    private void requestSelectionToScreen() {
        if (!mSelectionVisible) {
            return; // If selection isn't visible we don't care.
        }
        int selectedPosition = view.getSelectedGroupPosition();
        if (selectedPosition != -1) {
            view.scrollGroupListToPosition(selectedPosition);
        }
    }

	public void setSelectionVisible(boolean flag) {
		 mSelectionVisible = flag;
	     view.updateSelectionVisible(flag);
	}

}
