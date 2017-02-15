package com.cleveroad.tablelayout.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.cleveroad.library.LinkedTableAdapter;
import com.cleveroad.library.OnItemClickListener;
import com.cleveroad.library.OnItemLongClickListener;
import com.cleveroad.library.TableLayout;
import com.cleveroad.tablelayout.R;
import com.cleveroad.tablelayout.adapter.SampleLinkedTableAdapter;
import com.cleveroad.tablelayout.datasource.CsvFileDataSourceImpl;
import com.cleveroad.tablelayout.datasource.UpdateFileCallback;
import com.cleveroad.tablelayout.ui.dialogs.EditItemDialog;
import com.cleveroad.tablelayout.utils.PermissionHelper;
import com.cleveroad.tablelayout.utils.UriHelper;

import static android.content.Intent.EXTRA_MIME_TYPES;

public class TableLayoutFragment
        extends Fragment
        implements OnItemClickListener, OnItemLongClickListener, UpdateFileCallback {
    private static final String TAG = TableLayoutFragment.class.getSimpleName();
    private static final int REQUEST_CODE_PICK_CSV = 3;
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1132;
    private static final String EXTRA_CSV_FILE = "EXTRA_CSV_FILE";
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private Uri mCsvFile;
    private LinkedTableAdapter mTableAdapter;
    private CsvFileDataSourceImpl mCsvFileDataSource;
    private TableLayout mTableLayout;
    private Toolbar mToolbar;

    public static TableLayoutFragment newInstance(@NonNull String filename) {
        Bundle args = new Bundle();
        args.putString(EXTRA_CSV_FILE, filename);

        TableLayoutFragment fragment = new TableLayoutFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mCsvFile = Uri.parse(getArguments().getString(EXTRA_CSV_FILE));
        mCsvFileDataSource = new CsvFileDataSourceImpl(getContext(), mCsvFile);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_tab_layout, container, false);

        mTableLayout = (TableLayout) view.findViewById(R.id.tableLayout);
        mToolbar = (Toolbar) view.findViewById(R.id.toolbar);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });
        mToolbar.inflateMenu(R.menu.table_layout);
        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.actionSave) {
                    if (PermissionHelper.checkOrRequest(
                            getActivity(),
                            REQUEST_EXTERNAL_STORAGE,
                            PERMISSIONS_STORAGE)) {

                        mCsvFileDataSource.applyChanges(
                                getLoaderManager(),
                                mTableLayout.getLinkedAdapterRowsModifications(),
                                mTableLayout.getLinkedAdapterColumnsModifications(),
                                mTableLayout.isSolidRowHeader(),
                                TableLayoutFragment.this);

                    }
                }
                return true;
            }
        });

        initAdapter();
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_CSV && data != null) {
            Activity activity = getActivity();
            if (activity instanceof CsvPickerFragment.OnCsvFileSelectedListener) {
                ((CsvPickerFragment.OnCsvFileSelectedListener) activity).onCsvFileSelected(
                        UriHelper.getPath(getContext(), data.getData()));
            }
        } else if (requestCode == EditItemDialog.REQUEST_CODE_EDIT_SONG && resultCode == Activity.RESULT_OK && data != null) {
            int columnIndex = data.getIntExtra(EditItemDialog.EXTRA_COLUMN_NUMBER, 0);
            int rowIndex = data.getIntExtra(EditItemDialog.EXTRA_ROW_NUMBER, 0);
            String value = data.getStringExtra(EditItemDialog.EXTRA_VALUE);
            mCsvFileDataSource.updateItem(rowIndex, columnIndex, value);
            mTableAdapter.notifyItemChanged(rowIndex, columnIndex);
        }
    }

    //------------------------------------- adapter callbacks --------------------------------------
    @Override
    public void onItemClick(int row, int column) {
        EditItemDialog.newInstance(
                row,
                column,
                mCsvFileDataSource.getColumnHeaderData(column),
                mCsvFileDataSource.getItemData(row, column))
                .show(getChildFragmentManager(), EditItemDialog.class.getSimpleName());
    }

    @Override
    public void onRowHeaderClick(int row) {
        EditItemDialog.newInstance(
                row,
                0,
                mCsvFileDataSource.getColumnHeaderData(0),
                mCsvFileDataSource.getItemData(row, 0))
                .show(getChildFragmentManager(), EditItemDialog.class.getSimpleName());
    }

    @Override
    public void onColumnHeaderClick(int column) {
        EditItemDialog.newInstance(
                0,
                column,
                mCsvFileDataSource.getColumnHeaderData(column),
                mCsvFileDataSource.getItemData(0, column))
                .show(getChildFragmentManager(), EditItemDialog.class.getSimpleName());
    }

    @Override
    public void onLeftTopHeaderClick() {
    }

    @Override
    public void onItemLongClick(int row, int column) {

    }


    @Override
    public void onLeftTopHeaderLongClick() {
    }

    @Override
    public void onFileUpdated(final String filePath, boolean isSuccess) {
        View view = getView();
        if (view == null) {
            return;
        }

        if (isSuccess) { //if data source have been changed
            initAdapter();
            mTableAdapter.notifyDataSetChanged();
            Log.e("Done", "File path = " + filePath);
            String text = getString(R.string.changes_saved) + " path = " + filePath;
            Snackbar.make(view, text, Snackbar.LENGTH_LONG)
                    .setAction("Open", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pickCsvFile(Uri.parse(filePath));
                        }
                    })
                    .show();
        } else {
            Snackbar.make(view, R.string.unexpected_error, Snackbar.LENGTH_LONG).show();
        }
    }

    private void initAdapter() {

        mTableAdapter = new SampleLinkedTableAdapter(getContext(), mCsvFileDataSource);
        mTableAdapter.setOnItemClickListener(this);
        mTableAdapter.setOnItemLongClickListener(this);

        mTableLayout.setAdapter(mTableAdapter);
    }

    private void pickCsvFile(Uri csvFileUri) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setDataAndType(csvFileUri, "*/*");
        String[] mimetypes = {"text/comma-separated-values", "text/csv"};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent.putExtra(EXTRA_MIME_TYPES, mimetypes);
        }
        startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_file)), REQUEST_CODE_PICK_CSV);
    }


}
