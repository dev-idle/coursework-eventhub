package com.comp1786.coursework.eventhub.admin;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.ListPopupWindow;

import java.util.function.Consumer;

/**
 * Opens a menu under its button, only as wide as its widest item, unlike the overflow menu.
 */
public final class MoreMenu {

    private MoreMenu() {
    }

    /** Shows the items under the anchor and returns the menu, to close along with the screen. */
    public static ListPopupWindow show(Context context, View anchor, Item... items) {
        return show(context, anchor, Gravity.END, items);
    }

    /** Shows the items under the anchor, lined up with its start or end as the gravity says. */
    public static ListPopupWindow show(Context context, View anchor, int gravity, Item... items) {
        ItemAdapter adapter = new ItemAdapter(items);
        ListPopupWindow menu = new ListPopupWindow(
                context, null, androidx.appcompat.R.attr.actionOverflowMenuStyle);
        menu.setAdapter(adapter);
        menu.setAnchorView(anchor);
        menu.setDropDownGravity(gravity);
        menu.setContentWidth(adapter.widestItem(anchor));
        menu.setModal(true);
        menu.setOnItemClickListener((parent, view, position, id) -> {
            Item item = items[position];
            if (view instanceof CompoundButton) {
                // A switch keeps the menu open, so its change is seen.
                item.onClick.run();
                ((CompoundButton) view).setChecked(item.checked);
            } else {
                menu.dismiss();
                item.onClick.run();
            }
        });
        menu.show();
        return menu;
    }

    /** One menu item: a layout that sets its icon and colors, its title and its action. */
    public static final class Item {

        @LayoutRes
        private final int layoutRes;
        @StringRes
        private final int titleRes;
        // Set instead of the title resource for a title that is not a resource, such as a name.
        @Nullable
        private final CharSequence title;
        private final Runnable onClick;
        // Kept for an item with a switch or a tick, as the list may show the item again.
        private boolean checked;

        public Item(@LayoutRes int layoutRes, @StringRes int titleRes, Runnable onClick) {
            this.layoutRes = layoutRes;
            this.titleRes = titleRes;
            title = null;
            this.onClick = onClick;
        }

        /** Makes one of several choices, ticked if it is the chosen one. */
        public Item(@LayoutRes int layoutRes, CharSequence title, boolean checked,
                Runnable onClick) {
            this.layoutRes = layoutRes;
            titleRes = 0;
            this.title = title;
            this.checked = checked;
            this.onClick = onClick;
        }

        /** Makes an item whose layout is a switch, which passes each new state on. */
        public Item(@LayoutRes int layoutRes, @StringRes int titleRes, boolean checked,
                Consumer<Boolean> onCheckedChange) {
            this.layoutRes = layoutRes;
            this.titleRes = titleRes;
            title = null;
            this.checked = checked;
            onClick = () -> {
                this.checked = !this.checked;
                onCheckedChange.accept(this.checked);
            };
        }
    }

    /** Shows each item with its own layout, such as the red one for deleting. */
    private static final class ItemAdapter extends BaseAdapter {

        private final Item[] items;

        ItemAdapter(Item[] items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public Item getItem(int position) {
            return items[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        // Every item has its own layout, so a view is never reused for another item.
        @Override
        public int getViewTypeCount() {
            return items.length;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = convertView != null ? (TextView) convertView
                    : (TextView) LayoutInflater.from(parent.getContext())
                            .inflate(items[position].layoutRes, parent, false);
            Item item = items[position];
            if (item.title != null) {
                view.setText(item.title);
            } else {
                view.setText(item.titleRes);
            }
            if (view instanceof Checkable) {
                ((Checkable) view).setChecked(item.checked);
            }
            return view;
        }

        /** Returns the width of the widest item, which the menu takes. */
        int widestItem(View anchor) {
            int width = 0;
            for (int i = 0; i < items.length; i++) {
                View item = getView(i, null, (ViewGroup) anchor.getParent());
                // Measured before it is attached, so it needs a direction to place its start icon.
                item.setLayoutDirection(anchor.getLayoutDirection());
                item.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                width = Math.max(width, item.getMeasuredWidth());
            }
            return width;
        }
    }
}
