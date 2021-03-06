/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * <p/>
 * Contributors:
 * Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.List;

import greendroid.widget.QuickActionGrid;
import greendroid.widget.QuickActionWidget;
import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.adapter.AccountListAdapter2;
import ru.orangesoftware.financisto.blotter.BlotterFilter;
import ru.orangesoftware.financisto.blotter.TotalCalculationTask;
import ru.orangesoftware.financisto.bus.GreenRobotBus_;
import ru.orangesoftware.financisto.bus.SwitchToMenuTabEvent;
import ru.orangesoftware.financisto.dialog.AccountInfoDialog;
import ru.orangesoftware.financisto.filter.Criteria;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.Total;
import ru.orangesoftware.financisto.utils.MenuItemInfo;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.view.NodeInflater;

import static ru.orangesoftware.financisto.utils.MyPreferences.isQuickMenuEnabledForAccount;

public class AccountListActivity extends AbstractListActivity {

    private static final int NEW_ACCOUNT_REQUEST = 1;

    public static final int EDIT_ACCOUNT_REQUEST = 2;
    private static final int VIEW_ACCOUNT_REQUEST = 3;
    private static final int PURGE_ACCOUNT_REQUEST = 4;

    private static final int MENU_UPDATE_BALANCE = MENU_ADD + 1;
    private static final int MENU_CLOSE_OPEN_ACCOUNT = MENU_ADD + 2;
    private static final int MENU_PURGE_ACCOUNT = MENU_ADD + 3;

    private QuickActionWidget accountActionGrid;

    public AccountListActivity() {
        super(R.layout.account_list);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupMenuButton();
        calculateTotals();
        prepareAccountActionGrid();
        integrityCheck();
    }

