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
 * limitations under the License.
 */

package com.android.contacts.group;

import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.list.AutoScrollListView;
import com.android.contacts.group.GroupBrowseListAdapter.GroupListItemViewCache;

/**
 * Fragment to display the list of groups.
 */
public class GroupBrowseListFragment extends Fragment
        implements OnFocusChangeListener, OnTouchListener {

    /**
     * Action callbacks that can be sent by a group list.
     */
    public interface OnGroupBrowserActionListener  {

        /**
         * Opens the specified group for viewing.
         *
         * @param groupUri for the group that the user wishes to view.
         */
        void onViewGroupAction(Uri groupUri);

    }

    private static final int LOADER_GROUPS = 1;
    
    private View mRootView;
    private AutoScrollListView mListView;
    private TextView mEmptyView;
    private View mAddAccountsView;
    private View mAddAccountButton;

    private GroupBrowseListAdapter mAdapter;


    private int mVerticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT;

    private OnGroupBrowserActionListener mListener;

	private GroupBrowseListPresenter presenter;

    public GroupBrowseListFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
    	presenter = new GroupBrowseListPresenter(this);

        mRootView = inflater.inflate(R.layout.group_browse_list_fragment, null);
        mEmptyView = (TextView)mRootView.findViewById(R.id.empty);
        mAddAccountsView = mRootView.findViewById(R.id.add_accounts);
        mAddAccountButton = mRootView.findViewById(R.id.add_account_button);
        mListView = (AutoScrollListView) mRootView.findViewById(R.id.list);

        
        
        mAddAccountButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                intent.putExtra(Settings.EXTRA_AUTHORITIES,
                        new String[] { ContactsContract.AUTHORITY });
                startActivity(intent);
            }
        });
       
        presenter.init(savedInstanceState);
        return mRootView;
    }

	

	void setupListView() {
		mListView.setOnFocusChangeListener(this);
        mListView.setOnTouchListener(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                GroupListItemViewCache groupListItem = (GroupListItemViewCache) view.getTag();
                if (groupListItem != null) {
                    viewGroup(groupListItem.getUri());
                }
            }
        });
        mListView.setEmptyView(mEmptyView);
        mAdapter = new GroupBrowseListAdapter(this.getActivity());
	}

    public void setVerticalScrollbarPosition(int position) {
        mVerticalScrollbarPosition = position;
        if (mListView != null) {
            configureVerticalScrollbar();
        }
    }

    //The logic implemented here does not depends on the model.
    void configureVerticalScrollbar() {
        mListView.setVerticalScrollbarPosition(mVerticalScrollbarPosition);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        int leftPadding = 0;
        int rightPadding = 0;
        if (mVerticalScrollbarPosition == View.SCROLLBAR_POSITION_LEFT) {
            leftPadding = this.getActivity().getResources().getDimensionPixelOffset(
                    R.dimen.list_visible_scrollbar_padding);
        } else {
            rightPadding = this.getActivity().getResources().getDimensionPixelOffset(
                    R.dimen.list_visible_scrollbar_padding);
        }
        mListView.setPadding(leftPadding, mListView.getPaddingTop(),
                rightPadding, mListView.getPaddingBottom());
    }


    @Override
    public void onStart() {
        getLoaderManager().initLoader(LOADER_GROUPS, null, presenter);
        super.onStart();
    }
        

	void updateGroupListAdapterAdapter(Cursor mGroupListCursor) {
		mAdapter.setCursor(mGroupListCursor);
	}

	void resetEmptyView(boolean clear) {
		if(clear){
			mEmptyView.setText(null);
		} else {
			mEmptyView.setText(R.string.noGroups);
		}
	}

    public void setListener(OnGroupBrowserActionListener listener) {
        mListener = listener;
    }

    public void setSelectionVisible(boolean flag) {
    	presenter.setSelectionVisible(flag);
    }

	void updateSelectionVisible(boolean flag) {
		if (mAdapter != null) {
            mAdapter.setSelectionVisible(flag);
        }
	}

    void setSelectedGroup(Uri groupUri) {
        mAdapter.setSelectedGroup(groupUri);
        mListView.invalidateViews();
    }

    void viewGroup(Uri groupUri) {
        setSelectedGroup(groupUri);
        if (mListener != null) mListener.onViewGroupAction(groupUri);
    }

    public void setSelectedUri(Uri groupUri) {
    	presenter.setSelectedUri(groupUri);
    }

	void scrollGroupListToPosition(int selectedPosition) {
		mListView.requestPositionToScreen(selectedPosition,
		        true /* smooth scroll requested */);
	}

	int getSelectedGroupPosition() {
		return mAdapter.getSelectedGroupPosition();
	}

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (view == mListView && hasFocus) {
            presenter.hideSoftKeyboard();
        }
    }
    
    IBinder getListViewToken(){
	    return mListView.getWindowToken();
    }

    /**
     * Dismisses the soft keyboard when the list is touched.
     */
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view == mListView) {
            presenter.hideSoftKeyboard();
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        presenter.saveSelectedGroupUri(outState);
    }

    public void setAddAccountsVisibility(boolean visible) {
        if (mAddAccountsView != null) {
            mAddAccountsView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

	Uri getSelectedGroup() {
		return  mAdapter.getSelectedGroup();
	}
}
