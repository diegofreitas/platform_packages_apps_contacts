package com.android.contacts.group;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;

import com.android.contacts.GroupListLoader;
import com.android.contacts.common.ContactsUtils;

public class GroupBrowseListPresenter implements LoaderCallbacks<Cursor>{
	
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

    private void bindGroupList() {
        view.resetEmptyView(false);
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

	public void hideSoftKeyboard() {
        if (mContext == null) {
            return;
        }
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getListViewToken(), 0);
    }
	
	 @Override
     public CursorLoader onCreateLoader(int id, Bundle args) {
		 view.resetEmptyView(true);
         return new GroupListLoader(mContext);
     }

     @Override
     public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    	 mGroupListCursor = data;
         bindGroupList();
     }

     public void onLoaderReset(Loader<Cursor> loader) {
     }


}