    private void setupMenuButton() {
        final ImageButton bMenu = (ImageButton) findViewById(R.id.bMenu);
        if (MyPreferences.isShowMenuButtonOnAccountsScreen(this)) {
            bMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu popupMenu = new PopupMenu(AccountListActivity.this, bMenu);
                    MenuInflater inflater = getMenuInflater();
                    inflater.inflate(R.menu.account_list_menu, popupMenu.getMenu());
                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            handlePopupMenu(item.getItemId());
                            return true;
                        }
                    });
                    popupMenu.show();
                }
            });
        } else {
            bMenu.setVisibility(View.GONE);
        }
    }

    private void handlePopupMenu(int id) {
        switch (id) {
            case R.id.backup:
                MenuListItem.MENU_BACKUP.call(this);
                break;
            case R.id.go_to_menu:
                GreenRobotBus_.getInstance_(this).post(new SwitchToMenuTabEvent());
                break;
        }
    }

    protected void prepareAccountActionGrid() {
        accountActionGrid = new QuickActionGrid(this);
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.gd_action_bar_info, R.string.info));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.gd_action_bar_list, R.string.blotter));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.gd_action_bar_edit, R.string.edit));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_input_add, R.string.transaction));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_input_transfer, R.string.transfer));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_bar_mark, R.string.balance));
        accountActionGrid.setOnQuickActionClickListener(accountActionListener);
    }

    private QuickActionWidget.OnQuickActionClickListener accountActionListener = new QuickActionWidget.OnQuickActionClickListener() {
        public void onQuickActionClicked(QuickActionWidget widget, int position) {
            switch (position) {
                case 0:
                    showAccountInfo(selectedId);
                    break;
                case 1:
                    showAccountTransactions(selectedId);
                    break;
                case 2:
                    editAccount(selectedId);
                    break;
                case 3:
                    addTransaction(selectedId, TransactionActivity.class);
                    break;
                case 4:
                    addTransaction(selectedId, TransferActivity.class);
                    break;
                case 5:
                    updateAccountBalance(selectedId);
                    break;
            }
        }

    };

    private void addTransaction(long accountId, Class<? extends AbstractTransactionActivity> clazz) {
        Intent intent = new Intent(this, clazz);
        intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId);
        startActivityForResult(intent, VIEW_ACCOUNT_REQUEST);
    }

    @Override
    public void recreateCursor() {
        super.recreateCursor();
        calculateTotals();
    }

    private AccountTotalsCalculationTask totalCalculationTask;

    private void calculateTotals() {
        if (totalCalculationTask != null) {
            totalCalculationTask.stop();
            totalCalculationTask.cancel(true);
        }
        TextView totalText = (TextView) findViewById(R.id.total);
        totalText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTotals();
            }
        });
        totalCalculationTask = new AccountTotalsCalculationTask(this, totalText);
        totalCalculationTask.execute();
    }

    private void showTotals() {
        Intent intent = new Intent(this, AccountListTotalsDetailsActivity.class);
        startActivityForResult(intent, -1);
    }

    public class AccountTotalsCalculationTask extends TotalCalculationTask {

        public AccountTotalsCalculationTask(Context context, TextView totalText) {
            super(context, totalText);
        }

        @Override
        public Total getTotalInHomeCurrency() {
            return db.getAccountsTotalInHomeCurrency();
        }

        @Override
        public Total[] getTotals() {
            return new Total[0];
        }

    }

    @Override
    protected ListAdapter createAdapter(Cursor cursor) {
        return new AccountListAdapter2(this, cursor);
    }

    @Override
    protected Cursor createCursor() {
        if (MyPreferences.isHideClosedAccounts(this)) {
            return db.getAllActiveAccounts();
        } else {
            return db.getAllAccounts();
        }
    }

    protected List<MenuItemInfo> createContextMenus(long id) {
        List<MenuItemInfo> menus = super.createContextMenus(id);
        Account a = db.getAccount(id);
        if (a != null && a.isActive) {
            menus.add(new MenuItemInfo(MENU_UPDATE_BALANCE, R.string.update_balance));
            menus.add(new MenuItemInfo(MENU_PURGE_ACCOUNT, R.string.delete_old_transactions));
            menus.add(new MenuItemInfo(MENU_CLOSE_OPEN_ACCOUNT, R.string.close_account));
        } else {
            menus.add(new MenuItemInfo(MENU_CLOSE_OPEN_ACCOUNT, R.string.reopen_account));
        }
        return menus;
    }

    @Override
    public boolean onPopupItemSelected(int itemId, View view, int position, long id) {
        if (super.onPopupItemSelected(itemId, view, position, id)) return true;
        switch (itemId) {
            case MENU_UPDATE_BALANCE: {
                updateAccountBalance(id);
                return true;
            }
            case MENU_PURGE_ACCOUNT: {
                Intent intent = new Intent(this, PurgeAccountActivity.class);
                intent.putExtra(PurgeAccountActivity.ACCOUNT_ID, id);
                startActivityForResult(intent, PURGE_ACCOUNT_REQUEST);
                return true;
            }
            case MENU_CLOSE_OPEN_ACCOUNT: {
                Account a = db.getAccount(id);
                a.isActive = !a.isActive;
                db.saveAccount(a);
                recreateCursor();
                return true;
            }
        }
        return false;
    }

    private boolean updateAccountBalance(long id) {
        Account a = db.getAccount(id);
        if (a != null) {
            Intent intent = new Intent(this, TransactionActivity.class);
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, a.id);
            intent.putExtra(TransactionActivity.CURRENT_BALANCE_EXTRA, a.totalAmount);
            startActivityForResult(intent, 0);
            return true;
        }
        return false;
    }

    @Override
    protected void addItem() {
        Intent intent = new Intent(AccountListActivity.this, AccountActivity.class);
        startActivityForResult(intent, NEW_ACCOUNT_REQUEST);
    }

    @Override
    protected void deleteItem(View v, int position, final long id) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_account_confirm)
                .setPositiveButton(R.string.yes, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        db.deleteAccount(id);
                        recreateCursor();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void editItem(View v, int position, long id) {
        editAccount(id);
    }

    private void editAccount(long id) {
        Intent intent = new Intent(AccountListActivity.this, AccountActivity.class);
        intent.putExtra(AccountActivity.ACCOUNT_ID_EXTRA, id);
        startActivityForResult(intent, EDIT_ACCOUNT_REQUEST);
    }

    private long selectedId = -1;

    private void showAccountInfo(long id) {
        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        NodeInflater inflater = new NodeInflater(layoutInflater);
        AccountInfoDialog accountInfoDialog = new AccountInfoDialog(this, id, db, inflater);
        accountInfoDialog.show();
    }

    @Override
    protected void onItemClick(View v, int position, long id) {
        if (isQuickMenuEnabledForAccount(this)) {
            selectedId = id;
            accountActionGrid.show(v);
        } else {
            showAccountTransactions(id);
        }
    }

    @Override
    protected void viewItem(View v, int position, long id) {
        showAccountTransactions(id);
    }

    private void showAccountTransactions(long id) {
        Account account = db.getAccount(id);
        if (account != null) {
            Intent intent = new Intent(AccountListActivity.this, BlotterActivity.class);
            Criteria.eq(BlotterFilter.FROM_ACCOUNT_ID, String.valueOf(id))
                    .toIntent(account.title, intent);
            intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, true);
            startActivityForResult(intent, VIEW_ACCOUNT_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VIEW_ACCOUNT_REQUEST || requestCode == PURGE_ACCOUNT_REQUEST) {
            recreateCursor();
        }
    }

}
